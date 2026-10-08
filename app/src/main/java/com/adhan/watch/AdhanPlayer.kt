package com.adhan.watch

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.PowerManager

object Voices {
    private val ext = setOf("mp3", "ogg", "m4a", "wav")

    fun list(c: Context): List<String> =
        (c.assets.list("adhan") ?: emptyArray())
            .filter { it.substringAfterLast('.', "").lowercase() in ext }
            .sorted()

    fun label(file: String) = file.substringBeforeLast('.').replace('_', ' ')
}

object AdhanPlayer {
    private var mp: MediaPlayer? = null

    fun isPlaying() = mp != null

    fun play(ctx: Context, voice: String?, onDone: () -> Unit) {
        stop()
        val c = ctx.applicationContext
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.setWakeMode(c, PowerManager.PARTIAL_WAKE_LOCK)
            if (voice != null) {
                c.assets.openFd("adhan/$voice").use { afd ->
                    p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            } else {
                p.setDataSource(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            }
            p.setOnCompletionListener { stop(); onDone() }
            p.setOnErrorListener { _, _, _ -> stop(); onDone(); true }
            p.prepare()
            p.start()
            mp = p
        } catch (e: Exception) {
            stop()
            onDone()
        }
    }

    fun stop() {
        mp?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        mp = null
    }
}
