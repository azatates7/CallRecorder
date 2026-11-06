package com.callrecorder.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Kalıcı bildirimle çalışan ön plan servisi; arama durumunu dinler (CLAUDE.md §2.4, §6).
 * OFFHOOK'ta kaydı başlatır, IDLE'da durdurup meta veriyi yazar.
 */
class CallMonitorService : Service() {

    private var telephony: TelephonyManager? = null
    private var callback: Any? = null // API 31+: TelephonyCallback, altı: PhoneStateListener
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var direction = "unknown"
    private var ringingAt = 0L

    /** Süren aramanın kayıt bilgisi; arama yokken null. */
    private data class Session(
        val id: String,
        val startedAt: Long,
        val lookupSince: Long,
        val direction: String,
        val audioRoute: String,
    )

    private var recorder: CallRecorder? = null
    private var session: Session? = null
    private val repository by lazy { RecordingRepository(this) }
    private val callLogResolver by lazy { CallLogResolver(this) }
    // Arama geçmişi beklemesi ve dosya yazma ana thread'i bloklamasın diye
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startInForeground()
        } catch (e: Exception) {
            // Örn. Android 12+ arka plandan başlatma yasağı veya eksik RECORD_AUDIO izni
            Log.e(TAG, "Ön plan servisi başlatılamadı", e)
            stopSelf()
            return
        }
        telephony = getSystemService(TelephonyManager::class.java)
        registerCallStateListener()
        isRunning = true
        Log.i(TAG, "İzleme servisi başladı")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        stopRecording() // servis arama sürerken kapatılırsa kaydı kaybetme
        unregisterCallStateListener()
        io.shutdown() // kuyruktaki meta veri yazımı tamamlanır
        isRunning = false
        Log.i(TAG, "İzleme servisi durdu")
        super.onDestroy()
    }

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Arama izleme", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Arama kaydedici etkin")
            .setContentText("Aramalar izleniyor")
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun registerCallStateListener() {
        val tm = telephony ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onCallState(state)
                }
                tm.registerTelephonyCallback(mainExecutor, cb)
                callback = cb
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
                }
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                callback = listener
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Arama durumu dinlenemiyor: READ_PHONE_STATE izni yok", e)
        }
    }

    private fun unregisterCallStateListener() {
        val tm = telephony ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (callback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            (callback as? PhoneStateListener)?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        callback = null
    }

    /** Durum geçişleri (CLAUDE.md §6). */
    private fun onCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                direction = "incoming"
                ringingAt = System.currentTimeMillis()
                Log.i(TAG, "RINGING — gelen arama")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.CALL_STATE_IDLE) direction = "outgoing"
                Log.i(TAG, "OFFHOOK — arama başladı, yön=$direction")
                startRecording()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                when (lastState) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        Log.i(TAG, "IDLE — arama bitti, yön=$direction")
                        stopRecording()
                    }
                    TelephonyManager.CALL_STATE_RINGING -> Log.i(TAG, "IDLE — cevapsız/reddedilen arama, kayıt yok")
                }
                direction = "unknown"
                ringingAt = 0L
            }
        }
        lastState = state
    }

    private fun startRecording() {
        // Bekleyen ikinci arama vb. durumlarda ikinci OFFHOOK yeni kayıt açmaz (CLAUDE.md §6.7)
        if (recorder != null) return
        val now = System.currentTimeMillis()
        val id = repository.newId(now)
        val rec = CallRecorder(this)
        if (!rec.start(repository.audioFile(id))) return
        recorder = rec
        // Arama geçmişi gelen aramayı çalma anıyla, gideni arama başlangıcıyla kaydeder
        val reference = if (direction == "incoming" && ringingAt > 0) ringingAt else now
        session = Session(
            id = id,
            startedAt = now,
            lookupSince = reference - LOOKUP_TOLERANCE_MS,
            direction = direction,
            audioRoute = CallRecorder.detectAudioRoute(this),
        )
    }

    private fun stopRecording() {
        val rec = recorder ?: return
        val s = session ?: return
        recorder = null
        session = null
        val result = rec.stop() ?: return
        if (result.durationMs < MIN_DURATION_MS) {
            result.file.delete()
            Log.i(TAG, "Kayıt 1 sn'den kısa, kaydedilmedi")
            return
        }
        io.execute {
            val entry = callLogResolver.resolve(s.lookupSince)
            val meta = RecordingMeta(
                id = s.id,
                number = entry?.number,
                direction = entry?.direction?.takeIf { it != "unknown" } ?: s.direction,
                startedAt = s.startedAt,
                durationMs = result.durationMs,
                sizeBytes = result.file.length(),
                audioSource = result.source,
                audioRoute = s.audioRoute,
            )
            try {
                repository.saveMetadata(meta)
                Log.i(TAG, "Kayıt kaydedildi: ${s.id}.m4a, süre=${meta.durationMs / 1000} sn, kaynak=${meta.audioSource}, yön=${meta.direction}")
                if (isDebuggable()) Log.d(TAG, "Numara: ${meta.number}")
            } catch (e: Exception) {
                Log.e(TAG, "Meta veri yazılamadı", e)
            }
        }
    }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        private const val TAG = CallRecorderModule.TAG
        private const val CHANNEL_ID = "call_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_DURATION_MS = 1_000L
        private const val LOOKUP_TOLERANCE_MS = 5_000L

        @Volatile
        var isRunning = false
            private set
    }
}adb shell am force-stop com.callrecorder