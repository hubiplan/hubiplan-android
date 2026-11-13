package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.CompanyEntry
import de.hubiplan.app.data.CompanyMember
import de.hubiplan.app.data.CompanyOverview
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.tr
import kotlinx.coroutines.launch

/** Zeitraum und Beitrag eines Mitarbeiters in einer Zeile (wie im Browser). */
fun companyMemberLine(m: CompanyMember): String = listOfNotNull(
    m.since?.let { tr("seit {date}", "date" to formatPlainDate(it)) },
    m.until?.let { tr("bis {date}", "date" to formatPlainDate(it)) },
    tr("über die Firma").takeIf { m.paysVia },
).joinToString(" · ")

/**
 * Mein Bereich › Mitarbeiter: Für wen die Firma zahlt, und neue Mitarbeiter melden (sie bekommen
 * den Link zum Online-Antrag). Zahlung beenden geht im Browser.
 */
@Composable
internal fun CompanySection(access: PortalAccess, vm: AppViewModel) {
    var data by remember(access.id) { mutableStateOf<CompanyOverview?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        error = null
        try {
            data = api.myCompanies(access.id)
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        val d = data
        if (d == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        if (d.companies.isEmpty()) Text(tr("Die Firma zahlt für niemanden."))
        d.companies.forEach { c -> CompanyCard(access, vm, c, d.canReport) }
        Text(tr("Die Zahlung für jemanden beenden geht im Browser."), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CompanyCard(access: PortalAccess, vm: AppViewModel, c: CompanyEntry, canReport: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            if (c.members.isEmpty()) Text(tr("Noch niemand."), style = MaterialTheme.typography.bodySmall)
            c.members.forEach { m ->
                Column {
                    Text(m.name, style = MaterialTheme.typography.bodyLarge)
                    companyMemberLine(m).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (canReport) ReportForm(access, vm, c)
        }
    }
}

@Composable
private fun ReportForm(access: PortalAccess, vm: AppViewModel, c: CompanyEntry) {
    val scope = rememberCoroutineScope()
    var first by remember(c.id) { mutableStateOf("") }
    var last by remember(c.id) { mutableStateOf("") }
    var email by remember(c.id) { mutableStateOf("") }
    var busy by remember(c.id) { mutableStateOf(false) }
    var message by remember(c.id) { mutableStateOf<String?>(null) }
    var problem by remember(c.id) { mutableStateOf<String?>(null) }
    Text(tr("Neuen Mitarbeiter melden"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
    Text(tr("Die Person bekommt eine Mail mit dem Link zum Online-Antrag."), style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(first, { first = it }, label = { Text(tr("Vorname")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(last, { last = it }, label = { Text(tr("Nachname")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        email, { email = it },
        label = { Text(tr("E-Mail")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
    message?.let { Text(it, modifier = Modifier.announce()) }
    Button(
        enabled = !busy && first.isNotBlank() && last.isNotBlank() && email.contains('@'),
        onClick = {
            val api = vm.api() ?: return@Button
            busy = true
            problem = null
            message = null
            scope.launch {
                try {
                    val to = email.trim()
                    api.reportEmployee(access.id, c.id, first.trim(), last.trim(), to)
                    message = tr("Mail an {email} gesendet.", "email" to to)
                    first = ""
                    last = ""
                    email = ""
                } catch (e: ApiException) {
                    if (e.status == 401) vm.deviceRevoked(e) else problem = e.userText()
                } finally {
                    busy = false
                }
            }
        },
    ) { Text(if (busy) tr("Wird gesendet …") else tr("Melden")) }
}
