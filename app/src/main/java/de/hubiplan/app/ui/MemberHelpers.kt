package de.hubiplan.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.HelperDuty
import de.hubiplan.app.data.HelperOverview
import de.hubiplan.app.data.HelperPlan
import de.hubiplan.app.data.HelperShift
import de.hubiplan.app.data.MyShift
import de.hubiplan.app.data.canCancel
import de.hubiplan.app.data.canSignup
import de.hubiplan.app.tr
import kotlinx.coroutines.launch

/** Umschalter im Reiter „Aufgaben“, wenn der Verein Helferdienste nutzt. */
@Composable
internal fun TaskSwitch(helpers: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(horizontal = Abstand.rand), horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
        FilterChip(selected = !helpers, onClick = { onChange(false) }, label = { Text(tr("Aufgaben")) })
        FilterChip(selected = helpers, onClick = { onChange(true) }, label = { Text(tr("Helferdienste")) })
    }
}

/** Belegung einer Schicht: „2 von 4 frei“ bzw. „besetzt“. */
internal fun shiftFreeLabel(shift: HelperShift): String =
    if (shift.free > 0) tr("{free} von {needed} frei", "free" to shift.free, "needed" to shift.needed) else tr("besetzt")

/** Kopfzeile eines Dienstes mit Ort und Mindestalter. */
internal fun dutyLine(duty: HelperDuty): String = buildList {
    add(duty.name)
    if (duty.location.isNotBlank()) add(duty.location)
    duty.minAge?.takeIf { it > 0 }?.let { add(tr("ab {age} Jahren", "age" to it)) }
}.joinToString(" · ")

/**
 * Helferdienste im Mitgliederbereich (HUBIplan Schritt H2/H5): eigene Dienste mit Absage bis zur
 * Frist und veröffentlichte Pläne mit freien Schichten zum Eintragen. Nichts wird gespeichert.
 */
@Composable
internal fun MemberHelpers(api: Api, access: Int, server: String, vm: AppViewModel, switch: @Composable () -> Unit) {
    var data by remember(api, access) { mutableStateOf<HelperOverview?>(null) }
    var error by remember(api, access) { mutableStateOf<Problem?>(null) }
    var notice by remember(api, access) { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loadedAt by remember(api, access) { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var cancel by remember { mutableStateOf<MyShift?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reload++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(api, access, reload) {
        try {
            data = api.memberHelpers(access)
            error = null
            loadedAt = System.currentTimeMillis()
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        } finally {
            refreshing = false
        }
    }
    fun act(done: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                action()
                notice = done
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
            } finally {
                busy = false
                reload++
            }
        }
    }
    cancel?.let { shift ->
        AlertDialog(
            onDismissRequest = { cancel = null },
            title = { Text(tr("Dienst absagen")) },
            text = { Text("${shift.duty} · ${formatWhen(shift.startsAt, shift.endsAt, false)}") },
            confirmButton = {
                TextButton(onClick = {
                    cancel = null
                    act(tr("Dienst abgesagt.")) { api.cancelShift(access, shift.id) }
                }) { Text(tr("Dienst absagen")) }
            },
            dismissButton = { TextButton(onClick = { cancel = null }) { Text(tr("Abbrechen")) } },
        )
    }
    val current = data
    // Ziehen zum Aktualisieren (UX-Audit 08.11. A-02)
    Refreshable(refreshing, onRefresh = { refreshing = true; reload++ }) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(tr("Helferdienste"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading().padding(Abstand.rand))
            switch()
            Row(Modifier.padding(horizontal = Abstand.rand), horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$server/mein-bereich/helferdienste?zugang=$access")))
                }) { Text(tr("Im Browser öffnen")) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Abstand.rand))
            error?.let { ProblemNotice(it, onRetry = { refreshing = true; reload++ }, since = loadedAt.takeIf { current != null }) }
            if (error == null) loadedAt?.let { FreshnessLine(it) { refreshing = true; reload++ } }
            notice?.let { StatusNotice(it, Modifier.padding(horizontal = Abstand.rand)) }
            if (current == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(Abstand.rand))
        }
        if (current != null) {
            item {
                Text(tr("Meine Dienste"), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.asHeading().padding(start = Abstand.rand, end = Abstand.rand, top = Abstand.rand))
                if (current.mine.isEmpty()) Text(tr("Du hast noch keine Dienste."), modifier = Modifier.padding(Abstand.rand))
            }
            items(current.mine, key = { "m${it.id}" }) { shift ->
                MyShiftRow(shift, current.canCancel(shift.id), busy) { cancel = shift }
                HorizontalDivider()
            }
            val plans = current.plans.filter { plan -> plan.duties.any { duty -> duty.shifts.any { !it.past } } }
            item {
                Text(tr("Dienstpläne"), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.asHeading().padding(start = Abstand.rand, end = Abstand.rand, top = Abstand.gross))
                if (plans.isEmpty()) Text(tr("Zurzeit gibt es keine offenen Dienstpläne."), modifier = Modifier.padding(Abstand.rand))
            }
            items(plans, key = { "p${it.id}" }) { plan ->
                PlanCard(plan, busy) { shift ->
                    act(tr("Du bist eingetragen.")) { api.signupShift(access, shift.id) }
                }
                HorizontalDivider()
            }
        }
    }
    }
}

