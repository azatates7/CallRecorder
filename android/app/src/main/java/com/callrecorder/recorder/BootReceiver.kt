package com.callrecorder.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Yeniden başlatmadan sonra izleme açık bırakılmışsa servisi başlatmayı dener (CLAUDE.md §2.5).
 * Yeni Android sürümleri açılışta mikrofon tipli servisi engeller; o zaman kullanıcıya bildirim gösterilir.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).monitoringEnabled) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, CallMonitorService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Açılışta izleme başlatılamadı: ${e.message}")
            StartupNotice.show(context)
        }
    }

    companion object {
        private const val TAG = CallRecorderModule.TAG
    }
}

/** "Kayıt için uygulamayı bir kez açın" bildirimi. Servis başarıyla başlayınca kaldırılır. */
object StartupNotice {
    private const val CHANNEL_ID = "alerts"
    private const val NOTIFICATION_ID = 1002

    fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Uyarılar", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Arama kaydı kapalı")
            .setContentText("Kayda devam etmek için uygulamayı bir kez açın.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(CallRecorderModule.TAG, "Bildirim izni yok, uyarı gösterilemedi")
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}