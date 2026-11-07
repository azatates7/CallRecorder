package com.callrecorder.recorder

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import java.io.File

/**
 * Tek seferde bir kaydı çalan MediaPlayer sarmalayıcı (CLAUDE.md §3).
 * Tüm MediaPlayer işlemleri ana thread'de yapılır; sonuç [result] ile bildirilir.
 */
class RecordingPlayer(private val emit: (String, WritableMap) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    @Volatile
    var currentId: String? = null
        private set

    private val progressTick = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (!p.isPlaying) return
            emitProgress(p)
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    /** Aynı kayıt duraklatılmış/bitmişse kaldığı yerden (bitmişse baştan) devam eder. */
    fun play(id: String, file: File, result: (Throwable?) -> Unit) {
        handler.post {
            try {
                val existing = player
                if (existing != null && currentId == id) {
                    existing.start()
                } else {
                    releasePlayer()
                    val p = MediaPlayer()
                    p.setDataSource(file.absolutePath)
                    p.prepare()
                    p.setOnCompletionListener {
                        handler.removeCallbacks(progressTick)
                        emitProgress(it)
                        emit("playbackEnded", Arguments.createMap().apply { putString("id", id) })
                    }
                    player = p
                    currentId = id
                    p.start()
                }
                handler.removeCallbacks(progressTick)
                handler.post(progressTick)
                result(null)
            } catch (e: Exception) {
                releasePlayer()
                result(e)
            }
        }
    }

    fun pause(result: (Throwable?) -> Unit) {
        handler.post {
            try {
                player?.let {
                    if (it.isPlaying) it.pause()
                    handler.removeCallbacks(progressTick)
                    emitProgress(it)
                }
                result(null)
            } catch (e: Exception) {
                result(e)
            }
        }
    }

    fun seekTo(positionMs: Int, result: (Throwable?) -> Unit) {
        handler.post {
            try {
                player?.let {
                    it.seekTo(positionMs)
                    emitProgress(it)
                }
                result(null)
            } catch (e: Exception) {
                result(e)
            }
        }
    }

    fun stop(result: (Throwable?) -> Unit = {}) {
        handler.post {
            releasePlayer()
            result(null)
        }
    }

    private fun releasePlayer() {
        handler.removeCallbacks(progressTick)
        player?.release()
        player = null
        currentId = null
    }

    private fun emitProgress(p: MediaPlayer) {
        val id = currentId ?: return
        emit(
            "playbackProgress",
            Arguments.createMap().apply {
                putString("id", id)
                putDouble("positionMs", p.currentPosition.toDouble())
                putDouble("durationMs", p.duration.toDouble())
            },
        )
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 250L
    }
}