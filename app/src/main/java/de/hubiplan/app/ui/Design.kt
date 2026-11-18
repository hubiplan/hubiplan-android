package de.hubiplan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.hubiplan.app.Texte
import java.time.OffsetDateTime
import java.time.ZoneId

/*
 * Design „Frisch“ (wie die Weboberfläche von HUBIplan): ruhige, wenig gerundete Formen,
 * Zustände als Abzeichen in den Zustandsfarben der Weboberfläche und ein Datumskästchen
 * vor Terminen. Die Werte folgen frontend/src/theme/theme.css.
 */

/** Rundungen in dp wie in der Weboberfläche (--radius 6, --radius-lg 8). */
const val RADIUS_KLEIN = 4
const val RADIUS = 6
const val RADIUS_GROSS = 8
const val RADIUS_DIALOG = 12

/** Formen für Karten, Felder, Chips, Menüs und Dialoge; Knöpfe behalten die Form von Material. */
val HubiShapes = Shapes(
    extraSmall = RoundedCornerShape(RADIUS_KLEIN.dp),
    small = RoundedCornerShape(RADIUS.dp),
    medium = RoundedCornerShape(RADIUS_GROSS.dp),
    large = RoundedCornerShape(RADIUS_GROSS.dp),
    extraLarge = RoundedCornerShape(RADIUS_DIALOG.dp),
)

/** Bedeutung eines Abzeichens. Der Text trägt die Bedeutung, die Farbe hilft nur. */
enum class Zustand { GUT, HINWEIS, WARNUNG, FEHLER, NEUTRAL }

/** Schrift- und Flächenfarbe eines Abzeichens. */
data class ZustandFarben(val schrift: Long, val flaeche: Long)

/** Zustandsfarben wie in der Weboberfläche (--color-success, -info, -warning, -error samt -bg). */
fun zustandFarben(zustand: Zustand, dark: Boolean): ZustandFarben = when (zustand) {
    Zustand.GUT -> if (dark) ZustandFarben(0xFF8FD6B0L, 0xFF15302AL) else ZustandFarben(0xFF1D6B46L, 0xFFE6F3EEL)
    Zustand.HINWEIS -> if (dark) ZustandFarben(0xFFA9C7F2L, 0xFF18263AL) else ZustandFarben(0xFF1F4F8FL, 0xFFEEF3FBL)
    Zustand.WARNUNG -> if (dark) ZustandFarben(0xFFF4CF86L, 0xFF3A2C10L) else ZustandFarben(0xFF7A5200L, 0xFFFDF1D8L)
    Zustand.FEHLER -> if (dark) ZustandFarben(0xFFFFB4ABL, 0xFF3B1714L) else ZustandFarben(0xFFB3261EL, 0xFFFBE9E7L)
    Zustand.NEUTRAL -> if (dark) ZustandFarben(0xFFC9D1CDL, 0xFF2A302DL) else ZustandFarben(0xFF4A5551L, 0xFFEEF1F0L)
}

/** Zustand als kleines Abzeichen: Klartext, wahlweise mit Symbol davor. */
@Composable
fun Abzeichen(text: String, zustand: Zustand, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val farben = zustandFarben(zustand, isSystemInDarkTheme())
    Row(
        modifier
            .background(Color(farben.flaeche), RoundedCornerShape(RADIUS_KLEIN.dp))
            .padding(horizontal = Abstand.klein, vertical = Abstand.zeile),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Abstand.eng),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = Color(farben.schrift), modifier = Modifier.size(16.dp)) }
        Text(text, color = Color(farben.schrift), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

private val MONATE_DE = listOf("JAN", "FEB", "MÄR", "APR", "MAI", "JUN", "JUL", "AUG", "SEP", "OKT", "NOV", "DEZ")
private val MONATE_EN = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

/** Tag und Monatskürzel für das Datumskästchen („14“, „NOV“); null bei unlesbarem Zeitpunkt. */
fun datumKaestchen(iso: String, zone: ZoneId = ZoneId.systemDefault(), english: Boolean = Texte.sprache == "en"): Pair<String, String>? {
    val date = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDate() }.getOrNull() ?: return null
    val monate = if (english) MONATE_EN else MONATE_DE
    return date.dayOfMonth.toString() to monate[date.monthValue - 1]
}

/**
 * Datumskästchen vor einem Termin. Nur Blickfang: Das Datum steht ausgeschrieben im Eintrag,
 * deshalb liest TalkBack das Kästchen nicht eigens vor.
 */
@Composable
fun DatumKaestchen(iso: String, gedaempft: Boolean = false) {
    val (tag, monat) = datumKaestchen(iso) ?: return
    val scheme = MaterialTheme.colorScheme
    val flaeche = if (gedaempft) scheme.surfaceVariant else scheme.primaryContainer
    val schrift = if (gedaempft) scheme.onSurfaceVariant else scheme.onPrimaryContainer
    Column(
        Modifier
            // Mindestens 48 dp, bei großer Schrift breiter statt abgeschnitten (AND-13)
            .widthIn(min = 48.dp)
            .background(flaeche, RoundedCornerShape(RADIUS.dp))
            .padding(vertical = Abstand.eng)
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tag, color = schrift, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(monat, color = schrift, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
