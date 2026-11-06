package com.callrecorder.recorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class CallRecorderModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = NAME

    /** Faz 0: JS <-> Kotlin köprü testi. */
    @ReactMethod
    fun ping(promise: Promise) {
        promise.resolve("pong")
    }

    /** İzleme servisini başlatır. Uygulama ön plandayken çağrılmalıdır (CLAUDE.md §2.4). */
    @ReactMethod
    fun startMonitoring(promise: Promise) {
        try {
            val ctx = reactApplicationContext
            val missing = REQUIRED_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                promise.reject(
                    "E_PERMISSION",
                    "Eksik izinler: " + missing.joinToString { it.substringAfterLast('.') },
                )
                return
            }
            ContextCompat.startForegroundService(ctx, Intent(ctx, CallMonitorService::class.java))
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("E_SERVICE", e.message, e)
        }
    }

    @ReactMethod
    fun stopMonitoring(promise: Promise) {
        try {
            val ctx = reactApplicationContext
            ctx.stopService(Intent(ctx, CallMonitorService::class.java))
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("E_SERVICE", e.message, e)
        }
    }

    /** Geçici (Faz 1a): Faz 3'te getStatus().monitoring alanına taşınacak. */
    @ReactMethod
    fun isMonitoring(promise: Promise) {
        promise.resolve(CallMonitorService.isRunning)
    }

    companion object {
        const val NAME = "CallRecorder"
        const val TAG = "CallRecorder"

        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.RECORD_AUDIO,     // mikrofon tipli ön plan servisi için şart
            Manifest.permission.READ_PHONE_STATE, // arama durumu dinleme
        )
    }
}