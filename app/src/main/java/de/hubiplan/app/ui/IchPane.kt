package de.hubiplan.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hubiplan.app.data.Ansicht
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.CardStore
import de.hubiplan.app.data.MandateState
import de.hubiplan.app.data.MyDocument
import de.hubiplan.app.data.MyDocuments
import de.hubiplan.app.data.MyInvoice
import de.hubiplan.app.data.MyInvoices
import de.hubiplan.app.data.MyMeetings
import de.hubiplan.app.data.OwnMotion
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.UpcomingMeeting
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import java.io.File
import kotlinx.coroutines.launch

/**
 * Bereiche unter „Ich“ in der Reihenfolge der Liste (Konzept app-ich, S-06/X-03). Begriffe wie
 * unter „Mein Bereich“ im Browser. „Helferdienste“ führt in den Reiter „Aufgaben“.
 */
internal enum class IchBereich(val label: String) {
    DATEN(tr("Meine Daten")),
    RECHNUNGEN(tr("Rechnungen")),
    VERSAMMLUNGEN(tr("Versammlungen")),
    ERGEBNISSE(tr("Meine Ergebnisse")),
    HELFERDIENSTE(tr("Helferdienste")),
    DOKUMENTE(tr("Dokumente")),
    // Ansprechpartner einer Firma, die für Mitarbeiter zahlt (wie Mein Bereich › Mitarbeiter)
    MITARBEITER(tr("Mitarbeiter")),
}

/** Nur Bereiche, deren Modul an ist (gleiche Regeln wie `portalSections` im Browser). */
internal fun ichBereiche(access: PortalAccess): List<IchBereich> = IchBereich.entries.filter {
    when (it) {
        IchBereich.DATEN -> true
        IchBereich.RECHNUNGEN -> access.hasInvoices
        IchBereich.VERSAMMLUNGEN -> access.hasMeetings
        IchBereich.ERGEBNISSE -> access.hasResults
        IchBereich.HELFERDIENSTE -> access.hasHelpers
        IchBereich.DOKUMENTE -> access.hasDocs
        IchBereich.MITARBEITER -> access.company
    }
}

/** Kurzstand rechts neben „Rechnungen“. */
internal fun invoiceSummary(list: MyInvoices): String = when {
    list.openSum != null -> tr("{x1} offen", "x1" to formatMoney(list.openSum))
    list.invoices.isEmpty() && list.collective.isEmpty() -> tr("Keine Rechnungen")
    else -> tr("Alles bezahlt")
}

/** Kurzstand rechts neben „Versammlungen“: die nächste mit Datum. */
internal fun meetingSummary(list: MyMeetings): String {
    val next = list.upcoming.firstOrNull()
    return when {
        next != null -> listOf(next.title, formatShortDate(next.startsAt)).filter { it.isNotBlank() }.joinToString(" ")
        list.minutes.isNotEmpty() -> trn("1 Protokoll", "{n} Protokolle", list.minutes.size)
        else -> tr("Keine Einladung")
    }
}

/** „06.12.“ bzw. „06/12“ aus einem Zeitpunkt (ohne Jahr, für den Kurzstand). */
internal fun formatShortDate(iso: String?): String = formatPlainDate(
    iso?.let { runCatching { java.time.OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate().toString() }.getOrNull() },
).let { full -> if (full.length == 10) full.substring(0, if (de.hubiplan.app.Texte.sprache == "en") 5 else 6) else full }

/**
 * Reiter „Ich“: Ausweis oben, darunter alles Persönliche wie „Mein Bereich“ im Browser.
 * [onHelpers] öffnet die Helferdienste im Reiter „Aufgaben“.
 */
@Composable
fun IchPane(access: PortalAccess, main: Screen.Main, vm: AppViewModel, onHelpers: () -> Unit) {
    var section by rememberSaveable(access.id) { mutableStateOf<String?>(null) }
    val open = section?.let { name -> IchBereich.entries.firstOrNull { it.name == name } }
    // Verknüpfung „Ausweis zeigen“: zurück zur Liste, Ausweis groß
    val openCard by vm.openCard.collectAsStateWithLifecycle()
    var bigRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(openCard) {
        if (openCard) {
            section = null
            bigRequest++
            vm.cardHandled()
        }
    }
    if (open != null) {
        val leave = LocalFormLeaveGuard.current
        val back: () -> Unit = {
            if (leave == null) {
                section = null
            } else {
                leave.request { section = null }
            }
        }
        androidx.activity.compose.BackHandler { back() }
        Box(Modifier.fillMaxSize()) {
            BackHead(open.label, onBack = back)
            when (open) {
                IchBereich.DATEN -> ProfilePane(access, main, vm)
                IchBereich.RECHNUNGEN -> InvoicesSection(access, vm)
                IchBereich.VERSAMMLUNGEN -> MeetingsSection(access, vm)
                IchBereich.ERGEBNISSE -> ResultsSection(access, vm)
                IchBereich.DOKUMENTE -> DocumentsSection(access, vm)
                IchBereich.MITARBEITER -> CompanySection(access, vm)
                IchBereich.HELFERDIENSTE -> Unit
            }
        }
        return
    }
    IchOverview(access, main, vm, bigRequest, onOpen = { b ->
        if (b == IchBereich.HELFERDIENSTE) onHelpers() else section = b.name
    })
}

