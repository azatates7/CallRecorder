package com.callrecorder.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Kalıcı bildirimle çalışan ön plan servisi; arama durumunu dinler (CLAUDE.md §2.4, §6).
 * Faz 1a: yalnızca durum geçişlerini loglar, kayıt yapmaz.
 */
class CallMonitorService : Service() {

    private var telephony: TelephonyManager? = null
    private var callback: Any? = null // API 31+: TelephonyCallback, altı: PhoneStateListener
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var direction = "unknown"

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
        unregisterCallStateListener()
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

    /** Durum geçişleri (CLAUDE.md §6). Kayıt Faz 1b'de OFFHOOK/IDLE noktalarına bağlanacak. */
    private fun onCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                direction = "incoming"
                Log.i(TAG, "RINGING — gelen arama")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.CALL_STATE_IDLE) direction = "outgoing"
                Log.i(TAG, "OFFHOOK — arama başladı, yön=$direction")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                when (lastState) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> Log.i(TAG, "IDLE — arama bitti, yön=$direction")
                    TelephonyManager.CALL_STATE_RINGING -> Log.i(TAG, "IDLE — cevapsız/reddedilen arama, kayıt yok")
                }
                direction = "unknown"
            }
        }
        lastState = state
    }

    companion object {
        private const val TAG = CallRecorderModule.TAG
        private const val CHANNEL_ID = "call_monitor"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning = false
            private set
    }
}