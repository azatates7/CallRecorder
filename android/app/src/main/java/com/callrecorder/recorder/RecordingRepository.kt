package com.callrecorder.recorder

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Kayıt meta verisi; `<id>.json` olarak `<id>.m4a` yanında tutulur (CLAUDE.md §3, §5). */
data class RecordingMeta(
    val id: String,
    val number: String?,
    val direction: String,
    val startedAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val audioSource: String,
    val audioRoute: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("fileName", "$id.m4a")
        .put("number", number ?: JSONObject.NULL)
        .put("direction", direction)
        .put("startedAt", startedAt)
        .put("durationMs", durationMs)
        .put("sizeBytes", sizeBytes)
        .put("audioSource", audioSource)
        .put("audioRoute", audioRoute)

    companion object {
        fun fromJson(o: JSONObject) = RecordingMeta(
            id = o.getString("id"),
            number = if (o.isNull("number")) null else o.optString("number"),
            direction = o.optString("direction", "unknown"),
            startedAt = o.getLong("startedAt"),
            durationMs = o.optLong("durationMs"),
            sizeBytes = o.optLong("sizeBytes"),
            audioSource = o.optString("audioSource", "MIC"),
            audioRoute = o.optString("audioRoute", "unknown"),
        )
    }
}

/** Servisten (kayıt kaydedildi) RN modülüne olay aktarımı; modül yoksa olay düşer. */
object RecordingEvents {
    @Volatile
    var onSaved: ((RecordingMeta) -> Unit)? = null

    fun saved(meta: RecordingMeta) {
        onSaved?.invoke(meta)
    }
}

/** Kayıt dosyalarının okunup yazıldığı ve silindiği tek yer. */
class RecordingRepository(context: Context) {

    val dir: File = run {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Environment.DIRECTORY_RECORDINGS
        } else {
            Environment.DIRECTORY_MUSIC
        }
        context.getExternalFilesDir(type) ?: File(context.filesDir, "recordings")
    }.also { it.mkdirs() }

    fun newId(now: Long): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        return "${stamp}_${Random.nextInt(1000, 10000)}"
    }

    fun audioFile(id: String): File = File(dir, "$id.m4a")

    /** JSON'u olan ve ses dosyası duran kayıtlar, en yeni önce. Bozuk JSON atlanır. */
    fun list(): List<RecordingMeta> =
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { file ->
                try {
                    RecordingMeta.fromJson(JSONObject(file.readText()))
                        .takeIf { audioFile(it.id).exists() }
                } catch (e: Exception) {
                    Log.w(TAG, "Meta veri okunamadı: ${file.name}: ${e.message}")
                    null
                }
            }
            .sortedByDescending { it.startedAt }

    /** Ses ve meta dosyasını siler; kayıt bulunamazsa false döner. */
    fun delete(id: String): Boolean {
        val audio = audioFile(id)
        val json = File(dir, "$id.json")
        if (!audio.exists() && !json.exists()) return false
        audio.delete()
        json.delete()
        return true
    }

    /** Önce geçici dosyaya yazar, sonra yeniden adlandırır: yarım JSON kalmaz. */
    fun saveMetadata(meta: RecordingMeta) {
        val target = File(dir, "${meta.id}.json")
        val tmp = File(dir, "${meta.id}.json.tmp")
        tmp.writeText(meta.toJson().toString(2))
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw java.io.IOException("Meta veri yazılamadı: ${target.name}")
        }
    }

    companion object {
        private const val TAG = CallRecorderModule.TAG
    }
}