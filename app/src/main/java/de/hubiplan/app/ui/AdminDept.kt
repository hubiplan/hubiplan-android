package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.hubiplan.app.data.AdminClient
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.DeptMemberDetail
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import de.hubiplan.app.tr

// Verwaltung in der App, Schritt B4: Mitglied einer Untereinheit (Zugführung)

const val DEPT_MEMBERS_EDIT = "dept.members.edit"

private val CONTACT_KEYS = listOf("phone", "email", "street", "postal_code", "city")

/** Kontaktdaten der Untereinheit-Ansicht als Feldwerte (für das Bearbeiten). */
fun deptContactValues(d: DeptMemberDetail): Map<String, String> {
    val c = d.contact
    return mapOf(
        "phone" to (c?.phone ?: ""),
        "email" to (c?.email ?: ""),
        "street" to (c?.street ?: ""),
        "postal_code" to (c?.postalCode ?: ""),
        "city" to (c?.city ?: ""),
    )
}

@Composable
fun DeptMemberScreen(
    client: AdminClient,
    permissions: List<String>,
    id: Int,
    errors: AdminErrors,
    onBack: () -> Unit,
    onChanged: OnChanged,
    reload: Int,
) {
    val scope = rememberCoroutineScope()
    var detail by remember(id) { mutableStateOf<DeptMemberDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var attempt by remember(id) { androidx.compose.runtime.mutableIntStateOf(0) }
    var loading by remember(id) { mutableStateOf(true) }
    LaunchedEffect(id, reload, attempt) {
        loading = true
        error = null
        try {
            detail = client.deptMember(id)
            error = null
        } catch (e: ApiException) {
            error = errors.message(e)
        } finally { loading = false }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
        BackHead(onBack = onBack)
        if (detail == null) Text(tr("Mitglied"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
        if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
        if (error != null) TextButton(onClick = { attempt++ }) { Text(tr("Erneut laden")) }
        val d = detail ?: return@Column
        Text(d.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
        Text(listOfNotNull(d.memberType, d.clubNumber.ifBlank { null }?.let { "Nr. $it" }).joinToString(" · "))
        if (d.departments.isNotEmpty()) Text(d.departments.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        d.birthDate?.let { Text(tr("geboren {date}", "date" to formatPlainDate(it)), style = MaterialTheme.typography.bodySmall) }
        d.entryDate?.let { Text(tr("eingetreten {date}", "date" to formatPlainDate(it)), style = MaterialTheme.typography.bodySmall) }
        d.contact?.let { ContactRows(it) }
        if (canEditDeptMembers(permissions)) OutlinedButton(onClick = { editing = true }) { Text(tr("Kontaktdaten bearbeiten")) }
        Text(tr("Mitgliedsart, Austritt und Beiträge regelt der Verein."), style = MaterialTheme.typography.bodySmall)
    }
    val d = detail
    if (editing && d != null) {
        // Offen bis zum Erfolg; bei Fehler bleiben die Eingaben (UX-Audit 31.10. A03)
        ContactDialog(deptContactValues(d), busy, saveError, onDismiss = { editing = false; saveError = null }) { changes ->
            if (changes.isEmpty()) {
                editing = false
                return@ContactDialog
            }
            scope.launch {
                busy = true
                saveError = null
                try {
                    val r = client.deptUpdate(id, changes.mapValues { JsonPrimitive(it.value) })
                    detail = r.value
                    editing = false
                    onChanged(tr("Kontaktdaten von {name} gespeichert", "name" to r.value.name), r.changeSet)
                } catch (e: ApiException) {
                    saveError = errors.message(e)
                }
                busy = false
            }
        }
    }
}

@Composable
private fun ContactDialog(
    before: Map<String, String>,
    saving: Boolean,
    saveError: String?,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    var values by remember { mutableStateOf(before) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Kontaktdaten")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                CONTACT_KEYS.forEach { k ->
                    OutlinedTextField(
                        value = values[k] ?: "",
                        onValueChange = { values = values + (k to it) },
                        label = { Text(FIELD_LABELS[k] ?: k) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (k) {
                                "email" -> KeyboardType.Email
                                "phone" -> KeyboardType.Phone
                                "postal_code" -> KeyboardType.Number
                                else -> KeyboardType.Text
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { Button(enabled = !saving, onClick = { onSave(masterDataChanges(before, values) ?: emptyMap()) }) { Text(tr("Speichern")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
