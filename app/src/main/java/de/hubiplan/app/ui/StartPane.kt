package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.Occurrence
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.StartTodo
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import java.time.LocalDate
import kotlinx.coroutines.launch

/** So weit schaut „Start“ nach vorn; die ganze Liste steht unter „Termine“. */
private const val START_TAGE = 120L

/** Der nächste Termin für „Start“: der früheste, der nicht abgesagt ist. */
internal fun naechsterTermin(termine: List<Occurrence>): Occurrence? = termine.firstOrNull { !it.isCancelled }

/** Termine, die noch auf die eigene Rückmeldung warten (ohne den schon groß gezeigten). */
internal fun offeneRueckmeldungen(termine: List<Occurrence>, ohne: Occurrence? = null): Int =
    termine.count { it.rsvpEnabled && !it.isCancelled && it.myResponse == null && it.key != ohne?.key }

/** Kann auf „Start“ gleich zu- oder abgesagt werden? */
internal fun wartetAufAntwort(termin: Occurrence): Boolean = termin.rsvpEnabled && !termin.isCancelled && termin.myResponse == null

/** Was „Start“ oben zeigt: vom Server ([StartSeite]) oder, bei älteren Ständen, aus der Terminliste. */
internal data class StartInhalt(val naechster: Occurrence?, val todo: List<StartTodo>)

/** Inhalt aus der Terminliste, wenn HUBIplan den Abruf „start“ noch nicht kennt. */
internal fun startAusTerminen(termine: List<Occurrence>): StartInhalt {
    val offen = offeneRueckmeldungen(termine)
    val todo = if (offen > 0) {
        listOf(StartTodo("rsvp", trn("{n} Termin wartet auf deine Rückmeldung", "{n} Termine warten auf deine Rückmeldung", offen), offen, "events"))
    } else {
        emptyList()
    }
    return StartInhalt(naechsterTermin(termine), todo)
}

/** Bereich unter „Ich“, in den eine Zeile „Für dich zu tun“ führt; null: Reiter „Termine“ oder unbekannt. */
internal fun startBereich(target: String): IchBereich? = when (target) {
    "surveys" -> IchBereich.UMFRAGEN
    "orders" -> IchBereich.BESTELLUNGEN
    "fee_proofs" -> IchBereich.NACHWEISE
    "expenses" -> IchBereich.AUSLAGEN
    "training" -> IchBereich.TRAINING
    "pinboard" -> IchBereich.PINNWAND
    else -> null
}

/** Symbol einer Zeile „Für dich zu tun“. */
internal fun startSymbol(target: String): Strich = when (target) {
    "events" -> Strich.ZU_ERLEDIGEN
    "surveys" -> Strich.UMFRAGE
    "orders" -> Strich.BESTELLUNG
    "fee_proofs" -> Strich.NACHWEIS
    "expenses" -> Strich.GELD
    "training" -> Strich.TRAINING
    "pinboard" -> Strich.PINNWAND
    else -> Strich.ZU_ERLEDIGEN
}

/** Zeilen, die diese App-Fassung und dieser Zugang öffnen können. */
internal fun startZeilen(todo: List<StartTodo>, bereiche: List<IchBereich>): List<StartTodo> =
    todo.filter { it.count > 0 && (it.target == "events" || startBereich(it.target) in bereiche) }

/**
 * Reiter „Start“ (Gestaltung „Frisch mobil“): der nächste Termin mit Zusage und Absage, was
 * gerade auf dich wartet, die Kacheln der Startseite und kurze Wege zu den Bereichen, die nicht
 * in der Leiste stehen.
 *
 * [weitere]: Reiter, die auf diesem Gerät nicht in der Leiste stehen.
 */
