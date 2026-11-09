package de.hubiplan.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.CardStore
import de.hubiplan.app.data.CardWallet
import de.hubiplan.app.data.LocalAccountData
import de.hubiplan.app.data.StoredCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import de.hubiplan.app.tr

/** Ab diesem Alter weist die App darauf hin, dass der Ausweis ohne Netz angezeigt wird. */
private const val STALE_MS = 24L * 60 * 60 * 1000

/**
 * Eigener Mitgliedsausweis: wie die gedruckte Karte, mit QR-Code zur Prüfung. Die App speichert
 * ihn verschlüsselt und zeigt ihn auch ohne Netz; der QR-Code führt zur Prüfseite von HUBIplan,
 * die immer den aktuellen Stand zeigt (gesperrte Ausweise gelten dort nicht mehr).
 */
@Composable
fun MemberCardSection(store: CardStore, accessId: Int, vm: AppViewModel?, bigRequest: Int = 0) {
    var stored by remember(accessId) { mutableStateOf(store.load(accessId)) }
    var offline by remember(accessId) { mutableStateOf(false) }
    var big by remember { mutableStateOf(false) }
    // App-Verknüpfung „Ausweis zeigen“: gleich groß (Konzept app-ich)
    LaunchedEffect(bigRequest) { if (bigRequest > 0) big = true }

    LaunchedEffect(accessId) {
        val api = vm?.api() ?: run { offline = true; return@LaunchedEffect }
        try {
            val card = api.card(accessId)
            val display = card.display
            if (card.state == "active" && display != null) {
                val photo = display.photoUrl?.let { api.image(it) }
                val logo = display.logoUrl?.let { api.image(it) }
                val fresh = StoredCard(card, System.currentTimeMillis(), StoredCard.encode(photo), StoredCard.encode(logo))
                withContext(Dispatchers.IO) { store.save(accessId, fresh) }
                stored = fresh
            } else {
                // Gesperrt, noch keiner oder Mitgliedschaft beendet: nichts mehr vorzeigen
                withContext(Dispatchers.IO) { store.delete(accessId) }
                stored = StoredCard(card, System.currentTimeMillis())
            }
            offline = false
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else offline = true
        }
    }

    val current = stored
    val card = current?.card
    val display = card?.display
    Text(tr("Mitgliedsausweis"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    when {
        current == null -> Text(
            if (offline) tr("Ohne Netz ist noch kein Ausweis gespeichert. Er erscheint, sobald die App einmal online war.")
            else tr("Ausweis wird geladen …"),
            style = MaterialTheme.typography.bodyMedium,
        )
        card?.state == "revoked" -> Text(
            tr("Dein Ausweis ist gesperrt. Bitte wende dich an den Vorstand."),
            color = MaterialTheme.colorScheme.error,
        )
        display == null || card.url.isNullOrBlank() -> Text(
            tr("Für dich gibt es zurzeit keinen gültigen Ausweis, z. B. weil die Mitgliedschaft noch nicht begonnen hat oder beendet ist."),
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> {
            MemberCardView(current, Modifier.fillMaxWidth().widthIn(max = 480.dp).clickable(onClickLabel = tr("QR-Code groß anzeigen")) { big = true })
            if (offline || System.currentTimeMillis() - current.savedAt > STALE_MS) {
                Text(
                    tr("Ohne Netz angezeigt, Stand {x1}. Wer den QR-Code prüft, sieht immer den aktuellen Stand.", "x1" to formatStamp(current.savedAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { big = true }) { Text(tr("QR-Code groß zeigen")) }
            val wallet = card.wallet
            if (vm != null && !offline && wallet != null && (wallet.google || wallet.apple)) {
                WalletButtons(vm, accessId, wallet)
            }
        }
    }
    if (big && current != null && card?.url != null && display != null) {
        BigCardDialog(current, onClose = { big = false })
    }
}

/**
 * Ausweis in eine Wallet legen: Google über den Speicherlink (öffnet Google Wallet oder den
 * Browser, ohne Google-Bibliothek, also auch in der F-Droid-Fassung), Apple-Pass als Datei für
 * Wallet-Apps wie WalletPasses oder PassAndroid. Gültig bleibt nur, was die Prüfseite zeigt.
 */
@Composable
private fun WalletButtons(vm: AppViewModel, accessId: Int, wallet: CardWallet) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmGoogle by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend (de.hubiplan.app.data.Api) -> Unit) {
        val api = vm.api() ?: return
        busy = true
        message = null
        scope.launch {
            try {
                block(api)
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else message = e.userText()
            } finally {
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
        if (wallet.google) {
            OutlinedButton(onClick = { confirmGoogle = true }, enabled = !busy) { Text(tr("Zu Google Wallet hinzufügen")) }
        }
        if (wallet.apple) {
            OutlinedButton(
                onClick = {
                    run { api ->
                        val bytes = api.walletApple(accessId)
                        val file = withContext(Dispatchers.IO) {
                            File(context.cacheDir, LocalAccountData.WALLET_PASS).apply { mkdirs() }.resolve("mitgliedsausweis.pkpass").apply { writeBytes(bytes) }
                        }
                        message = openPass(context, file)
                    }
                },
                enabled = !busy,
            ) { Text(tr("Als Wallet-Pass öffnen")) }
        }
    }
    Text(
        tr("Der Wallet-Pass zeigt deinen Ausweis an. Ob er gilt, zeigt nur die Prüfung des QR-Codes."),
        style = MaterialTheme.typography.bodySmall,
    )
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (confirmGoogle) {
        AlertDialog(
            onDismissRequest = { confirmGoogle = false },
            title = { Text(tr("Zu Google Wallet hinzufügen")) },
            text = {
                Text(tr("Dabei werden Name, Verein, Mitgliedsart, Abteilung, Dachverband und QR-Code an Google übertragen. Dein Foto wird nicht übertragen. Die aktuelle Gültigkeit wird beim Scannen online geprüft."))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmGoogle = false
                    run { api ->
                        val url = api.walletGoogle(accessId)
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: ActivityNotFoundException) {
                            message = tr("Keine App zum Öffnen des Links gefunden.")
                        }
                    }
                }) { Text(tr("Weiter")) }
            },
            dismissButton = { TextButton(onClick = { confirmGoogle = false }) { Text(tr("Abbrechen")) } },
        )
    }
}

/** Pass einer Wallet-App übergeben; Fehlertext, wenn keine dafür da ist. */
private fun openPass(context: Context, file: File): String? {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.dateien", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.apple.pkpass")
    intent.clipData = ClipData.newRawUri(file.name, uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        tr("Keine Wallet-App für .pkpass-Dateien gefunden, z. B. WalletPasses oder PassAndroid.")
    }
}

@Composable
private fun MemberCardView(stored: StoredCard, modifier: Modifier = Modifier) {
    val display = stored.card.display ?: return
    val primary = Color(parseHexColor(display.theme?.primary) ?: DEFAULT_PRIMARY)
    val onPrimary = Color(parseHexColor(display.theme?.onPrimary) ?: WHITE)
    val photo = remember(stored.savedAt) { decodeBitmap(stored.photoBytes()) }
    val logo = remember(stored.savedAt) { decodeBitmap(stored.logoBytes()) }
    val qr = rememberQr(stored.card.url.orEmpty(), 360)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().background(primary).padding(horizontal = Abstand.mittel, vertical = Abstand.klein),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Abstand.mittel),
        ) {
            logo?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(36.dp)) }
            Column {
                Text(display.club, color = onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(tr("Mitgliedsausweis"), color = onPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth().padding(Abstand.mittel), horizontalArrangement = Arrangement.spacedBy(Abstand.mittel)) {
            photo?.let {
                if (display.isOrganization) {
                    // Logo der Organisation: ganz zeigen, nicht beschneiden
                    Image(
                        it.asImageBitmap(),
                        contentDescription = tr("Logo"),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(88.dp).aspectRatio(4f / 3f),
                    )
                } else {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = tr("Foto"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(72.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Abstand.zeile)) {
                Text(cardFirstLine(display), style = MaterialTheme.typography.bodyMedium)
                Text(display.lastName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                CardLine(display.departmentLabel, display.departments.joinToString(", "))
                CardLine(tr("Mitgliedsnummer"), display.clubNumber)
                CardLine(tr("Mitgliedsart"), display.memberType.orEmpty())
                Text(
                    display.statusLabel,
                    color = if (display.status == "active") Color(0xFF1B5E20) else Color(0xFFB71C1C),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            qr?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = tr("QR-Code zur Prüfung"),
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(96.dp),
                )
            }
        }
    }
}

/** Erste Zeile im Ausweis: Vorname, bei Organisationen „Organisationsmitglied“. */
fun cardFirstLine(display: de.hubiplan.app.data.CardDisplay): String =
    if (display.isOrganization) tr("Organisationsmitglied") else display.firstName

@Composable
private fun CardLine(label: String, value: String) {
    if (value.isBlank() || label.isBlank()) return
    Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF555555))
    Text(value, style = MaterialTheme.typography.bodySmall)
}

/** Vorzeigen: QR-Code bildschirmfüllend auf Weiß, Bildschirm so hell wie möglich. */
@Composable
private fun BigCardDialog(stored: StoredCard, onClose: () -> Unit) {
    val display = stored.card.display ?: return
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val before = window?.attributes?.screenBrightness
        window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        }
        onDispose {
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
    val qr = rememberQr(stored.card.url.orEmpty(), 800)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.White).clickable(onClickLabel = tr("Schließen"), onClick = onClose), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(Abstand.gross),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
            ) {
                Text(display.club, color = Color.Black, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    display.name,
                    color = Color.Black,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                qr?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = tr("QR-Code zur Prüfung"),
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Sichtbarer Knopf zusätzlich zum Tippen irgendwo (A-09)
                androidx.compose.material3.OutlinedButton(
                    onClick = onClose,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                ) { Text(tr("Schließen")) }
            }
        }
    }
}

private fun decodeBitmap(bytes: ByteArray?): Bitmap? =
    de.hubiplan.app.data.SafeBitmap.decode(bytes, 1600)

@Composable
private fun rememberQr(content: String, size: Int): Bitmap? = remember(content, size) {
    if (content.isBlank()) null else runCatching { qrBitmap(content, size) }.getOrNull()
}

/** QR-Code als Bild (schwarz auf weiß, mit Ruhezone). */
fun qrBitmap(content: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}
