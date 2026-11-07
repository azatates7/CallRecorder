package com.callrecorder.recorder

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * Olay işlemeyen, ekran okumayan erişilebilirlik servisi (CLAUDE.md §2.3).
 * Tek amacı Android 10+'da arama sırasında mikrofonun uygulamaya kapatılmasını önlemek.
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Erişilebilirlik servisi bağlandı")
        // Sistem bu servisi açılışta da bağlar: izleme açık bırakılmışsa servisi geri getirmeyi dene.
        val prefs = Prefs(this)
        if (prefs.monitoringEnabled && !CallMonitorService.isRunning) {
            try {
                ContextCompat.startForegroundService(this, Intent(this, CallMonitorService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Erişilebilirlik servisinden izleme başlatılamadı: ${e.message}")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = CallRecorderModule.TAG

        /** Kullanıcı bu servisi Ayarlar > Erişilebilirlik'ten açmış mı. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val self = ComponentName(context, KeepAliveAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }
    }
}