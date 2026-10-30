package de.hubiplan.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.hubiplan.app.BuildConfig

private const val SOURCE_URL = "https://github.com/DeltaRP/hubiplan-android"

@Composable
fun AccountPane(main: Screen.Main, vm: AppViewModel) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    var deleteInfo by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(main.account.display.ifBlank { main.account.email }, style = MaterialTheme.typography.headlineSmall)
        Text(main.account.email)
        Text("Verbunden mit ${main.account.server.removePrefix("https://")}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { open("${main.account.server}/mein-konto") }) { Text("Mein Konto im Browser öffnen") }
        main.access?.let { a ->
            val key = a.imprintKey?.let { "/$it" } ?: ""
            TextButton(onClick = { open("${main.account.server}/datenschutz$key") }) { Text("Datenschutzhinweise") }
            TextButton(onClick = { open("${main.account.server}/impressum$key") }) { Text("Impressum") }
            Text("Angemeldet als Mitglied von ${a.title}", style = MaterialTheme.typography.bodySmall)
        }
        androidx.compose.material3.HorizontalDivider()
        AccountsSection(main, vm)
        androidx.compose.material3.HorizontalDivider()
        SettingsSection(vm)
        androidx.compose.material3.HorizontalDivider()
        OutlinedButton(onClick = vm::logout) { Text("Dieses Konto vom Gerät abmelden") }
        // Google Play verlangt einen Weg zum Löschen von Konto und Daten in der App
        TextButton(onClick = { deleteInfo = true }) { Text("Konto und Daten löschen") }
        Text(
            "HUBIplan-App ${BuildConfig.VERSION_NAME} · freie Software unter der GNU AGPL 3.0 oder neuer",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { open(SOURCE_URL) }) { Text("Quellcode") }
    }

    if (deleteInfo) {
        val key = main.access?.imprintKey?.let { "/$it" } ?: ""
        AlertDialog(
            onDismissRequest = { deleteInfo = false },
            title = { Text("Konto und Daten löschen") },
            text = {
                Text(
                    "Deine Mitgliedsdaten verwaltet dein Verein in seiner HUBIplan-Instanz " +
                        "(${main.account.server.removePrefix("https://")}), nicht die App.\n\n" +
                        "• Abmelden löscht den Geräteschlüssel dieses Telefons beim Verein und alle Daten " +
                        "dieses Kontos in der App, auch die eingetragenen Kalendertermine.\n" +
                        "• Dein Foto kannst du unter Profil selbst löschen.\n" +
                        "• Die Löschung deines Zugangs und deiner Mitgliedsdaten beantragst du beim Verein. " +
                        "Wen du ansprichst, steht in seinen Datenschutzhinweisen.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteInfo = false
                    open("${main.account.server}/datenschutz$key")
                }) { Text("Datenschutzhinweise öffnen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteInfo = false
                    vm.logout()
                }) { Text("Jetzt abmelden") }
            },
        )
    }
}

/** Weitere Konten (andere HUBIplan-Adressen): wechseln oder hinzufügen. */
@Composable
private fun AccountsSection(main: Screen.Main, vm: AppViewModel) {
    val list by vm.accountList.collectAsStateWithLifecycle()
    val active by vm.activeSlot.collectAsStateWithLifecycle()
    Text("Konten", style = MaterialTheme.typography.titleMedium)
    val others = list.filter { it.first != active }
    if (others.isEmpty()) {
        Text(
            "Bist du auch in einem Verein mit eigener HUBIplan-Adresse, kannst du ihn hier als weiteres Konto verbinden.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    others.forEach { (slot, account) ->
        TextButton(onClick = { vm.switchAccount(slot) }) {
            Text("Wechseln zu ${account.display.ifBlank { account.email }} · ${account.server.removePrefix("https://")}")
        }
    }
    OutlinedButton(onClick = vm::addAccount) { Text("Weiteres Konto verbinden") }
}
