package com.callrecorder.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Arama bittikten sonra numara ve yönü arama geçmişinden alır (CLAUDE.md §2.6, §6.5).
 * Bekleme (Thread.sleep) içerdiği için ana thread'den çağrılmaz.
 */
class CallLogResolver(private val context: Context) {

    data class Entry(val number: String?, val direction: String)

    /** [since] (epoch ms) sonrasındaki en yeni kaydı arar; sistem geç yazabildiği için 3 kez dener. */
    fun resolve(since: Long): Entry? {
        repeat(ATTEMPTS) { attempt ->
            query(since)?.let { return it }
            if (attempt < ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MS)
        }
        Log.w(TAG, "Arama geçmişinde eşleşen kayıt bulunamadı")
        return null
    }

    private fun query(since: Long): Entry? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_CALL_LOG izni yok, numara alınamıyor")
            return null
        }
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(since.toString()),
                "${CallLog.Calls.DATE} DESC", // LIMIT eklenmez: Android 11+'da sağlayıcı reddedebilir
            )?.use { c ->
                if (!c.moveToFirst()) return null
                val number = c.getString(0)?.takeIf { it.isNotBlank() }
                val direction = when (c.getInt(1)) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    else -> "unknown"
                }
                Entry(number, direction)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Arama geçmişi okunamadı: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = CallRecorderModule.TAG
        private const val ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1_000L
    }
}