@Composable
internal fun StartPane(
    access: PortalAccess,
    main: Screen.Main,
    vm: AppViewModel,
    weitere: List<Tab>,
    onTab: (Tab) -> Unit,
    onTermin: (String) -> Unit,
    onBereich: (IchBereich) -> Unit,
) {
    val start = remember(access.id) { Loadable<StartInhalt>() }
    LaunchedEffect(access.id, start.tick) {
        val api = vm.api() ?: return@LaunchedEffect
        start.load(vm::deviceRevoked) {
            try {
                api.start(access.id).let { StartInhalt(it.nextEvent, it.todo) }
            } catch (e: ApiException) {
                // Älterer HUBIplan-Stand ohne „start“: aus der Terminliste bauen
                if (e.status != 404) throw e
                val heute = LocalDate.now()
                startAusTerminen(api.events(access.id, heute.toString(), heute.plusDays(START_TAGE).toString()))
            }
        }
    }
    val ungelesen = vm.unreadCirculars.collectAsStateWithLifecycle().value.size
    val inhalt = start.value
    val naechster = inhalt?.naechster
    Refreshable(refreshing = start.refreshing, onRefresh = start::refresh) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Abstand.rand),
            verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
        ) {
            Abschnittstitel(tr("Nächster Termin"), aktion = tr("Alle Termine"), onAktion = { onTab(Tab.TERMINE) })
            when {
                inhalt == null && start.problem == null -> Karte { Platzhalter(tr("Wird geladen …"), zeilen = 1) }
                inhalt == null -> start.problem?.let { ProblemNotice(it, onRetry = start::refresh) }
                naechster == null -> Karte { Leerzustand(strichsymbol(Strich.TERMINE), tr("Keine anstehenden Termine.")) }
                else -> NaechsterTermin(access, naechster, vm, onOpen = { onTermin(naechster.key) }, onChanged = start::reload)
            }

            val zeilen = startZeilen(inhalt?.todo.orEmpty(), remember(access) { ichBereiche(access) })
            if (zeilen.isNotEmpty() || ungelesen > 0) {
                Abschnittstitel(tr("Für dich zu tun"))
                Karte {
                    zeilen.forEachIndexed { i, zeile ->
                        if (i > 0) ZeilenTrenner()
                        Zeile(
                            zeile.title,
                            symbol = strichsymbol(startSymbol(zeile.target)),
                            onClick = { startBereich(zeile.target)?.let(onBereich) ?: onTab(Tab.TERMINE) },
                            ende = { Zaehler(zeile.count) },
                        )
                    }
                    if (zeilen.isNotEmpty() && ungelesen > 0) ZeilenTrenner()
                    if (ungelesen > 0) {
                        Zeile(
                            trn("{n} neue Mitteilung", "{n} neue Mitteilungen", ungelesen),
                            symbol = strichsymbol(Strich.MITTEILUNG),
                            onClick = { onTab(Tab.MITTEILUNGEN) },
                            ende = { Zaehler(ungelesen) },
                        )
                    }
                }
            }

            // Kacheln wie auf der Startseite im Browser: mit Anlass von selbst
            TilesOverview(
                server = main.account.server,
                reloadKey = access.id to start.tick,
                load = { vm.api()?.let { api -> still(vm) { api.memberTiles(access.id) } } },
                save = { key, state -> vm.api()?.let { api -> still(vm) { api.setMemberTile(access.id, key, state) } } },
            )

            if (access.hasCard || weitere.isNotEmpty()) {
                Abschnittstitel(tr("Schnell erreichbar"))
                Karte {
                    var erste = true
                    if (access.hasCard) {
                        Zeile(tr("Ausweis zeigen"), symbol = strichsymbol(Strich.AUSWEIS), onClick = { vm.openCard() })
                        erste = false
                    }
                    weitere.forEach { reiter ->
                        if (!erste) ZeilenTrenner()
                        erste = false
                        Zeile(reiter.label, symbol = strichsymbol(reiter.symbol), onClick = { onTab(reiter) })
                    }
                }
            }
        }
    }
}

private suspend fun <T> still(vm: AppViewModel, block: suspend () -> T): T? = try {
    block()
} catch (e: ApiException) {
    if (e.status == 401) vm.deviceRevoked(e)
    null
}

/** Karte des nächsten Termins: Datum, Titel, Ort und gleich die Rückmeldung. */
@Composable
private fun NaechsterTermin(access: PortalAccess, termin: Occurrence, vm: AppViewModel, onOpen: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val meldungen = LocalActionMessages.current
    var busy by remember(termin.key) { mutableStateOf(false) }
    fun antworten(status: String) {
        if (busy) return
        val api = vm.api() ?: return
        busy = true
        scope.launch {
            try {
                api.respond(access.id, termin.eventId, status)
                meldungen?.show(if (status == "yes") tr("Deine Zusage ist gespeichert.") else tr("Deine Absage ist gespeichert."))
                onChanged()
                vm.syncSoon()
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else meldungen?.show(e.problem().text, error = true)
            } finally {
                busy = false
            }
        }
    }
    Karte {
        Row(
            Modifier.fillMaxWidth().padding(Abstand.rand),
            horizontalArrangement = Arrangement.spacedBy(Abstand.mittel),
            verticalAlignment = Alignment.Top,
        ) {
            DatumKaestchen(termin.startsAt)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Abstand.zeile)) {
                Text(termin.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.asHeading())
                Text(formatWhen(termin.startsAt, termin.endsAt, termin.allDay), style = MaterialTheme.typography.bodyMedium)
                val ort = listOf(termin.location, termin.owner).filter { it.isNotBlank() }.joinToString(" · ")
                if (ort.isNotBlank()) {
                    Text(ort, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (termin.rsvpEnabled && !wartetAufAntwort(termin)) {
                    Row(Modifier.padding(top = Abstand.eng)) { RsvpStatus(termin.myResponse) }
                }
            }
        }
        if (wartetAufAntwort(termin)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Abstand.rand),
                horizontalArrangement = Arrangement.spacedBy(Abstand.klein),
            ) {
                Button(onClick = { antworten("yes") }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tr("Zusage")) }
                OutlinedButton(onClick = { antworten("no") }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tr("Absage")) }
            }
        }
        TextButton(onClick = onOpen, modifier = Modifier.padding(horizontal = Abstand.eng)) { Text(tr("Termin ansehen")) }
    }
}
