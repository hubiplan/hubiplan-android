package de.hubiplan.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hubiplan.app.calendar.DeviceCalendar
import de.hubiplan.app.sync.Notifications
import java.text.DateFormat
import java.util.Date

private val REMINDER_CHOICES: List<Pair<Int?, String>> = listOf(
    null to "keine",
    120 to "2 Stunden vorher",
    24 * 60 to "1 Tag vorher",
    2 * 24 * 60 to "2 Tage vorher",
    7 * 24 * 60 to "1 Woche vorher",
)

fun reminderLabel(minutes: Int?): String =
    REMINDER_CHOICES.firstOrNull { it.first == minutes }?.second ?: "$minutes Minuten vorher"

/** Konto › Gerätekalender, Erinnerungen und Hinweise. */
@Composable
fun SettingsSection(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var denied by remember { mutableStateOf(false) }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val ok = result.values.all { it }
        denied = !ok
        if (ok) vm.setCalendarOn(true)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.setRsvpHints(ok)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Gerätekalender", style = MaterialTheme.typography.titleMedium)
        SwitchRow(
            label = "Termine in den Kalender des Telefons übernehmen",
            checked = settings.calendarOn,
        ) { on ->
            if (!on) {
                vm.setCalendarOn(false)
            } else if (DeviceCalendar(context).hasPermission()) {
                vm.setCalendarOn(true)
            } else {
                calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }
        }
        Text(
            "Je Einheit ein eigener Kalender „HUBIplan“, nur auf diesem Gerät (nicht im Google-Konto). " +
                "Er wird alle paar Stunden und nach jeder Rückmeldung aktualisiert.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (denied) {
            Text(
                "Ohne Zugriff auf den Kalender bleiben die Termine nur in der App. Du kannst ihn in den Android-Einstellungen erlauben.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (settings.calendarOn) {
            ReminderPicker(settings.reminderMinutes, vm::setReminderMinutes)
        }

        Text("Hinweise", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        SwitchRow(
            label = "Hinweis, wenn eine Rückmeldung fehlt",
            checked = settings.rsvpHints && Notifications.allowed(context),
        ) { on ->
            if (on && !Notifications.allowed(context) && Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.setRsvpHints(on)
            }
        }
        Text(
            "Etwa einen Tag vor dem Anmeldeschluss, mit den Knöpfen Zusage und Absage.",
            style = MaterialTheme.typography.bodySmall,
        )
        settings.lastSync?.let {
            Text(
                "Zuletzt abgeglichen: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = vm::syncSoon) { Text("Jetzt abgleichen") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ReminderPicker(current: Int?, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Erinnerung: ")
        TextButton(onClick = { open = true }) { Text(reminderLabel(current) + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            REMINDER_CHOICES.forEach { (minutes, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    open = false
                    onPick(minutes)
                })
            }
        }
    }
}
