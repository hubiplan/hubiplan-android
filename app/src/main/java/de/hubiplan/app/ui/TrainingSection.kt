package de.hubiplan.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.MemberTraining
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.ShootingDraft
import de.hubiplan.app.data.ShootingEntry
import de.hubiplan.app.data.TrainingCode
import de.hubiplan.app.data.TrainingDetail
import de.hubiplan.app.data.TrainingLink
import de.hubiplan.app.data.evaluationLines
import de.hubiplan.app.data.shootingDetails
import de.hubiplan.app.data.trainingTime
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Kurzstand rechts neben „Training“. */
internal fun trainingSummary(t: MemberTraining): String = when {
    t.checkin.any { !it.present } -> tr("Einchecken ist offen")
    else -> tr("Dieses Jahr {n}-mal da", "n" to t.countYear)
}

/** Ergebnis eines Scans prüfen: eigene Instanz, eigener Verein, gültiger Code. */
internal fun trainingScan(text: String, server: String, access: PortalAccess): Pair<TrainingLink?, String?> {
    val link = TrainingLink.parse(text) ?: return null to tr("Das ist kein QR-Code zum Einchecken ins Training.")
    if (link.server != server) return null to tr("Der QR-Code gehört zu einer anderen HUBIplan-Adresse.")
    if (access.clubId != 0 && link.clubId != access.clubId) {
        return null to tr("Der QR-Code gehört zu einem anderen Verein. Wähle oben den passenden Zugang.")
    }
    return link to null
}

/**
 * Training unter „Ich“: Einchecken per QR-Code, eigene Anwesenheit und mit Schießsport das eigene
 * Schießbuch mit Stand nach § 14 WaffG und „Schießtermin melden“. Wer Trainings leiten darf,
 * kommt von hier zu „Trainings leiten“.
 */
