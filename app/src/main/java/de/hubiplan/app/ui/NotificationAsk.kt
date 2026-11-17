package de.hubiplan.app.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.hubiplan.app.sync.Notifications
import de.hubiplan.app.tr

private const val PREFS = "benachrichtigung-frage"
private const val KEY = "gefragt"

/**
 * Ab Android 13 bleiben Hinweise stumm, bis die Erlaubnis erteilt ist. Gefragt wird einmal
 * nach dem Verbinden, mit Begründung; wer „Später“ wählt, findet die Schalter unter
 * „Mein Konto“ (UX-Audit 16.11. AND-01).
 */
internal fun shouldAskNotifications(sdk: Int, allowed: Boolean, asked: Boolean): Boolean =
    sdk >= 33 && !allowed && !asked

@Composable
internal fun NotificationPermissionAsk() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var ask by remember {
        mutableStateOf(shouldAskNotifications(Build.VERSION.SDK_INT, Notifications.allowed(context), prefs.getBoolean(KEY, false)))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (!ask) return
    fun done() {
        prefs.edit().putBoolean(KEY, true).apply()
        ask = false
    }
    AlertDialog(
        onDismissRequest = { done() },
        title = { Text(tr("Benachrichtigungen erlauben?")) },
        text = {
            Text(tr("Dann erinnert dich die App, wenn eine Rückmeldung zu einem Termin fehlt, und zeigt neue Mitteilungen und Aufgaben. Ohne Erlaubnis bleibt sie stumm. Du kannst das später unter „Mein Konto“ ändern."))
        },
        confirmButton = {
            TextButton(onClick = {
                done()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text(tr("Erlauben")) }
        },
        dismissButton = { TextButton(onClick = { done() }) { Text(tr("Später")) } },
    )
}
