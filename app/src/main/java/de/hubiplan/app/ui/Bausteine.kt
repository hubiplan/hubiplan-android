package de.hubiplan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/*
 * Bausteine der Gestaltung „Frisch mobil“ (wie die iOS-App, Werte in assets/design/werte.json):
 * flache Karten mit Rand statt Schatten, Zeilen mit Symbolkopf, Abschnittstitel, Zähler,
 * Leerzustand und Platzhalter beim Laden. Neue Bildschirme setzen sich aus diesen Teilen zusammen.
 */

/** Anteil der Vereinsfarbe in getönten Flächen (werte.json: marke.getoent). */
const val GETOENT = 0.11f

/** Höhe einer antippbaren Zeile (werte.json: ziel.zeile). */
const val ZEILE_HOEHE = 56

/** Seitenlänge des Symbolkopfs (werte.json: symbol.kopf). */
const val SYMBOLKOPF = 36

/** Kartenfläche: weiß im Hellen, eine Stufe heller als der Grund im Dunkeln. */
@Composable
fun kartenFlaeche(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) scheme.surfaceContainer else scheme.surfaceContainerLowest
}

/** Getönte Fläche in Vereinsfarbe, deckend über der Kartenfläche gemischt. */
@Composable
fun getoenteFlaeche(): Color = MaterialTheme.colorScheme.primary.copy(alpha = GETOENT).compositeOver(kartenFlaeche())

/** Flache Karte mit Rand; mit [onClick] als Ganzes antippbar. */
@Composable
fun Karte(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val form = RoundedCornerShape(RADIUS_GROSS.dp)
    var m = modifier
        .clip(form)
        .background(kartenFlaeche())
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, form)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Column(m, content = content)
}

/** Titel über einem Abschnitt, als Überschrift für TalkBack; rechts wahlweise ein Textknopf. */
@Composable
fun Abschnittstitel(text: String, modifier: Modifier = Modifier, aktion: String? = null, onAktion: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(top = Abstand.rand, bottom = Abstand.klein),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).asHeading(),
        )
        if (aktion != null) TextButton(onClick = onAktion) { Text(aktion) }
    }
}

/** Symbol auf getönter Fläche, vorn in Zeilen und Karten. Nur Blickfang, ohne Ansage. */
@Composable
fun SymbolKopf(symbol: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(SYMBOLKOPF.dp)
            .background(getoenteFlaeche(), RoundedCornerShape(RADIUS_GROSS.dp))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/**
 * Zeile einer Liste: Symbolkopf, Titel, Kurzstand darunter, rechts [ende] (Betrag, Abzeichen,
 * Zähler) und ein Pfeil, wenn sie weiterführt. TalkBack liest die Zeile als ein Element.
 */
@Composable
fun Zeile(
    titel: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    symbol: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    pfeil: Boolean = onClick != null,
    ende: (@Composable () -> Unit)? = null,
) {
    var m = modifier.fillMaxWidth().heightIn(min = ZEILE_HOEHE.dp)
    m = if (onClick != null) m.clickable(role = Role.Button, onClick = onClick) else m.semantics(mergeDescendants = true) { }
    Row(
        m.padding(horizontal = Abstand.rand, vertical = Abstand.mittel),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Abstand.mittel),
    ) {
        symbol?.let { SymbolKopf(it) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Abstand.zeile)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ende?.invoke()
        if (pfeil) {
            Icon(
                strichsymbol(Strich.WEITER),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Kurze spürbare Bestätigung nach Zusage, Buchung oder Scan (Gestaltung „Frisch mobil“). Das Gerät
 * beachtet dabei seine eigene Einstellung für Berührungsrückmeldung.
 */
@Composable
fun rememberBestaetigung(): () -> Unit {
    val view = androidx.compose.ui.platform.LocalView.current
    return remember(view) {
        {
            val art = if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.view.HapticFeedbackConstants.CONFIRM
            } else {
                android.view.HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(art)
        }
    }
}

/** Trennlinie zwischen zwei Zeilen einer Karte. */
@Composable
fun ZeilenTrenner() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Beschriftung eines Zählers: ab 100 „99+“. */
fun zaehlerText(anzahl: Int): String = if (anzahl > 99) "99+" else anzahl.toString()

/** Zähler in Vereinsfarbe; bei 0 nichts. */
@Composable
fun Zaehler(anzahl: Int, modifier: Modifier = Modifier) {
    if (anzahl <= 0) return
    Box(
        modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(11.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            zaehlerText(anzahl),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Leerer Zustand: Symbol, ein Satz und wahlweise der nächste Schritt. */
@Composable
fun Leerzustand(
    symbol: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    aktion: String? = null,
    onAktion: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(Abstand.gross),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
    ) {
        Icon(
            symbol,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (aktion != null) TextButton(onClick = onAktion) { Text(aktion) }
    }
}

/** Platzhalter in Form von Zeilen, solange Inhalte laden; TalkBack liest nur [ansage]. */
@Composable
fun Platzhalter(ansage: String, modifier: Modifier = Modifier, zeilen: Int = 3) {
    val farbe = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = ansage },
        verticalArrangement = Arrangement.spacedBy(Abstand.klein),
    ) {
        repeat(zeilen) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = ZEILE_HOEHE.dp).padding(horizontal = Abstand.rand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Abstand.mittel),
            ) {
                Box(Modifier.size(SYMBOLKOPF.dp).background(farbe, RoundedCornerShape(RADIUS_GROSS.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).background(farbe, RoundedCornerShape(RADIUS_KLEIN.dp)))
                    Box(Modifier.fillMaxWidth(0.4f).height(12.dp).background(farbe, RoundedCornerShape(RADIUS_KLEIN.dp)))
                }
            }
        }
    }
}
