package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.MemberSurvey
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.ResultOption
import de.hubiplan.app.data.ResultQuestion
import de.hubiplan.app.data.SurveyAnswerRow
import de.hubiplan.app.data.SurveyList
import de.hubiplan.app.data.SurveyOption
import de.hubiplan.app.data.SurveyQuestion
import de.hubiplan.app.data.SurveyResult
import de.hubiplan.app.data.answerPayload
import de.hubiplan.app.data.initialRow
import de.hubiplan.app.data.ready
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import kotlinx.coroutines.launch
import androidx.compose.runtime.key

/** Ja/Nein/Vielleicht/Enthaltung wie im Browser. */
internal fun surveyValueLabel(value: String): String = when (value) {
    "yes" -> tr("Ja")
    "no" -> tr("Nein")
    "maybe" -> tr("Vielleicht")
    else -> tr("Enthaltung")
}

/** Bezeichnung einer Antwort: Text oder bei Terminfindung der Zeitpunkt. */
internal fun surveyOptionLabel(label: String, startsAt: String?, endsAt: String?, allDay: Boolean): String = when {
    label.isNotBlank() -> label
    startsAt == null -> ""
    allDay -> formatDate(startsAt, CALENDAR_ZONE)
    endsAt != null -> runCatching { formatWhen(startsAt, endsAt, false) }.getOrDefault(formatDayTime(startsAt))
    else -> formatDayTime(startsAt)
}

internal fun SurveyOption.display() = surveyOptionLabel(label, startsAt, endsAt, allDay)
internal fun ResultOption.display() = surveyOptionLabel(label, startsAt, endsAt, allDay)

/** Zweite Zeile einer Umfrage: Untereinheit, Termin, Ende, anonym. */
internal fun surveyLine(s: MemberSurvey): String = listOfNotNull(
    s.unit.ifBlank { null },
    s.event?.let { tr("zum Termin {title}", "title" to it.title) },
    if (s.isOpen && s.closesAt != null) tr("läuft bis {when}", "when" to formatDayTime(s.closesAt)) else null,
    if (!s.isOpen) tr("beendet") else null,
    if (s.anonymous) tr("anonym") else null,
).joinToString(" · ")

/** Kurzstand rechts neben „Umfragen“. */
internal fun surveySummary(list: SurveyList): String {
    val waiting = list.surveys.count { it.waiting }
    return when {
        waiting > 0 -> trn("1 wartet auf dich", "{n} warten auf dich", waiting)
        list.surveys.any { it.isOpen } -> tr("alle beantwortet")
        else -> tr("Keine offenen Umfragen")
    }
}

/**
 * Umfragen unter „Ich“ (HUBIplan Umfragen, Stufe U2): offene zuerst, Antworten abgeben, ändern
 * oder zurückziehen, Ergebnis und „Zusage übernehmen“. Nichts wird auf dem Gerät gespeichert.
 */
