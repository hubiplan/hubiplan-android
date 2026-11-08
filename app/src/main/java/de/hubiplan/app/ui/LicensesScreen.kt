package de.hubiplan.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.hubiplan.app.BuildConfig
import de.hubiplan.app.data.Licenses
import de.hubiplan.app.tr

const val SOURCE_URL = "https://github.com/hubiplan/hubiplan-android"

// Datenschutzerklärung des Herausgebers zur App (Google Play verlangt den Link auch in der App)
const val APP_PRIVACY_URL = "https://hubiplan.de/datenschutz/"

private fun Context.asset(path: String): String? =
    runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

/**
 * Lizenzen der App und aller enthaltenen Bibliotheken (Lizenzaudit 31.10.2026, L01).
 * Erreichbar ohne Anmeldung (Startseite) und unter Konto. Vollbild mit Kopfleiste (Pfeil zurück,
 * Titel) und Abstand zu Status- und Gestenleiste (UX-Audit 08.11. A-11).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val libraries = remember { Licenses.parse(context.asset(Licenses.LIBRARIES_ASSET).orEmpty()) }
    // Angezeigter Volltext: Titel und Inhalt
    var shown by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Symbole der Gestenleiste passend zur hellen bzw. dunklen Fläche
        val dark = isSystemInDarkTheme()
        val view = LocalView.current
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = !dark }
        }
        // Die Kopfleiste der App liegt unter dem Dialog: Detailansichten hier nicht dort anmelden
        CompositionLocalProvider(LocalBackHost provides null) {
            Surface(Modifier.fillMaxSize()) {
                // Statusleiste wie im Hauptfenster in der Vereinsfarbe (A-10)
                StatusBarStrip(dialogWindow)
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val text = shown
                    androidx.activity.compose.BackHandler(enabled = text != null) { shown = null }
                    TopAppBar(
                        title = {
                            Text(text?.first ?: tr("Lizenzen"), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.asHeading())
                        },
                        navigationIcon = {
                            IconButton(onClick = { if (text != null) shown = null else onClose() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Zurück"))
                            }
                        },
                        windowInsets = WindowInsets(0),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = LocalBrand.current.brand,
                            titleContentColor = LocalBrand.current.onBrand,
                            navigationIconContentColor = LocalBrand.current.onBrand,
                        ),
                    )
                    if (text != null) {
                        Text(
                            text.second,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState()).padding(Abstand.rand),
                        )
                    } else {
                        LicenseList(libraries, onShow = { shown = it }, onOpen = ::open, onClose = onClose)
                    }
                }
            }
        }
    }
}

/** Paketnamen ohne Silbentrennung; umbrochen wird höchstens an Punkt oder Doppelpunkt (A-11). */
internal fun breakableCoordinate(coordinate: String): String =
    coordinate.replace(".", ".\u200B").replace(":", ":\u200B")

@Composable
private fun LicenseList(
    libraries: List<de.hubiplan.app.data.Library>,
    onShow: (Pair<String, String>) -> Unit,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Abstand.rand),
        verticalArrangement = Arrangement.spacedBy(Abstand.klein),
    ) {
        Text(
            tr("HUBIplan-App {VERSION_NAME}, Copyright © 2026 Ralf Philipsen (Philipsen EDV). Freie Software unter der GNU Affero General Public License, Version 3 oder neuer. Sie wird ohne jede Gewährleistung bereitgestellt.", "VERSION_NAME" to BuildConfig.VERSION_NAME),
        )
        TextButton(onClick = {
            onShow(
                "GNU Affero General Public License 3.0" to
                    (context.asset(Licenses.APP_LICENSE_ASSET) ?: tr("Text nicht gefunden: https://www.gnu.org/licenses/agpl-3.0.txt")),
            )
        }) { Text(tr("Lizenztext der App (AGPL)")) }
        TextButton(onClick = { onOpen(SOURCE_URL) }) { Text(tr("Quellcode")) }

        HorizontalDivider()
        Text(tr("Enthaltene Bibliotheken"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        if (libraries.isEmpty()) Text(tr("Die Liste fehlt in dieser Fassung der App."))
        for ((id, list) in Licenses.byLicense(libraries)) {
            val info = Licenses.KNOWN[id]
            Text(info?.title ?: id, style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
            if (info != null) {
                val licenseText = info.asset?.let { context.asset(it) }
                if (licenseText != null) {
                    TextButton(onClick = { onShow(info.title to licenseText) }) { Text(tr("Lizenztext anzeigen")) }
                } else {
                    TextButton(onClick = { onOpen(info.url) }) { Text(tr("Bedingungen im Browser öffnen")) }
                }
            }
            Text(
                list.joinToString("\n") { breakableCoordinate(it.coordinate) },
                style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.None),
                fontFamily = FontFamily.Monospace,
            )
        }

        HorizontalDivider()
        Text(tr("Hinweise der Bibliotheken"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        for ((name, notice) in Licenses.NOTICES) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(notice, style = MaterialTheme.typography.bodySmall)
        }
        Licenses.KNOWN["MPL-2.0"]?.let { mpl ->
            TextButton(onClick = {
                onShow(mpl.title to (mpl.asset?.let { context.asset(it) } ?: mpl.url))
            }) { Text(tr("Lizenztext MPL 2.0 anzeigen")) }
        }
        TextButton(onClick = onClose) { Text(tr("Schließen")) }
    }
}
