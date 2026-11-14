package de.hubiplan.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.MeetingLink
import de.hubiplan.app.data.MeetingLive
import de.hubiplan.app.data.Poll
import de.hubiplan.app.data.PollVote
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ziel der Versammlungsansicht: Versammlung und, nach dem Scan, der Code vom Bildschirm. */
internal data class LiveTarget(val meetingId: Int, val code: String?)

/** Ergebnis eines Scans prüfen: eigene Instanz, eigener Verein, gültiger Code. */
internal fun meetingTarget(text: String, server: String, access: PortalAccess): Pair<LiveTarget?, String?> {
    val link = MeetingLink.parse(text) ?: return null to tr("Das ist kein QR-Code zum Anmelden in einer Versammlung.")
    if (link.server != server) return null to tr("Der QR-Code gehört zu einer anderen HUBIplan-Instanz.")
    if (access.clubId != 0 && link.clubId != access.clubId) {
        return null to tr("Der QR-Code gehört zu einem anderen Verein. Wähle oben den passenden Zugang.")
    }
    return LiveTarget(link.meetingId, link.code) to null
}

/** QR-Code der Leitung scannen; [onResult] bekommt den gelesenen Text. */
@Composable
internal fun rememberMeetingScanner(onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onResult)
    }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(tr("QR-Code der Versammlung scannen"))
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }
}

internal fun voteLabel(v: PollVote): String = when (v.how) {
    "own" -> tr("Deine Stimme")
    "proxy" -> tr("Vollmacht von {name}", "name" to v.name)
    else -> tr("Für {name}", "name" to v.name)
}

internal fun liveStatus(state: MeetingLive): String = when {
    state.present && state.mode == "online" -> tr("Du nimmst aus der Ferne teil.")
    state.present -> tr("Du bist als anwesend eingetragen.")
    state.status == "left" -> tr("Du hast dich abgemeldet.")
    else -> tr("Du bist noch nicht angemeldet.")
}

/**
 * Laufende Versammlung: „Ich bin da“, „Online teilnehmen“, „Ich gehe“ und Abstimmen. Fragt alle
 * vier Sekunden nach, solange die Ansicht offen ist.
 */
@Composable
internal fun MeetingLivePane(access: PortalAccess, vm: AppViewModel, target: LiveTarget) {
    var state by remember(target) { mutableStateOf<MeetingLive?>(null) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var closed by remember(target) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var codeUsed by remember(target) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(target) {
        // Frisch gescannt: gleich anmelden, bevor der Code abläuft
        if (target.code != null && !codeUsed) {
            codeUsed = true
            val api = vm.api() ?: return@LaunchedEffect
            try {
                state = api.meetingCheckin(access.id, target.meetingId, target.code)
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else error = e.generalError ?: e.userText()
            }
        }
        while (true) {
            val api = vm.api() ?: return@LaunchedEffect
            try {
                state = api.meetingLive(access.id, target.meetingId)
                closed = false
            } catch (e: ApiException) {
                when (e.status) {
                    401 -> { vm.deviceRevoked(e); return@LaunchedEffect }
                    404 -> closed = true
                    else -> error = e.userText()
                }
            }
            delay(4000)
        }
    }
    fun send(action: suspend (de.hubiplan.app.data.Api) -> MeetingLive) {
        val api = vm.api() ?: return
        busy = true
        error = null
        scope.launch {
            try {
                state = action(api)
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else error = e.generalError ?: e.userText()
            } finally {
                busy = false
            }
        }
    }
    val scan = rememberMeetingScanner { text ->
        val (next, problem) = meetingTarget(text, vm.api()?.server ?: "", access)
        if (next == null || next.meetingId != target.meetingId) {
            error = problem ?: tr("Der QR-Code gehört zu einer anderen Versammlung.")
        } else {
            send { it.meetingCheckin(access.id, target.meetingId, next.code) }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Abstand.rand),
        verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
    ) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
        val s = state
        if (s == null) {
            if (closed) Text(tr("Die Anmeldung zu dieser Versammlung ist nicht geöffnet.")) else Loading()
            return@Column
        }
        Text(s.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
        val sub = listOf(s.kindName, s.unit, s.location).filter { it.isNotBlank() && it != s.title }.joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)
        StatusNotice(
            liveStatus(s) + if (s.present && s.votes > 1) " " + tr("Du hast {n} Stimmen (mit Vollmachten).", "n" to s.votes.toString()) else "",
        )
        if (s.isOrganization) Text(tr("Organisationsmitglieder trägt die Leitung mit ihrem Vertreter ein."))
        if (!s.present && !s.isOrganization && s.checkinOpen) {
            Button(onClick = scan, enabled = !busy) { Text(tr("QR-Code scannen: Ich bin da")) }
        }
        if (s.canJoinRemote) {
            OutlinedButton(
                onClick = { send { it.meetingCheckin(access.id, target.meetingId, null, remote = true) } },
                enabled = !busy,
            ) { Text(tr("Online teilnehmen")) }
        }
        if (s.present) {
            OutlinedButton(onClick = { send { it.meetingLeave(access.id, target.meetingId) } }, enabled = !busy) {
                Text(tr("Ich gehe"))
            }
        }
        if (s.polls.isNotEmpty()) {
            Text(tr("Abstimmungen"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            s.polls.forEach { poll ->
                PollCard(poll, busy) { votes -> send { it.meetingVote(access.id, target.meetingId, poll.id, votes) } }
            }
        }
    }
}

@Composable
private fun PollCard(poll: Poll, busy: Boolean, onVote: (Map<Int, Int>) -> Unit) {
    val choice = remember(poll.id) { mutableStateMapOf<Int, Int>() }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Text("${poll.agendaNumber.ifBlank { poll.agendaPosition.toString() }}. ${poll.question}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            val details = listOf(
                poll.candidate,
                poll.office,
                if (poll.secret) tr("geheime Abstimmung") else tr("offene Abstimmung"),
            ).filter { it.isNotBlank() }.joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall)
            val open = poll.openVotes
            when {
                poll.running && open.isNotEmpty() -> {
                    open.forEach { v ->
                        Text(voteLabel(v), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                        Column(Modifier.selectableGroup()) {
                            poll.options.forEach { o ->
                                Row(
                                    Modifier.fillMaxWidth().selectable(
                                        selected = choice[v.attendance] == o.id,
                                        onClick = { choice[v.attendance] = o.id },
                                        role = Role.RadioButton,
                                    ).padding(vertical = Abstand.eng),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = choice[v.attendance] == o.id, onClick = null)
                                    Text(o.label, Modifier.padding(start = Abstand.klein))
                                }
                            }
                        }
                    }
                    Text(tr("Eine abgegebene Stimme lässt sich nicht mehr ändern."), style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { onVote(open.associate { it.attendance to choice.getValue(it.attendance) }) },
                        enabled = !busy && open.all { choice[it.attendance] != null },
                    ) { Text(if (open.size > 1) tr("Stimmen abgeben") else tr("Stimme abgeben")) }
                }
                poll.running && poll.votes.isNotEmpty() ->
                    Text(tr("Danke, deine Stimme ist abgegeben. Das Ergebnis kommt nach dem Ende."))
                poll.running -> Text(tr("Abstimmen können nur anwesende Stimmberechtigte."))
                poll.result != null -> {
                    Text(tr("Ergebnis:"), style = MaterialTheme.typography.titleSmall)
                    poll.result.forEach { r -> Text("${r.label}: ${r.votes}") }
                }
            }
        }
    }
}
