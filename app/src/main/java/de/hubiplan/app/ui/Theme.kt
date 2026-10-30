package de.hubiplan.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.hubiplan.app.data.Theme

/**
 * Farbschema aus den Vereinsfarben in HUBIplan (Primär- und Akzentfarbe, mit Vererbung
 * Zug ← Verein ← Dachverband). Die dynamischen Systemfarben von Android werden bewusst
 * nicht genutzt, damit die Vereinsfarben bleiben (Konzept, Ralf 30.10.).
 */
fun colorSchemeFor(theme: Theme?, dark: Boolean): ColorScheme {
    val primary = parseHexColor(theme?.primary) ?: DEFAULT_PRIMARY
    val onPrimary = parseHexColor(theme?.onPrimary) ?: WHITE
    val accent = parseHexColor(theme?.accent) ?: DEFAULT_ACCENT
    val onAccent = parseHexColor(theme?.onAccent) ?: BLACK
    return if (!dark) {
        lightColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(mix(primary, WHITE, 0.85f)),
            onPrimaryContainer = Color(mix(primary, BLACK, 0.5f)),
            secondary = Color(accent),
            onSecondary = Color(onAccent),
            secondaryContainer = Color(mix(accent, WHITE, 0.8f)),
            onSecondaryContainer = Color(mix(accent, BLACK, 0.6f)),
            tertiary = Color(accent),
            onTertiary = Color(onAccent),
        )
    } else {
        // Im Dunkeln hellere Töne derselben Farben, damit Text lesbar bleibt
        val lightPrimary = mix(primary, WHITE, 0.45f)
        val lightAccent = mix(accent, WHITE, 0.3f)
        darkColorScheme(
            primary = Color(lightPrimary),
            onPrimary = Color(mix(primary, BLACK, 0.6f)),
            primaryContainer = Color(mix(primary, BLACK, 0.35f)),
            onPrimaryContainer = Color(mix(primary, WHITE, 0.85f)),
            secondary = Color(lightAccent),
            onSecondary = Color(mix(accent, BLACK, 0.7f)),
            secondaryContainer = Color(mix(accent, BLACK, 0.5f)),
            onSecondaryContainer = Color(mix(accent, WHITE, 0.85f)),
            tertiary = Color(lightAccent),
            onTertiary = Color(mix(accent, BLACK, 0.7f)),
        )
    }
}

@Composable
fun HubiplanTheme(theme: Theme?, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorSchemeFor(theme, isSystemInDarkTheme()), content = content)
}
