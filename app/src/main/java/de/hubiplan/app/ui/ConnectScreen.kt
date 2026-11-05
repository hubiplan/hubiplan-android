package de.hubiplan.app.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalUriHandler
import de.hubiplan.app.data.passwordResetUrl
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.hubiplan.app.tr

@Composable
fun ConnectScreen(state: ConnectState, vm: AppViewModel) {
    // Lizenzen auch ohne Anmeldung (Lizenzaudit 31.10.2026, L01)
    var licenses by rememberSaveable { mutableStateOf(false) }
    if (licenses) LicensesDialog(onClose = { licenses = false })
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(vm::scanned)
    }
    state.pendingPairing?.let { link ->
        AlertDialog(
            onDismissRequest = vm::cancelPairing,
            title = { Text(tr("Mit dieser Adresse verbinden?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(link.server.removePrefix("https://"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Verbinde die App nur, wenn das die Adresse deines Vereins ist und du den QR-Code selbst gerade im Browser erzeugt hast."),
                    )
                }
            },
            confirmButton = { Button(onClick = vm::confirmPairing) { Text(tr("Ja, verbinden")) } },
            dismissButton = { TextButton(onClick = vm::cancelPairing) { Text(tr("Abbrechen")) } },
        )
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("HUBIplan", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                if (state.adding) {
                    Text(
                        tr("Weiteres Konto verbinden, zum Beispiel für einen zweiten Verein mit eigener HUBIplan-Adresse. Für weitere Einheiten derselben Adresse ist kein zweites Konto nötig."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true))
                }
                if (state.pendingSession != null) {
                    SecondFactor(state, vm)
                    return@Column
                }
                Text(
                    tr("Am einfachsten verbindest du die App mit dem QR-Code aus HUBIplan: im Browser unter „Mein Konto › App auf dem Telefon“ auf „App verbinden“ tippen."),
                )
                Button(
                    onClick = {
                        vm.clearError()
                        scanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt(tr("QR-Code aus „Mein Konto“ scannen"))
                                .setBeepEnabled(false)
                                .setOrientationLocked(false),
                        )
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(tr("Mit QR-Code verbinden")) }
                HorizontalDivider()
                PasswordLogin(state, vm::login)
                if (state.adding) {
                    TextButton(onClick = vm::cancelAdd, enabled = !state.busy) { Text(tr("Abbrechen, zurück zum bisherigen Konto")) }
                }
                TextButton(onClick = { licenses = true }) { Text(tr("Lizenzen")) }
            }
        }
    }
}

@Composable
internal fun PasswordLogin(state: ConnectState, onLogin: (String, String, String) -> Unit) {
    var server by rememberSaveable { mutableStateOf(state.server.removePrefix("https://")) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    val recoveryUrl = passwordResetUrl(server)
    var browserError by rememberSaveable { mutableStateOf(false) }
    Text(tr("Oder mit E-Mail und Passwort anmelden"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    OutlinedTextField(
        value = server,
        onValueChange = { server = it; browserError = false },
        label = { Text(tr("Adresse von HUBIplan")) },
        placeholder = { Text("verein.example.de") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text(tr("E-Mail")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(tr("Passwort")) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { onLogin(server, email, password) },
        enabled = !state.busy && server.isNotBlank() && email.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(tr("Anmelden")) }
    TextButton(
        onClick = {
            val url = recoveryUrl ?: return@TextButton
            browserError = false
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
                browserError = true
            } catch (_: ActivityNotFoundException) {
                browserError = true
            } catch (_: SecurityException) {
                browserError = true
            }
        },
        enabled = !state.busy && recoveryUrl != null,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(tr("Passwort vergessen? Im Browser zurücksetzen")) }
    Text(
        if (recoveryUrl == null) tr("Gib zuerst die gültige HTTPS-Adresse deines Vereins ein, z. B. verein.example.de.")
        else tr("Setze dein Passwort auf der Vereinsseite im Browser zurück. Kehre danach zur App zurück und melde dich mit dem neuen Passwort an."),
        style = MaterialTheme.typography.bodySmall,
    )
    if (browserError && recoveryUrl != null) {
        Text(
            tr("Der Browser konnte nicht geöffnet werden. Öffne diese Adresse in einem Browser und kehre danach zur App zurück:"),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.announce(urgent = true),
        )
        SelectionContainer { Text(recoveryUrl) }
    }
}

@Composable
private fun SecondFactor(state: ConnectState, vm: AppViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    Text(tr("Zwei-Faktor-Anmeldung"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    Text(tr("Bitte den Code aus deiner Authenticator-App oder einen Wiederherstellungscode eingeben."))
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text(tr("Code")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { vm.secondFactor(code) },
        enabled = !state.busy && code.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(tr("Bestätigen")) }
    TextButton(onClick = vm::cancelSecondFactor) { Text(tr("Abbrechen")) }
}
