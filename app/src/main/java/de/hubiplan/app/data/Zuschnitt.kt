package de.hubiplan.app.data

/**
 * Weiße Ränder eines Notenscans finden (Konzept 4.2, N5), ohne Android: [pixels] im Format
 * ARGB wie `Bitmap.getPixels`. Liefert [links, oben, rechts, unten] (rechts/unten exklusiv) mit
 * etwas Luft, oder null, wenn die Seite leer ist oder sich Abschneiden nicht lohnt.
 */
object Zuschnitt {
    /** Heller als das gilt als Papier (Grauwert 0–255). */
    private const val PAPIER = 200
    /** Mindestens so viele dunkle Punkte je Zeile/Spalte, damit Staub nicht zählt. */
    private const val MIN_PUNKTE = 2

    fun grenzen(pixels: IntArray, width: Int, height: Int, luft: Float = 0.02f): IntArray? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val rows = IntArray(height)
        val cols = IntArray(width)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val p = pixels[row + x]
                val alpha = (p ushr 24) and 0xFF
                if (alpha < 128) continue
                val grau = (((p shr 16) and 0xFF) * 30 + ((p shr 8) and 0xFF) * 59 + (p and 0xFF) * 11) / 100
                if (grau < PAPIER) {
                    rows[y]++
                    cols[x]++
                }
            }
        }
        val top = rows.indexOfFirst { it >= MIN_PUNKTE }
        if (top < 0) return null
        val bottom = rows.indexOfLast { it >= MIN_PUNKTE } + 1
        val left = cols.indexOfFirst { it >= MIN_PUNKTE }
        val right = cols.indexOfLast { it >= MIN_PUNKTE } + 1
        val padX = (width * luft).toInt()
        val padY = (height * luft).toInt()
        val out = intArrayOf(
            (left - padX).coerceAtLeast(0),
            (top - padY).coerceAtLeast(0),
            (right + padX).coerceAtMost(width),
            (bottom + padY).coerceAtMost(height),
        )
        // Weniger als 5 % gewonnen: so lassen
        val area = (out[2] - out[0]).toLong() * (out[3] - out[1])
        return if (area > width.toLong() * height * 95 / 100) null else out
    }
}
