package com.callrecorder.recorder

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * MediaRecorder sarmalayıcı + ses kaynağı fallback zinciri (CLAUDE.md §6.4).
 * Ana thread'den çağrılır.
 */
class CallRecorder(private val context: Context) {

    data class Result(val file: File, val source: String, val durationMs: Long)

    private val handler = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var sourceIndex = 0
    private var activeSource: String? = null
    private var startedAtElapsed = 0L
    private var silenceCheck: Runnable? = null

    /** Kaydı başlatır; hiçbir kaynak çalışmazsa false döner. */
    fun start(output: File): Boolean {
        file = output
        return startFrom(0)
    }

    /** Kaydı durdurur. Kayıt yoksa veya dosya bozuksa null döner (dosya silinir). */
    fun stop(): Result? {
        silenceCheck?.let { handler.removeCallbacks(it) }
        silenceCheck = null
        val r = recorder ?: return null
        val f = file ?: return null
        val source = activeSource ?: return null
        val duration = SystemClock.elapsedRealtime() - startedAtElapsed
        recorder = null
        return try {
            r.stop()
            Result(f, source, duration)
        } catch (e: RuntimeException) {
            // start()'tan hemen sonra stop() çağrılırsa MediaRecorder exception atar
            Log.w(TAG, "Kayıt düzgün kapatılamadı, dosya siliniyor: ${e.message}")
            f.delete()
            null
        } finally {
            r.release()
        }
    }

    private fun startFrom(index: Int): Boolean {
        val output = file ?: return false
        for (i in index until SOURCES.size) {
            val (name, source) = SOURCES[i]
            val r = newMediaRecorder()
            try {
                r.setAudioSource(source)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioChannels(1)
                r.setAudioSamplingRate(SAMPLE_RATE)
                r.setAudioEncodingBitRate(BIT_RATE)
                r.setOutputFile(output.absolutePath)
                r.prepare()
                r.start()
                recorder = r
                sourceIndex = i
                activeSource = name
                startedAtElapsed = SystemClock.elapsedRealtime()
                Log.i(TAG, "Kayıt başladı, kaynak=$name")
                // Son kaynakta sessizlik kontrolü yapılmaz: sessiz de olsa kayıt alınır.
                if (i < SOURCES.lastIndex) scheduleSilenceCheck(r)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Kaynak $name kullanılamadı: ${e.message}")
                r.release()
                output.delete()
            }
        }
        Log.e(TAG, "Hiçbir ses kaynağı başlatılamadı")
        return false
    }

    /** Bazı cihazlarda kaynak exception atmadan sessiz kayıt üretir: ilk 2 sn genliği kontrol et. */
    private fun scheduleSilenceCheck(r: MediaRecorder) {
        r.maxAmplitude // ilk çağrı her zaman 0 döner; sayacı sıfırlamak için
        var checks = 0
        val check = object : Runnable {
            override fun run() {
                if (recorder !== r) return
                if (r.maxAmplitude > 0) return
                if (++checks < SILENCE_CHECKS) {
                    handler.postDelayed(this, SILENCE_INTERVAL_MS)
                    return
                }
                Log.w(TAG, "Kaynak $activeSource sessiz, sonraki kaynağa geçiliyor")
                recorder = null
                try { r.stop() } catch (e: RuntimeException) { Log.w(TAG, "Sessiz kayıt durdurulamadı: ${e.message}") }
                r.release()
                file?.delete()
                startFrom(sourceIndex + 1)
            }
        }
        silenceCheck = check
        handler.postDelayed(check, SILENCE_INTERVAL_MS)
    }

    private fun newMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    companion object {
        private const val TAG = CallRecorderModule.TAG
        private const val SAMPLE_RATE = 16_000
        private const val BIT_RATE = 64_000
        private const val SILENCE_CHECKS = 8
        private const val SILENCE_INTERVAL_MS = 250L

        private val SOURCES = listOf(
            "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL,
            "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "MIC" to MediaRecorder.AudioSource.MIC,
        )

        /** Kayıt başladığı andaki ses yolu (CLAUDE.md §2.2). Arama içinde değişirse yansımaz. */
        fun detectAudioRoute(context: Context): String {
            val am = context.getSystemService(AudioManager::class.java) ?: return "unknown"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return when (am.communicationDevice?.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> "wired"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> "bluetooth"
                    else -> "unknown"
                }
            }
            @Suppress("DEPRECATION")
            return when {
                am.isBluetoothScoOn -> "bluetooth"
                am.isSpeakerphoneOn -> "speaker"
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                } -> "wired"
                else -> "earpiece"
            }
        }
    }
}