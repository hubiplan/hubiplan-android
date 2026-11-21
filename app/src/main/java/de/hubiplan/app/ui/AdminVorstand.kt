package de.hubiplan.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.hubiplan.app.data.AdminClient
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.ApplicationDetail
import de.hubiplan.app.data.ApplicationList
import de.hubiplan.app.data.ApplicationRow
import de.hubiplan.app.data.AttendanceList
import de.hubiplan.app.data.AttendanceRow
import de.hubiplan.app.data.BookingRequest
import de.hubiplan.app.data.LeadLive
import de.hubiplan.app.data.LeadPoll
import de.hubiplan.app.data.MeetingItem
import de.hubiplan.app.data.acceptApplication
import de.hubiplan.app.data.application
import de.hubiplan.app.data.applications
import de.hubiplan.app.data.approveApplication
import de.hubiplan.app.data.askApplication
import de.hubiplan.app.data.attendance
import de.hubiplan.app.data.bookingRequests
import de.hubiplan.app.data.checkinLink
import de.hubiplan.app.data.createPoll
import de.hubiplan.app.data.decideBooking
import de.hubiplan.app.data.deletePoll
import de.hubiplan.app.data.meeting
import de.hubiplan.app.data.meetingLive
import de.hubiplan.app.data.meetings
import de.hubiplan.app.data.pollAction
import de.hubiplan.app.data.rejectApplication
import de.hubiplan.app.data.removeAttendance
import de.hubiplan.app.data.setAttendance
import de.hubiplan.app.data.setCheckin
import de.hubiplan.app.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Vorstandsaufgaben in der App (wie im Browser): Beitrittsanträge entscheiden, Belegungsanfragen
// freigeben, Versammlung leiten. Vorbereiten, Korrigieren und neue Buchungen bleiben im Browser.

// ---------------------------------------------------------------- Beitrittsanträge

/** Zeile der Antragsliste: Stand, Mitgliedsart, was noch fehlt. */
fun applicationLine(row: ApplicationRow): String = listOfNotNull(
    row.statusLabel.ifBlank { null },
    row.memberType.ifBlank { null },
    row.unit.ifBlank { null },
    tr("minderjährig").takeIf { row.minor },
    when {
        row.disputed -> tr("Ablehnung liegt vor")
        row.status == "open" && row.approvalsMet == false && row.missing.isNotEmpty() ->
            tr("es fehlt: {missing}", "missing" to row.missing.joinToString(", "))
        row.status == "open" && row.approvalsMet == true -> tr("bereit zur Aufnahme")
        else -> null
    },
).joinToString(" · ")

