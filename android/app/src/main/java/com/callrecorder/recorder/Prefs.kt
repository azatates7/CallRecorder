package com.callrecorder.recorder

import android.content.Context

/** Uygulama ayarları (SharedPreferences). Servis, modül ve boot alıcısı aynı değerleri okur. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("call_recorder", Context.MODE_PRIVATE)

    /** Servis çalışırken aramalar kaydedilsin mi. */
    var autoRecord: Boolean
        get() = sp.getBoolean(KEY_AUTO_RECORD, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_RECORD, value).apply()

    /** "AUTO" veya tek bir kaynak adı (CallRecorder.SOURCE_NAMES). */
    var preferredSource: String
        get() = sp.getString(KEY_SOURCE, SOURCE_AUTO) ?: SOURCE_AUTO
        set(value) = sp.edit().putString(KEY_SOURCE, value).apply()

    var sampleRate: Int
        get() = sp.getInt(KEY_SAMPLE_RATE, SAMPLE_RATES.first())
        set(value) = sp.edit().putInt(KEY_SAMPLE_RATE, value).apply()

    /** Kullanıcı izlemeyi açık bıraktı mı; yeniden başlatmadan sonra servisi geri getirmek için. */
    var monitoringEnabled: Boolean
        get() = sp.getBoolean(KEY_MONITORING, false)
        set(value) = sp.edit().putBoolean(KEY_MONITORING, value).apply()

    /** Kayıt sırasında hoparlörü açmayı dene; karşı tarafın sesi mikrofona ulaşsın (CLAUDE.md §14). */
    var autoSpeaker: Boolean
        get() = sp.getBoolean(KEY_AUTO_SPEAKER, false)
        set(value) = sp.edit().putBoolean(KEY_AUTO_SPEAKER, value).apply()

    /** İlk açılıştaki yasal bilgilendirme onaylandı mı (CLAUDE.md §13). */
    var disclaimerAccepted: Boolean
        get() = sp.getBoolean(KEY_DISCLAIMER, false)
        set(value) = sp.edit().putBoolean(KEY_DISCLAIMER, value).apply()

    companion object {
        const val SOURCE_AUTO = "AUTO"
        val SAMPLE_RATES = listOf(16_000, 44_100)

        private const val KEY_AUTO_RECORD = "autoRecord"
        private const val KEY_SOURCE = "preferredSource"
        private const val KEY_SAMPLE_RATE = "sampleRate"
        private const val KEY_MONITORING = "monitoringEnabled"
        private const val KEY_DISCLAIMER = "disclaimerAccepted"
        private const val KEY_AUTO_SPEAKER = "autoSpeaker"
    }
}