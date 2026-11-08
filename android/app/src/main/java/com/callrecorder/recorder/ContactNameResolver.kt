package com.callrecorder.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Numaradan rehberdeki kişi adını bulur. Ad kayıt dosyasına yazılmaz, her listelemede okunur:
 * rehberdeki değişiklikler eski kayıtlara da yansır. Her listeleme için yeni örnek oluşturulur;
 * önbellek yalnızca aynı numaranın o listede tekrar sorgulanmasını önler.
 */
class ContactNameResolver(private val context: Context) {

    private val cache = HashMap<String, String?>()
    private val permitted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** İzin yoksa, numara yoksa veya rehberde kayıtlı değilse null döner. */
    fun nameFor(number: String?): String? {
        if (!permitted || number.isNullOrBlank()) return null
        if (cache.containsKey(number)) return cache[number]
        val name = query(number)
        cache[number] = name
        return name
    }

    private fun query(number: String): String? = try {
        // PhoneLookup, "+90 532..." ile "0532..." gibi biçim farklarını kendisi eşleştirir
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
    } catch (e: Exception) {
        Log.w(CallRecorderModule.TAG, "Rehber okunamadı: ${e.message}")
        null
    }
}