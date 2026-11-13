package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.hubiplan.app.data.AdminClient
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.HouseholdInfo
import de.hubiplan.app.data.MemberQualification
import de.hubiplan.app.data.ObligationItem
import de.hubiplan.app.data.ObligationList
import de.hubiplan.app.data.QualificationRow
import de.hubiplan.app.tr
import de.hubiplan.app.trn

// Haushalte in der Akte, Qualifikationen und Fristenkalender in der Verwaltung der App.
// Alles nur lesen; gepflegt wird im Browser (hubiplan docs/app.md).

/** Rolle im Haushalt wie im Browser (Erwachsener/Kind); bei Firmen ohne Rolle. */
fun householdRoleLabel(role: String): String = when (role) {
    "adult" -> tr("Erwachsener")
    "child" -> tr("Kind")
    else -> ""
}

/** Stand eines Nachweises wie im Browser. */
fun qualificationStatusLabel(status: String): String = when (status) {
    "expired" -> tr("abgelaufen")
    "expiring" -> tr("läuft bald ab")
    else -> tr("gültig")
}

/** Stand einer Pflicht im Fristenkalender wie im Browser. */
fun obligationStatusLabel(status: String): String = when (status) {
    "overdue" -> tr("überfällig")
    "soon" -> tr("bald fällig")
    "planned" -> tr("geplant")
    "election" -> tr("nach jeder Vorstandswahl")
    "done" -> tr("erledigt")
    "inactive" -> tr("abgeschaltet")
    else -> status
}

/** Pflichten: überfällige und bald fällige zuerst, dann nach Frist. */
fun sortObligations(items: List<ObligationItem>): List<ObligationItem> {
    val rank = mapOf("overdue" to 0, "soon" to 1, "planned" to 2, "election" to 3, "done" to 4, "inactive" to 5)
    return items.sortedWith(compareBy({ rank[it.status] ?: 6 }, { it.dueDate ?: "9999" }, { it.title.lowercase() }))
}

/** Gültigkeit in einer Zeile: „bis 31.12.2027“ oder „unbefristet“. */
fun qualificationUntil(validUntil: String?): String =
    validUntil?.let { tr("bis {date}", "date" to formatPlainDate(it)) } ?: tr("unbefristet")

