package de.hubiplan.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.hubiplan.app.data.AdminClient
import de.hubiplan.app.data.AdminCompetition
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.StandAccess
import de.hubiplan.app.data.StandStore
import de.hubiplan.app.data.openCompetitions
import de.hubiplan.app.data.standUrl
import de.hubiplan.app.tr
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Bereich „Wettbewerbe“ (hubiplan konzepte/schiessen-android.md): eigene, nicht abgeschlossene
 * Wettbewerbe; „Standmodus öffnen“ legt einen Standzugang bis Ende des Wettbewerbstags an (oder
 * nimmt den schon angelegten) und öffnet den Standmodus in der App.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompetitionsSection(
    client: AdminClient,
    server: String,
    deviceName: String,
    errors: AdminErrors,
    reload: Int,
    onScan: () -> Unit,
    onOpenStand: (StandSession) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember(client) { mutableStateOf<List<AdminCompetition>?>(null) }
    var error by remember(client) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<Int?>(null) }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }
    var accesses by remember(client) { mutableStateOf<Map<Int, List<StandAccess>>>(emptyMap()) }
    LaunchedEffect(client, reload, attempt) {
        error = null
        try {
            val open = openCompetitions(client.competitions(), LocalDate.now().toString())
            list = open
            accesses = open.associate { c -> c.id to runCatching { client.standAccesses(c.id).filter { it.active } }.getOrDefault(emptyList()) }
        } catch (e: ApiException) {
            error = errors.message(e)
        }
    }

    /** Gültigen Standzugang dieses Telefons nehmen oder neu anlegen. */
    suspend fun accessFor(c: AdminCompetition, label: String, reuse: Boolean): StandAccess {
        val now = OffsetDateTime.now()
        if (reuse) {
            StandStore.get(context, client.accountKey, client.unitId, c.id, now)?.let { stored ->
                val stillActive = accesses[c.id]?.any { it.id == stored.id } ?: true
                if (stillActive && StandStore.valid(stored, now)) return stored
                StandStore.remove(context, client.accountKey, client.unitId, c.id)
            }
        }
        val created = client.createStandAccess(c.id, label)
        if (reuse) StandStore.put(context, client.accountKey, client.unitId, c.id, created)
        accesses = accesses + (c.id to (accesses[c.id].orEmpty() + created.copy(token = null)))
        return created
    }

    fun run(c: AdminCompetition, action: suspend () -> Unit) {
        if (busy != null) return
        busy = c.id
        error = null
        scope.launch {
            try {
                action()
            } catch (e: ApiException) {
                error = errors.message(e)
            } finally {
                busy = null
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Text(
                    tr("Im Standmodus erfasst du Punkte, tippst Treffer auf der Scheibe an oder lässt ein Foto auswerten, auch ohne Empfang. Neue Wettbewerbe legst du im Browser an."),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Stand-QR scannen")) }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true))
                    TextButton(onClick = { attempt++ }) { Text(tr("Erneut laden")) }
                }
            }
        }
        val rows = list
        if (rows == null && error == null) item { Text(tr("Wird geladen …"), modifier = Modifier.padding(Abstand.rand)) }
        if (rows != null && rows.isEmpty()) {
            item { Text(tr("Keine offenen Wettbewerbe. Abgeschlossene Wettbewerbe stehen im Browser."), modifier = Modifier.padding(Abstand.rand)) }
        }
        items(rows.orEmpty(), key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth().padding(horizontal = Abstand.rand, vertical = Abstand.klein)) {
                Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
                    Text(competitionLine(c), style = MaterialTheme.typography.bodySmall)
                    val active = accesses[c.id].orEmpty()
                    if (active.isNotEmpty()) {
                        Text(
                            tr("Aktive Standzugänge: {n}", "n" to active.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        Button(onClick = {
                            run(c) {
                                val a = accessFor(c, tr("App ({name})", "name" to deviceName), reuse = true)
                                onOpenStand(StandSession(standUrl(server, a.token ?: return@run), server, c.title))
                            }
                        }, enabled = busy == null) { Text(tr("Standmodus öffnen")) }
                        OutlinedButton(onClick = {
                            run(c) {
                                val a = accessFor(c, tr("Weiteres Gerät"), reuse = false)
                                qr = c.title to standUrl(server, a.token ?: return@run)
                            }
                        }, enabled = busy == null) { Text(tr("QR-Code für weiteres Gerät")) }
                        if (active.isNotEmpty()) {
                            TextButton(onClick = {
                                run(c) {
                                    active.forEach { client.endStandAccess(c.id, it.id) }
                                    StandStore.remove(context, client.accountKey, client.unitId, c.id)
                                    accesses = accesses + (c.id to emptyList())
                                }
                            }, enabled = busy == null) { Text(tr("Standzugang beenden")) }
                        }
                    }
                }
            }
        }
    }

    qr?.let { (title, link) -> StandQrDialog(title, link) { qr = null } }
}

/** „So, 14. Juni 2027 · Schützenhaus · 23 Starter“ */
internal fun competitionLine(c: AdminCompetition): String = listOfNotNull(
    formatPlainDate(c.date).takeIf { it.isNotBlank() },
    c.location.takeIf { it.isNotBlank() },
    if (c.isBird) tr("Vogelschießen") else null,
    c.starterCount?.takeIf { it > 0 }?.let { trnStarter(it) },
).joinToString(" · ")

private fun trnStarter(n: Int): String = de.hubiplan.app.trn("{n} Starter", "{n} Starter", n)

@Composable
private fun StandQrDialog(title: String, link: String, onDismiss: () -> Unit) {
    val bitmap: Bitmap? = remember(link) { runCatching { qrBitmap(link, 720) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Text(tr("Mit dem anderen Gerät scannen (HUBIplan-App oder Kamera). Der Zugang gilt bis zum Ende des Wettbewerbstags und kann nur erfassen. Der Code erscheint nur jetzt."))
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = tr("QR-Code für den Standzugang"),
                        modifier = Modifier.size(260.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Fertig")) } },
    )
}