@Composable
internal fun TrainingSection(access: PortalAccess, vm: AppViewModel) {
    var data by remember(access.id) { mutableStateOf<MemberTraining?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    var notice by remember(access.id) { mutableStateOf<String?>(null) }
    var actionError by remember(access.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reporting by rememberSaveable(access.id) { mutableStateOf(false) }
    var leading by rememberSaveable(access.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            data = api.memberTraining(access.id)
            error = null
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    fun act(done: String?, action: suspend (Api) -> MemberTraining) {
        val api = vm.api() ?: return
        if (busy) return
        busy = true
        notice = null
        actionError = null
        scope.launch {
            try {
                data = action(api)
                notice = done
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else actionError = e.generalError ?: e.userText()
            } finally {
                busy = false
            }
        }
    }
    val scan = rememberMeetingScanner(tr("QR-Code der Übungsleitung scannen")) { text ->
        val (link, problem) = trainingScan(text, vm.api()?.server ?: "", access)
        if (link == null) {
            actionError = problem
        } else {
            act(tr("Du bist eingecheckt. Viel Spaß beim Training!")) { it.trainingCheckin(access.id, link.sessionId, link.code) }
        }
    }
    if (leading) {
        androidx.activity.compose.BackHandler { leading = false; attempt++ }
        TrainingLeadPane(access, vm)
        return
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        ActionNotice(notice) { notice = null }
        ActionNotice(actionError, error = true) { actionError = null }
        val d = data
        if (d == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        Button(onClick = scan, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(tr("QR-Code scannen und einchecken")) }
        if (d.targets.isNotEmpty()) {
            OutlinedButton(onClick = { leading = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Trainings leiten")) }
        }
        d.checkin.forEach { s ->
            Text(
                listOf(formatPlainDate(s.date), s.title, if (s.present) tr("eingecheckt") else tr("Einchecken offen"))
                    .filter { it.isNotBlank() }.joinToString(" · "),
            )
        }
        Text(tr("Meine Anwesenheit"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        Text(tr("Dieses Jahr {year}-mal da, in den letzten 12 Monaten {last}-mal.", "year" to d.countYear, "last" to d.count12))
        if (d.attendance.isEmpty()) {
            Text(tr("Noch keine Anwesenheit eingetragen."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        d.attendance.take(30).forEach { a ->
            Text(
                listOf(formatPlainDate(a.date), a.title, a.kindLabel, a.departmentName).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val log = d.shooting
        if (d.shootingEnabled && log != null) {
            Text(tr("Schießnachweis nach § 14 WaffG"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            Text(
                tr("Es zählen nur vom Verein bestätigte Termine mit erlaubnispflichtigen Waffen. Was der Verband anerkennt, entscheidet er selbst."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            evaluationLines(log.evaluation).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (reporting) {
                ShootingReportForm(busy, onCancel = { reporting = false }, onSend = { draft ->
                    val key = UUID.randomUUID().toString()
                    act(tr("Gemeldet. Der Verein bestätigt den Termin.")) { api ->
                        api.reportShooting(access.id, draft, key).also { reporting = false }
                    }
                })
            } else {
                OutlinedButton(onClick = { reporting = true }) { Text(tr("Schießtermin melden")) }
            }
            Text(tr("Mein Schießbuch"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            if (log.entries.isEmpty()) Text(tr("Noch keine Einträge."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            log.entries.forEach { e ->
                ShootingEntryCard(e, busy, onWithdraw = {
                    act(tr("Meldung zurückgezogen.")) { it.withdrawShooting(access.id, e.id) }
                })
            }
        }
    }
}

@Composable
private fun ShootingEntryCard(e: ShootingEntry, busy: Boolean, onWithdraw: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
            Text("${formatPlainDate(e.date)}: ${e.discipline}", style = MaterialTheme.typography.titleSmall)
            val details = shootingDetails(e)
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall)
            Text(
                e.statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (e.status == "rejected") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            if (e.canWithdraw) TextButton(onClick = onWithdraw, enabled = !busy) { Text(tr("Zurückziehen")) }
        }
    }
}

@Composable
private fun ShootingReportForm(busy: Boolean, onCancel: () -> Unit, onSend: (ShootingDraft) -> Unit) {
    var date by rememberSaveable { mutableStateOf(germanDate(java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue, java.time.LocalDate.now().dayOfMonth)) }
    var discipline by rememberSaveable { mutableStateOf("") }
    var long by rememberSaveable { mutableStateOf(false) }
    var caliber by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf("") }
    var permit by rememberSaveable { mutableStateOf(true) }
    var competition by rememberSaveable { mutableStateOf(false) }
    var external by rememberSaveable { mutableStateOf(true) }
    // Angefangene Eingaben: Zurück und Reiterwechsel fragen nach (Gestaltung „Frisch mobil“, U6)
    rememberFormDraft(date, discipline, long, caliber, place, permit, competition, external, busy = busy, retainOnDispose = false)
    val iso = parseGermanDate(date)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
            Text(tr("Schießtermin melden"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
            Text(
                tr("Für Termine außerhalb der Trainings, etwa in einem anderen Verein oder bei einem Wettkampf. Der Verein prüft die Meldung und bestätigt sie; erst dann zählt sie."),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text(tr("Datum")) },
                singleLine = true,
                isError = iso == null,
                trailingIcon = { DatePickButton(date) { date = it } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(discipline, { discipline = it.take(100) }, label = { Text(tr("Disziplin")) }, singleLine = true, keyboardOptions = WEITER, modifier = Modifier.fillMaxWidth())
            LabeledRadio(tr("Kurzwaffe"), selected = !long, onClick = { long = false })
            LabeledRadio(tr("Langwaffe"), selected = long, onClick = { long = true })
            OutlinedTextField(caliber, { caliber = it.take(40) }, label = { Text(tr("Kaliber (optional)")) }, singleLine = true, keyboardOptions = WEITER, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(place, { place = it.take(200) }, label = { Text(tr("Schießstand (optional)")) }, singleLine = true, keyboardOptions = WEITER, modifier = Modifier.fillMaxWidth())
            LabeledCheckbox(tr("erlaubnispflichtige Waffe"), permit, { permit = it })
            LabeledCheckbox(tr("Wettkampf"), competition, { competition = it })
            LabeledCheckbox(tr("in einem anderen Verein"), external, { external = it })
            Button(
                onClick = {
                    if (iso != null) {
                        onSend(
                            ShootingDraft(
                                date = iso,
                                discipline = discipline.trim(),
                                weaponClass = if (long) "long" else "short",
                                caliber = caliber.trim(),
                                permitRequired = permit,
                                competition = competition,
                                place = place.trim(),
                                external = external,
                            ),
                        )
                    }
                },
                enabled = !busy && iso != null && discipline.isNotBlank(),
            ) { Text(tr("Melden")) }
            TextButton(onClick = onCancel) { Text(tr("Abbrechen")) }
        }
    }
}

/**
 * Trainings leiten (Recht „Training und Anwesenheit“ bearbeiten): Trainings der letzten 60 und
 * nächsten 30 Tage, Anwesenheit abhaken und QR-Code zum Einchecken zeigen.
 */
@Composable
private fun TrainingLeadPane(access: PortalAccess, vm: AppViewModel) {
    var list by remember(access.id) { mutableStateOf<de.hubiplan.app.data.ManageTrainings?>(null) }
    var open by rememberSaveable(access.id) { mutableStateOf<Int?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            list = api.manageTrainings(access.id)
            error = null
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    fun createToday(target: de.hubiplan.app.data.TrainingTarget) {
        val api = vm.api() ?: return
        if (busy) return
        busy = true
        val key = UUID.randomUUID().toString()
        scope.launch {
            try {
                val made = api.createTraining(access.id, target.department, java.time.LocalDate.now().toString(), tr("Übungsabend"), "practice", key)
                open = made.id
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else actionError = e.generalError ?: e.userText()
            } finally {
                busy = false
            }
        }
    }
    open?.let { id ->
        androidx.activity.compose.BackHandler { open = null; attempt++ }
        TrainingDetailPane(access, vm, id)
        return
    }
    SectionColumn {
        Text(tr("Trainings leiten"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        ActionNotice(actionError, error = true) { actionError = null }
        val l = list
        if (l == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        // Schnell ein Training für heute anlegen, je Ziel (Verein oder Untereinheit)
        l.targets.forEach { target ->
            OutlinedButton(onClick = { createToday(target) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Übungsabend heute: {name}", "name" to target.name))
            }
        }
        if (l.sessions.isEmpty()) Text(tr("Keine Trainings in den letzten 60 und nächsten 30 Tagen."))
        l.sessions.forEach { s ->
            OutlinedCard(onClick = { open = s.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                    Text(s.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOf(formatPlainDate(s.date), trainingTime(s.startsAt, s.endsAt), s.departmentName, s.discipline)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(trn("1 anwesend", "{n} anwesend", s.count), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TrainingDetailPane(access: PortalAccess, vm: AppViewModel, id: Int) {
    var detail by remember(id) { mutableStateOf<TrainingDetail?>(null) }
    var error by remember(id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var code by remember(id) { mutableStateOf<TrainingCode?>(null) }
    var big by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            detail = api.manageTraining(access.id, id)
            error = null
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    val checkinOpen = detail?.checkinOpen == true
    // Offenes Einchecken: Code holen, solange die Ansicht offen ist; neue Anwesende alle 5 Sekunden
    LaunchedEffect(id, checkinOpen) {
        if (!checkinOpen) {
            code = null
            return@LaunchedEffect
        }
        while (true) {
            val api = vm.api() ?: return@LaunchedEffect
            val next = try {
                api.trainingCode(access.id, id)
            } catch (e: ApiException) {
                if (e.status == 401) { vm.deviceRevoked(e); return@LaunchedEffect }
                null
            }
            if (next != null) {
                if (next.count != detail?.presentCount) {
                    runCatching { detail = api.manageTraining(access.id, id) }
                }
                code = next
            }
            delay(minOf(5, next?.refreshIn ?: 15) * 1000L)
        }
    }
    fun act(action: suspend (Api) -> TrainingDetail) {
        val api = vm.api() ?: return
        if (busy) return
        busy = true
        actionError = null
        scope.launch {
            try {
                detail = action(api)
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else actionError = e.generalError ?: e.userText()
            } finally {
                busy = false
            }
        }
    }
    val server = vm.api()?.server ?: ""
    val link = code?.let { TrainingLink.build(server, it) }
    if (big && link != null) {
        androidx.activity.compose.BackHandler { big = false }
        Column(
            Modifier.fillMaxSize().background(Color.White).padding(Abstand.rand),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Abstand.klein, Alignment.CenterVertically),
        ) {
            TrainingQr(link, 340)
            Text(tr("Mit der HUBIplan-App oder der Handykamera scannen, um einzuchecken."), color = Color.Black)
            TextButton(onClick = { big = false }) { Text(tr("Schließen")) }
        }
        return
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        ActionNotice(actionError, error = true) { actionError = null }
        val d = detail
        if (d == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        Text(d.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        Text(
            listOf(formatPlainDate(d.date), trainingTime(d.startsAt, d.endsAt), d.kindLabel, d.location, d.departmentName, d.discipline)
                .filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
        if (d.checkinPossible || d.checkinOpen) {
            Text(tr("Einchecken per QR-Code"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
            if (d.checkinOpen) {
                if (link != null) {
                    TrainingQr(link, 220)
                    OutlinedButton(onClick = { big = true }) { Text(tr("Code groß zeigen")) }
                }
                OutlinedButton(onClick = { act { it.openTrainingCheckin(access.id, id, false) } }, enabled = !busy) {
                    Text(tr("Einchecken schließen"))
                }
            } else {
                Button(onClick = { act { it.openTrainingCheckin(access.id, id, true) } }, enabled = !busy) {
                    Text(tr("Einchecken öffnen"))
                }
            }
        }
        Text(
            tr("Anwesenheit: {n} von {total}", "n" to d.presentCount, "total" to d.attendance.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.asHeading(),
        )
        d.attendance.forEach { row ->
            LabeledCheckbox(
                label = row.name + if (row.source == "code") " · " + tr("per QR-Code") else "",
                checked = row.present,
                enabled = !busy,
                onCheckedChange = { on -> act { it.setTrainingPresent(access.id, id, row.membership, on) } },
            )
        }
    }
}

@Composable
private fun TrainingQr(content: String, size: Int) {
    val bitmap = remember(content, size) { runCatching { qrBitmap(content, size * 3) }.getOrNull() } ?: return
    Image(
        bitmap.asImageBitmap(),
        contentDescription = tr("QR-Code zum Einchecken"),
        modifier = Modifier.size(size.dp).background(Color.White),
    )
}