@Composable
private fun IchOverview(access: PortalAccess, main: Screen.Main, vm: AppViewModel, bigRequest: Int, onOpen: (IchBereich) -> Unit) {
    val context = LocalContext.current
    val cardStore = remember(main.account.server, main.account.deviceId) {
        CardStore(context, CardStore.key(main.account.server, main.account.deviceId))
    }
    val bereiche = ichBereiche(access)
    var invoices by remember(access.id) { mutableStateOf<MyInvoices?>(null) }
    var meetings by remember(access.id) { mutableStateOf<MyMeetings?>(null) }
    var documents by remember(access.id) { mutableStateOf<MyDocuments?>(null) }
    // Kurzstand nur nebenbei: Fehler zeigen erst die Unterseiten
    LaunchedEffect(access.id) {
        val api = vm.api() ?: return@LaunchedEffect
        if (IchBereich.RECHNUNGEN in bereiche) invoices = quietly(vm) { api.myInvoices(access.id) }
        if (IchBereich.VERSAMMLUNGEN in bereiche) meetings = quietly(vm) { api.myMeetings(access.id) }
        if (IchBereich.DOKUMENTE in bereiche) documents = quietly(vm) { api.myDocuments(access.id) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Abstand.rand),
        verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
    ) {
        if (access.hasCard) {
            MemberCardSection(cardStore, access.id, vm, bigRequest = bigRequest)
        }
        Text(tr("Mein Bereich"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        Column {
            bereiche.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider()
                val (summary, count) = when (b) {
                    IchBereich.DATEN -> (if (access.isContact) tr("Anschrift, Kontakt, Logo") else tr("Anschrift, Telefon, Foto")) to 0
                    IchBereich.RECHNUNGEN -> (invoices?.let(::invoiceSummary) ?: "") to (invoices?.openCount ?: 0)
                    IchBereich.VERSAMMLUNGEN -> (meetings?.let(::meetingSummary) ?: "") to (meetings?.upcoming?.size ?: 0)
                    IchBereich.ERGEBNISSE -> tr("Plätze, Werte und Titel") to 0
                    IchBereich.HELFERDIENSTE -> tr("unter „Aufgaben“") to 0
                    IchBereich.DOKUMENTE -> (documents?.let { trn("1 Dokument", "{n} Dokumente", it.count) } ?: "") to 0
                    IchBereich.MITARBEITER -> tr("Für wen die Firma zahlt") to 0
                }
                IchRow(b.label, summary, count) { onOpen(b) }
            }
        }
    }
}

/** Zeile der Liste: Name links, Kurzstand rechts, Zahl offener Punkte als Plakette. */
@Composable
private fun IchRow(label: String, summary: String, count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = Abstand.klein),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Abstand.klein),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (count > 0) {
            val spoken = trn("1 offen", "{n} offen", count)
            Badge(Modifier.semantics { contentDescription = spoken }) { Text(count.toString()) }
        }
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** Abruf für den Kurzstand: 401 meldet das Gerät ab, sonst bleibt der Kurzstand leer. */
private suspend fun <T> quietly(vm: AppViewModel, block: suspend () -> T): T? = try {
    block()
} catch (e: ApiException) {
    if (e.status == 401) vm.deviceRevoked(e)
    null
}

/** Datei nur zum Ansehen öffnen (PDF-Betrachter o. Ä.); Fehlertext, wenn keine App dafür da ist. */
internal fun openForViewing(context: Context, file: File, mime: String): String? {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.dateien", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "application/octet-stream" })
    intent.clipData = ClipData.newRawUri(file.name, uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        if (mime == "application/pdf") tr("Keine App zum Öffnen von PDF gefunden.") else tr("Keine App zum Öffnen dieser Datei gefunden.")
    }
}

