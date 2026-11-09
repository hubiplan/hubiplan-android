package de.hubiplan.app.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalAutofillManager
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.hubiplan.app.tr

@Composable
fun ConnectScreen(state: ConnectState, vm: AppViewModel) {
    // Lizenzen auch ohne Anmeldung (Lizenzaudit 31.10.2026, L01)
    var licenses by rememberSaveable { mutableStateOf(false) }
    // Nach einem Fehler mit bekannter Adresse gleich das Anmeldeformular zeigen
    var withPassword by rememberSaveable { mutableStateOf(state.server.isNotBlank()) }
    if (licenses) {
        LicensesPage(onClose = { licenses = false })
        return
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(vm::scanned)
    }
    state.pendingPairing?.let { link ->
        AlertDialog(
            onDismissRequest = vm::cancelPairing,
            title = { Text(tr("Mit dieser Adresse verbinden?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    Text(link.server.removePrefix("https://"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Verbinde die App nur, wenn das die Adresse deines Vereins ist und du den QR-Code oder Link selbst gerade im Browser erzeugt hast."),
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
                    .padding(Abstand.gross),
                verticalArrangement = Arrangement.spacedBy(Abstand.rand),
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
                // Beide Wege gleichrangig; auf nur einem Telefon führt der Knopf im Browser
                // „In der HUBIplan-App öffnen“ direkt hierher (UX-Audit 08.11. X-04)
                Text(tr("Wie möchtest du dich verbinden?"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
                ConnectChoice(
                    title = tr("QR-Code von einem anderen Bildschirm scannen"),
                    hint = tr("Im Browser, z. B. am Computer, unter „Mein Konto › App & andere Instanzen“ auf „App verbinden“ tippen."),
                    selected = false,
                    enabled = !state.busy,
                ) {
                    vm.clearError()
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt(tr("QR-Code aus „Mein Konto“ scannen"))
                            .setBeepEnabled(false)
                            .setOrientationLocked(false),
                    )
                }
                ConnectChoice(
                    title = tr("Mit E-Mail und Passwort anmelden"),
                    hint = tr("Mit der Adresse deines Vereins und deinen Zugangsdaten aus HUBIplan."),
                    selected = withPassword,
                    enabled = !state.busy,
                ) { withPassword = !withPassword }
                if (withPassword) PasswordLogin(state, vm::login)
                Text(
                    tr("Nur ein Telefon? Öffne HUBIplan im Browser des Telefons, dann „Mein Konto › App & andere Instanzen“ und tippe auf „In der HUBIplan-App öffnen“."),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.adding) {
                    TextButton(onClick = vm::cancelAdd, enabled = !state.busy) { Text(tr("Abbrechen, zurück zum bisherigen Konto")) }
                }
                TextButton(onClick = { licenses = true }) { Text(tr("Lizenzen")) }
            }
        }
    }
}

/** Ein Weg zum Verbinden: großer Knopf mit Erklärung darunter, beide Wege gleich gewichtet. */
@Composable
private fun ConnectChoice(title: String, hint: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(enabled),
    ) {
        Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Welche Anmeldefelder fehlen (Reihenfolge wie auf dem Bildschirm). */
internal enum class LoginField { SERVER, EMAIL, PASSWORD }

internal fun missingLoginFields(server: String, email: String, password: String): List<LoginField> = buildList {
    if (server.isBlank()) add(LoginField.SERVER)
    if (email.isBlank()) add(LoginField.EMAIL)
    if (password.isEmpty()) add(LoginField.PASSWORD)
}

/**
 * Anmelden und „Passwort vergessen?“ bleiben immer antippbar (außer während einer Anfrage).
 * Fehlt etwas, steht der Hinweis am Feld, das Feld bekommt den Fokus und der Hinweis wird
 * vorgelesen, statt dass der Knopf stumm gesperrt ist (UX-Audit 08.11. A-12).
 */
@Composable
internal fun PasswordLogin(state: ConnectState, onLogin: (String, String, String) -> Unit) {
    var server by rememberSaveable { mutableStateOf(state.server.removePrefix("https://")) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // Felder, zu denen nach einem Tippen ein Hinweis steht
    var missing by rememberSaveable { mutableStateOf(emptyList<LoginField>()) }
    var serverHint by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val autofillManager = LocalAutofillManager.current
    val recoveryUrl = passwordResetUrl(server)
    var browserError by rememberSaveable { mutableStateOf(false) }
    val serverFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    fun focus(field: LoginField) = when (field) {
        LoginField.SERVER -> serverFocus
        LoginField.EMAIL -> emailFocus
        LoginField.PASSWORD -> passwordFocus
    }.requestFocus()
    val serverError = when {
        LoginField.SERVER in missing -> tr("Bitte die Adresse deines Vereins eingeben.")
        serverHint && recoveryUrl == null -> tr("Gib zuerst die gültige HTTPS-Adresse deines Vereins ein, z. B. verein.example.de.")
        else -> null
    }
    OutlinedTextField(
        value = server,
        onValueChange = { server = it; browserError = false; missing = missing - LoginField.SERVER },
        label = { Text(tr("Adresse von HUBIplan")) },
        placeholder = { Text("verein.example.de") },
        singleLine = true,
        isError = serverError != null,
        supportingText = serverError?.let { { Text(it, modifier = Modifier.announce(urgent = true)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().focusRequester(serverFocus),
    )
    val emailError = LoginField.EMAIL in missing
    OutlinedTextField(
        value = email,
        onValueChange = { email = it; missing = missing - LoginField.EMAIL },
        label = { Text(tr("E-Mail")) },
        singleLine = true,
        isError = emailError,
        supportingText = if (emailError) {
            { Text(tr("Bitte deine E-Mail-Adresse eingeben."), modifier = Modifier.announce(urgent = true)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth().focusRequester(emailFocus).autofill(ContentType.Username),
    )
    val passwordError = LoginField.PASSWORD in missing
    OutlinedTextField(
        value = password,
        onValueChange = { password = it; missing = missing - LoginField.PASSWORD },
        label = { Text(tr("Passwort")) },
        singleLine = true,
        isError = passwordError,
        supportingText = if (passwordError) {
            { Text(tr("Bitte dein Passwort eingeben."), modifier = Modifier.announce(urgent = true)) }
        } else {
            null
        },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).autofill(ContentType.Password),
    )
    OutlinedButton(
        onClick = {
            missing = missingLoginFields(server, email, password)
            if (missing.isEmpty()) {
                // Passwortmanager bietet an, die Anmeldung zu speichern (A-05)
                autofillManager?.commit()
                onLogin(server, email, password)
            } else {
                focus(missing.first())
            }
        },
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(tr("Anmelden")) }
    TextButton(
        onClick = {
            browserError = false
            val url = recoveryUrl
            if (url == null) {
                serverHint = true
                serverFocus.requestFocus()
                return@TextButton
            }
            serverHint = false
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
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(tr("Passwort vergessen? Im Browser zurücksetzen")) }
    if (serverError == null) {
        Text(
            if (recoveryUrl == null) tr("Gib zuerst die gültige HTTPS-Adresse deines Vereins ein, z. B. verein.example.de.")
            else tr("Setze dein Passwort auf der Vereinsseite im Browser zurück. Kehre danach zur App zurück und melde dich mit dem neuen Passwort an."),
            style = MaterialTheme.typography.bodySmall,
        )
    }
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
