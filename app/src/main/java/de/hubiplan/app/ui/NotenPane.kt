package de.hubiplan.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.FolderItem
import de.hubiplan.app.data.LocalAccountData
import de.hubiplan.app.data.Mappe
import de.hubiplan.app.data.NotenStore
import de.hubiplan.app.data.NotenSuche
import de.hubiplan.app.data.NotenSync
import de.hubiplan.app.data.PultEinstellungen
import de.hubiplan.app.data.PultStelle
import de.hubiplan.app.data.ScoreFolders
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Notenschlüssel-Symbol für die Navigationsleiste (Material „music_note“, Apache 2.0). */
val NotenSymbol: ImageVector by lazy {
    ImageVector.Builder("Noten", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes("M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"),
            fill = SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
    }.build()
}

/**
 * Bereich „Noten“ (Konzept Noten in der App, N3): feste Mappen und Termine, Suche über alles,
 * Favoriten und zuletzt gespielt. Alles läuft auch ohne Netz mit dem Stand des letzten Abgleichs.
 */
@Composable
fun NotenPane(accessId: Int, main: Screen.Main, vm: AppViewModel) {
    val context = LocalContext.current
    val accountKey = accountKey(main.account)
    val store = remember(accountKey) { NotenStore(context, LocalAccountData.key(main.account.server, main.account.deviceId)) }
    var folders by remember(accessId) { mutableStateOf<ScoreFolders?>(null) }
    var error by remember(accessId) { mutableStateOf<String?>(null) }
    var stale by remember(accessId) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable(accessId) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(accessId) { mutableStateOf("") }
    // Merkliste neu lesen, wenn sich ein Stern ändert
    var marks by remember { mutableIntStateOf(0) }

    LaunchedEffect(accessId, reload) {
        error = null
        if (folders == null) {
            withContext(Dispatchers.IO) { store.folders(accessId) }?.let { folders = it; stale = true }
        }
        val api = vm.api()
        if (api == null || main.offline) {
            if (folders == null) error = tr("Ohne Verbindung sind noch keine Noten auf diesem Gerät.")
            return@LaunchedEffect
        }
        try {
            folders = NotenSync(api, store, accessId).refresh()
            stale = false
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else if (folders == null) error = e.message
        }
    }

    val mappen = folders?.mappen.orEmpty()
    val open: (Mappe, Int) -> Unit = { mappe, piece ->
        val start = store.position(accessId, mappe.key)?.takeIf { it.piece == piece } ?: PultStelle(piece, 0)
        vm.openPult(PultSession(accountKey, LocalAccountData.key(main.account.server, main.account.deviceId), accessId, mappen, mappe.key, start))
    }

    ListDetail(
        hasDetail = selected != null,
        onBack = { selected = null },
        emptyDetail = tr("Mappe auswählen"),
        list = {
            Column(Modifier.fillMaxSize()) {
                SearchField(query) { query = it }
                if (stale && folders != null) {
                    Text(
                        tr("Stand des letzten Abgleichs"),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = Abstand.rand),
                    )
                }
                when {
                    error != null -> Column(Modifier.padding(Abstand.rand)) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true))
                        TextButton(onClick = { reload++ }) { Text(tr("Erneut laden")) }
                    }
                    folders == null -> Loading()
                    query.isNotBlank() -> SearchResults(query, mappen, open)
                    mappen.isEmpty() -> Message(tr("Für deine Stimme sind noch keine Noten freigegeben."))
                    else -> FolderList(accessId, mappen, store, selected, marks, open) { selected = it }
                }
            }
        },
        detail = {
            val mappe = mappen.firstOrNull { it.key == selected }
            if (mappe == null) {
                if (selected != null && folders != null) LaunchedEffect(selected) { selected = null }
            } else {
                BackHead(mappe.name) { selected = null }
                MappeDetail(accessId, mappe, store, vm, marks, { marks++ }) { piece -> open(mappe, piece) }
            }
        },
    )
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(tr("Titel, Komponist oder Nummer")) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, contentDescription = tr("Suche leeren")) }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Abstand.rand, vertical = Abstand.klein),
    )
}