/** Gemeinsamer Ablauf für „PDF ansehen“: laden, kurz ablegen, öffnen; Fehler als Text. */
@Composable
internal fun rememberViewer(vm: AppViewModel): Viewer {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(vm) { Viewer(context, scope, vm) }
}

internal class Viewer(val context: Context, val scope: kotlinx.coroutines.CoroutineScope, val vm: AppViewModel) {
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun open(name: String, mime: String = "application/pdf", load: suspend (Api) -> ByteArray) {
        if (busy) return
        val api = vm.api() ?: return
        busy = true
        message = null
        scope.launch {
            try {
                val bytes = load(api)
                val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Ansicht.store(context, name, bytes) }
                message = openForViewing(context, file, mime)
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else message = e.userText()
            } finally {
                busy = false
            }
        }
    }
}

@Composable
internal fun SectionColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Abstand.rand),
        verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
        content = content,
    )
}

// --- Meine Ergebnisse ---

@Composable
private fun ResultsSection(access: PortalAccess, vm: AppViewModel) {
    SectionColumn {
        MyResultsSection(access, vm)
        // Ranglisten wie auf der Webseite (Abschnitt fehlt, wenn der Verein keine zeigt)
        RankingsSection(access, vm)
    }
}

// --- Rechnungen (I2) ---

