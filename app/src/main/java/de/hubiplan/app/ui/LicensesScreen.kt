package de.hubiplan.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import de.hubiplan.app.BuildConfig
import de.hubiplan.app.data.Licenses
import de.hubiplan.app.tr

/** Quellcode genau dieser Version der App (Tag v<versionName>). */
val SOURCE_URL: String get() = Licenses.sourceUrl(BuildConfig.VERSION_NAME)

// Datenschutzerklärung des Herausgebers zur App (Google Play verlangt den Link auch in der App)
const val APP_PRIVACY_URL = "https://hubiplan.de/datenschutz/"

private fun Context.asset(path: String): String? =
    runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

/**
 * Lizenzen der App und aller enthaltenen Bibliotheken (Lizenzaudit 31.10.2026, L01).
 * Erreichbar ohne Anmeldung (Startseite) und unter Konto. Als Seite im Hauptfenster statt als
 * eigenes Dialogfenster: so gelten dieselben Abstände zu Status- und Gestenleiste wie überall
 * (UX-Audit 08.11. A-10/A-11). Im Hauptbildschirm stehen Pfeil und Titel in dessen Kopfleiste,
 * auf der Anmeldeseite in einer eigenen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val libraries = remember { Licenses.parse(context.asset(Licenses.LIBRARIES_ASSET).orEmpty()) }
    // Angezeigter Volltext: Titel und Inhalt
    var shown by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    val text = shown
    val back = { if (shown != null) shown = null else onClose() }
    androidx.activity.compose.BackHandler { back() }
    val title = text?.first ?: tr("Lizenzen")
    Column(Modifier.fillMaxSize()) {
        if (LocalBackHost.current != null) {
            BackHead(title) { back() }
        } else {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.asHeading()) },
                navigationIcon = {
                    IconButton(onClick = { back() }) {
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
        }
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

/** Paketnamen ohne Silbentrennung; umbrochen wird am Doppelpunkt, nie in der Versionsnummer (A-11). */
internal fun breakableCoordinate(coordinate: String): String =
    coordinate.replace(":", ":\u200B")

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
        TextButton(onClick = { onOpen(SOURCE_URL) }) {
            Text(tr("Quellcode dieser Version (v{VERSION_NAME})", "VERSION_NAME" to BuildConfig.VERSION_NAME))
        }
        TextButton(onClick = { onOpen(Licenses.SOURCE_REPO) }) { Text(tr("Alle Fassungen des Quellcodes")) }

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
        Licenses.publicSuffixSource(libraries)?.let { url ->
            TextButton(onClick = { onOpen(url) }) { Text(tr("Eingebaute Fassung der Public Suffix List")) }
        }
        Licenses.KNOWN["MPL-2.0"]?.let { mpl ->
            TextButton(onClick = {
                onShow(mpl.title to (mpl.asset?.let { context.asset(it) } ?: mpl.url))
            }) { Text(tr("Lizenztext MPL 2.0 anzeigen")) }
        }
        TextButton(onClick = onClose) { Text(tr("Schließen")) }
    }
}
