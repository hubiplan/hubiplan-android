package de.hubiplan.app.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import de.hubiplan.app.data.PultDateien
import java.io.File

/**
 * Hörbeispiel (MIDI) abspielen mit dem in Android eingebauten Klangerzeuger, ohne Netz und ohne
 * Fremdbibliothek (Konzept 4.8). Die entschlüsselte Datei liegt nur während des Abspielens im Cache.
 */
class Hoerbeispiel {
    private var player: MediaPlayer? = null
    private var file: File? = null

    val playing: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /** [tempo] 0,5–1,2; [onEnd] nach dem Ende oder bei einem Fehler. */
    fun play(context: Context, midi: ByteArray, tempo: Float = 1f, onEnd: () -> Unit = {}) {
        stop()
        val f = PultDateien.write(context, midi, ".mid")
        file = f
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setDataSource(f.absolutePath)
            mp.setOnCompletionListener { stop(); onEnd() }
            mp.setOnErrorListener { _, _, _ -> stop(); onEnd(); true }
            mp.prepare()
            setTempo(tempo)
            mp.start()
        } catch (e: Exception) {
            stop()
            onEnd()
        }
    }

    /** Tempo ändern; nicht jedes Gerät kann das bei MIDI (dann bleibt es beim Original). */
    fun setTempo(tempo: Float) {
        val mp = player ?: return
        runCatching {
            val wasPlaying = mp.isPlaying
            mp.playbackParams = PlaybackParams().setSpeed(tempo.coerceIn(0.5f, 1.2f))
            if (!wasPlaying) mp.pause()
        }
    }

    fun pause() {
        runCatching { player?.pause() }
    }

    fun resume() {
        runCatching { player?.start() }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        file?.delete()
        file = null
    }

    fun release() = stop()
}
