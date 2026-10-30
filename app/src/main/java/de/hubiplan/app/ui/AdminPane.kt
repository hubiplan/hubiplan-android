package de.hubiplan.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hubiplan.app.data.AdminTask
import de.hubiplan.app.data.AdminUnit
import de.hubiplan.app.data.ApiException

/**
 * Reiter „Verwaltung“ (Schritt A8): nur für Konten mit Verwaltungsrechten. Erst nach dem
 * Freischalten (Passwort + 2FA) und jedes Mal nach Fingerabdruck/PIN; keine Bildschirmfotos.
 */
@Composable
fun AdminPane(main: Screen.Main, vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.admin.collectAsStateWithLifecycle()
    // Im Verwaltungsbereich keine Bildschirmfotos und keine Vorschau im App-Wechsler
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    // Beim Öffnen und nach Rückkehr in die App: nach 5 Minuten Pause neu entsperren
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.checkAdminIdle()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { vm.checkAdminIdle() }
    val overview = state.overview ?: return Loading()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            !overview.twoFactor -> Notice(
                "Für die Verwaltung in der App ist die Zwei-Faktor-Anmeldung nötig. Bitte im Browser unter " +
                    "„Mein Konto“ einrichten, danach hier freischalten.",
                "Mein Konto öffnen",
            ) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${main.account.server}/mein-konto"))) }
            !DeviceUnlock.available(context) -> Notice(
                "Für die Verwaltung braucht das Telefon eine Bildschirmsperre (PIN, Muster oder Fingerabdruck).",
                null,
            ) {}
            !state.sealed -> UnlockForm(state, vm)
            state.token == null -> Column(
                Modifier.widthIn(max = 520.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Verwaltung", style = MaterialTheme.typography.headlineSmall)
                Text("Zum Öffnen mit Fingerabdruck, Gesicht oder Geräte-PIN entsperren.")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    DeviceUnlock.ask(context, "Verwaltung öffnen", onSuccess = vm::openAdmin, onError = {})
                }) { Text("Entsperren") }
                LaunchedEffect(Unit) {
                    DeviceUnlock.ask(context, "Verwaltung öffnen", onSuccess = vm::openAdmin, onError = {})
                }
            }
            else -> AdminHome(overview.units, overview.until, main, vm)
        }
    }
}

@Composable
private fun Notice(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.widthIn(max = 520.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Verwaltung", style = MaterialTheme.typography.headlineSmall)
        Text(text)
        action?.let { OutlinedButton(onClick = onAction) { Text(it) } }
    }
}

@Composable
private fun UnlockForm(state: AdminState, vm: AppViewModel) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Verwaltung freischalten", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Für Mitglieder, Termine und Kasse in der App bitte mit Passwort und Zwei-Faktor-Code bestätigen. " +
                "Die Freischaltung gilt 30 Tage auf diesem Telefon; geöffnet wird jedes Mal mit Fingerabdruck oder PIN.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(8) },
            label = { Text("Code aus der Authenticator-App") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                error = null
                // Erst das Telefon entsperren: Der Tresor für den Schlüssel braucht es
                DeviceUnlock.ask(
                    context,
                    "Verwaltung freischalten",
                    onSuccess = { vm.unlockAdmin(password, code) },
                    onError = { error = it },
                )
            },
            enabled = !state.busy && password.isNotEmpty() && code.length >= 6,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Freischalten") }
    }
}

@Composable
private fun AdminHome(units: List<AdminUnit>, until: String?, main: Screen.Main, vm: AppViewModel) {
    val context = LocalContext.current
    var unitId by rememberSaveable { mutableStateOf(units.first().id) }
    val unit = units.firstOrNull { it.id == unitId } ?: units.first()
    var tasks by remember(unit.id) { mutableStateOf<List<AdminTask>?>(null) }
    var error by remember(unit.id) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(unit.id, reload) {
        val api = vm.adminApi() ?: return@LaunchedEffect
        try {
            tasks = api.adminTasks(unit.id).items
            error = null
        } catch (e: ApiException) {
            if (e.status == 401) vm.adminRejected() else error = e.message
        }
    }
    Column(Modifier.fillMaxSize()) {
        UnitPicker(units, unit) { unitId = it.id }
        HorizontalDivider()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        val list = tasks
        when {
            list == null && error == null -> Loading()
            list.isNullOrEmpty() -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keine offenen Aufgaben.")
                Footer(until, vm) { reload++ }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Offene Aufgaben",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                    )
                }
                items(list) { task ->
                    ListItem(
                        overlineContent = { Text(task.kindLabel) },
                        headlineContent = { Text(task.title) },
                        supportingContent = { Text(task.detail) },
                        modifier = Modifier.clickable {
                            // Bis B2/B3 wird im Browser entschieden
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(main.account.server + task.link)))
                        },
                    )
                    HorizontalDivider()
                }
                item { Box(Modifier.padding(16.dp)) { Footer(until, vm) { reload++ } } }
            }
        }
    }
}

@Composable
private fun Footer(until: String?, vm: AppViewModel, onReload: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onReload) { Text("Aktualisieren") }
        until?.let { Text("Freigeschaltet bis ${formatDate(it)}", style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = vm::lockAdmin) { Text("Verwaltung auf diesem Telefon sperren") }
    }
}

@Composable
private fun UnitPicker(units: List<AdminUnit>, current: AdminUnit, onPick: (AdminUnit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 8.dp)) {
        if (units.size == 1) {
            Text(current.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
        } else {
            TextButton(onClick = { open = true }) { Text(current.name + " ▾", style = MaterialTheme.typography.titleMedium) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                units.forEach { u ->
                    DropdownMenuItem(text = { Text(u.name) }, onClick = {
                        open = false
                        onPick(u)
                    })
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
