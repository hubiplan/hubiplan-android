package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.Belegung
import de.hubiplan.app.data.BookingWeek
import de.hubiplan.app.data.CancellationInfo
import de.hubiplan.app.data.CancellationItem
import de.hubiplan.app.data.MusicAbsence
import de.hubiplan.app.data.MyEntry
import de.hubiplan.app.data.OwnAbsences
import de.hubiplan.app.data.OwnBooking
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.tr
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable

/*
 * Mein Bereich wie im Browser: Belegung anfragen, Meldungen zu Wettbewerben, Abwesenheiten in
 * der Musik und Kündigung (unter „Meine Daten“).
 */

/** Stand einer Anfrage in Worten. */
fun bookingStatusLabel(status: String): String = when (status) {
    "requested" -> tr("angefragt")
    "confirmed" -> tr("bestätigt")
    "rejected" -> tr("abgelehnt")
    "cancelled" -> tr("storniert")
    else -> status
}

/** Zeitraum einer Buchung; ganztägig bis zum letzten Tag. */
fun bookingWhen(startsAt: String, endsAt: String, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { formatWhen(startsAt, Belegung.shownEnd(endsAt, allDay), allDay, zone) }.getOrDefault("")

/** „Jägerzug als Schütze 2 (Ersatz), vom Veranstalter bestätigt“ */
fun entryLine(e: MyEntry): String = buildString {
    append(tr("Gemeldet in {group}", "group" to e.group))
    if (e.position.isNotBlank()) append(tr(" als {position}", "position" to e.position))
    if (e.substitute) append(tr(" (Ersatz)"))
    if (e.confirmed) append(tr(", vom Veranstalter bestätigt"))
}

/** Datum, Ort und Veranstalter einer Meldung in einer Zeile. */
fun entryMeta(e: MyEntry): String = listOf(
    e.date?.let { formatPlainDate(it) }.orEmpty(),
    e.location,
    if (e.organizer.isNotBlank()) tr("Veranstalter: {organizer}", "organizer" to e.organizer) else "",
).filter { it.isNotBlank() }.joinToString(" · ")

/** Eingabe „TT.MM.JJJJ“ für den Tag eines Zeitpunkts (Abwesenheit bearbeiten). */
private fun dayInput(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate().format(DAY_INPUT_FORMAT) }.getOrDefault("")

private fun timeInput(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("")

@Composable
private fun DayField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = value.isNotBlank() && parseGermanDate(value) == null,
        trailingIcon = { DatePickButton(value, onChange) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ClockField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = value.isNotBlank() && parseTime(value) == null,
        trailingIcon = { TimePickButton(value) { onChange(it) } },
        modifier = modifier,
    )
}

@Composable
private fun ErrorLine(text: String?) {
    text?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
}

@Composable
private fun NoticeLine(text: String?) {
    text?.let { Text(it, modifier = Modifier.announce()) }
}

// --- Belegung ---

/** Mein Bereich › Belegung: belegte Zeiten je Woche, Anfrage stellen, eigene Anfragen stornieren. */
@Composable
internal fun BookingsSection(access: PortalAccess, vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var monday by remember(access.id) { mutableStateOf(Belegung.monday(LocalDate.now())) }
    var data by remember(access.id) { mutableStateOf<BookingWeek?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var cancelling by remember { mutableStateOf<OwnBooking?>(null) }
    LaunchedEffect(access.id, monday, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        error = null
        try {
            data = api.bookings(access.id, monday.toString())
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        val d = data
        if (d == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        if (d.items.isEmpty()) {
            Text(tr("Zurzeit lassen sich hier keine Objekte anfragen."))
            return@SectionColumn
        }
        // Belegte Zeiten der Woche
        Text(tr("Belegte Zeiten"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        Text(
            tr("Woche vom {from} bis {until}", "from" to formatPlainDate(monday.toString()), "until" to formatPlainDate(monday.plusDays(6).toString())),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            OutlinedButton(onClick = { monday = monday.minusWeeks(1) }) { Text(tr("Vorige Woche")) }
            OutlinedButton(onClick = { monday = monday.plusWeeks(1) }) { Text(tr("Nächste Woche")) }
        }
        d.items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                val rows = d.occurrences.filter { it.item == item.id }
                if (rows.isEmpty()) Text(tr("In dieser Woche frei."), style = MaterialTheme.typography.bodySmall)
                rows.forEach { o -> Text("${bookingWhen(o.startsAt, o.endsAt, o.allDay)} · ${o.title}") }
            }
        }
        HorizontalDivider()
        BookingForm(access, vm, d, onSent = { attempt++ })
        HorizontalDivider()
        Text(tr("Meine Anfragen und Buchungen"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        NoticeLine(notice)
        ErrorLine(failure)
        if (d.own.isEmpty()) Text(tr("Noch keine."), style = MaterialTheme.typography.bodySmall)
        d.own.forEach { b ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    Text(b.title, style = MaterialTheme.typography.titleSmall)
                    Text(listOf(b.itemTitle, bookingWhen(b.startsAt, b.endsAt, b.allDay)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    Text(bookingStatusLabel(b.status), style = MaterialTheme.typography.bodyMedium)
                    if (b.cancellable) {
                        OutlinedButton(enabled = !busy, onClick = { cancelling = b }) { Text(tr("Buchung stornieren")) }
                    }
                }
            }
        }
    }
    cancelling?.let { b ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text(tr("Buchung stornieren?")) },
            text = { Text("${b.title} · ${bookingWhen(b.startsAt, b.endsAt, b.allDay)}") },
            confirmButton = {
                TextButton(onClick = {
                    cancelling = null
                    val api = vm.api() ?: return@TextButton
                    busy = true
                    failure = null
                    notice = null
                    scope.launch {
                        try {
                            api.cancelBooking(access.id, b)
                            notice = tr("Storniert.")
                            attempt++
                        } catch (e: ApiException) {
                            if (e.status == 401) vm.deviceRevoked(e) else failure = e.userText()
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(tr("Stornieren")) }
            },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

@Composable
private fun BookingForm(access: PortalAccess, vm: AppViewModel, week: BookingWeek, onSent: () -> Unit) {
    val scope = rememberCoroutineScope()
    var itemId by remember(access.id) { mutableStateOf(week.items.first().id) }
    var pick by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var until by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val item = week.items.firstOrNull { it.id == itemId } ?: week.items.first()
    val fromDay = parseGermanDate(from)?.let(LocalDate::parse)
    val untilDay = parseGermanDate(until)?.let(LocalDate::parse)
    val times = fromDay?.let {
        if (until.isNotBlank() && untilDay == null) null
        else Belegung.requestTimes(it, untilDay, parseTime(start), parseTime(end), allDay)
    }
    Text(tr("Anfrage stellen"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    Text(tr("Die Verwaltung bestätigt deine Anfrage oder lehnt sie ab; du bekommst eine E-Mail."), style = MaterialTheme.typography.bodySmall)
    Box {
        TextButton(onClick = { pick = true }, enabled = !busy, modifier = Modifier.dropdown(tr("Objekt"), item.title, pick)) {
            DropdownText(item.title)
        }
        DropdownMenu(expanded = pick, onDismissRequest = { pick = false }) {
            week.items.forEach { i ->
                DropdownMenuItem(
                    text = { Text(i.title) },
                    leadingIcon = { MenuCheck(i.id == item.id) },
                    onClick = { itemId = i.id; pick = false },
                )
            }
        }
    }
    OutlinedTextField(title, { title = it.take(200) }, label = { Text(tr("Zweck")) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
    LabeledSwitch(tr("Ganztägig"), allDay, { allDay = it })
    if (allDay) {
        DayField(tr("Von (TT.MM.JJJJ)"), from, !busy, { from = it })
        DayField(tr("Bis (optional, TT.MM.JJJJ)"), until, !busy, { until = it })
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            DayField(tr("Tag (TT.MM.JJJJ)"), from, !busy, { from = it }, Modifier.weight(1.4f))
            ClockField(tr("Beginn"), start, !busy, { start = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            DayField(tr("Bis Tag (optional)"), until, !busy, { until = it }, Modifier.weight(1.4f))
            ClockField(tr("Ende"), end, !busy, { end = it }, Modifier.weight(1f))
        }
    }
    if (item.horizonDays > 0) {
        Text(tr("Bis zu {n} Tage im Voraus buchbar.", "n" to item.horizonDays), style = MaterialTheme.typography.bodySmall)
    }
    OutlinedTextField(note, { note = it.take(2000) }, label = { Text(tr("Notiz (optional)")) }, minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
    if (from.isNotBlank() && times == null) {
        Text(tr("Bitte Tag als TT.MM.JJJJ und Zeit als HH:MM eingeben; das Ende liegt nach dem Beginn."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    ErrorLine(failure)
    NoticeLine(notice)
    Button(
        enabled = !busy && title.isNotBlank() && times != null,
        onClick = {
            val api = vm.api() ?: return@Button
            val (s, e) = times ?: return@Button
            busy = true
            failure = null
            notice = null
            scope.launch {
                try {
                    api.requestBooking(access.id, item.id, title.trim(), note.trim(), s, e, allDay)
                    notice = tr("Deine Anfrage ist eingegangen.")
                    title = ""
                    from = ""
                    start = ""
                    until = ""
                    end = ""
                    note = ""
                    onSent()
                } catch (ex: ApiException) {
                    if (ex.status == 401) vm.deviceRevoked(ex) else failure = ex.userText()
                } finally {
                    busy = false
                }
            }
        },
    ) { Text(if (busy) tr("Wird gesendet …") else tr("Anfrage senden")) }
}

// --- Meldungen ---

/** „Gemeldet“: wofür der Verein das Mitglied gemeldet hat (oben unter „Wettbewerbe“). */
@Composable
internal fun EntriesBlock(access: PortalAccess, vm: AppViewModel) {
    var list by remember(access.id) { mutableStateOf<List<MyEntry>?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        error = null
        try {
            list = api.myEntries(access.id)
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    Text(tr("Gemeldet"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
    val l = list ?: return
    if (l.isEmpty()) {
        Text(
            tr("Zurzeit bist du für nichts gemeldet. Sobald der Verein dich zu einem Wettbewerb oder Anlass meldet, steht das hier."),
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    val today = LocalDate.now()
    val coming = l.filterNot { it.past(today) }
    val past = l.filter { it.past(today) }
    if (coming.isNotEmpty()) EntryGroup(tr("Demnächst"), coming)
    if (past.isNotEmpty()) EntryGroup(tr("Vergangen"), past)
}

@Composable
private fun EntryGroup(title: String, rows: List<MyEntry>) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
    rows.forEach { e ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall)
                entryMeta(e).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(entryLine(e))
            }
        }
    }
}

// --- Abwesenheiten (Musik) ---

/** Mein Bereich › Musik: Zeiträume, in denen man nicht mitspielen kann (ohne Begründung). */
@Composable
internal fun AbsencesSection(access: PortalAccess, vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var data by remember(access.id) { mutableStateOf<OwnAbsences?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    // null = keine Eingabe offen, id 0 = neue Abwesenheit
    var editing by remember { mutableStateOf<MusicAbsence?>(null) }
    var removing by remember { mutableStateOf<MusicAbsence?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Ein Schlüssel je Löschauftrag: Wiederholen löscht nicht doppelt
    val deleteKeys = remember { mutableMapOf<Int, String>() }
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        error = null
        try {
            data = api.myAbsences(access.id)
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        val d = data
        if (d == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        if (!d.enabled) {
            Text(tr("Du hast in diesem Verein kein aktives Musikerprofil."))
            return@SectionColumn
        }
        Text(
            tr("Trage Zeiträume ein, in denen du nicht mitspielen kannst. Eine Begründung wird nicht erfasst. Bestehende Einteilungen bleiben erhalten; bitte dort zusätzlich absagen."),
            style = MaterialTheme.typography.bodySmall,
        )
        NoticeLine(notice)
        ErrorLine(failure)
        val open = editing
        if (open == null) {
            OutlinedButton(onClick = { notice = null; editing = MusicAbsence(0, "", "") }) { Text(tr("Abwesenheit eintragen")) }
        } else {
            AbsenceForm(access, vm, open, onDone = { saved ->
                editing = null
                if (saved) {
                    notice = tr("Gespeichert.")
                    attempt++
                }
            })
        }
        if (d.items.isEmpty()) Text(tr("Keine Abwesenheiten eingetragen."), style = MaterialTheme.typography.bodySmall)
        d.items.forEach { row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    Text(bookingWhen(row.startsAt, row.endsAt, false))
                    Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        OutlinedButton(onClick = { notice = null; editing = row }) { Text(tr("Bearbeiten")) }
                        TextButton(onClick = { removing = row }) { Text(tr("Entfernen")) }
                    }
                }
            }
        }
    }
    removing?.let { row ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(tr("Abwesenheit entfernen?")) },
            text = { Text(bookingWhen(row.startsAt, row.endsAt, false)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    val api = vm.api() ?: return@TextButton
                    failure = null
                    notice = null
                    val key = deleteKeys.getOrPut(row.id) { UUID.randomUUID().toString() }
                    scope.launch {
                        try {
                            api.deleteAbsence(access.id, row.id, key)
                            if (editing?.id == row.id) editing = null
                            notice = tr("Entfernt.")
                            attempt++
                        } catch (e: ApiException) {
                            if (e.status == 401) vm.deviceRevoked(e) else failure = e.userText()
                        }
                    }
                }) { Text(tr("Entfernen")) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

@Composable
private fun AbsenceForm(access: PortalAccess, vm: AppViewModel, row: MusicAbsence, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var fromDay by remember(row.id) { mutableStateOf(dayInput(row.startsAt)) }
    var fromTime by remember(row.id) { mutableStateOf(timeInput(row.startsAt).ifBlank { "00:00" }) }
    var untilDay by remember(row.id) { mutableStateOf(dayInput(row.endsAt)) }
    var untilTime by remember(row.id) { mutableStateOf(timeInput(row.endsAt).ifBlank { "23:59" }) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Gleicher Schlüssel beim Wiederholen derselben Eingabe; neue Eingabe, neuer Schlüssel
    val values = listOf(fromDay, fromTime, untilDay, untilTime)
    val key = remember(row.id, values) { UUID.randomUUID().toString() }
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val start = parseGermanDate(fromDay)?.let(LocalDate::parse)?.let { d -> parseTime(fromTime)?.let { d.atTime(it).atZone(zone) } }
    val end = parseGermanDate(untilDay.ifBlank { fromDay })?.let(LocalDate::parse)?.let { d -> parseTime(untilTime)?.let { d.atTime(it).atZone(zone) } }
    val valid = start != null && end != null && end.isAfter(start)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Text(if (row.id == 0) tr("Abwesenheit eintragen") else tr("Abwesenheit ändern"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
            Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                DayField(tr("Von (TT.MM.JJJJ)"), fromDay, !busy, { fromDay = it }, Modifier.weight(1.4f))
                ClockField(tr("Uhrzeit"), fromTime, !busy, { fromTime = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                DayField(tr("Bis (TT.MM.JJJJ)"), untilDay, !busy, { untilDay = it }, Modifier.weight(1.4f))
                ClockField(tr("Uhrzeit"), untilTime, !busy, { untilTime = it }, Modifier.weight(1f))
            }
            if (fromDay.isNotBlank() && untilDay.isNotBlank() && !valid) {
                Text(tr("Das Ende der Abwesenheit muss nach dem Beginn liegen."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            ErrorLine(failure)
            Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Button(enabled = !busy && valid, onClick = {
                    val api = vm.api() ?: return@Button
                    val s = start ?: return@Button
                    val e = end ?: return@Button
                    busy = true
                    failure = null
                    scope.launch {
                        try {
                            api.saveAbsence(access.id, row.id.takeIf { it > 0 }, s.format(fmt), e.format(fmt), key)
                            onDone(true)
                        } catch (ex: ApiException) {
                            if (ex.status == 401) vm.deviceRevoked(ex) else failure = ex.userText()
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (busy) tr("Wird gespeichert …") else tr("Speichern")) }
                TextButton(enabled = !busy, onClick = { onDone(false) }) { Text(tr("Abbrechen")) }
            }
        }
    }
}

// --- Kündigung (unter „Meine Daten“) ---

/**
 * „Mitgliedschaft kündigen“ wie im Browser: frühestes Datum nach der Regel des Vereins, späteres
 * wählbar, Nachricht an den Vorstand; offene Kündigung zurücknehmen.
 */
@Composable
internal fun CancellationBlock(access: PortalAccess, vm: AppViewModel, info: CancellationInfo, onChanged: () -> Unit) {
    if (!info.shown) return
    val scope = rememberCoroutineScope()
    // Angaben überstehen Drehen (AND-03); Zurück und Reiterwechsel fragen nach (AND-05)
    var open by rememberSaveable { mutableStateOf(false) }
    var exitDate by rememberSaveable(info.earliestDate) { mutableStateOf(info.earliestDate?.let { formatPlainDate(it) } ?: "") }
    var message by rememberSaveable { mutableStateOf("") }
    if (open) rememberFormDraft(exitDate, message, retainOnDispose = false)
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var withdraw by remember { mutableStateOf<CancellationItem?>(null) }
    val pending = info.pending
    val chosen = parseGermanDate(exitDate)
    val tooEarly = chosen != null && info.earliestDate != null && chosen < info.earliestDate
    HorizontalDivider()
    Text(tr("Mitgliedschaft kündigen"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    if (info.plannedExit != null && pending == null) {
        Text(tr("Dein Austritt ist zum {date} eingetragen.", "date" to formatPlainDate(info.plannedExit)))
    }
    if (info.possible && !open && info.earliestDate != null) {
        Text(
            tr("Kündigen ist {rule} möglich, heute also frühestens zum {date}.", "rule" to info.rule, "date" to formatPlainDate(info.earliestDate)),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { notice = null; open = true }) { Text(tr("Kündigen")) }
    }
    pending?.let { p ->
        Text(
            tr("Deine Kündigung zum {date} vom {sent} wartet auf den Vorstand.", "date" to formatPlainDate(p.exitDate), "sent" to formatDayTime(p.createdAt)),
        )
        OutlinedButton(enabled = !busy, onClick = { withdraw = p }) { Text(tr("Zurücknehmen")) }
    }
    if (open && info.earliestDate != null) {
        Text(tr("Kündigen ist {rule} möglich. Ein späteres Datum kannst du wählen.", "rule" to info.rule), style = MaterialTheme.typography.bodySmall)
        DayField(tr("Austritt zum (TT.MM.JJJJ)"), exitDate, !busy, { exitDate = it })
        if (tooEarly) {
            Text(tr("Frühestens zum {date}.", "date" to formatPlainDate(info.earliestDate)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(message, { message = it.take(500) }, label = { Text(tr("Nachricht an den Vorstand (optional)")) }, minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Button(
                enabled = !busy && chosen != null && !tooEarly,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                onClick = { confirm = true },
            ) { Text(tr("Kündigung absenden")) }
            TextButton(enabled = !busy, onClick = { open = false }) { Text(tr("Abbrechen")) }
        }
    }
    NoticeLine(notice)
    ErrorLine(failure)
    info.requests.filterNot { it.pending }.forEach { c ->
        Column {
            Text(c.statusLabel, style = MaterialTheme.typography.bodyMedium)
            Text(
                tr("Kündigung zum {date}, gesendet {sent}", "date" to formatPlainDate(c.exitDate), "sent" to formatDayTime(c.createdAt)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (c.response.isNotBlank()) Text(tr("Antwort des Vorstands: {text}", "text" to c.response), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirm && chosen != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(tr("Mitgliedschaft wirklich kündigen?")) },
            text = { Text(tr("Du trittst zum {date} aus. Der Vorstand bestätigt die Kündigung per Mail.", "date" to formatPlainDate(chosen))) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val api = vm.api() ?: return@TextButton
                    busy = true
                    failure = null
                    scope.launch {
                        try {
                            api.cancelMembership(access.id, chosen, message.trim())
                            open = false
                            message = ""
                            notice = tr("Kündigung gesendet. Du bekommst eine Eingangsbestätigung per Mail.")
                            onChanged()
                        } catch (e: ApiException) {
                            if (e.status == 401) vm.deviceRevoked(e) else failure = e.userText()
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(tr("Kündigung absenden")) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Abbrechen")) } },
        )
    }
    withdraw?.let { c ->
        AlertDialog(
            onDismissRequest = { withdraw = null },
            title = { Text(tr("Kündigung zurücknehmen?")) },
            text = { Text(tr("Deine Mitgliedschaft läuft dann weiter.")) },
            confirmButton = {
                TextButton(onClick = {
                    withdraw = null
                    val api = vm.api() ?: return@TextButton
                    busy = true
                    failure = null
                    scope.launch {
                        try {
                            api.withdrawCancellation(access.id, c.id)
                            notice = tr("Kündigung zurückgenommen.")
                            onChanged()
                        } catch (e: ApiException) {
                            if (e.status == 401) vm.deviceRevoked(e) else failure = e.userText()
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(tr("Zurücknehmen")) }
            },
            dismissButton = { TextButton(onClick = { withdraw = null }) { Text(tr("Abbrechen")) } },
        )
    }
}
