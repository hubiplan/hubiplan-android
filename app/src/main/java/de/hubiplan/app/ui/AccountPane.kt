package de.hubiplan.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.hubiplan.app.BuildConfig

private const val SOURCE_URL = "https://github.com/DeltaRP/hubiplan-android"

@Composable
fun AccountPane(main: Screen.Main, vm: AppViewModel) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
        OutlinedButton(onClick = vm::logout) { Text("Von diesem Gerät abmelden") }
        Text(
            "HUBIplan-App ${BuildConfig.VERSION_NAME} · freie Software unter der GNU AGPL 3.0 oder neuer",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { open(SOURCE_URL) }) { Text("Quellcode") }
    }
}