@Composable
private fun MyShiftRow(shift: MyShift, canCancel: Boolean, busy: Boolean, onCancel: () -> Unit) {
    ListItem(
        headlineContent = { Text("${shift.duty} · ${shift.plan}") },
        supportingContent = {
            Column {
                Text(formatWhen(shift.startsAt, shift.endsAt, false))
                if (shift.location.isNotBlank()) Text(shift.location)
                if (!canCancel) Text(tr("Absage nur über die Verwaltung"), style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = if (canCancel) {
            { TextButton(onClick = onCancel, enabled = !busy) { Text(tr("Dienst absagen")) } }
        } else null,
    )
}

@Composable
private fun PlanCard(plan: HelperPlan, busy: Boolean, onSignup: (HelperShift) -> Unit) {
    Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
        Text(plan.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
        val info = listOf(plan.unit, plan.eventTitle).filter { it.isNotBlank() }
        if (info.isNotEmpty()) Text(info.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        if (plan.note.isNotBlank()) Text(plan.note)
        if (plan.signup != "self") Text(tr("In diesen Plan trägt nur die Verwaltung ein."), style = MaterialTheme.typography.bodySmall)
        plan.maxShifts?.takeIf { it > 0 }?.let {
            Text(tr("Höchstens {n} Schichten je Person.", "n" to it), style = MaterialTheme.typography.bodySmall)
        }
        plan.duties.forEach { duty ->
            val open = duty.shifts.filter { !it.past }
            if (open.isEmpty()) return@forEach
            Text(dutyLine(duty), style = MaterialTheme.typography.titleSmall)
            if (duty.description.isNotBlank()) Text(duty.description, style = MaterialTheme.typography.bodySmall)
            open.forEach { shift ->
                Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                    Text(formatWhen(shift.startsAt, shift.endsAt, false))
                    Text(shiftFreeLabel(shift), style = MaterialTheme.typography.bodySmall)
                    if (shift.note.isNotBlank()) Text(shift.note, style = MaterialTheme.typography.bodySmall)
                    if (shift.names.isNotEmpty()) Text(shift.names.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    when {
                        shift.mine != null -> Text(tr("Du bist eingetragen."), color = MaterialTheme.colorScheme.primary)
                        plan.canSignup(shift) -> Button(onClick = { onSignup(shift) }, enabled = !busy) { Text(tr("Mich eintragen")) }
                    }
                }
            }
        }
    }
}