@Composable
internal fun SurveysSection(access: PortalAccess, vm: AppViewModel) {
    var list by remember(access.id) { mutableStateOf<SurveyList?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    var notice by remember(access.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember(access.id) { mutableStateOf<Int?>(null) }
    var withdraw by remember { mutableStateOf<MemberSurvey?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            list = api.memberSurveys(access.id)
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
                editing = null
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
        if (l.surveys.isEmpty()) Text(tr("Gerade läuft keine Umfrage für dich."))
        val (open, closed) = l.surveys.partition { it.isOpen }
        open.forEach { s ->
            SurveyCard(
                s, busy, editing = editing == s.id,
                onEdit = { editing = if (it) s.id else null },
                onSubmit = { draft -> act(tr("Danke, deine Antwort ist gespeichert.")) { it.answerSurvey(access.id, s.id, s.answerPayload(draft)) } },
                onWithdraw = { withdraw = s },
                onFollow = { act(tr("Zusage gespeichert.")) { it.followSurvey(access.id, s.id) } },
            )
        }
        if (closed.isNotEmpty()) {
            Text(tr("Beendete Umfragen"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            closed.forEach { s ->
                SurveyCard(
                    s, busy, editing = false, onEdit = {}, onSubmit = {}, onWithdraw = {},
                    onFollow = { act(tr("Zusage gespeichert.")) { it.followSurvey(access.id, s.id) } },
                )
            }
        }
    }
    withdraw?.let { s ->
        AlertDialog(
            onDismissRequest = { withdraw = null },
            title = { Text(tr("Antwort zurückziehen?")) },
            text = { Text(s.title) },
            confirmButton = {
                TextButton(onClick = {
                    withdraw = null
                    act(tr("Antwort zurückgezogen.")) { it.withdrawSurvey(access.id, s.id) }
                }) { Text(tr("Antwort zurückziehen")) }
            },
            dismissButton = { TextButton(onClick = { withdraw = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

@Composable
private fun SurveyCard(
    s: MemberSurvey,
    busy: Boolean,
    editing: Boolean,
    onEdit: (Boolean) -> Unit,
    onSubmit: (Map<Int, SurveyAnswerRow>) -> Unit,
    onWithdraw: () -> Unit,
    onFollow: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            Text(surveyLine(s), style = MaterialTheme.typography.bodySmall)
            if (s.description.isNotBlank()) Text(s.description)
            s.followEvent?.let { ev ->
                Text(tr("Termin steht fest: {title} am {day}.", "title" to ev.title, "day" to formatDate(ev.startsAt)))
                if (ev.response == "yes") Text(tr("Du hast zugesagt."), style = MaterialTheme.typography.bodySmall)
                if (s.canFollow) Button(onClick = onFollow, enabled = !busy) { Text(tr("Zusage übernehmen")) }
            }
            if (s.answered && !editing) {
                Text(tr("Du hast geantwortet."), style = MaterialTheme.typography.bodyMedium)
                if (s.anonymous) Text(tr("Anonym, nicht mehr änderbar."), style = MaterialTheme.typography.bodySmall)
            }
            if (s.canAnswer && (!s.answered || editing)) {
                SurveyForm(s, busy, onSubmit = onSubmit, onCancel = if (editing) ({ onEdit(false) }) else null)
            } else if (s.canChange) {
                Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    OutlinedButton(onClick = { onEdit(true) }, enabled = !busy) { Text(tr("Antwort ändern")) }
                    TextButton(onClick = onWithdraw, enabled = !busy) { Text(tr("Antwort zurückziehen")) }
                }
            }
            s.results?.let { r ->
                Text(tr("Ergebnis"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                SurveyResults(r)
            } ?: run {
                if (s.answered && s.resultsMode == "after_close" && s.isOpen) {
                    Text(tr("Das Ergebnis erscheint nach dem Ende der Umfrage."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SurveyForm(
    s: MemberSurvey,
    busy: Boolean,
    onSubmit: (Map<Int, SurveyAnswerRow>) -> Unit,
    onCancel: (() -> Unit)?,
) {
    val draft = remember(s.id, s.mine) {
        mutableStateMapOf<Int, SurveyAnswerRow>().apply { s.questions.forEach { put(it.id, it.initialRow(s.mine)) } }
    }
    // Angefangene Antworten: Zurück und Reiterwechsel fragen nach (AND-05)
    key(s.id, s.mine) { rememberFormDraft(draft.toMap().toSortedMap().toString(), busy = busy, retainOnDispose = false) }
    Column(verticalArrangement = Arrangement.spacedBy(Abstand.mittel)) {
        s.questions.forEach { q ->
            QuestionInput(q, draft[q.id] ?: SurveyAnswerRow(q.id)) { draft[q.id] = it }
        }
        if (s.anonymous) {
            Text(tr("Anonym. Deine Antwort lässt sich danach nicht mehr ändern."), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            onCancel?.let { TextButton(onClick = it) { Text(tr("Abbrechen")) } }
            Button(onClick = { onSubmit(draft.toMap()) }, enabled = !busy && s.ready(draft)) {
                Text(if (s.answered) tr("Änderung speichern") else tr("Absenden"))
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, radio: Boolean, onClick: () -> Unit) {
    if (!radio) {
        LabeledCheckbox(label, selected, { onClick() })
        return
    }
    LabeledRadio(label, selected, onClick)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionInput(q: SurveyQuestion, row: SurveyAnswerRow, set: (SurveyAnswerRow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
        Text(
            if (q.required) q.text else tr("{text} (freiwillig)", "text" to q.text),
            style = MaterialTheme.typography.titleSmall,
        )
        when (q.kind) {
            "single", "multiple" -> {
                val chosen = row.options.orEmpty()
                val multi = q.kind == "multiple"
                if (multi && q.maxChoices != null) {
                    Text(tr("Höchstens {n} Antworten", "n" to q.maxChoices), style = MaterialTheme.typography.bodySmall)
                }
                q.options.forEach { o ->
                    val label = o.capacity?.let { "${o.display()} (${tr("{n} Plätze", "n" to it)})" } ?: o.display()
                    Choice(label, o.id in chosen, radio = !multi) {
                        set(
                            row.copy(
                                options = when {
                                    !multi -> listOf(o.id)
                                    o.id in chosen -> chosen - o.id
                                    else -> chosen + o.id
                                },
                            ),
                        )
                    }
                }
                if (!multi && !q.required && chosen.isNotEmpty()) {
                    TextButton(onClick = { set(row.copy(options = emptyList())) }) { Text(tr("Auswahl löschen")) }
                }
            }
            "yesno" -> FlowRow {
                listOf("yes", "no", "abstain").forEach { v ->
                    Choice(surveyValueLabel(v), row.value == v, radio = true) { set(row.copy(value = v)) }
                }
            }
            "dates" -> {
                val values = if (q.allowMaybe) listOf("yes", "maybe", "no") else listOf("yes", "no")
                q.options.forEach { o ->
                    Text(o.display(), style = MaterialTheme.typography.bodyMedium)
                    FlowRow {
                        values.forEach { v ->
                            val key = o.id.toString()
                            Choice(surveyValueLabel(v), row.dates?.get(key) == v, radio = true) {
                                set(row.copy(dates = row.dates.orEmpty() + (key to v)))
                            }
                        }
                    }
                }
            }
            "quantity" -> {
                val amounts = row.quantities.orEmpty()
                val limit = q.quantityLimit
                Text(trn("Zusammen höchstens {n} Stück", "Zusammen höchstens {n} Stück", limit), style = MaterialTheme.typography.bodySmall)
                q.options.forEach { o ->
                    val key = o.id.toString()
                    OutlinedTextField(
                        value = (amounts[key] ?: 0).toString(),
                        onValueChange = { text ->
                            val n = text.filter(Char::isDigit).take(3).toIntOrNull() ?: 0
                            set(row.copy(quantities = amounts + (key to n)))
                        },
                        label = { Text(o.display()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (amounts.values.sum() > limit) {
                    Text(
                        tr("Zusammen höchstens {n} Stück.", "n" to limit),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.announce(urgent = true),
                    )
                }
            }
            else -> OutlinedTextField(
                value = row.text.orEmpty(),
                onValueChange = { set(row.copy(text = it.take(500))) },
                label = { Text(tr("Antwort")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }
}

/** Ergebnis wie im Browser: Zahl, Balken, Namen nur bei offenen Umfragen. */
@Composable
internal fun SurveyResults(r: SurveyResult) {
    if (r.hidden) {
        Text(
            tr("Das Ergebnis erscheint ab drei Antworten, damit niemand zurückrechnen kann.") + " " +
                trn("Bisher {n} Antwort.", "Bisher {n} Antworten.", r.participants),
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    Text(trn("{n} Teilnahme", "{n} Teilnahmen", r.participants), style = MaterialTheme.typography.bodySmall)
    r.questions.forEach { q -> ResultBlock(q, r.participants) }
}

@Composable
private fun ResultBlock(q: ResultQuestion, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
        Text(q.text, style = MaterialTheme.typography.titleSmall)
        q.values?.let { values ->
            listOf("yes", "no", "abstain").forEach { v ->
                val entry = values[v] ?: return@forEach
                ResultLine(surveyValueLabel(v), entry.count, total, entry.names, best = false)
            }
        }
        if (q.kind == "text") {
            val texts = q.texts.orEmpty()
            if (texts.isEmpty()) Text(tr("Noch keine Antworten."), style = MaterialTheme.typography.bodySmall)
            texts.forEach { t -> Text(if (t.name.isNotBlank()) "„${t.text}“ · ${t.name}" else "„${t.text}“") }
        }
        q.options?.let { options ->
            val base = if (q.kind == "quantity") maxOf(1, options.maxOfOrNull { it.count } ?: 1) else total
            options.forEach { o ->
                val count = o.capacity?.let { "${o.count} / $it" } ?: o.count.toString()
                val extra = if (q.kind == "dates" && (o.maybe ?: 0) > 0) " " + tr("+{n} vielleicht", "n" to (o.maybe ?: 0)) else ""
                ResultLine(o.display(), o.count, base, o.names, best = q.best?.contains(o.id) == true, countText = count + extra)
            }
        }
    }
}

@Composable
private fun ResultLine(label: String, count: Int, total: Int, names: List<String>, best: Boolean, countText: String = count.toString()) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (best) "★ $label" else label, modifier = Modifier.weight(1f))
            Text(countText, style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { if (total > 0) count.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        if (names.isNotEmpty()) Text(names.joinToString(", "), style = MaterialTheme.typography.bodySmall)
    }
}
