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

/** Standardfarben, solange keine Vereinsfarben geladen sind (wie HUBIplan: Hubi-Grün, Hubi-Gold). */
const val DEFAULT_PRIMARY = 0xFF0E7C66L
const val DEFAULT_ACCENT = 0xFFF2B33DL

// --- Kontrast nach WCAG 2.x (wie HUBIplan, frontend/src/theme/contrast.ts) ---

private fun channel(c: Long, shift: Int): Double {
    val v = ((c shr shift) and 0xFF).toDouble() / 255.0
    return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
}

fun luminance(color: Long): Double =
    0.2126 * channel(color, 16) + 0.7152 * channel(color, 8) + 0.0722 * channel(color, 0)

fun contrast(a: Long, b: Long): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** Besser lesbare Schrift (weiß oder fast schwarz) auf einer Farbfläche. */
fun readableOn(background: Long): Long {
    val dark = if (contrast(background, DARK_TEXT) >= 4.5) DARK_TEXT else BLACK
    return if (contrast(background, WHITE) >= contrast(background, dark)) WHITE else dark
}

const val DARK_TEXT = 0xFF1D2421L

/**
 * Neutrale Flächen, Schrift und Linien wie in der Weboberfläche (frontend/src/theme/theme.css:
 * --color-bg, --color-surface, --color-text, --color-text-muted, --color-border), statt der
 * violettstichigen Grundwerte von Material (UX-Audit 16.11., AND-19).
 */
data class Neutral(
    val surface: Long,
    val onSurface: Long,
    val onSurfaceVariant: Long,
    val containerLowest: Long,
    val containerLow: Long,
    val container: Long,
    val containerHigh: Long,
    val containerHighest: Long,
    val outline: Long,
    val outlineVariant: Long,
    val inverseSurface: Long,
    val inverseOnSurface: Long,
    val dim: Long,
    val bright: Long,
) {
    /** Alle Flächen, auf denen Text oder Vereinsfarbe stehen kann. */
    val flaechen: List<Long>
        get() = listOf(surface, containerLowest, containerLow, container, containerHigh, containerHighest, dim, bright)
}

val NEUTRAL_HELL = Neutral(
    surface = 0xFFFBFCFBL,
    onSurface = 0xFF121614L,
    onSurfaceVariant = 0xFF545E5AL,
    containerLowest = 0xFFFFFFFFL,
    containerLow = 0xFFF5F7F5L,
    container = 0xFFEEF0EDL,
    containerHigh = 0xFFE7EAE7L,
    containerHighest = 0xFFE1E4E1L,
    outline = 0xFF737D79L,
    outlineVariant = 0xFFDDE1DEL,
    inverseSurface = 0xFF2A302DL,
    inverseOnSurface = 0xFFEEF1F0L,
    dim = 0xFFDADDDAL,
    bright = 0xFFFBFCFBL,
)

val NEUTRAL_DUNKEL = Neutral(
    surface = 0xFF111614L,
    onSurface = 0xFFDFE5E2L,
    onSurfaceVariant = 0xFFA3ADA8L,
    containerLowest = 0xFF0C100EL,
    containerLow = 0xFF161B19L,
    container = 0xFF1A201DL,
    containerHigh = 0xFF242B27L,
    containerHighest = 0xFF2F3833L,
    outline = 0xFF838D88L,
    outlineVariant = 0xFF2F3833L,
    inverseSurface = 0xFFDFE5E2L,
    inverseOnSurface = 0xFF1A201DL,
    dim = 0xFF111614L,
    bright = 0xFF353D39L,
)

/** Hintergründe (hell/dunkel), auf denen Text in Vereinsfarbe steht. */
val LIGHT_SURFACE = NEUTRAL_HELL.surface
val DARK_SURFACE = NEUTRAL_DUNKEL.surface

/**
 * Vereinsfarbe so wenig wie nötig zu [toward] (Schwarz im hellen, Weiß im dunklen Schema)
 * verschieben, bis sie auf allen [backgrounds] mindestens [target] : 1 erreicht
 * (WCAG 1.4.3 für Text, 1.4.11 für Rahmen und Schalter).
 */
fun withContrast(color: Long, backgrounds: List<Long>, toward: Long, target: Double = 4.5): Long {
    for (step in 0..20) {
        val c = mix(color, toward, step * 0.05f)
        if (backgrounds.all { contrast(c, it) >= target }) return c
    }
    return toward
}
