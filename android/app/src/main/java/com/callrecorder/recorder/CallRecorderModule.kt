package com.callrecorder.recorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap

class CallRecorderModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val repository by lazy { RecordingRepository(reactApplicationContext) }
    private val prefs by lazy { Prefs(reactApplicationContext) }
    private val player by lazy { RecordingPlayer(::emit) }
    private val savedListener: (RecordingMeta) -> Unit = { meta -> emit("recordingSaved", toMap(meta)) }

    override fun getName(): String = NAME

    override fun initialize() {
        super.initialize()
        RecordingEvents.onSaved = savedListener
    }

    override fun invalidate() {
        if (RecordingEvents.onSaved === savedListener) RecordingEvents.onSaved = null
        player.stop()
        super.invalidate()
    }

    /** Faz 0: JS <-> Kotlin köprü testi. */
    @ReactMethod
    fun ping(promise: Promise) {
        promise.resolve("pong")
    }

    // ---- Servis ----

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
            prefs.monitoringEnabled = true
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("E_SERVICE", e.message, e)
        }
    }

    @ReactMethod
    fun stopMonitoring(promise: Promise) {
        try {
            val ctx = reactApplicationContext
            prefs.monitoringEnabled = false
            ctx.stopService(Intent(ctx, CallMonitorService::class.java))
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("E_SERVICE", e.message, e)
        }
    }

    /** İzinler yalnızca granted/denied döner; "kalıcı reddedildi" bilgisi JS'te istek sonucundan gelir. */
    @ReactMethod
    fun getStatus(promise: Promise) {
        try {
            val ctx = reactApplicationContext
            val permissions = Arguments.createMap()
            STATUS_PERMISSIONS.forEach { (key, permission, minSdk) ->
                val granted = Build.VERSION.SDK_INT < minSdk ||
                    ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
                permissions.putString(key, if (granted) "granted" else "denied")
            }
            val power = ctx.getSystemService(PowerManager::class.java)
            promise.resolve(
                Arguments.createMap().apply {
                    putBoolean("monitoring", CallMonitorService.isRunning)
                    putBoolean("accessibilityEnabled", KeepAliveAccessibilityService.isEnabled(ctx))
                    putBoolean("ignoringBatteryOptimizations", power.isIgnoringBatteryOptimizations(ctx.packageName))
                    putMap("permissions", permissions)
                },
            )
        } catch (e: Exception) {
            promise.reject("E_SERVICE", e.message, e)
        }
    }

    @ReactMethod
    fun openAccessibilitySettings(promise: Promise) {
        launch(promise, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** Kalıcı reddedilen izinler ve Android 13+ "kısıtlı ayarlar" için uygulama bilgisi ekranı. */
    @ReactMethod
    fun openAppSettings(promise: Promise) {
        val ctx = reactApplicationContext
        launch(
            promise,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
        )
    }

    // Uygulama yan yüklemeyle kullanıldığı için doğrudan istem kabul edilebilir (CLAUDE.md §1).
    @SuppressLint("BatteryLife")
    @ReactMethod
    fun requestIgnoreBatteryOptimizations(promise: Promise) {
        val ctx = reactApplicationContext
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}"),
        )
        // Bazı üretici ROM'ları doğrudan istemi desteklemez: genel pil ayarlarına düş
        val intent = if (direct.resolveActivity(ctx.packageManager) != null) {
            direct
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        launch(promise, intent)
    }

    // ---- Ayarlar ----

    @ReactMethod
    fun getSettings(promise: Promise) {
        try {
            promise.resolve(settingsMap())
        } catch (e: Exception) {
            promise.reject("E_IO", e.message, e)
        }
    }

    /** Yalnızca gönderilen alanları günceller; geçersiz değerde hiçbir alan değişmez. */
    @ReactMethod
    fun setSettings(settings: ReadableMap, promise: Promise) {
        try {
            val source = if (settings.hasKey("preferredSource")) settings.getString("preferredSource") else null
            if (source != null && source != Prefs.SOURCE_AUTO && source !in CallRecorder.SOURCE_NAMES) {
                promise.reject("E_INVALID", "Geçersiz ses kaynağı: $source")
                return
            }
            val rate = if (settings.hasKey("sampleRate")) settings.getDouble("sampleRate").toInt() else null
            if (rate != null && rate !in Prefs.SAMPLE_RATES) {
                promise.reject("E_INVALID", "Geçersiz örnekleme hızı: $rate")
                return
            }
            if (settings.hasKey("autoRecord")) prefs.autoRecord = settings.getBoolean("autoRecord")
            if (source != null) prefs.preferredSource = source
            if (rate != null) prefs.sampleRate = rate
            promise.resolve(settingsMap())
        } catch (e: Exception) {
            promise.reject("E_IO", e.message, e)
        }
    }

    // ---- Kayıtlar ----

    @ReactMethod
    fun listRecordings(promise: Promise) {
        try {
            val array = Arguments.createArray()
            repository.list().forEach { array.pushMap(toMap(it)) }
            promise.resolve(array)
        } catch (e: Exception) {
            promise.reject("E_IO", e.message, e)
        }
    }

    @ReactMethod
    fun deleteRecording(id: String, promise: Promise) {
        try {
            if (player.currentId == id) player.stop()
            if (repository.delete(id)) {
                promise.resolve(null)
            } else {
                promise.reject("E_NOT_FOUND", "Kayıt bulunamadı: $id")
            }
        } catch (e: Exception) {
            promise.reject("E_IO", e.message, e)
        }
    }

    @ReactMethod
    fun shareRecording(id: String, promise: Promise) {
        try {
            val file = repository.audioFile(id)
            if (!file.exists()) {
                promise.reject("E_NOT_FOUND", "Kayıt bulunamadı: $id")
                return
            }
            val ctx = reactApplicationContext
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(file.name, uri) // okuma izninin seçiciden geçmesi için
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            launch(promise, Intent.createChooser(send, "Kaydı paylaş"))
        } catch (e: Exception) {
            promise.reject("E_IO", e.message, e)
        }
    }

    // ---- Oynatma ----

    @ReactMethod
    fun play(id: String, promise: Promise) {
        val file = repository.audioFile(id)
        if (!file.exists()) {
            promise.reject("E_NOT_FOUND", "Kayıt bulunamadı: $id")
            return
        }
        player.play(id, file) { e -> settle(promise, e) }
    }

    @ReactMethod
    fun pause(promise: Promise) {
        player.pause { e -> settle(promise, e) }
    }

    @ReactMethod
    fun seekTo(positionMs: Double, promise: Promise) {
        player.seekTo(positionMs.toInt()) { e -> settle(promise, e) }
    }

    @ReactMethod
    fun stop(promise: Promise) {
        player.stop { e -> settle(promise, e) }
    }

    // ---- Yardımcılar ----

    /** Aktivite varsa onun üzerinden, yoksa yeni görevde açar. */
    private fun launch(promise: Promise, intent: Intent) {
        try {
            val activity = reactApplicationContext.currentActivity
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                reactApplicationContext.startActivity(intent)
            }
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("E_SERVICE", e.message, e)
        }
    }

    private fun settingsMap(): WritableMap = Arguments.createMap().apply {
        putBoolean("autoRecord", prefs.autoRecord)
        putString("preferredSource", prefs.preferredSource)
        putInt("sampleRate", prefs.sampleRate)
    }

    private fun settle(promise: Promise, error: Throwable?) {
        if (error == null) promise.resolve(null) else promise.reject("E_PLAYER", error.message, error)
    }

    private fun emit(name: String, params: WritableMap) {
        val ctx = reactApplicationContext
        if (ctx.hasActiveReactInstance()) ctx.emitDeviceEvent(name, params)
    }

    private fun toMap(meta: RecordingMeta): WritableMap = Arguments.createMap().apply {
        putString("id", meta.id)
        putString("filePath", repository.audioFile(meta.id).absolutePath)
        if (meta.number != null) putString("number", meta.number) else putNull("number")
        putString("direction", meta.direction)
        putDouble("startedAt", meta.startedAt.toDouble())
        putDouble("durationMs", meta.durationMs.toDouble())
        putDouble("sizeBytes", meta.sizeBytes.toDouble())
        putString("audioSource", meta.audioSource)
        putString("audioRoute", meta.audioRoute)
    }

    companion object {
        const val NAME = "CallRecorder"
        const val TAG = "CallRecorder"

        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.RECORD_AUDIO,     // mikrofon tipli ön plan servisi için şart
            Manifest.permission.READ_PHONE_STATE, // arama durumu dinleme
        )

        /** Durum ekranındaki izinler: JS anahtarı, Android izni, izin hangi API'de geldi. */
        private val STATUS_PERMISSIONS = listOf(
            Triple("RECORD_AUDIO", Manifest.permission.RECORD_AUDIO, 1),
            Triple("READ_PHONE_STATE", Manifest.permission.READ_PHONE_STATE, 1),
            Triple("READ_CALL_LOG", Manifest.permission.READ_CALL_LOG, 1),
            Triple("POST_NOTIFICATIONS", Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU),
        )
    }
}