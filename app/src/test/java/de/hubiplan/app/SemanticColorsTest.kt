package de.hubiplan.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.hubiplan.app.data.Theme
import de.hubiplan.app.ui.*
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticColorsTest {
    private fun argb(color: Color) = color.toArgb().toLong() and 0xFFFFFFFFL
    @Test fun textrollenAufAllenMaterialFlaechenUndEigeneFarbpaare() {
        for (hex in listOf("#f2b33d", "#ffffff", "#000000", "#808080", "#ff00ff", "#00ffff", "#c08020")) {
            for (dark in listOf(false, true)) {
                val s = colorSchemeFor(Theme(primary = hex, onPrimary = "#ffffff", accent = hex, onAccent = "#000000"), dark)
                val surfaces = listOf(s.surface, s.background, s.surfaceVariant, s.surfaceContainerLowest,
                    s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest)
                for (ink in listOf(s.primary, s.secondary, s.tertiary)) for (bg in surfaces) {
                    assertTrue("$hex, dunkel=$dark, Text auf Fläche", contrast(argb(ink), argb(bg)) >= 4.5)
                }
                for ((ink, bg) in listOf(s.onPrimary to s.primary, s.onSecondary to s.secondary,
                    s.onTertiary to s.tertiary, s.onPrimaryContainer to s.primaryContainer,
                    s.onSecondaryContainer to s.secondaryContainer)) {
                    assertTrue("$hex, dunkel=$dark, Farbfläche", contrast(argb(ink), argb(bg)) >= 4.5)
                }
            }
        }
    }
}
