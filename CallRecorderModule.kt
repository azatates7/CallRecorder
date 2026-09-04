package com.frontend.callrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import java.io.File

class CallRecorderModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "CallRecorderModule"

    /** Permissions the recorder needs at runtime; requesting is left to the JS side (e.g. via
     * react-native-permissions) so this module only reports what's still missing. */
    @ReactMethod
    fun getMissingPermissions(promise: Promise) {
        val required = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            required.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing: WritableArray = Arguments.createArray()
        for (permission in required) {
            val granted = ContextCompat.checkSelfPermission(reactApplicationContext, permission) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) missing.pushString(permission)
        }
        promise.resolve(missing)
    }

    @ReactMethod
    fun setEnabled(enabled: Boolean, promise: Promise) {
        PhoneStateReceiver.setRecorderEnabled(reactApplicationContext, enabled)
        if (enabled) {
            CallRecordingService.start(reactApplicationContext)
        } else {
            val intent = Intent(reactApplicationContext, CallRecordingService::class.java).apply {
                action = CallRecordingService.ACTION_STOP_SERVICE
            }
            reactApplicationContext.startService(intent)
        }
        promise.resolve(enabled)
    }

    @ReactMethod
    fun isEnabled(promise: Promise) {
        promise.resolve(PhoneStateReceiver.isRecorderEnabled(reactApplicationContext))
    }

    /** Lists saved recordings from the CallRecordings folder (MediaStore on Android 10+, plain
     * file listing on older versions). */
    @ReactMethod
    fun listRecordings(promise: Promise) {
        val result: WritableArray = Arguments.createArray()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.SIZE,
                )
                val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf("Music/${CallRecordingService.RECORDINGS_FOLDER}/")
                reactApplicationContext.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    while (cursor.moveToNext()) {
                        val uri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idCol).toString(),
                        )
                        val item: WritableMap = Arguments.createMap()
                        item.putString("uri", uri.toString())
                        item.putString("name", cursor.getString(nameCol))
                        item.putDouble("dateAdded", cursor.getLong(dateCol).toDouble())
                        item.putDouble("size", cursor.getLong(sizeCol).toDouble())
                        result.pushMap(item)
                    }
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    CallRecordingService.RECORDINGS_FOLDER,
                )
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { file ->
                    val item: WritableMap = Arguments.createMap()
                    item.putString("uri", Uri.fromFile(file).toString())
                    item.putString("name", file.name)
                    item.putDouble("dateAdded", (file.lastModified() / 1000).toDouble())
                    item.putDouble("size", file.length().toDouble())
                    result.pushMap(item)
                }
            }
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("LIST_FAILED", e)
        }
    }
}