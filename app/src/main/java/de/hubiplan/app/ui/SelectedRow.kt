package de.hubiplan.app.ui

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Farben der gewählten Zeile im Tablet-Layout (UX-Audit 16.11., AND-12): Fläche in getönter
 * Vereinsfarbe und alle Texte in der dazu geprüften Schriftfarbe. Vorher blieben Titel und
 * Zusatztext in den Farben der normalen Fläche und waren bei hellen Vereinsfarben im
 * Dunkelmodus nicht lesbar.
 */
@Composable
internal fun selectedRowColors(isSelected: Boolean): ListItemColors {
    if (!isSelected) return ListItemDefaults.colors()
    val scheme = MaterialTheme.colorScheme
    return ListItemDefaults.colors(
        containerColor = scheme.primaryContainer,
        headlineColor = scheme.onPrimaryContainer,
        supportingColor = scheme.onPrimaryContainer,
        overlineColor = scheme.onPrimaryContainer,
        leadingIconColor = scheme.onPrimaryContainer,
        trailingIconColor = scheme.onPrimaryContainer,
    )
}

/**
 * Balken am Anfang der gewählten Zeile: Im hellen Modus hebt sich die getönte Fläche kaum vom
 * Hintergrund ab, der Balken zeigt die Auswahl auch ohne Farbunterschied der Fläche (AND-12).
 */
internal fun Modifier.selectedRowMark(isSelected: Boolean, color: Color): Modifier =
    if (!isSelected) this else drawWithContent {
        drawContent()
        drawRect(color, size = Size(4.dp.toPx(), size.height))
    }
