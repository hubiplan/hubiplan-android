package de.hubiplan.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.hubiplan.app.data.AdminClient
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.CheckInList
import de.hubiplan.app.data.CheckInResult
import de.hubiplan.app.data.CheckInRow
import de.hubiplan.app.data.PendingCheckIn
import de.hubiplan.app.data.filterCheckIn
import de.hubiplan.app.data.queueCheckIn
import de.hubiplan.app.data.withRow
import de.hubiplan.app.data.withPending
import de.hubiplan.app.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Einlass vor Ort (HUBIplan A6/A7): Karten mit der Kamera scannen, Personen ohne Karte von Hand
// einlassen, offene Beträge an der Abendkasse über die Rechnung bar kassieren. Die Liste lädt sich
// alle 10 Sekunden neu; ohne Netz bleibt sie lesbar und Einlässe von Hand warten auf das Netz.

/** Wie oft die Liste neu geladen wird (wie im Browser). */
const val CHECKIN_REFRESH_MS = 10_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInScreen(
    client: AdminClient,
    eventId: Int,
    errors: AdminErrors,
    onBack: () -> Unit,
    onOpenInvoice: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var list by remember(eventId) { mutableStateOf<CheckInList?>(null) }
    var error by remember(eventId) { mutableStateOf<String?>(null) }
    var last by remember(eventId) { mutableStateOf<CheckInResult?>(null) }
    var pending by remember(eventId) { mutableStateOf<List<PendingCheckIn>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    suspend fun load() {
        // Erst nachreichen, was ohne Netz abgehakt wurde
        for (step in pending) {
            client.checkIn(eventId, participant = step.participant, undo = step.undo)
            pending = pending.drop(1)
        }
        list = client.checkIns(eventId)
        error = null
    }

    LaunchedEffect(eventId) {
        while (true) {
            try {
                load()
            } catch (ex: ApiException) {
                // Ohne Netz bleibt die letzte Liste stehen
                error = if (ex.network != null && list != null) tr("Ohne Netz: Die Liste ist vielleicht nicht aktuell.") else errors.message(ex)
            }
            delay(CHECKIN_REFRESH_MS)
        }
    }

    fun send(row: CheckInRow?, scanned: String?, undo: Boolean = false) {
        scope.launch {
            busy = true
            try {
                val r = client.checkIn(eventId, code = scanned, participant = row?.id, undo = undo)
                last = r
                r.participant?.let { p -> list = list?.withRow(p) }
                error = null
            } catch (ex: ApiException) {
                if (ex.network != null && row != null) {
                    pending = queueCheckIn(pending, PendingCheckIn(row.id, undo))
                    list = list?.withPending(listOf(PendingCheckIn(row.id, undo)), java.time.OffsetDateTime.now().toString())
                } else {
                    error = errors.message(ex)
                }
            }
            busy = false
        }
    }

    var scanProblem by remember { mutableStateOf<String?>(null) }
    ScanProblemDialog(scanProblem) { scanProblem = null }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents
        if (scanned == null) {
            scanProblem = scanWithoutResult(context, tr("Ohne Kamera suchst du die Person in der Liste und lässt sie dort ein."))
        } else {
            send(null, scanned)
        }
    }

    Column(Modifier.fillMaxSize().padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
        BackHead(tr("Einlass"), onBack = onBack)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
        val l = list
        if (l == null) {
            if (error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            return@Column
        }
        Text(l.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        Text(tr("{n} von {count} eingelassen", "n" to l.checkedIn, "count" to l.participants.size))
        if (pending.isNotEmpty()) {
            Text(tr("Warten auf Netz: {n}", "n" to pending.size), color = MaterialTheme.colorScheme.error)
        }
        if (l.canEdit) {
            Button(onClick = {
                scanner.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt(tr("Eintrittskarte scannen"))
                        .setBeepEnabled(true)
                        .setOrientationLocked(false),
                )
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(tr("Mit der Kamera scannen")) }
            // Handscanner tippen den Code wie eine Tastatur ein
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(tr("Code der Eintrittskarte")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (code.isNotBlank()) { send(null, code); code = "" }
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        last?.let { ResultCard(it) }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(tr("Name suchen")) },
            singleLine = true,
            keyboardOptions = SUCHE,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        val shown = filterCheckIn(l.participants, query)
        if (shown.isEmpty()) Text(tr("Keine Treffer."), style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Abstand.klein), modifier = Modifier.fillMaxWidth()) {
            items(shown, key = { it.id }) { row ->
                Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                    Text(row.name + if (row.minor) " · " + tr("minderjährig") else "", style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(row.number, row.priceName.ifBlank { null }, paidLabel(row)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    row.checkedInAt?.let {
                        Text(
                            tr("eingelassen {when}", "when" to formatDayTime(it)) +
                                if (row.checkedInVia.isNotBlank()) " · ${row.checkedInVia}" else "",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    for (b in row.bookings) {
                        Text(
                            b.name + " · " + (b.redeemedAt?.let { tr("eingelöst {when}", "when" to formatDayTime(it)) } ?: tr("noch nicht eingelöst")),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        if (l.canEdit && !row.checkedIn) {
                            Button(onClick = { send(row, null) }, enabled = !busy, modifier = Modifier.semantics {
                                contentDescription = tr("{name} einlassen", "name" to row.name)
                            }) { Text(tr("Einlassen")) }
                        }
                        if (l.canEdit && row.checkedIn) {
                            OutlinedButton(onClick = { send(row, null, undo = true) }, enabled = !busy, modifier = Modifier.semantics {
                                contentDescription = tr("Einlass von {name} zurücknehmen", "name" to row.name)
                            }) { Text(tr("Einlass zurücknehmen")) }
                        }
                        val invoice = row.invoiceId
                        if (l.canCash && row.owes && invoice != null) {
                            OutlinedButton(onClick = { onOpenInvoice(invoice) }) {
                                Text(tr("Bar kassieren") + " · " + formatMoney(row.outstanding))
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun paidLabel(row: CheckInRow): String? = when (row.paidState) {
    "paid" -> tr("bezahlt")
    "open", "partly" -> tr("offen: {amount}", "amount" to formatMoney(row.outstanding))
    "free" -> tr("kostenlos")
    else -> null
}

@Composable
private fun ResultCard(r: CheckInResult) {
    val colors = if (r.good) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth().announce(urgent = !r.good)) {
        Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
            Text(r.message, style = MaterialTheme.typography.titleMedium)
            r.participant?.let { p ->
                Text(p.name)
                Text(listOfNotNull(p.number, p.priceName.ifBlank { null }, paidLabel(p)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
