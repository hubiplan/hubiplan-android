package de.hubiplan.app.ui

/** Farbe aus HUBIplan (#RRGGBB) als ARGB-Wert; null bei ungültiger Angabe. */
fun parseHexColor(hex: String?): Long? {
    val text = hex?.trim()?.removePrefix("#") ?: return null
    if (text.length != 6 || !text.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return 0xFF000000L or text.toLong(16)
}

/** Farbe zum Mischen: [amount] 0 = [base], 1 = [other]. */
fun mix(base: Long, other: Long, amount: Float): Long {
    fun channel(c: Long, shift: Int) = ((c shr shift) and 0xFF).toFloat()
    fun blend(shift: Int) =
        (channel(base, shift) * (1 - amount) + channel(other, shift) * amount).toLong().coerceIn(0, 255)
    return 0xFF000000L or (blend(16) shl 16) or (blend(8) shl 8) or blend(0)
}

const val WHITE = 0xFFFFFFFFL
const val BLACK = 0xFF000000L

/** Standardfarben, solange keine Vereinsfarben geladen sind (wie HUBIplan). */
const val DEFAULT_PRIMARY = 0xFF1F4E79L
const val DEFAULT_ACCENT = 0xFFC9A227L
