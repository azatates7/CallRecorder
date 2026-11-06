package com.callrecorder.recorder

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

    companion object {
        const val NAME = "CallRecorder"
        const val TAG = "CallRecorder"
    }
}