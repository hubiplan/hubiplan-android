package de.hubiplan.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Bilder vom Server begrenzt dekodieren (Audit 02.11., WA-S01): erst nur die Größe lesen, dann
 * mit `inSampleSize` so verkleinert laden, dass die längste Kante etwa [maxEdge] erreicht und
 * höchstens [MAX_PIXELS] Bildpunkte entstehen. Ein kleines, stark komprimiertes Bild mit
 * riesiger Pixelzahl bringt die App so nicht an die Speichergrenze.
 */
object SafeBitmap {
    /** Etwa 16 MB bei 4 Byte je Bildpunkt. */
    const val MAX_PIXELS = 4_000_000L

    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
        while ((width.toLong() / sample) * (height.toLong() / sample) > MAX_PIXELS) sample *= 2
        return sample
    }

    fun decode(bytes: ByteArray?, maxEdge: Int): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
