package de.hubiplan.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import de.hubiplan.app.tr

/**
 * Adresse im Browser öffnen. Ohne Browser (Arbeits- oder Kinderprofil, eingeschränktes Gerät)
 * erscheint ein Hinweis mit der Adresse statt eines Absturzes (UX-Audit 16.11., AND-21).
 * Einzige Stelle der App für `ACTION_VIEW` mit einer Webadresse.
 */
fun openInBrowser(context: Context, url: String, messages: ActionMessages? = null): Boolean {
    val ok = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
    if (!ok) {
        messages?.show(tr("Der Browser konnte nicht geöffnet werden. Öffne diese Adresse in einem Browser: {url}", "url" to url), error = true)
    }
    return ok
}

/**
 * Geheimes (z. B. Passwort eines Videoraums) in die Zwischenablage legen: ab Android 13 als
 * vertraulich markiert, damit es nicht in der Vorschau der Zwischenablage steht (SEC-06).
 */
fun copySensitive(context: Context, label: String, text: String) {
    val clip = android.content.ClipData.newPlainText(label, text)
    val key = if (android.os.Build.VERSION.SDK_INT >= 33) android.content.ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
    clip.description.extras = android.os.PersistableBundle().apply { putBoolean(key, true) }
    runCatching { context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(clip) }
}

/** Kamera ist für diese App erlaubt. */
fun cameraAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Der QR-Scan kam ohne Inhalt zurück (UX-Audit 16.11., AND-17): ohne Kamera-Erlaubnis ein Satz
 * mit Ausweg, bei einfachem Abbruch nichts. [alternative] nennt den Weg ohne Kamera.
 */
fun scanWithoutResult(context: Context, alternative: String? = null): String? {
    if (cameraAllowed(context)) return null
    val base = tr("Ohne Kamera lässt sich der QR-Code nicht scannen. Erlaube die Kamera in den Android-Einstellungen.")
    return if (alternative == null) base else "$base $alternative"
}

/** App-Einstellungen von Android öffnen (Kamera erlauben). */
fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }
}

/** Hinweis nach einem QR-Scan ohne Kamera-Erlaubnis, mit Weg in die Einstellungen (AND-17). */
@androidx.compose.runtime.Composable
fun ScanProblemDialog(text: String?, onDismiss: () -> Unit) {
    if (text == null) return
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(tr("Kamera nicht erlaubt")) },
        text = { androidx.compose.material3.Text(text) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onDismiss(); openAppSettings(context) }) {
                androidx.compose.material3.Text(tr("Einstellungen öffnen"))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text(tr("Schließen")) }
        },
    )
}