/** Wo ein Stück steht: „Marschbuch Nr. 27 · Schützenfest“. */
internal fun stellenText(stellen: List<NotenSuche.Stelle>): String =
    stellen.joinToString(" · ") { s ->
        if (s.item.number.isNotBlank()) tr("{mappe} Nr. {number}", "mappe" to s.mappe.name, "number" to s.item.number) else s.mappe.name
    }

@Composable
private fun SearchResults(query: String, mappen: List<Mappe>, open: (Mappe, Int) -> Unit) {
    val hits = remember(query, mappen) { NotenSuche.suche(query, mappen) }
    if (hits.isEmpty()) {
        Message(tr("Kein Stück gefunden."))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(hits, key = { it.arrangement }) { hit ->
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) {
                    val first = hit.stellen.first()
                    open(first.mappe, first.mappe.pieces.indexOf(first.item).coerceAtLeast(0))
                },
                headlineContent = { Text(hit.item.title) },
                supportingContent = {
                    Text(listOf(hit.item.composer, stellenText(hit.stellen)).filter { it.isNotBlank() }.joinToString(" · "))
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FolderList(
    accessId: Int,
    mappen: List<Mappe>,
    store: NotenStore,
    selected: String?,
    marks: Int,
    open: (Mappe, Int) -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val favorites = remember(accessId, marks) { store.favorites(accessId) }
    val recent = remember(accessId, marks) { store.recent(accessId) }
    var bytes by remember { mutableStateOf(-1L) }
    var cleared by remember { mutableIntStateOf(0) }
    LaunchedEffect(cleared, marks) { bytes = withContext(Dispatchers.IO) { store.bytes() } }
    var settings by remember { mutableStateOf(PultEinstellungen.load(context)) }

    // Für Favoriten und „zuletzt gespielt“: erste Mappe, in der das Stück steht
    fun where(arrangement: Int): Pair<Mappe, Int>? = mappen.firstNotNullOfOrNull { m ->
        m.pieces.indexOfFirst { it.arrangement == arrangement }.takeIf { it >= 0 }?.let { m to it }
    }
    val quick = (recent.take(5) + favorites).distinct().mapNotNull { id -> where(id)?.let { id to it } }

    LazyColumn(Modifier.fillMaxSize()) {
        if (quick.isNotEmpty()) {
            item { SectionHead(tr("Favoriten und zuletzt gespielt")) }
            items(quick, key = { "q-" + it.first }) { (id, place) ->
                val item = place.first.pieces[place.second]
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { open(place.first, place.second) },
                    leadingContent = { if (id in favorites) Icon(Icons.Filled.Star, contentDescription = tr("Favorit")) },
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(place.first.name + if (item.number.isNotBlank()) " · " + tr("Nr. {number}", "number" to item.number) else "") },
                )
            }
        }
        val fixed = mappen.filter { !it.isEvent }
        val events = mappen.filter { it.isEvent }
        if (fixed.isNotEmpty()) {
            item { SectionHead(tr("Notenmappen")) }
            items(fixed, key = { it.key }) { m -> MappeRow(m, m.key == selected) { onSelect(m.key) } }
        }
        if (events.isNotEmpty()) {
            item { SectionHead(tr("Proben und Auftritte")) }
            items(events, key = { it.key }) { m -> MappeRow(m, m.key == selected) { onSelect(m.key) } }
        }
        item {
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Proben und Auftritte am Vortag über WLAN laden"), Modifier.weight(1f))
                    Switch(checked = settings.prefetch, onCheckedChange = { on ->
                        settings = settings.copy(prefetch = on).also { it.save(context) }
                    })
                }
                if (bytes > 0) {
                    Text(
                        tr("Noten auf dem Gerät: {size}", "size" to formatBytes(bytes)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        store.clearFiles()
                        cleared++
                    }) { Text(tr("Noten vom Gerät löschen")) }
                }
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0).replace('.', if (de.hubiplan.app.Texte.sprache == "de") ',' else '.')
    else -> "${(bytes + 1023) / 1024} KB"
}

@Composable
private fun SectionHead(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Abstand.rand, end = Abstand.rand, top = Abstand.mittel, bottom = Abstand.eng).asHeading(),
    )
}