@Composable
private fun InvoicesSection(access: PortalAccess, vm: AppViewModel) {
    var list by remember(access.id) { mutableStateOf<MyInvoices?>(null) }
    var mandate by remember(access.id) { mutableStateOf<MandateState?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    val viewer = rememberViewer(vm)
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        error = null
        try {
            list = api.myInvoices(access.id)
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
            return@LaunchedEffect
        }
        mandate = quietly(vm) { api.myMandate(access.id) }
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        val l = list
        if (l == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        viewer.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
        if (l.invoices.isEmpty() && l.collective.isEmpty()) Text(tr("Noch keine Rechnungen."))
        l.sorted.forEach { InvoiceCard(access, it, viewer) }
        if (l.collective.isNotEmpty()) {
            Text(tr("Über eine Sammelrechnung"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            Text(tr("Diese Beiträge zahlt deine Einheit gesammelt; die Rechnung bekommt sie."), style = MaterialTheme.typography.bodySmall)
            l.collective.forEach { c ->
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(c.subject.ifBlank { c.number }, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOf(c.departmentName, formatPlainDate(c.date), formatMoney(c.amount), c.statusLabel).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        mandate?.let { MandateBlock(it) }
        Text(tr("Rechnungen öffnet die App nur online und speichert sie nicht."), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InvoiceCard(access: PortalAccess, invoice: MyInvoice, viewer: Viewer) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Text(invoice.subject.ifBlank { invoice.number }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).asHeading())
                Text(formatMoney(invoice.total), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                listOf(invoice.number, formatPlainDate(invoice.date), invoice.departmentName ?: invoice.issuerName)
                    .filter { !it.isNullOrBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            when {
                invoice.open && invoice.overdue -> Text(
                    tr("Überfällig: {x1} offen seit {x2}", "x1" to formatMoney(invoice.outstanding), "x2" to formatPlainDate(invoice.dueDate)),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                invoice.open -> Text(
                    tr("Offen: {x1}, fällig am {x2}", "x1" to formatMoney(invoice.outstanding), "x2" to formatPlainDate(invoice.dueDate)),
                    fontWeight = FontWeight.Bold,
                )
                else -> Text(invoice.statusLabel, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                OutlinedButton(
                    onClick = { viewer.open("rechnung-${invoice.number}.pdf") { it.invoicePdf(access.id, invoice.id) } },
                    enabled = !viewer.busy,
                ) { Text(tr("PDF ansehen")) }
                if (invoice.hasCancellationPdf) {
                    TextButton(
                        onClick = { viewer.open("storno-${invoice.number}.pdf") { it.invoicePdf(access.id, invoice.id, cancellation = true) } },
                        enabled = !viewer.busy,
                    ) { Text(tr("Stornorechnung")) }
                }
            }
        }
    }
}

@Composable
private fun MandateBlock(state: MandateState) {
    val m = state.mandate
    if (m == null && !state.available) return
    HorizontalDivider()
    Text(tr("Lastschrift"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    if (m == null) {
        Text(tr("Du hast kein Lastschriftmandat erteilt. Das geht im Browser unter „Mein Bereich › Rechnungen“."))
        return
    }
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
        Text(listOf(m.schemeLabel, m.statusLabel).filter { it.isNotBlank() }.joinToString(" · "))
        Text(tr("Mandatsreferenz: {x1}", "x1" to m.reference), style = MaterialTheme.typography.bodyMedium)
        if (m.ibanMasked.isNotBlank()) Text(tr("Konto: {x1}", "x1" to m.ibanMasked), style = MaterialTheme.typography.bodyMedium)
        if (m.holder.isNotBlank()) Text(tr("Kontoinhaber: {x1}", "x1" to m.holder), style = MaterialTheme.typography.bodyMedium)
        m.signedOn?.let { Text(tr("Erteilt am {x1}", "x1" to formatPlainDate(it)), style = MaterialTheme.typography.bodyMedium) }
        if (state.creditorId.isNotBlank()) {
            Text(tr("Gläubiger-ID von {club}: {x1}", "club" to state.clubName, "x1" to state.creditorId), style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(tr("Ändern oder widerrufen geht im Browser unter „Mein Bereich › Rechnungen“."), style = MaterialTheme.typography.bodySmall)
}

// --- Versammlungen (I3) ---

/** Stand eines eigenen Antrags in Worten (wie im Browser). */
internal fun motionStatus(status: String): String = when (status) {
    "submitted" -> tr("eingereicht")
    "accepted" -> tr("in die Tagesordnung aufgenommen")
    "rejected" -> tr("nicht aufgenommen")
    "withdrawn" -> tr("zurückgezogen")
    else -> status
}

@Composable
private fun MeetingsSection(access: PortalAccess, vm: AppViewModel) {
    var list by remember(access.id) { mutableStateOf<MyMeetings?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    var notice by remember(access.id) { mutableStateOf<String?>(null) }
    var motionFor by remember { mutableStateOf<UpcomingMeeting?>(null) }
    var withdraw by remember { mutableStateOf<OwnMotion?>(null) }
    var sending by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    val viewer = rememberViewer(vm)
    val scope = rememberCoroutineScope()
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        error = null
        try {
            list = api.myMeetings(access.id)
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        notice?.let { StatusNotice(it) }
        viewer.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
        val l = list
        if (l == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        if (l.upcoming.isEmpty() && l.minutes.isEmpty()) {
            Text(tr("Zurzeit gibt es keine Einladung und kein Protokoll für dich."))
        }
        l.upcoming.forEach { m ->
            MeetingCard(m, onMotion = { formError = null; motionFor = m }, onWithdraw = { withdraw = it })
        }
        if (l.minutes.isNotEmpty()) {
            Text(tr("Protokolle"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            l.minutes.forEach { m ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                        Text(m.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
                        Text(
                            listOf(formatDate(m.startsAt), m.unit).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = { viewer.open("protokoll-${m.id}.pdf") { it.minutesPdf(access.id, m.id) } },
                            enabled = !viewer.busy,
                        ) { Text(tr("Protokoll (PDF)")) }
                    }
                }
            }
        }
    }

    motionFor?.let { meeting ->
        MotionDialog(
            meeting,
            sending = sending,
            error = formError,
            onCancel = { motionFor = null },
            onSend = { title, text ->
                val api = vm.api() ?: return@MotionDialog
                sending = true
                formError = null
                scope.launch {
                    try {
                        api.submitMotion(access.id, meeting.id, title, text)
                        motionFor = null
                        notice = tr("Antrag eingereicht. Der Vorstand entscheidet, ob er auf die Tagesordnung kommt.")
                        attempt++
                    } catch (e: ApiException) {
                        if (e.status == 401) vm.deviceRevoked(e) else formError = e.generalError ?: e.userText()
                    } finally {
                        sending = false
                    }
                }
            },
        )
    }
    withdraw?.let { motion ->
        AlertDialog(
            onDismissRequest = { withdraw = null },
            title = { Text(tr("Antrag zurückziehen?")) },
            text = { Text(tr("„{title}“ wird zurückgezogen. Das lässt sich nicht rückgängig machen.", "title" to motion.title)) },
            confirmButton = {
                TextButton(onClick = {
                    withdraw = null
                    val api = vm.api() ?: return@TextButton
                    scope.launch {
                        try {
                            api.withdrawMotion(access.id, motion.id)
                            notice = tr("Antrag zurückgezogen.")
                            attempt++
                        } catch (e: ApiException) {
                            if (e.status == 401) vm.deviceRevoked(e) else notice = e.userText()
                        }
                    }
                }) { Text(tr("Zurückziehen")) }
            },
            dismissButton = { TextButton(onClick = { withdraw = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

@Composable
private fun MeetingCard(m: UpcomingMeeting, onMotion: () -> Unit, onWithdraw: (OwnMotion) -> Unit) {
    var agendaOpen by rememberSaveable(m.id) { mutableStateOf(true) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
            Text(m.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            val kind = listOf(m.kindName, m.unit).filter { it.isNotBlank() && it != m.title }.joinToString(" · ")
            if (kind.isNotBlank()) Text(kind, style = MaterialTheme.typography.bodySmall)
            Text(
                listOf(formatDayTime(m.startsAt), m.location).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (m.address.isNotBlank()) Text(m.address, style = MaterialTheme.typography.bodySmall)
            if (m.agenda.isNotEmpty()) {
                TextButton(onClick = { agendaOpen = !agendaOpen }) {
                    Text(
                        if (agendaOpen) tr("Tagesordnung ausblenden")
                        else trn("Tagesordnung (1 Punkt)", "Tagesordnung ({n} Punkte)", m.agenda.size),
                    )
                }
                if (agendaOpen) {
                    Text(tr("Tagesordnung"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                    m.agenda.sortedBy { it.position }.forEach { item ->
                        Column(Modifier.semantics(mergeDescendants = true) {}) {
                            Text("${item.position}. ${item.title}", style = MaterialTheme.typography.bodyMedium)
                            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (m.motions.isNotEmpty()) {
                Text(tr("Deine Anträge"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                m.motions.forEach { motion ->
                    Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                        Text("${motion.title} · ${motionStatus(motion.status)}", style = MaterialTheme.typography.bodyMedium)
                        if (motion.reply.isNotBlank()) Text(tr("Antwort: {x1}", "x1" to motion.reply), style = MaterialTheme.typography.bodySmall)
                        if (motion.withdrawable && m.motionsOpen()) {
                            TextButton(onClick = { onWithdraw(motion) }) { Text(tr("Antrag zurückziehen")) }
                        }
                    }
                }
            }
            when {
                m.motionsOpen() -> {
                    Text(tr("Anträge bis {x1}", "x1" to formatDayTime(m.motionUntil)), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onMotion) { Text(tr("Antrag stellen")) }
                }
                m.motionUntil != null -> Text(tr("Die Frist für Anträge ist vorbei."), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MotionDialog(
    meeting: UpcomingMeeting,
    sending: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onSend: (String, String) -> Unit,
) {
    var title by rememberSaveable(meeting.id) { mutableStateOf("") }
    var text by rememberSaveable(meeting.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!sending) onCancel() },
        title = { Text(tr("Antrag stellen")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Text(meeting.title, style = MaterialTheme.typography.bodyMedium)
                error?.let { Text(tr("Nicht gesendet: {it}", "it" to it), color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(300) },
                    label = { Text(tr("Titel")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(10000) },
                    label = { Text(tr("Begründung")) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSend(title.trim(), text.trim()) }, enabled = !sending && title.isNotBlank() && text.isNotBlank()) {
                Text(if (sending) tr("Wird gesendet …") else tr("Einreichen"))
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !sending) { Text(tr("Abbrechen")) } },
    )
}

// --- Dokumente (nur lesen) ---

@Composable
private fun DocumentsSection(access: PortalAccess, vm: AppViewModel) {
    var list by remember(access.id) { mutableStateOf<MyDocuments?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    val viewer = rememberViewer(vm)
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        error = null
        try {
            list = api.myDocuments(access.id)
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        viewer.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
        val l = list
        if (l == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        val folders = l.folders.filter { f -> f.documents.any { it.current != null } }
        if (folders.isEmpty()) Text(tr("Für dich sind keine Dokumente freigegeben."))
        folders.forEach { folder ->
            Text(
                listOfNotNull(folder.name, folder.department).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.asHeading(),
            )
            folder.documents.filter { it.current != null }.forEach { doc -> DocumentRow(access, doc, viewer) }
        }
    }
}

@Composable
private fun DocumentRow(access: PortalAccess, doc: MyDocument, viewer: Viewer) {
    val version = doc.current ?: return
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(enabled = !viewer.busy, role = Role.Button, onClickLabel = tr("Öffnen")) {
                viewer.open(version.name, version.contentType) { it.documentFile(access.id, version.url) }
            }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = Abstand.eng),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(doc.title.ifBlank { version.name }, style = MaterialTheme.typography.bodyLarge)
            val line = listOf(formatPlainDate(doc.date), doc.note).filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