@Composable
internal fun HouseholdsSection(households: List<HouseholdInfo>, onOpenMember: ((Int) -> Unit)?) {
    HorizontalDivider()
    Text(tr("Haushalt und Firma"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    households.forEach { h ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                Text(h.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(
                        if (h.isCompany) tr("Firma") else tr("Familie/Haushalt"),
                        householdRoleLabel(h.role).ifBlank { null }.takeIf { !h.isCompany },
                        h.since?.let { tr("seit {date}", "date" to formatPlainDate(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (h.others.isEmpty()) {
                    Text(tr("Keine weiteren Mitglieder."), style = MaterialTheme.typography.bodySmall)
                }
                h.others.forEach { o ->
                    val line = listOfNotNull(o.name, householdRoleLabel(o.role).ifBlank { null }.takeIf { !h.isCompany }).joinToString(" · ")
                    if (onOpenMember != null) {
                        ListItem(headlineContent = { Text(line) }, modifier = Modifier.openRow(tr("Akte öffnen")) { onOpenMember(o.memberId) })
                    } else {
                        Text(line)
                    }
                }
            }
        }
    }
    Text(tr("Haushalte und Firmen pflegen geht im Browser."), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun MemberQualificationsSection(client: AdminClient, id: Int, errors: AdminErrors, reload: Int) {
    var list by remember(id) { mutableStateOf<List<MemberQualification>?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id, reload) {
        try {
            list = client.memberQualifications(id)
            error = null
        } catch (e: ApiException) {
            error = errors.message(e)
        }
    }
    HorizontalDivider()
    Text(tr("Qualifikationen"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
    val current = list?.filter { it.current }
    if (current != null && current.isEmpty()) Text(tr("Keine."), style = MaterialTheme.typography.bodySmall)
    current?.forEach { q ->
        Column {
            Text(q.kindName)
            Text(
                listOfNotNull(
                    qualificationStatusLabel(q.status),
                    q.acquiredOn?.let {
                        if (q.inspectionOnly) tr("eingesehen am {date}", "date" to formatPlainDate(it)) else tr("seit {date}", "date" to formatPlainDate(it))
                    },
                    qualificationUntil(q.validUntil),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (q.status == "valid") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Bereich „Qualifikationen“: bald ablaufende (Vorgabe) oder alle maßgeblichen Nachweise. */
@Composable
fun QualificationsSection(client: AdminClient, errors: AdminErrors, reload: Int, onOpen: (Int) -> Unit) {
    var soonOnly by remember(client.unitId) { mutableStateOf(true) }
    var attempt by remember(client.unitId) { mutableIntStateOf(0) }
    var rows by remember(client.unitId, soonOnly, reload, attempt) { mutableStateOf<List<QualificationRow>?>(null) }
    var error by remember(client.unitId, soonOnly, reload, attempt) { mutableStateOf<String?>(null) }
    LaunchedEffect(client, soonOnly, reload, attempt) {
        try {
            rows = client.qualifications(soonOnly)
        } catch (e: ApiException) {
            error = errors.message(e)
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    FilterChip(selected = soonOnly, onClick = { soonOnly = true }, label = { Text(tr("Läuft bald ab")) })
                    FilterChip(selected = !soonOnly, onClick = { soonOnly = false }, label = { Text(tr("Alle")) })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                if (rows == null && error != null) OutlinedButton(onClick = { attempt++ }) { Text(tr("Erneut laden")) }
                val list = rows
                if (list == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (list != null) {
                    Text(
                        when {
                            list.isEmpty() && soonOnly -> tr("Kein Nachweis läuft bald ab.")
                            list.isEmpty() -> tr("Noch keine Nachweise.")
                            else -> trn("1 Nachweis", "{n} Nachweise", list.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(rows.orEmpty(), key = { "nachweis-${it.id}" }) { r ->
            ListItem(
                headlineContent = { Text(r.name) },
                supportingContent = {
                    Text(listOf(r.kindName, qualificationStatusLabel(r.status), qualificationUntil(r.validUntil)).joinToString(" · "))
                },
                modifier = Modifier.openRow(tr("Akte öffnen")) { onOpen(r.member) },
            )
            HorizontalDivider()
        }
        item {
            Text(
                tr("Nachweise eintragen und ändern geht im Browser."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(Abstand.rand),
            )
        }
    }
}

/** Bereich „Fristen“: Pflichten des Fristenkalenders, überfällige zuerst. */
@Composable
fun ObligationsSection(client: AdminClient, errors: AdminErrors, reload: Int) {
    var attempt by remember(client.unitId) { mutableIntStateOf(0) }
    var data by remember(client.unitId, reload, attempt) { mutableStateOf<ObligationList?>(null) }
    var error by remember(client.unitId, reload, attempt) { mutableStateOf<String?>(null) }
    var open by remember(client.unitId) { mutableStateOf<ObligationItem?>(null) }
    LaunchedEffect(client, reload, attempt) {
        try {
            data = client.obligations()
        } catch (e: ApiException) {
            error = errors.message(e)
        }
    }
    val current = open
    if (current != null) {
        ObligationScreen(current) { open = null }
        return
    }
    val sorted = data?.items?.let(::sortObligations)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                if (data == null && error != null) OutlinedButton(onClick = { attempt++ }) { Text(tr("Erneut laden")) }
                if (data == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (sorted != null && sorted.isEmpty()) Text(tr("Noch keine Pflichten eingetragen."), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(sorted.orEmpty(), key = { "pflicht-${it.id}" }) { o ->
            ListItem(
                headlineContent = { Text(o.title) },
                supportingContent = { Text(obligationLine(o)) },
                modifier = Modifier.openRow { open = o },
            )
            HorizontalDivider()
        }
        item {
            Text(
                tr("Pflichten anlegen und als erledigt eintragen geht im Browser."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(Abstand.rand),
            )
        }
    }
}

/** Stand, Frist und zuständige Rolle in einer Zeile. */
fun obligationLine(o: ObligationItem): String = listOfNotNull(
    obligationStatusLabel(o.status),
    o.dueDate?.let { tr("Frist {date}", "date" to formatPlainDate(it)) },
    o.roleName.ifBlank { null },
).joinToString(" · ")

@Composable
private fun ObligationScreen(o: ObligationItem, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHead(o.title, onBack = onBack)
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Text(obligationLine(o), style = MaterialTheme.typography.bodyMedium)
                o.intervalMonths?.let {
                    Text(trn("wiederholt sich jeden Monat", "wiederholt sich alle {n} Monate", it), style = MaterialTheme.typography.bodySmall)
                }
                if (o.note.isNotBlank()) Text(o.note, style = MaterialTheme.typography.bodyMedium)
                if (o.history.isNotEmpty()) {
                    Text(tr("Verlauf"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
                    o.history.forEach { h ->
                        Text(
                            listOf(tr("erledigt am {date}", "date" to formatPlainDate(h.doneOn)), h.doneBy, h.note)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