@Composable
private fun MappeRow(m: Mappe, isSelected: Boolean, onClick: () -> Unit) {
    val count = m.pieces.size
    ListItem(
        modifier = Modifier.semantics { selected = isSelected }.clickable(role = Role.Button, onClick = onClick),
        colors = if (isSelected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ListItemDefaults.colors(),
        overlineContent = {
            val head = when (m.kind) {
                "marchbook" -> tr("Marschbuch")
                "repertoire" -> tr("Repertoire")
                "performance" -> tr("Auftritt") + " · " + formatDate(m.startsAt)
                "rehearsal" -> tr("Probe") + " · " + formatDate(m.startsAt)
                else -> ""
            }
            if (head.isNotBlank()) Text(head)
        },
        headlineContent = { Text(m.name) },
        supportingContent = {
            Text(
                listOfNotNull(
                    trn("1 Stück", "{n} Stücke", count),
                    tr("abgesagt").takeIf { m.cancelled },
                    m.location.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
            )
        },
    )
    HorizontalDivider()
}

private enum class Sortierung { REIHENFOLGE, NUMMER, TITEL }

/** Natürliche Ordnung für Nummern: 2 < 10 < 10a < ohne Nummer. */
internal fun nummerSchluessel(number: String): String {
    val digits = number.takeWhile { it.isDigit() }.take(9)
    val rest = number.drop(digits.length).lowercase()
    return if (digits.isEmpty()) "~$rest" else digits.padStart(9, '0') + rest
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MappeDetail(
    accessId: Int,
    mappe: Mappe,
    store: NotenStore,
    vm: AppViewModel,
    marks: Int,
    onMarked: () -> Unit,
    play: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var progress by remember(mappe.key) { mutableStateOf<Float?>(null) }
    var notice by remember(mappe.key) { mutableStateOf<String?>(null) }
    var complete by remember(mappe.key) { mutableStateOf(false) }
    var kept by remember(mappe.key, marks) { mutableStateOf(mappe.key in store.kept(accessId)) }
    val favorites = remember(accessId, marks) { store.favorites(accessId) }
    var sort by rememberSaveable(mappe.key) { mutableStateOf(Sortierung.REIHENFOLGE) }
    val resume = remember(mappe.key) { store.position(accessId, mappe.key) }
    val audio = remember { Hoerbeispiel() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { audio.release() } }
    val context = LocalContext.current
    var playing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(mappe.key, progress) {
        complete = withContext(Dispatchers.IO) { store.complete(accessId, mappe) }
    }
    val pieces = mappe.pieces
    val hasNumbers = pieces.any { it.number.isNotBlank() }
    val shown: List<FolderItem> = when (sort) {
        Sortierung.REIHENFOLGE -> mappe.items
        Sortierung.NUMMER -> pieces.sortedBy { nummerSchluessel(it.number) }
        Sortierung.TITEL -> pieces.sortedBy { NotenSuche.normalize(it.title) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Abstand.gross)) {
        item {
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                if (mappe.isEvent) {
                    Text(listOf(formatDate(mappe.startsAt), mappe.location).filter { it.isNotBlank() }.joinToString(" · "))
                }
                if (mappe.cancelled) Text(tr("Abgesagt: Noten gibt es dafür nicht mehr."), color = MaterialTheme.colorScheme.error)
                if (mappe.note.isNotBlank()) Text(mappe.note, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    if (pieces.isNotEmpty()) {
                        if (resume != null && (resume.piece > 0 || resume.page > 0) && resume.piece < pieces.size) {
                            Button(onClick = { play(resume.piece) }) { Text(tr("Weiterspielen")) }
                            OutlinedButton(onClick = { play(0) }) { Text(tr("Von vorn")) }
                        } else {
                            Button(onClick = { play(0) }) { Text(tr("Spielen")) }
                        }
                    }
                    if (pieces.isNotEmpty() && progress == null) {
                        OutlinedButton(onClick = {
                            val api = vm.api() ?: run { notice = tr("Ohne Verbindung lassen sich keine Noten laden."); return@OutlinedButton }
                            if (!mappe.isEvent) {
                                store.keep(accessId, mappe.key, true)
                                kept = true
                            }
                            progress = 0f
                            notice = null
                            scope.launch {
                                val failed = NotenSync(api, store, accessId).download(mappe, PultEinstellungen.load(context).lastLayout) { done, total ->
                                    progress = if (total == 0) 1f else done.toFloat() / total
                                }
                                notice = if (failed == 0) tr("Alle Noten sind auf dem Gerät.") else trn(
                                    "1 Datei ließ sich nicht laden.", "{n} Dateien ließen sich nicht laden.", failed,
                                )
                                progress = null
                                onMarked()
                            }
                        }) { Text(if (complete) tr("Auf dem Gerät") else tr("Für unterwegs laden")) }
                    }
                    if (!mappe.isEvent && kept) {
                        TextButton(onClick = { store.keep(accessId, mappe.key, false); kept = false; onMarked() }) {
                            Text(tr("Nicht mehr dauerhaft behalten"))
                        }
                    }
                }
                progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                notice?.let { StatusNotice(it) }
                if (!mappe.isEvent && hasNumbers) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        FilterChip(sort == Sortierung.REIHENFOLGE, onClick = { sort = Sortierung.REIHENFOLGE }, label = { Text(tr("Reihenfolge")) })
                        FilterChip(sort == Sortierung.NUMMER, onClick = { sort = Sortierung.NUMMER }, label = { Text(tr("Nummer")) })
                        FilterChip(sort == Sortierung.TITEL, onClick = { sort = Sortierung.TITEL }, label = { Text(tr("A–Z")) })
                    }
                } else if (!mappe.isEvent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        FilterChip(sort == Sortierung.REIHENFOLGE, onClick = { sort = Sortierung.REIHENFOLGE }, label = { Text(tr("Reihenfolge")) })
                        FilterChip(sort == Sortierung.TITEL, onClick = { sort = Sortierung.TITEL }, label = { Text(tr("A–Z")) })
                    }
                }
            }
            HorizontalDivider()
        }
        items(shown, key = { "${it.kind}-${it.arrangement}-${it.position}" }) { item ->
            if (!item.isPiece) {
                Text(
                    item.title.ifBlank { item.note }.ifBlank { tr("Pause") },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Abstand.rand, vertical = Abstand.klein),
                )
                HorizontalDivider()
                return@items
            }
            val index = pieces.indexOf(item)
            val arrangement = item.arrangement!!
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) { play(index) },
                leadingContent = if (item.number.isNotBlank()) {
                    { Text(item.number, style = MaterialTheme.typography.titleMedium) }
                } else null,
                headlineContent = { Text(item.title) },
                supportingContent = {
                    Column {
                        val info = listOf(item.composer, item.arrangementName, item.note).filter { it.isNotBlank() }.joinToString(" · ")
                        if (info.isNotBlank()) Text(info)
                        if (item.sheets.isEmpty() && !mappe.cancelled) {
                            Text(tr("Keine Noten für deine Stimme"), color = MaterialTheme.colorScheme.error)
                        }
                        if (item.licenseNote.isNotBlank()) Text("ⓘ " + item.licenseNote, style = MaterialTheme.typography.bodySmall)
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        item.audio?.let { score ->
                            IconButton(onClick = {
                                if (playing == arrangement) {
                                    audio.stop(); playing = null
                                } else {
                                    val api = vm.api()
                                    scope.launch {
                                        val bytes = runCatching {
                                            if (api == null) withContext(Dispatchers.IO) { store.read(accessId, score, "midi") } ?: error("offline")
                                            else NotenSync(api, store, accessId).audio(score)
                                        }.getOrNull()
                                        if (bytes == null) {
                                            notice = tr("Das Hörbeispiel ließ sich nicht laden.")
                                        } else {
                                            playing = arrangement
                                            audio.play(context, bytes) { playing = null }
                                        }
                                    }
                                }
                            }) {
                                Icon(
                                    if (playing == arrangement) Icons.Filled.Clear else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing == arrangement) tr("Anhören beenden") else tr("Anhören"),
                                )
                            }
                        }
                        val fav = arrangement in favorites
                        IconButton(onClick = { store.toggleFavorite(accessId, arrangement); onMarked() }) {
                            Icon(
                                if (fav) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (fav) tr("Kein Favorit mehr") else tr("Als Favorit merken"),
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
        }
    }
}
