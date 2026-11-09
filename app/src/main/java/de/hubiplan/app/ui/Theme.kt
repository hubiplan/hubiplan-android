package de.hubiplan.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.hubiplan.app.data.Theme

/**
 * Farbschema aus den Vereinsfarben in HUBIplan (Primär- und Akzentfarbe, mit Vererbung
 * Zug ← Verein ← Dachverband). Die dynamischen Systemfarben von Android werden bewusst
 * nicht genutzt, damit die Vereinsfarben bleiben.
 */
fun colorSchemeFor(theme: Theme?, dark: Boolean): ColorScheme {
    val primary = parseHexColor(theme?.primary) ?: DEFAULT_PRIMARY
    val onPrimary = parseHexColor(theme?.onPrimary) ?: WHITE
    val accent = parseHexColor(theme?.accent) ?: DEFAULT_ACCENT
    val onAccent = parseHexColor(theme?.onAccent) ?: BLACK
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val surfaces = listOf(base.surface, base.background, base.surfaceVariant,
        base.surfaceContainerLowest, base.surfaceContainerLow, base.surfaceContainer,
        base.surfaceContainerHigh, base.surfaceContainerHighest).map { it.toArgb().toLong() and 0xFFFFFFFFL }
    fun on(color: Long, preferred: Long) = if (contrast(color, preferred) >= 4.5) preferred else readableOn(color)
    return if (!dark) {
        // Primärfarbe dient in Material als Text- und Rahmenfarbe (Textknöpfe, aktive Felder,
        // Schalter): bei hellen Vereinsfarben abdunkeln (Barrierefreiheit A-03). Die
        // unveränderte Vereinsfarbe bleibt im hellen Modus für die Kopfleiste (LocalBrand).
        val uiPrimary = withContrast(primary, surfaces, BLACK)
        val uiAccent = withContrast(accent, surfaces, BLACK)
        lightColorScheme(
            primary = Color(uiPrimary),
            onPrimary = Color(on(uiPrimary, onPrimary)),
            primaryContainer = Color(mix(primary, WHITE, 0.85f)),
            onPrimaryContainer = Color(readableOn(mix(primary, WHITE, 0.85f))),
            secondary = Color(uiAccent),
            onSecondary = Color(on(uiAccent, onAccent)),
            secondaryContainer = Color(mix(accent, WHITE, 0.8f)),
            onSecondaryContainer = Color(readableOn(mix(accent, WHITE, 0.8f))),
            tertiary = Color(uiAccent),
            onTertiary = Color(on(uiAccent, onAccent)),
        )
    } else {
        // Im Dunkeln hellere Töne derselben Farben, damit Text lesbar bleibt
        val lightPrimary = withContrast(mix(primary, WHITE, 0.45f), surfaces, WHITE)
        val lightAccent = withContrast(mix(accent, WHITE, 0.3f), surfaces, WHITE)
        darkColorScheme(
            primary = Color(lightPrimary),
            onPrimary = Color(readableOn(lightPrimary)),
            primaryContainer = Color(mix(primary, BLACK, 0.35f)),
            onPrimaryContainer = Color(readableOn(mix(primary, BLACK, 0.35f))),
            secondary = Color(lightAccent),
            onSecondary = Color(readableOn(lightAccent)),
            secondaryContainer = Color(mix(accent, BLACK, 0.5f)),
            onSecondaryContainer = Color(readableOn(mix(accent, BLACK, 0.5f))),
            tertiary = Color(lightAccent),
            onTertiary = Color(readableOn(lightAccent)),
        )
    }
}

/** Vereinsfarbe für farbige Flächen (Kopfleiste) samt lesbarer Schrift; im Dunkelmodus gedämpft. */
data class BrandColors(val brand: Color, val onBrand: Color)

val LocalBrand = staticCompositionLocalOf { BrandColors(Color(DEFAULT_PRIMARY), Color(WHITE)) }

fun brandFor(theme: Theme?, dark: Boolean = false): BrandColors {
    val raw = parseHexColor(theme?.primary) ?: DEFAULT_PRIMARY
    // Im Dunkelmodus kein heller Balken über dunklem Inhalt (UX-Audit 08.11. A-06): helle
    // Vereinsfarben so weit abdunkeln, bis weiße Schrift lesbar ist. Dunkle bleiben, wie sie sind.
    val primary = if (dark) withContrast(raw, listOf(WHITE), BLACK) else raw
    // Abgedunkelt trägt der Balken immer weiße Schrift (und helle Statussymbole)
    val onPrimary = if (dark && primary != raw) WHITE
    else parseHexColor(theme?.onPrimary)?.takeIf { contrast(it, primary) >= 4.5 } ?: readableOn(primary)
    return BrandColors(Color(primary), Color(onPrimary))
}

@Composable
fun HubiplanTheme(theme: Theme?, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalBrand provides brandFor(theme, dark)) {
        MaterialTheme(colorScheme = colorSchemeFor(theme, dark), content = content)
    }
}
