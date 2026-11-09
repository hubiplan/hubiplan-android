package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.RankingDetail
import de.hubiplan.app.data.RankingPrize
import de.hubiplan.app.data.RankingRow
import de.hubiplan.app.data.RankingSummary
import de.hubiplan.app.data.Rankings
import de.hubiplan.app.tr
import kotlinx.coroutines.delay

/**
 * „Ranglisten“ im Reiter Ausweis (Schießen in der App, SB4): genau das, was die Webseite des
 * Vereins zu Wettbewerben zeigt (Preisplätze oder ganze Rangliste, Namen nach Einwilligung).
 * Läuft ein Wettbewerb, fragt die App wie die Webseite alle 15 Sekunden nach einem neuen Stand.
 */
@Composable
fun RankingsSection(access: PortalAccess, vm: AppViewModel) {
    var data by remember(access.id) { mutableStateOf<Rankings?>(null) }
    var error by remember(access.id) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var open by remember(access.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        error = null
        try {
            data = api.rankings(access.id)
        } catch (e: ApiException) {
            when (e.status) {
                401 -> vm.deviceRevoked(e)
                // Ältere HUBIplan-Stände kennen die Ranglisten noch nicht: Abschnitt weglassen
                404 -> data = Rankings()
                else -> error = e.userText()
            }
        }
    }
    val d = data
    // Ohne veröffentlichte Wettbewerbe bleibt der Abschnitt weg (Verein zeigt keine Ergebnisse)
    if (d != null && d.competitions.isEmpty()) return
    Text(tr("Ranglisten"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    if (d == null) {
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { attempt++ }) { Text(tr("Erneut laden")) }
        }
        return
    }
    d.competitions.forEach { c ->
        val expanded = open == c.id
        ListItem(
            headlineContent = { Text(c.title) },
            supportingContent = { Text(rankingSummaryLine(c)) },
            modifier = Modifier.openRow(if (expanded) tr("Schließen") else tr("Öffnen")) { open = if (expanded) null else c.id },
        )
        if (expanded) RankingDetailView(access, c.id, vm)
        HorizontalDivider()
    }
}

@Composable
private fun RankingDetailView(access: PortalAccess, id: String, vm: AppViewModel) {
    var detail by remember(id) { mutableStateOf<RankingDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(access.id, id) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            var current = api.ranking(access.id, id)
            detail = current
            // Live: kleinen Stand abfragen, nur bei Änderung neu laden
            while (!current.final && current.pollSeconds != null) {
                delay(current.pollSeconds!!.coerceAtLeast(5) * 1000L)
                val v = runCatching { api.rankingVersion(access.id, id) }.getOrNull() ?: continue
                if (v.version != current.version || v.final) {
                    current = api.ranking(access.id, id)
                    detail = current
                }
            }
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.userText()
        }
    }
    Column(Modifier.padding(horizontal = Abstand.rand, vertical = Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val d = detail ?: run {
            if (error == null) Text(tr("Wird geladen …"), style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        if (!d.final) {
            Text(
                if (d.live) tr("Läuft noch – der Stand aktualisiert sich selbst.") else tr("Vorläufiges Ergebnis"),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.announce(),
            )
        }
        d.titles.forEach { Text("${it.title}: ${it.name}", fontWeight = FontWeight.Bold) }
        if (d.prizes.isNotEmpty()) {
            Text(tr("Pfänder"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
            d.prizes.forEach { Text(prizeLine(it)) }
        }
        d.scorings.forEach { s ->
            Text(s.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
            if (s.rows.isEmpty()) Text(tr("Noch keine Ergebnisse."), style = MaterialTheme.typography.bodySmall)
            s.rows.forEach { r -> Text(rankingRowLine(r), fontWeight = if (r.prize) FontWeight.SemiBold else null) }
            if (!s.complete) Text(tr("Gezeigt werden die Preisplätze."), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** „Sa, 14.06.2027 · Schützenhaus · läuft“ */
internal fun rankingSummaryLine(c: RankingSummary): String = listOfNotNull(
    formatPlainDate(c.date).takeIf { it.isNotBlank() },
    c.location.takeIf { it.isNotBlank() },
    c.organizer.takeIf { !c.own && it.isNotBlank() },
    when {
        c.live -> tr("läuft")
        c.provisional -> tr("vorläufig")
        else -> null
    },
).joinToString(" · ")

/** „1. Max M. (Zug 1) · 30“, außer Konkurrenz und Vermerk dahinter. */
internal fun rankingRowLine(r: RankingRow): String {
    val who = buildString {
        append(r.name.ifBlank { tr("ohne Namen") })
        val extra = listOf(r.unit, r.club).filter { it.isNotBlank() }
        if (extra.isNotEmpty()) append(" (").append(extra.joinToString(", ")).append(")")
    }
    val place = r.place?.let { "$it. " } ?: ""
    return buildString {
        append(place).append(who)
        if (r.value.isNotBlank()) append(" · ").append(r.value)
        if (r.outOfCompetition) append(" · ").append(tr("außer Konkurrenz"))
        if (r.note.isNotBlank()) append(" · ").append(r.note)
    }
}

/** „Kopf: Max M. (Zug 1), 37. Schuss“ bzw. „Kopf: noch dran“ */
internal fun prizeLine(p: RankingPrize): String {
    if (!p.fallen) return tr("{name}: noch dran", "name" to p.name)
    val who = listOf(p.winner, p.unit.takeIf { it.isNotBlank() }?.let { "($it)" } ?: "").filter { it.isNotBlank() }.joinToString(" ")
    val shot = p.shot?.let { tr("{n}. Schuss", "n" to it) }
    return p.name + ": " + listOfNotNull(who.ifBlank { null }, shot).joinToString(", ").ifBlank { tr("gefallen") }
}
