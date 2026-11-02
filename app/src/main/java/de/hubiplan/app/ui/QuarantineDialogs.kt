package de.hubiplan.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hubiplan.app.data.LocalAccountData
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hinweise zur Quarantäne nicht gesendeter Belege (Fachfrage 16, Audit 31.10.; FQ24, Audit
 * 02.11.): nach erneuter Anmeldung zurückgeholt; nach 90 Tagen Rückfrage mit Export; nach
 * 120 Tagen oder wenn unlesbar endgültig gelöscht, mit Hinweis.
 */
@Composable
fun QuarantineDialogs(vm: AppViewModel) {
    val restored by vm.restored.collectAsStateWithLifecycle()
    val stale by vm.staleQuarantine.collectAsStateWithLifecycle()
    val deadline by vm.staleDeadline.collectAsStateWithLifecycle()
    val swept by vm.swept.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportError by remember { mutableStateOf<String?>(null) }
    val sweep = swept
    if (restored > 0) {
        AlertDialog(
            onDismissRequest = vm::dismissRestored,
            title = { Text(tr("Belege zurückgeholt")) },
            text = {
                Text(
                    (if (restored == 1) tr("1 nicht gesendeter Vorgang") else tr("{restored} nicht gesendete Vorgänge", "restored" to restored)) +
                        tr(" aus einer früheren Anmeldung sind wieder da. Sie stehen unter „Verwaltung“ und werden wie gewohnt gesendet."),
                )
            },
            confirmButton = { TextButton(onClick = vm::dismissRestored) { Text("OK") } },
        )
    } else if (sweep != null) {
        AlertDialog(
            onDismissRequest = vm::dismissSwept,
            title = { Text(tr("Alte Belege gelöscht")) },
            text = {
                Text(
                    listOfNotNull(
                        if (sweep.expired > 0) {
                            trn(
                                "1 nicht gesendeter Vorgang einer früheren Anmeldung lag länger als {days} Tage auf diesem Gerät und wurde gelöscht.",
                                "{n} nicht gesendete Vorgänge einer früheren Anmeldung lagen länger als {days} Tage auf diesem Gerät und wurden gelöscht.",
                                sweep.expired,
                                "days" to LocalAccountData.FINAL_DAYS,
                            )
                        } else {
                            null
                        },
                        if (sweep.unreadable > 0) {
                            trn(
                                "1 nicht gesendeter Vorgang ließ sich nicht mehr öffnen (Schlüssel fehlt, z. B. nach dem Entfernen der Bildschirmsperre) und wurde gelöscht.",
                                "{n} nicht gesendete Vorgänge ließen sich nicht mehr öffnen (Schlüssel fehlt, z. B. nach dem Entfernen der Bildschirmsperre) und wurden gelöscht.",
                                sweep.unreadable,
                            )
                        } else {
                            null
                        },
                    ).joinToString("\n\n") + tr(" Falls sie beim Verein fehlen, bitte neu erfassen."),
                )
            },
            confirmButton = { TextButton(onClick = vm::dismissSwept) { Text("OK") } },
        )
    } else if (stale > 0) {
        AlertDialog(
            onDismissRequest = vm::keepStaleQuarantine,
            title = { Text(tr("Alte, nicht gesendete Belege")) },
            text = {
                Text(
                    trn(
                        "1 Vorgang einer früheren Anmeldung liegt seit über {STALE_DAYS} Tagen verschlüsselt auf diesem Gerät.",
                        "{n} Vorgänge einer früheren Anmeldung liegen seit über {STALE_DAYS} Tagen verschlüsselt auf diesem Gerät.",
                        stale,
                        "STALE_DAYS" to LocalAccountData.STALE_DAYS,
                    ) +
                        tr(" Nach erneuter Anmeldung mit demselben Konto bei derselben HUBIplan-Instanz werden sie wieder angeboten.") +
                        (deadline?.let { tr(" Sonst löscht die App sie am {date} endgültig. Was noch beim Verein ankommen muss, vorher exportieren und weitergeben.", "date" to formatDate(Instant.ofEpochMilli(it).toString())) } ?: "") +
                        (exportError?.let { "\n\n$it" } ?: ""),
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { LocalAccountData.exportStale(context) }.getOrNull() }
                            exportError = if (result == null) tr("Nichts ließ sich öffnen. Bitte das Telefon entsperren und erneut versuchen.") else shareExport(context, result.first)
                        }
                    }) { Text(tr("Exportieren")) }
                    TextButton(onClick = vm::discardStaleQuarantine) { Text(tr("Löschen")) }
                }
            },
            dismissButton = { TextButton(onClick = vm::keepStaleQuarantine) { Text(tr("Behalten")) } },
        )
    }
}

/** Exportdatei teilen (Mail, Ablage, …); Fehlertext, wenn keine App dafür da ist. */
private fun shareExport(context: Context, file: File): String? {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.dateien", file)
    val intent = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
    intent.clipData = ClipData.newRawUri(file.name, uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(Intent.createChooser(intent, tr("Belege exportieren")))
        null
    } catch (e: ActivityNotFoundException) {
        tr("Keine App zum Teilen gefunden.")
    }
}
