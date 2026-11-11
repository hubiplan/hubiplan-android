package de.hubiplan.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import de.hubiplan.app.data.AdminClient
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.PartnerContact
import de.hubiplan.app.data.PartnerItem
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import kotlinx.coroutines.delay

/** Bezeichnung einer Rolle wie im Browser (hubiplan frontend pages/partners/access.ts). */
fun partnerRoleLabel(role: String): String = when (role) {
    "supplier" -> tr("Lieferant")
    "service" -> tr("Dienstleister")
    "customer" -> tr("Kunde/Auftraggeber")
    "sponsor" -> tr("Sponsor")
    "donor" -> tr("Spender")
    "venue" -> tr("Vermieter/Veranstaltungsort")
    "artist" -> tr("Künstler/Musiker")
    "authority" -> tr("Behörde")
    "press" -> tr("Presse")
    "insurance" -> tr("Versicherung")
    "bank" -> tr("Bank")
    else -> role
}

/**
 * Geschäftspartner nachschlagen (hubiplan konzepte/geschaeftspartner.md, P5): Suche, Liste und
 * Akte mit Anrufen und E-Mail. Gepflegt wird im Browser.
 */
@Composable
fun PartnersSection(client: AdminClient, errors: AdminErrors, reload: Int) {
    var search by remember(client.unitId) { mutableStateOf("") }
    var attempt by remember(client.unitId) { mutableIntStateOf(0) }
    var rows by remember(client.unitId, search, reload, attempt) { mutableStateOf<List<PartnerItem>?>(null) }
    var error by remember(client.unitId, search, reload, attempt) { mutableStateOf<String?>(null) }
    var open by remember(client.unitId) { mutableStateOf<PartnerItem?>(null) }
    LaunchedEffect(client, search, reload, attempt) {
        delay(300) // erst suchen, wenn die Eingabe ruht
        try {
            rows = client.partners(search)
        } catch (e: ApiException) {
            error = errors.message(e)
        }
    }
    val current = open
    if (current != null) {
        PartnerScreen(current) { open = null }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(tr("Name, Ort oder Ansprechpartner")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                if (rows == null && error != null) {
                    OutlinedButton(onClick = { attempt++ }) { Text(tr("Erneut laden")) }
                }
                val list = rows
                if (list == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (list != null) {
                    Text(
                        if (list.isEmpty()) tr("Keine Geschäftspartner gefunden.") else trn("{n} Geschäftspartner", "{n} Geschäftspartner", list.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(rows.orEmpty(), key = { "partner-${it.id}" }) { p ->
            val primary = p.contacts.firstOrNull()
            ListItem(
                headlineContent = { Text(p.name) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            p.roles.joinToString(", ") { partnerRoleLabel(it) }.ifBlank { null },
                            p.details.city.ifBlank { null },
                            primary?.fullName?.ifBlank { null },
                        ).joinToString(" · "),
                    )
                },
                modifier = Modifier.openRow { open = p },
            )
            HorizontalDivider()
        }
        item {
            Text(
                tr("Geschäftspartner anlegen und ändern geht im Browser."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(Abstand.rand),
            )
        }
    }
}

@Composable
private fun PartnerScreen(p: PartnerItem, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHead(p.name, onBack)
            Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                val line = listOfNotNull(
                    p.number.ifBlank { null },
                    p.roles.joinToString(", ") { partnerRoleLabel(it) }.ifBlank { null },
                ).joinToString(" · ")
                if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            ContactRows(p.contact)
        }
        if (p.contacts.isNotEmpty()) {
            item {
                Text(
                    tr("Ansprechpartner"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Abstand.rand, vertical = Abstand.klein).asHeading(),
                )
            }
            items(p.contacts, key = { "kontakt-${it.id}" }) { c ->
                ContactPersonRows(c)
                HorizontalDivider()
            }
        }
        if (p.notes.isNotBlank()) {
            item {
                Text(
                    p.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Abstand.rand),
                )
            }
        }
    }
}

@Composable
private fun ContactPersonRows(c: PartnerContact) {
    val context = LocalContext.current
    fun start(intent: Intent) = runCatching { context.startActivity(intent) }
    ListItem(
        headlineContent = { Text(c.fullName.ifBlank { c.lastName }) },
        supportingContent = {
            val info = listOfNotNull(c.function.ifBlank { null }, tr("Hauptansprechpartner").takeIf { c.isPrimary })
            if (info.isNotEmpty()) Text(info.joinToString(" · "))
        },
    )
    if (c.phone.isNotBlank()) {
        ListItem(
            leadingContent = { Icon(Icons.Filled.Call, contentDescription = null) },
            headlineContent = { Text(c.phone) },
            overlineContent = { Text(tr("Telefon")) },
            modifier = Modifier.openRow(tr("Anrufen")) { start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}"))) },
        )
    }
    if (c.email.isNotBlank()) {
        ListItem(
            leadingContent = { Icon(Icons.Filled.Email, contentDescription = null) },
            headlineContent = { Text(c.email) },
            overlineContent = { Text(tr("E-Mail")) },
            modifier = Modifier.openRow(tr("E-Mail schreiben")) { start(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${c.email}"))) },
        )
    }
}