@Composable
fun ApplicationsSection(client: AdminClient, errors: AdminErrors, reload: Int, onOpen: (Int) -> Unit) {
    var attempt by remember(client.unitId) { mutableIntStateOf(0) }
    var data by remember(client.unitId, reload, attempt) { mutableStateOf<ApplicationList?>(null) }
    var error by remember(client.unitId, reload, attempt) { mutableStateOf<String?>(null) }
    LaunchedEffect(client, reload, attempt) {
        try {
            data = client.applications()
        } catch (e: ApiException) {
            error = errors.message(e)
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                if (data == null && error != null) OutlinedButton(onClick = { attempt++ }) { Text(tr("Erneut laden")) }
                if (data == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                data?.let { d ->
                    if (d.ruleText.isNotBlank()) Text(d.ruleText, style = MaterialTheme.typography.bodySmall)
                    if (d.results.isEmpty()) Text(tr("Keine offenen Beitrittsanträge."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(data?.results.orEmpty(), key = { "antrag-${it.id}" }) { row ->
            ListItem(
                headlineContent = { Text(row.name.ifBlank { row.number }) },
                supportingContent = { Text(applicationLine(row)) },
                overlineContent = { Text(listOfNotNull(row.number.ifBlank { null }, formatDate(row.createdAt).ifBlank { null }).joinToString(" · ")) },
                modifier = Modifier.openRow { onOpen(row.id) },
            )
            HorizontalDivider()
        }
        item {
            Text(
                tr("Angaben korrigieren und mehrere Anträge auf einmal aufnehmen geht im Browser."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(Abstand.rand),
            )
        }
    }
}

private enum class ApplicationDialog { ACCEPT, REJECT, ASK }

@Composable
fun ApplicationScreen(client: AdminClient, id: Int, errors: AdminErrors, onBack: () -> Unit, onChanged: OnChanged) {
    val scope = rememberCoroutineScope()
    var data by remember(id) { mutableStateOf<ApplicationDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by rememberSaveable(id) { mutableStateOf("") }
    var dialog by remember { mutableStateOf<ApplicationDialog?>(null) }
    LaunchedEffect(client, id) {
        try {
            data = client.application(id)
        } catch (e: ApiException) {
            error = errors.message(e)
        }
    }
    fun run(done: String, action: suspend () -> ApplicationDetail) {
        scope.launch {
            busy = true
            try {
                data = action()
                error = null
                dialog = null
                onChanged(done, null)
            } catch (e: ApiException) {
                error = errors.message(e)
            }
            busy = false
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHead(data?.name ?: tr("Beitrittsantrag"), onBack = onBack)
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                val d = data
                if (d == null) {
                    if (error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    return@Column
                }
                Text(listOfNotNull(d.number.ifBlank { null }, d.statusLabel).joinToString(" · "), style = MaterialTheme.typography.titleSmall)
                for (line in d.summary) Text(line, style = MaterialTheme.typography.bodyMedium)
                // Treffer: schon Mitglied, früher Mitglied oder anderswo aktiv
                if (d.matches.persons.isNotEmpty()) {
                    Text(tr("Mögliche Treffer"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                    for (m in d.matches.persons) {
                        Text(
                            listOfNotNull(
                                m.name,
                                m.birthDate?.let { formatPlainDate(it) },
                                when {
                                    m.here && m.former -> tr("früher Mitglied")
                                    m.here -> tr("schon Mitglied")
                                    else -> null
                                },
                                m.activeElsewhere.ifBlank { null }?.let { tr("aktiv in {org}", "org" to it) },
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (d.ruleText.isNotBlank()) Text(d.ruleText, style = MaterialTheme.typography.bodySmall)
                if (d.approvals.isNotEmpty()) {
                    Text(tr("Zustimmungen"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                    for (a in d.approvals) {
                        Text(
                            listOfNotNull(
                                a.userName,
                                a.roles.ifBlank { null },
                                if (a.decision == "approve") tr("stimmt zu") else tr("lehnt ab"),
                                a.note.ifBlank { null },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (d.isOpen && d.missing.isNotEmpty()) {
                    Text(tr("Es fehlen noch Zustimmungen: {missing}", "missing" to d.missing.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                }
                if (d.disputed) Text(tr("Es gibt eine Ablehnung. Bitte klärt das, bevor ihr aufnehmt."), color = MaterialTheme.colorScheme.error)
                if (!d.isOpen && d.decisionNote.isNotBlank()) Text(tr("Vermerk: {note}", "note" to d.decisionNote), style = MaterialTheme.typography.bodySmall)
                if (d.canApprove) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(500) },
                        label = { Text(tr("Vermerk (freiwillig)")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        Button(onClick = { run(tr("Zustimmung eingetragen")) { client.approveApplication(id, true, note) } }, enabled = !busy) {
                            Text(tr("Zustimmen"))
                        }
                        OutlinedButton(onClick = { run(tr("Ablehnung eingetragen")) { client.approveApplication(id, false, note) } }, enabled = !busy) {
                            Text(tr("Nicht zustimmen"))
                        }
                    }
                } else if (d.ownDecision != null && d.isOpen) {
                    Text(
                        if (d.ownDecision == "approve") tr("Du hast zugestimmt.") else tr("Du hast nicht zugestimmt."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (d.mayDecide && d.isOpen) {
                    HorizontalDivider()
                    if (d.status == "open") {
                        Button(onClick = { dialog = ApplicationDialog.ACCEPT }, enabled = !busy && d.canAccept, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Aufnehmen"))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        OutlinedButton(onClick = { dialog = ApplicationDialog.ASK }, enabled = !busy) { Text(tr("Rückfrage")) }
                        OutlinedButton(onClick = { dialog = ApplicationDialog.REJECT }, enabled = !busy) { Text(tr("Ablehnen")) }
                    }
                }
            }
        }
    }
    val d = data ?: return
    when (dialog) {
        ApplicationDialog.ACCEPT -> AcceptDialog(d, busy, onDismiss = { dialog = null }) { date, text, welcome ->
            run(tr("{name} ist aufgenommen", "name" to d.name)) { client.acceptApplication(id, date, text, welcome) }
        }
        ApplicationDialog.REJECT -> RejectDialog(busy, onDismiss = { dialog = null }) { text, mail ->
            run(tr("Antrag abgelehnt")) { client.rejectApplication(id, text, mail) }
        }
        ApplicationDialog.ASK -> AskDialog(busy, onDismiss = { dialog = null }) { text ->
            run(tr("Rückfrage verschickt")) { client.askApplication(id, text) }
        }
        null -> Unit
    }
}

@Composable
private fun AcceptDialog(d: ApplicationDetail, busy: Boolean, onDismiss: () -> Unit, onAccept: (String?, String, Boolean) -> Unit) {
    var date by rememberSaveable { mutableStateOf(d.entryDate.orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
    var welcome by rememberSaveable { mutableStateOf(true) }
    val valid = date.isBlank() || runCatching { java.time.LocalDate.parse(date) }.isSuccess
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("{name} aufnehmen", "name" to d.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                if (d.memberType.isNotBlank()) Text(tr("Mitgliedsart: {type}", "type" to d.memberType))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text(tr("Eintritt (JJJJ-MM-TT)")) },
                    isError = !valid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text(tr("Vermerk (z. B. Beschluss)")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.toggleable(welcome, role = Role.Checkbox) { welcome = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = welcome, onCheckedChange = null)
                    Text(tr("Begrüßung per E-Mail schicken"))
                }
            }
        },
        confirmButton = { Button(onClick = { onAccept(date.ifBlank { null }, note, welcome) }, enabled = valid && !busy) { Text(tr("Aufnehmen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun RejectDialog(busy: Boolean, onDismiss: () -> Unit, onReject: (String, Boolean) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    var mail by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Antrag ablehnen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text(tr("Vermerk (nur intern)")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.toggleable(mail, role = Role.Checkbox) { mail = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = mail, onCheckedChange = null)
                    Text(tr("Absage per E-Mail schicken"))
                }
            }
        },
        confirmButton = { Button(onClick = { onReject(note, mail) }, enabled = !busy) { Text(tr("Ablehnen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun AskDialog(busy: Boolean, onDismiss: () -> Unit, onAsk: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Rückfrage stellen")) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(2000) },
                label = { Text(tr("Frage an die antragstellende Person")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        },
        confirmButton = { Button(onClick = { onAsk(text) }, enabled = !busy && text.isNotBlank()) { Text(tr("Verschicken")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

// ---------------------------------------------------------------- Belegung freigeben

/** Zeitraum einer Anfrage: ganztägig nur mit Datum (Ende ist 0 Uhr des Folgetags). */
fun bookingRequestWhen(b: BookingRequest): String {
    if (b.allDay) {
        val start = formatDate(b.startsAt)
        val endDay = runCatching { java.time.OffsetDateTime.parse(b.endsAt).minusSeconds(1).toString() }.getOrNull()
        val end = formatDate(endDay)
        return if (end.isBlank() || end == start) start else "$start – $end"
    }
    return "${formatDayTime(b.startsAt)} – ${formatTimeOf(b.endsAt, b.startsAt)}"
}

/** Ende am selben Tag nur als Uhrzeit, sonst mit Datum. */
private fun formatTimeOf(end: String, start: String): String {
    val e = runCatching { java.time.OffsetDateTime.parse(end).atZoneSameInstant(java.time.ZoneId.systemDefault()) }.getOrNull() ?: return ""
    val s = runCatching { java.time.OffsetDateTime.parse(start).atZoneSameInstant(java.time.ZoneId.systemDefault()) }.getOrNull()
    return if (s != null && s.toLocalDate() == e.toLocalDate()) {
        e.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + if (de.hubiplan.app.Texte.sprache == "en") "" else " Uhr"
    } else {
        formatDayTime(end)
    }
}

@Composable
fun BookingRequestsSection(client: AdminClient, errors: AdminErrors, reload: Int, onChanged: OnChanged) {
    val scope = rememberCoroutineScope()
    var attempt by remember(client.unitId) { mutableIntStateOf(0) }
    var data by remember(client.unitId, reload, attempt) { mutableStateOf<List<BookingRequest>?>(null) }
    var error by remember(client.unitId, reload, attempt) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(client, reload, attempt) {
        try {
            data = client.bookingRequests(java.time.LocalDate.now().toString())
        } catch (e: ApiException) {
            error = errors.message(e)
        }
    }
    fun decide(b: BookingRequest, action: String) {
        scope.launch {
            busy = b.id
            try {
                client.decideBooking(b.id, b.version, action)
                data = data?.filterNot { it.id == b.id }
                error = null
                onChanged(if (action == "confirm") tr("Anfrage freigegeben") else tr("Anfrage abgelehnt"), null)
            } catch (e: ApiException) {
                error = if (e.status == 409) tr("Die Anfrage wurde inzwischen geändert. Die Liste ist neu geladen.") else errors.message(e)
                if (e.status == 409) attempt++
            }
            busy = null
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                if (data == null && error != null) OutlinedButton(onClick = { attempt++ }) { Text(tr("Erneut laden")) }
                if (data == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (data?.isEmpty() == true) Text(tr("Keine offenen Anfragen."), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(data.orEmpty(), key = { "anfrage-${it.id}" }) { b ->
            Card(Modifier.fillMaxWidth().padding(horizontal = Abstand.rand, vertical = Abstand.eng)) {
                Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                    Text(b.itemTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                    Text(bookingRequestWhen(b))
                    Text(listOfNotNull(b.title.ifBlank { null }, b.requester.ifBlank { null }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (b.repeat.isNotBlank()) Text(tr("wiederholt sich"), style = MaterialTheme.typography.bodySmall)
                    if (b.note.isNotBlank()) Text(b.note, style = MaterialTheme.typography.bodySmall)
                    if (busy == b.id) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        Button(onClick = { decide(b, "confirm") }, enabled = busy == null) { Text(tr("Freigeben")) }
                        OutlinedButton(onClick = { decide(b, "reject") }, enabled = busy == null) { Text(tr("Ablehnen")) }
                    }
                }
            }
        }
        item {
            Text(
                tr("Den Kalender ansehen und selbst buchen geht im Browser."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(Abstand.rand),
            )
        }
    }
}

// ---------------------------------------------------------------- Versammlung leiten

fun meetingStatusLabel(status: String): String = when (status) {
    "draft" -> tr("in Vorbereitung")
    "invited" -> tr("eingeladen")
    "held" -> tr("gehalten")
    "minutes" -> tr("Protokoll in Arbeit")
    "done" -> tr("abgeschlossen")
    "cancelled" -> tr("abgesagt")
    else -> status
}

fun pollStatusLabel(status: String): String = when (status) {
    "draft" -> tr("vorbereitet")
    "open" -> tr("läuft")
    "closed" -> tr("beendet")
    else -> status
}

@Composable
fun MeetingsSection(client: AdminClient, errors: AdminErrors, reload: Int, onOpen: (Int) -> Unit) {
    var attempt by remember(client.unitId) { mutableIntStateOf(0) }
    var data by remember(client.unitId, reload, attempt) { mutableStateOf<List<MeetingItem>?>(null) }
    var error by remember(client.unitId, reload, attempt) { mutableStateOf<String?>(null) }
    LaunchedEffect(client, reload, attempt) {
        try {
            data = client.meetings()
        } catch (e: ApiException) {
            error = errors.message(e)
        }
    }
    // Laufende und kommende zuerst; abgeschlossene lassen sich nicht mehr leiten
    val shown = data?.filter { it.isLive }.orEmpty().sortedBy { it.startsAt }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                if (data == null && error != null) OutlinedButton(onClick = { attempt++ }) { Text(tr("Erneut laden")) }
                if (data == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (data != null && shown.isEmpty()) Text(tr("Keine Versammlung, die sich jetzt leiten lässt."), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(shown, key = { "versammlung-${it.id}" }) { m ->
            ListItem(
                headlineContent = { Text(m.title) },
                supportingContent = {
                    Text(listOfNotNull(formatDayTime(m.startsAt), m.location.ifBlank { null }, meetingStatusLabel(m.status)).joinToString(" · "))
                },
                modifier = Modifier.openRow { onOpen(m.id) },
            )
            HorizontalDivider()
        }
        item {
            Text(
                tr("Vorbereiten, Einladen und das Protokoll gehen im Browser."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(Abstand.rand),
            )
        }
    }
}

/** Wie oft Anwesenheit und Abstimmungen neu geladen werden, solange etwas läuft. */
const val LEAD_REFRESH_MS = 5_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetingLeadScreen(
    client: AdminClient,
    meetingId: Int,
    server: String,
    canScanCards: Boolean,
    canAttendance: Boolean,
    errors: AdminErrors,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var meeting by remember(meetingId) { mutableStateOf<MeetingItem?>(null) }
    var attendance by remember(meetingId) { mutableStateOf<AttendanceList?>(null) }
    var live by remember(meetingId) { mutableStateOf<LeadLive?>(null) }
    var error by remember(meetingId) { mutableStateOf<String?>(null) }
    var info by remember(meetingId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var query by rememberSaveable(meetingId) { mutableStateOf("") }
    var guest by rememberSaveable(meetingId) { mutableStateOf("") }
    var bigQr by remember { mutableStateOf(false) }
    var newPoll by remember { mutableStateOf(false) }

    suspend fun load() {
        if (meeting == null) meeting = client.meeting(meetingId)
        live = client.meetingLive(meetingId)
        if (canAttendance) attendance = client.attendance(meetingId)
        error = null
    }

    LaunchedEffect(client, meetingId) {
        while (true) {
            try {
                load()
            } catch (e: ApiException) {
                error = if (e.network != null && live != null) tr("Ohne Netz: Der Stand ist vielleicht nicht aktuell.") else errors.message(e)
            }
            // Offene Anmeldung: der Code wechselt alle 30 Sekunden; laufende Abstimmung: Stimmen zählen mit
            val l = live
            val wait = when {
                l?.checkinOpen == true -> minOf(LEAD_REFRESH_MS, (l.refreshIn.coerceAtLeast(1) * 1000L))
                l?.polls?.any { it.status == "open" } == true -> LEAD_REFRESH_MS
                else -> LEAD_REFRESH_MS * 3
            }
            delay(wait)
        }
    }
    // Bildschirm bleibt an, solange der QR-Code zum Anmelden zu sehen ist
    val view = LocalView.current
    DisposableEffect(live?.checkinOpen) {
        view.keepScreenOn = live?.checkinOpen == true
        onDispose { view.keepScreenOn = false }
    }

    fun act(done: String? = null, action: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                action()
                error = null
                info = done
            } catch (e: ApiException) {
                error = errors.message(e)
            }
            busy = false
        }
    }

    var scanProblem by remember { mutableStateOf<String?>(null) }
    ScanProblemDialog(scanProblem) { scanProblem = null }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents
        if (code == null) {
            scanProblem = scanWithoutResult(context, tr("Ohne Kamera suchst du die Person in der Liste und trägst sie dort ein."))
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            try {
                val card = client.checkCard(code)
                val membership = card.membership
                if (membership == null || card.status == "unknown") {
                    info = null
                    error = if (card.status == "unknown") card.statusLabel else tr("Der Ausweis gehört nicht zu diesem Verein.")
                } else {
                    attendance = client.setAttendance(meetingId, membership, null, "present")
                    val name = listOfNotNull(card.firstName, card.lastName).joinToString(" ")
                    error = null
                    info = tr("{name} ist als anwesend eingetragen.", "name" to name)
                }
            } catch (e: ApiException) {
                info = null
                error = errors.message(e)
            }
            busy = false
        }
    }

    val m = meeting
    val l = live
    if (bigQr && l != null) {
        val link = checkinLink(server, l)
        if (link != null) {
            BigQr(link, onClose = { bigQr = false })
            return
        }
    }
    if (newPoll && m != null) {
        PollForm(m, busy, onCancel = { newPoll = false }) { item, question, secret, office, candidates ->
            act(tr("Abstimmung angelegt")) {
                live = client.createPoll(meetingId, item, question, secret, office, candidates)
                newPoll = false
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHead(m?.title ?: tr("Versammlung leiten"), onBack = onBack)
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.announce()) }
                if (m == null || l == null) {
                    if (error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    return@Column
                }
                Text(listOfNotNull(formatDayTime(m.startsAt), m.location.ifBlank { null }, meetingStatusLabel(m.status)).joinToString(" · "))

                // Anmeldung per App
                Text(tr("Anmeldung per App"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
                SwitchRow(tr("Anmeldung offen"), l.checkinOpen, enabled = !busy && l.live) { open ->
                    act { live = client.setCheckin(meetingId, open) }
                }
                if (l.remoteAllowed) {
                    SwitchRow(tr("Teilnahme aus der Ferne erlauben"), l.remote, enabled = !busy && l.live) { remote ->
                        act { live = client.setCheckin(meetingId, l.checkinOpen, remote) }
                    }
                }
                val link = checkinLink(server, l)
                if (link != null) {
                    QrImage(link, 240)
                    Text(tr("Mitglieder scannen den Code mit der HUBIplan-App. Er wechselt alle 30 Sekunden."), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { bigQr = true }) { Text(tr("Groß zeigen")) }
                }
                Text(
                    tr("{app} per App angemeldet, {online} online · {entitled} Stimmen", "app" to l.presentApp, "online" to l.presentOnline, "entitled" to l.entitled),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Abstimmungen
        if (m != null && l != null && l.onlineVoting) {
            item {
                Column(Modifier.padding(horizontal = Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    HorizontalDivider()
                    Text(tr("Abstimmungen"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
                    if (l.polls.isEmpty()) Text(tr("Noch keine Abstimmung angelegt."), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(l.polls, key = { "abstimmung-${it.id}" }) { p ->
                PollCard(p, busy, onAction = { action ->
                    val done = when (action) {
                        "open" -> tr("Abstimmung gestartet")
                        "close" -> tr("Abstimmung beendet")
                        else -> tr("Ergebnis als Beschluss übernommen")
                    }
                    act(done) { live = client.pollAction(meetingId, p.id, action) }
                }, onDelete = { act(tr("Abstimmung gelöscht")) { live = client.deletePoll(meetingId, p.id) } })
            }
            item {
                OutlinedButton(
                    onClick = { newPoll = true },
                    enabled = !busy && l.live && m.agenda.any { it.votable },
                    modifier = Modifier.padding(horizontal = Abstand.rand),
                ) { Text(tr("Neue Abstimmung")) }
            }
        }

        // Anwesenheit
        val a = attendance
        if (canAttendance && a != null) {
            item {
                Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    HorizontalDivider()
                    Text(tr("Anwesenheit"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
                    Text(
                        tr("{present} von {voters} Stimmberechtigten anwesend, {represented} vertreten, {guests} Gäste",
                            "present" to a.present, "voters" to a.voters, "represented" to a.represented, "guests" to a.guests),
                    )
                    if (a.editable) {
                        if (canScanCards) {
                            Button(onClick = {
                                scanner.launch(
                                    ScanOptions()
                                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        .setPrompt(tr("Mitgliedsausweis scannen"))
                                        .setBeepEnabled(true)
                                        .setOrientationLocked(false),
                                )
                            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(tr("Ausweis scannen")) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                            OutlinedTextField(
                                value = guest,
                                onValueChange = { guest = it.take(200) },
                                label = { Text(tr("Gast eintragen")) },
                                singleLine = true,
                                modifier = Modifier.widthIn(min = 200.dp),
                            )
                            OutlinedButton(onClick = {
                                val name = guest.trim()
                                act(tr("{name} ist eingetragen.", "name" to name)) {
                                    attendance = client.setAttendance(meetingId, null, name, "present")
                                    guest = ""
                                }
                            }, enabled = !busy && guest.isNotBlank(), modifier = Modifier.align(Alignment.CenterVertically)) { Text(tr("Eintragen")) }
                        }
                    } else {
                        Text(tr("Die Anwesenheit lässt sich jetzt nicht mehr ändern."), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(tr("Name suchen")) },
                        singleLine = true,
                        keyboardOptions = SUCHE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val q = query.trim().lowercase()
            val rows = a.rows.filter { q.isBlank() || it.name.lowercase().contains(q) }
            items(rows, key = { r -> "anwesend-${r.membership ?: 0}-${r.id ?: 0}-${r.name}" }) { r ->
                AttendanceLine(r, enabled = a.editable && !busy) { present ->
                    act {
                        attendance = when {
                            present -> client.setAttendance(meetingId, r.membership, r.name.takeIf { r.membership == null }, "present")
                            r.id != null -> client.removeAttendance(meetingId, r.id)
                            else -> a
                        }
                    }
                }
                HorizontalDivider()
            }
            item {
                Text(
                    tr("Vollmachten und Vertreter von Organisationen trägst du im Browser ein."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(Abstand.rand),
                )
            }
        }
    }
}

/** Zeile der Anwesenheitsliste: Haken setzt „anwesend“, Entfernen löscht den Eintrag. */
fun attendanceLabel(r: AttendanceRow): String = listOfNotNull(
    if (r.voter) null else tr("Gast"),
    when (r.status) {
        "present" -> when (r.mode) {
            "app" -> tr("per App angemeldet")
            "online" -> tr("online dabei")
            else -> tr("anwesend")
        }
        "delegate" -> tr("durch Vertreter")
        "represented" -> tr("vertreten")
        "excused" -> tr("entschuldigt")
        "left" -> tr("gegangen")
        else -> null
    },
).joinToString(" · ")

@Composable
private fun AttendanceLine(r: AttendanceRow, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(r.name) },
        supportingContent = { attendanceLabel(r).ifBlank { null }?.let { Text(it) } },
        leadingContent = { Checkbox(checked = r.present, onCheckedChange = null) },
        modifier = Modifier.toggleable(r.present, enabled = enabled && r.status != "represented", role = Role.Checkbox) { onToggle(it) },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch) { onChange(it) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun QrImage(content: String, size: Int) {
    val bitmap = remember(content, size) { runCatching { qrBitmap(content, size * 3) }.getOrNull() } ?: return
    Image(
        bitmap.asImageBitmap(),
        contentDescription = tr("QR-Code zum Anmelden"),
        modifier = Modifier.size(size.dp).background(Color.White),
    )
}

@Composable
private fun BigQr(content: String, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    Column(
        Modifier.fillMaxSize().background(Color.White).padding(Abstand.rand),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Abstand.klein, Alignment.CenterVertically),
    ) {
        QrImage(content, 340)
        Text(tr("Mit der HUBIplan-App scannen, um dich anzumelden."), color = Color.Black)
        Button(onClick = onClose) { Text(tr("Schließen")) }
    }
}

@Composable
private fun PollCard(p: LeadPoll, busy: Boolean, onAction: (String) -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Abstand.rand, vertical = Abstand.eng)) {
        Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
            Text(
                listOfNotNull(p.agendaNumber.ifBlank { null }, p.agendaTitle).joinToString(" "),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.asHeading(),
            )
            val headline = listOfNotNull(p.question.ifBlank { null }, p.office.ifBlank { null }?.let { tr("Wahl: {office}", "office" to it) }, p.candidate.ifBlank { null })
            if (headline.isNotEmpty()) Text(headline.joinToString(" · "))
            Text(
                listOfNotNull(
                    pollStatusLabel(p.status),
                    tr("geheim").takeIf { p.secret },
                    tr("{cast} von {entitled} abgestimmt", "cast" to p.cast, "entitled" to p.entitled).takeIf { p.status != "draft" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            p.result?.let { result ->
                for (r in result) Text("${r.label}: ${r.votes}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                when (p.status) {
                    "draft" -> {
                        Button(onClick = { onAction("open") }, enabled = !busy) { Text(tr("Starten")) }
                        OutlinedButton(onClick = onDelete, enabled = !busy) { Text(tr("Löschen")) }
                    }
                    "open" -> Button(onClick = { onAction("close") }, enabled = !busy) { Text(tr("Beenden")) }
                    "closed" -> if (p.resolution == null) {
                        OutlinedButton(onClick = { onAction("apply") }, enabled = !busy) { Text(tr("Als Beschluss übernehmen")) }
                    } else {
                        Text(tr("als Beschluss übernommen"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PollForm(
    m: MeetingItem,
    busy: Boolean,
    onCancel: () -> Unit,
    onCreate: (item: Int, question: String, secret: Boolean, office: String, candidates: List<String>) -> Unit,
) {
    val items = m.agenda.filter { it.votable }
    var item by rememberSaveable { mutableStateOf(items.firstOrNull()?.id) }
    var question by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf(false) }
    var office by rememberSaveable { mutableStateOf("") }
    var candidates by rememberSaveable { mutableStateOf("") }
    androidx.activity.compose.BackHandler(onBack = onCancel)
    val chosen = items.firstOrNull { it.id == item }
    val election = chosen?.type == "election"
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHead(tr("Neue Abstimmung"), onBack = onCancel)
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Text(tr("Tagesordnungspunkt"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                for (a in items) {
                    Row(
                        Modifier.fillMaxWidth().selectable(a.id == item, role = Role.RadioButton) { item = a.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = a.id == item, onClick = null)
                        Text(listOfNotNull(a.number.ifBlank { null }, a.title).joinToString(" "))
                    }
                }
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it.take(2000) },
                    label = { Text(if (election) tr("Frage (freiwillig)") else tr("Frage, über die abgestimmt wird")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (election) {
                    OutlinedTextField(
                        value = office,
                        onValueChange = { office = it.take(200) },
                        label = { Text(tr("Amt")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = candidates,
                        onValueChange = { candidates = it },
                        label = { Text(tr("Kandidaten (je Zeile ein Name)")) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    Modifier.toggleable(secret, role = Role.Checkbox) { secret = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = secret, onCheckedChange = null)
                    Text(tr("Geheime Abstimmung"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    Button(
                        onClick = {
                            val id = item ?: return@Button
                            onCreate(id, question, secret, if (election) office else "", if (election) candidates.lines() else emptyList())
                        },
                        enabled = !busy && item != null,
                    ) { Text(tr("Anlegen")) }
                    TextButton(onClick = onCancel) { Text(tr("Abbrechen")) }
                }
            }
        }
    }
}
