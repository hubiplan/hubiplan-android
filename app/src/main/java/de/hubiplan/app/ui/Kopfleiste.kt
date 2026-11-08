package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.hubiplan.app.tr

/**
 * Pfeil zurück und Titel einer Detailansicht stehen in der Kopfleiste, nicht als „‹ Zurück“ im
 * Inhalt (UX-Audit 08.11. A-01). Eine Detailansicht meldet sich mit [BackHead] an; die zuletzt
 * geöffnete gilt. Beim Schließen der Ansicht meldet sie sich selbst wieder ab.
 */
class BackHost {
    private var next = 0L
    private val entries = mutableStateListOf<BackEntry>()

    /** Die zuletzt geöffnete Detailansicht, sonst null. */
    val current: BackEntry? get() = entries.maxByOrNull { it.seq }

    internal fun open(onBack: () -> Unit): BackEntry = BackEntry(next++, onBack)
    internal fun add(entry: BackEntry) { entries += entry }
    internal fun remove(entry: BackEntry) { entries -= entry }
}

class BackEntry internal constructor(internal val seq: Long, val onBack: () -> Unit) {
    var title by mutableStateOf<String?>(null)
        internal set
    var enabled by mutableStateOf(true)
        internal set
}

val LocalBackHost = staticCompositionLocalOf<BackHost?> { null }

/**
 * Meldet Pfeil zurück und [title] für die Kopfleiste an. Ohne Kopfleiste (z. B. in einem
 * Vollbild-Dialog) steht der Pfeil mit Titel oben im Inhalt.
 */
@Composable
fun BackHead(title: String? = null, enabled: Boolean = true, onBack: () -> Unit) {
    val latest by rememberUpdatedState(onBack)
    val host = LocalBackHost.current
    if (host == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { latest() }, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Zurück"))
            }
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        }
        return
    }
    val entry = remember(host) { host.open { latest() } }
    SideEffect {
        entry.title = title
        entry.enabled = enabled
    }
    DisposableEffect(host, entry) {
        host.add(entry)
        onDispose { host.remove(entry) }
    }
}
