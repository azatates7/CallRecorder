package com.frontend.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that owns the call lifecycle + the actual MediaRecorder instance.
 *
 * Recording source strategy:
 *  We deliberately try VOICE_CALL first, then VOICE_COMMUNICATION, then MIC. VOICE_CALL /
 *  VOICE_COMMUNICATION tap the call audio path itself, not the physical microphone signal, so
 *  the resulting recording does not depend on whether the user is on the earpiece, the loud
 *  speaker, a wired headset or Bluetooth - the *output* route can change freely without
 *  affecting what gets captured. Only if neither call-audio source is available on a given
 *  device/ROM do we fall back to MIC, in which case the recording quality can vary with the
 *  chosen audio route (this fallback is a device limitation, not something the app controls).
 */
class CallRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var currentUri: Uri? = null
    private var currentPfd: ParcelFileDescriptor? = null
    private var currentFile: File? = null
    private var isRecording = false
    private var callDirection: String = "unknown"
    private var callNumber: String = "unknown"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Call recorder is watching for calls"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CALL_RINGING -> {
                callDirection = "incoming"
                callNumber = intent.getStringExtra(EXTRA_NUMBER) ?: "unknown"
            }
            ACTION_CALL_OFFHOOK -> {
                if (callDirection == "unknown") {
                    // OFFHOOK without a preceding RINGING means this is an outgoing call.
                    callDirection = "outgoing"
                    callNumber = intent.getStringExtra(EXTRA_NUMBER) ?: callNumber
                }
                startRecording()
            }
            ACTION_CALL_IDLE -> {
                stopRecordingAndReset()
            }
            ACTION_STOP_SERVICE -> {
                stopRecordingAndReset()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopRecordingAndReset()
        super.onDestroy()
    }

    // ---- Recording -----------------------------------------------------

    private fun startRecording() {
        if (isRecording) return

        val (recorder, target) = createRecorderWithBestSource() ?: run {
            Log.e(TAG, "Could not open any output target for the recording")
            return
        }

        try {
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            isRecording = true
            updateNotification("Recording $callDirection call ($callNumber)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            releaseRecorder()
            discardTarget()
        }
    }

    /**
     * Tries each audio source in order until one both constructs AND prepares successfully.
     * Some OEMs let you construct a MediaRecorder with VOICE_CALL but throw only once you call
     * prepare()/start(), so each candidate is fully validated before being accepted.
     */
    private fun createRecorderWithBestSource(): Pair<MediaRecorder, Any>? {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
        )

        for (source in sources) {
            val target = openOutputTarget() ?: continue
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            try {
                recorder.setAudioSource(source)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128000)
                recorder.setAudioSamplingRate(44100)
                when (target) {
                    is ParcelFileDescriptor -> recorder.setOutputFile(target.fileDescriptor)
                    is File -> recorder.setOutputFile(target.absolutePath)
                }
                recorder.prepare()
                Log.i(TAG, "Recording with audio source $source")
                return Pair(recorder, target)
            } catch (e: Exception) {
                Log.w(TAG, "Audio source $source unavailable, trying next", e)
                recorder.release()
                discardTargetHandle(target)
            }
        }
        return null
    }

    private fun stopRecordingAndReset() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "stop() threw, recording may be too short", e)
            }
        }
        releaseRecorder()
        finalizeTarget()
        isRecording = false
        callDirection = "unknown"
        callNumber = "unknown"
        updateNotification("Call recorder is watching for calls")
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
    }

    // ---- Output target (MediaStore on Q+, legacy File below) -----------

    private fun openOutputTarget(): Any? {
        val fileName = buildFileName()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$RECORDINGS_FOLDER")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            val pfd = contentResolver.openFileDescriptor(uri, "w") ?: return null
            currentUri = uri
            currentPfd = pfd
            pfd
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                RECORDINGS_FOLDER,
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            currentFile = file
            file
        }
    }

    private fun discardTargetHandle(target: Any) {
        when (target) {
            is ParcelFileDescriptor -> {
                try { target.close() } catch (_: Exception) {}
                currentUri?.let { contentResolver.delete(it, null, null) }
                currentUri = null
                currentPfd = null
            }
            is File -> {
                target.delete()
                currentFile = null
            }
        }
    }

    private fun discardTarget() {
        currentPfd?.let { discardTargetHandle(it) }
        currentFile?.let { discardTargetHandle(it) }
    }

    private fun finalizeTarget() {
        currentPfd?.let {
            try { it.close() } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            currentUri?.let { uri ->
                val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                try { contentResolver.update(uri, values, null, null) } catch (_: Exception) {}
            }
        }
        currentUri = null
        currentPfd = null
        currentFile = null
    }

    private fun buildFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeNumber = callNumber.replace(Regex("[^0-9+]"), "").ifEmpty { "unknown" }
        return "${callDirection}_${safeNumber}_$stamp.m4a"
    }

    // ---- Notification ----------------------------------------------------

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Call Recorder", NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, CallRecordingService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recorder")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "CallRecordingService"
        private const val CHANNEL_ID = "call_recorder_channel"
        private const val NOTIFICATION_ID = 42
        const val RECORDINGS_FOLDER = "CallRecordings"

        const val ACTION_CALL_RINGING = "com.frontend.callrecorder.action.RINGING"
        const val ACTION_CALL_OFFHOOK = "com.frontend.callrecorder.action.OFFHOOK"
        const val ACTION_CALL_IDLE = "com.frontend.callrecorder.action.IDLE"
        const val ACTION_STOP_SERVICE = "com.frontend.callrecorder.action.STOP"
        const val EXTRA_NUMBER = "extra_number"

        fun start(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}