package com.frontend.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.telephony.TelephonyManager

/**
 * Listens for android.intent.action.PHONE_STATE broadcasts (RINGING / OFFHOOK / IDLE) and
 * forwards them to CallRecordingService. Also fires on BOOT_COMPLETED so an already-enabled
 * recorder keeps working after a reboot without the user having to reopen the app.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (isRecorderEnabled(context)) {
                CallRecordingService.start(context)
            }
            return
        }

        if (intent.action != "android.intent.action.PHONE_STATE") return
        if (!isRecorderEnabled(context)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        // EXTRA_INCOMING_NUMBER is only populated pre-Android 10 for receivers holding
        // READ_CALL_LOG; on newer versions we simply record without tagging the number.
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val serviceIntent = Intent(context, CallRecordingService::class.java)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                serviceIntent.action = CallRecordingService.ACTION_CALL_RINGING
                serviceIntent.putExtra(CallRecordingService.EXTRA_NUMBER, number)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                serviceIntent.action = CallRecordingService.ACTION_CALL_OFFHOOK
                serviceIntent.putExtra(CallRecordingService.EXTRA_NUMBER, number)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                serviceIntent.action = CallRecordingService.ACTION_CALL_IDLE
            }
            else -> return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val PREFS_NAME = "call_recorder_prefs"
        private const val KEY_ENABLED = "recorder_enabled"

        fun isRecorderEnabled(context: Context): Boolean {
            val prefs: SharedPreferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ENABLED, false)
        }

        fun setRecorderEnabled(context: Context, enabled: Boolean) {
            val prefs: SharedPreferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }
}