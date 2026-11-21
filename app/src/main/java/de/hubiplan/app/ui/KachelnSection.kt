package de.hubiplan.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.hubiplan.app.data.HomeTile
import de.hubiplan.app.data.HomeTiles
import de.hubiplan.app.data.TileState
import de.hubiplan.app.data.serverLink
import de.hubiplan.app.tr
import kotlinx.coroutines.launch

/** Beschriftung der Auswahl einer Kachel (wie „Startseite anpassen“ im Browser). */
internal fun tileStateLabel(state: String): String = when (state) {
    TileState.ON -> tr("immer")
    TileState.OFF -> tr("nie")
    else -> tr("bei Anlass")
}

/**
 * Übersicht mit den Kacheln der Startseite (hubiplan backend/hometiles): Kacheln mit Anlass
 * erscheinen von selbst, „Kacheln anpassen“ stellt jede auf „bei Anlass“, „immer“ oder „nie“.
 * Gleiche Kacheln wie im Browser und in der iOS-App. Ohne Antwort des Servers bleibt die
 * Übersicht einfach weg; Fehler zeigen die Bereiche selbst.
 *
 * [server]: eigene Instanz, Verweise öffnen dort im Browser.
 */
@Composable
internal fun TilesOverview(
    server: String,
    reloadKey: Any?,
    load: suspend () -> HomeTiles?,
    save: suspend (key: String, state: String) -> HomeTiles?,
) {
    var data by remember { mutableStateOf<HomeTiles?>(null) }
    var customizing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reloadKey) { data = load() }
    val tiles = data ?: return
    if (tiles.tiles.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
        tiles.visible.forEach { TileCard(it, server) }
        TextButton(onClick = { customizing = true }) {
            Text(if (tiles.visible.isEmpty()) tr("Kacheln einblenden") else tr("Kacheln anpassen"))
        }
    }
    if (customizing) {
        AlertDialog(
            onDismissRequest = { customizing = false },
            title = { Text(tr("Kacheln anpassen")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    Text(
                        tr("„Bei Anlass“ zeigt eine Kachel nur, wenn gerade etwas ansteht. Die Auswahl gilt auch für die Startseite im Browser."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    tiles.tiles.forEachIndexed { i, tile ->
                        if (i > 0) HorizontalDivider()
                        Text(tile.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                        if (tile.description.isNotBlank()) {
                            Text(tile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TileState.ALL.forEach { state ->
                            LabeledRadio(
                                label = tileStateLabel(state),
                                selected = tile.state == state,
                                enabled = !busy,
                                onClick = {
                                    if (tile.state != state) {
                                        busy = true
                                        scope.launch {
                                            save(tile.key, state)?.let { data = it }
                                            busy = false
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { customizing = false }) { Text(tr("Fertig")) } },
        )
    }
}

@Composable
private fun toneColor(tone: String?): Color = when (tone) {
    "bad" -> MaterialTheme.colorScheme.error
    "warn" -> MaterialTheme.colorScheme.tertiary
    "good" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileCard(tile: HomeTile, server: String) {
    val context = LocalContext.current
    val messages = LocalActionMessages.current
    val open: (String?) -> Unit = { path ->
        val url = path?.let { serverLink(server, it) }
        if (url == null) {
            messages?.show(tr("Dieser Verweis lässt sich nicht öffnen."), error = true)
        } else {
            openInBrowser(context, url, messages)
        }
    }
    val c = tile.content
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Text(tile.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            if (c == null) {
                Text(tile.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (c.reason.isNotBlank()) {
                Text(c.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            if (c.figures.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    c.figures.forEach { f ->
                        Column(Modifier.widthIn(min = 96.dp)) {
                            Text(f.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(f.value, style = MaterialTheme.typography.titleMedium, color = toneColor(f.tone))
                        }
                    }
                }
            }
            c.rows.forEach { r ->
                val rowModifier = if (r.link != null) {
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = tr("Öffnen"), role = Role.Button) { open(r.link) }
                } else {
                    Modifier.fillMaxWidth()
                }
                Column(rowModifier.padding(vertical = Abstand.eng)) {
                    Text(r.title, style = MaterialTheme.typography.bodyLarge, color = toneColor(r.tone), fontWeight = FontWeight.SemiBold)
                    if (r.detail.isNotBlank()) {
                        Text(r.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (c.text.isNotBlank()) {
                Text(c.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            c.link?.let { link ->
                TextButton(onClick = { open(link) }) { Text(c.linkLabel.ifBlank { tr("Öffnen") }) }
            }
        }
    }
}
