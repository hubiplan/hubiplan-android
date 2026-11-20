package de.hubiplan.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.PinNotice
import de.hubiplan.app.data.PinboardList
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.SafeBitmap
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kurzstand rechts neben „Pinnwand“. */
internal fun pinboardSummary(list: PinboardList): String = when {
    list.newCount > 0 -> trn("1 neuer Beitrag", "{n} neue Beiträge", list.newCount)
    list.notices.isEmpty() -> tr("Noch keine Beiträge")
    else -> tr("Kurze Neuigkeiten aus dem Verein")
}

/** Zweite Zeile eines Beitrags: wer, wann, bis wann. */
internal fun pinNoticeLine(n: PinNotice): String = listOfNotNull(
    n.author.ifBlank { null },
    n.createdAt?.let { formatDayTime(it) },
    if (n.editedAt != null) tr("geändert") else null,
    n.visibleUntil?.let { tr("sichtbar bis {day}", "day" to formatPlainDate(it)) },
).joinToString(" · ")

/**
 * Pinnwand unter „Ich“ (HUBIplan Pinnwand): Beiträge des Vereins und der eigenen Untereinheiten,
 * angeheftete oben. Kein Chat. Wer in HUBIplan schreiben darf, schreibt hier auch, mit Fotos aus
 * Kamera oder Galerie und auf Wunsch Push. Nichts wird auf dem Gerät gespeichert.
 */
@Composable
internal fun PinnwandSection(access: PortalAccess, vm: AppViewModel) {
    var list by remember(access.id) { mutableStateOf<PinboardList?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    var notice by remember(access.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var writing by rememberSaveable(access.id) { mutableStateOf(false) }
    var remove by remember { mutableStateOf<PinNotice?>(null) }
    var big by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            list = api.memberPinboard(access.id)
            error = null
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    fun act(done: String, action: suspend (de.hubiplan.app.data.Api) -> Unit) {
        val api = vm.api() ?: return
        if (busy) return
        busy = true
        notice = null
        error = null
        scope.launch {
            try {
                action(api)
                notice = done
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
            } finally {
                busy = false
                attempt++
            }
        }
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        ActionNotice(notice) { notice = null }
        val l = list
        if (l == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        if (l.targets.isNotEmpty()) {
            if (writing) {
                PinNoticeForm(access, vm, l, onDone = { made ->
                    writing = false
                    if (made != null) {
                        notice = if (made) tr("Beitrag veröffentlicht, Push geht an die App.") else tr("Beitrag veröffentlicht.")
                        attempt++
                    }
                })
            } else {
                Button(onClick = { writing = true }) { Text(tr("Neuer Beitrag")) }
            }
        }
        if (l.notices.isEmpty()) {
            Text(tr("Hier erscheinen kurze Neuigkeiten deines Vereins und deiner Untereinheiten."))
        }
        l.notices.forEach { n ->
            PinNoticeCard(
                access, vm, n, busy,
                onPin = { act(if (n.pinned) tr("Beitrag nicht mehr angeheftet.") else tr("Beitrag angeheftet.")) { it.pinPinNotice(access.id, n, !n.pinned) } },
                onDelete = { remove = n },
                onPhoto = { big = it },
            )
        }
    }
    remove?.let { n ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text(tr("Beitrag löschen?")) },
            text = { Text(n.text.take(120)) },
            confirmButton = {
                TextButton(onClick = {
                    remove = null
                    act(tr("Beitrag gelöscht.")) { it.deletePinNotice(access.id, n.id) }
                }) { Text(tr("Löschen")) }
            },
            dismissButton = { TextButton(onClick = { remove = null }) { Text(tr("Abbrechen")) } },
        )
    }
    big?.let { bitmap ->
        Dialog(onDismissRequest = { big = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = tr("Foto"),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { big = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinNoticeCard(
    access: PortalAccess,
    vm: AppViewModel,
    n: PinNotice,
    busy: Boolean,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onPhoto: (Bitmap) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Text(n.from, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false).asHeading())
                if (n.pinned) Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(tr("angeheftet")) }
                if (n.new) Badge { Text(tr("neu")) }
            }
            Text(n.text)
            if (n.photos.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    n.photos.forEachIndexed { i, p ->
                        PinPhotoThumb(vm, p.url, tr("Foto {n} von {m}", "n" to i + 1, "m" to n.photos.size), onPhoto)
                    }
                }
            }
            Text(pinNoticeLine(n), style = MaterialTheme.typography.bodySmall)
            if (n.canEdit) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    OutlinedButton(onClick = onPin, enabled = !busy) {
                        Text(if (n.pinned) tr("Nicht mehr anheften") else tr("Anheften"))
                    }
                    TextButton(onClick = onDelete, enabled = !busy) { Text(tr("Löschen")) }
                }
            }
        }
    }
}

/** Vorschaubild; lädt über die Anmeldung der App, Antippen zeigt es groß. */
@Composable
private fun PinPhotoThumb(vm: AppViewModel, url: String, description: String, onOpen: (Bitmap) -> Unit) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        val api = vm.api() ?: return@LaunchedEffect
        bitmap = api.image(url)?.let { withContext(Dispatchers.Default) { SafeBitmap.decode(it, 1600) } }
    }
    val b = bitmap
    if (b == null) {
        Text(description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.size(96.dp))
    } else {
        Image(
            b.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp).clickable(role = Role.Button) { onOpen(b) },
        )
    }
}

/** Neuer Beitrag: Ziel (Verein oder Untereinheit), Text, Fotos, Anheften, Push. Ergebnis: Push ja/nein, null = abgebrochen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinNoticeForm(access: PortalAccess, vm: AppViewModel, list: PinboardList, onDone: (Boolean?) -> Unit) {
    var text by rememberSaveable(access.id) { mutableStateOf("") }
    var target by rememberSaveable(access.id) { mutableIntStateOf(0) }
    var pinned by rememberSaveable(access.id) { mutableStateOf(false) }
    var push by rememberSaveable(access.id) { mutableStateOf(false) }
    val photos = remember(access.id) { mutableStateListOf<ByteArray>() }
    // Ein Schlüssel je Formular: erneutes Senden nach einem Abbruch legt nichts doppelt an
    val key = remember(access.id) { UUID.randomUUID().toString() }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Problem?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val saved = rememberFormDraft(text, photos.size, busy = busy, retainOnDispose = false)
    val leave = LocalFormLeaveGuard.current
    val (shoot, pick) = rememberReceiptPhotos(
        onPhoto = { if (photos.size < list.maxPhotos) photos.add(it) },
        onFail = { problem = Problem(it) },
    )
    fun send() {
        val api = vm.api() ?: return
        if (busy || text.isBlank()) return
        busy = true
        problem = null
        scope.launch {
            try {
                val dept = list.targets.getOrNull(target)?.department
                val made = api.createPinNotice(access.id, text.trim(), dept, pinned, push, photos.toList(), key)
                photos.clear()
                saved()
                onDone(made.pushed || push)
            } catch (e: ApiException) {
                if (e.status == 401) {
                    vm.deviceRevoked(e)
                } else {
                    fieldErrors = e.fieldErrors
                    problem = if (e.network != null) {
                        Problem(tr("Ohne Verbindung: Die Angaben bleiben hier stehen. Bitte später erneut senden."), offline = true)
                    } else {
                        e.problem()
                    }
                }
            } finally {
                busy = false
            }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Text(tr("Neuer Beitrag"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            if (list.targets.size > 1) {
                Text(tr("An"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    list.targets.forEachIndexed { i, t ->
                        FilterChip(target == i, { target = i }, { Text(t.name) })
                    }
                }
            }
            OutlinedTextField(
                text, { text = it.take(list.maxText) },
                label = { Text(tr("Text")) },
                placeholder = { Text(tr("z. B. Der Bus fährt heute 10 Minuten später ab.")) },
                isError = "text" in fieldErrors,
                supportingText = fieldErrors["text"]?.let { { Text(it) } },
                keyboardOptions = FREITEXT,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (photos.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    photos.forEachIndexed { i, jpeg ->
                        val bitmap = remember(jpeg) { android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) }
                        Column {
                            bitmap?.let {
                                Image(it.asImageBitmap(), contentDescription = tr("Foto {n}", "n" to i + 1), modifier = Modifier.size(72.dp))
                            }
                            TextButton(
                                onClick = { photos.removeAt(i) },
                                modifier = Modifier.semantics { contentDescription = tr("Foto {n} entfernen", "n" to i + 1) },
                            ) { Text(tr("Entfernen")) }
                        }
                    }
                }
            }
            fieldErrors["photos"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (photos.size < list.maxPhotos) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    OutlinedButton(onClick = shoot, enabled = !busy) { Text(tr("Foto aufnehmen")) }
                    OutlinedButton(onClick = pick, enabled = !busy) { Text(tr("Aus der Galerie")) }
                }
            }
            CheckRow(tr("Oben anheften"), pinned) { pinned = it }
            CheckRow(tr("Push an die App senden"), push) { push = it }
            problem?.let { ProblemNotice(it, onRetry = null, inset = false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Button(onClick = { send() }, enabled = !busy && text.isNotBlank()) { Text(tr("Veröffentlichen")) }
                TextButton(onClick = { if (leave == null) onDone(null) else leave.request { onDone(null) } }, enabled = !busy) { Text(tr("Abbrechen")) }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = Abstand.klein))
    }
}
