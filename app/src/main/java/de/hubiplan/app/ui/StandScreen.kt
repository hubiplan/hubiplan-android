package de.hubiplan.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.hubiplan.app.data.sameInstance
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import java.io.File
import java.util.UUID

/** Laufender Standmodus in der App (Adresse mit Standzugang, Instanz, Titel). */
data class StandSession(val url: String, val server: String, val title: String)

/**
 * Standmodus eingebettet (hubiplan konzepte/schiessen-android.md): dieselbe Oberfläche wie im
 * Browser, im Vollbild. Die Ansicht lädt nur Seiten der eigenen Instanz, bekommt die Kamera
 * (Foto und Live-Kamera) und hält den Bildschirm wach. Zurück fragt nach, wenn noch Eingaben
 * auf dem Gerät auf die Übertragung warten.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StandScreen(session: StandSession, onClose: () -> Unit) {
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var pendingLeave by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Kamera-Anfrage der Seite (Live-Kamera) bzw. Dateiauswahl (Foto), bis die App sie beantwortet
    var cameraRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var photo by remember { mutableStateOf<Uri?>(null) }

    fun answerFiles(uris: Array<Uri>?) {
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photo
        answerFiles(if (ok && uri != null) arrayOf(uri) else null)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        answerFiles(uri?.let { arrayOf(it) })
    }

    fun startCamera() {
        val dir = File(context.cacheDir, "fotos").apply { mkdirs() }
        // Vorheriges Standfoto löschen: Fotos bleiben nicht auf dem Gerät
        dir.listFiles { f -> f.name.startsWith("stand-") }?.forEach { it.delete() }
        val file = File(dir, "stand-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.dateien", file)
        photo = uri
        takePicture.launch(uri)
    }

    // Nach der Antwort auf die Kamerafreigabe: Live-Kamera freigeben oder Foto aufnehmen
    var afterPermission by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val next = afterPermission
        afterPermission = null
        next?.invoke(ok)
    }

    fun withCamera(action: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            action(true)
        } else {
            afterPermission = action
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    fun leave() {
        val view = web
        if (view == null) {
            onClose()
            return
        }
        // Der Standmodus meldet seine Warteschlange (window.hubiplanStand.pending)
        view.evaluateJavascript("(function(){try{return window.hubiplanStand?window.hubiplanStand.pending():0}catch(e){return 0}})()") { result ->
            val count = result?.trim('"')?.toDoubleOrNull()?.toInt() ?: 0
            if (count > 0) pendingLeave = count else onClose()
        }
    }

    BackHandler {
        val view = web
        if (view != null && view.canGoBack()) view.goBack() else leave()
    }

    DisposableEffect(Unit) {
        onDispose {
            answerFiles(null)
            cameraRequest?.deny()
            web?.apply {
                stopLoading()
                destroy()
            }
            web = null
            File(context.cacheDir, "fotos").listFiles { f -> f.name.startsWith("stand-") }?.forEach { it.delete() }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Abstand.klein), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = Abstand.klein).asHeading(),
                )
                IconButton(onClick = ::leave) { Icon(Icons.Filled.Close, contentDescription = tr("Standmodus schließen")) }
            }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true).padding(Abstand.rand)) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        keepScreenOn = true
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url.toString()
                                if (sameInstance(session.server, url)) return false
                                // Fremde Links (Hilfe, Impressum anderer Seiten) im Browser öffnen
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) {
                                val wantsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                                if (!wantsCamera || !sameInstance(session.server, request.origin.toString())) {
                                    request.deny()
                                    return
                                }
                                cameraRequest = request
                                post {
                                    withCamera { ok ->
                                        val r = cameraRequest
                                        cameraRequest = null
                                        if (ok) r?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else r?.deny()
                                        if (!ok) notice = tr("Ohne Zugriff auf die Kamera geht nur das Antippen der Treffer.")
                                    }
                                }
                            }

                            override fun onShowFileChooser(
                                view: WebView,
                                callback: ValueCallback<Array<Uri>>,
                                params: FileChooserParams,
                            ): Boolean {
                                answerFiles(null)
                                fileCallback = callback
                                if (params.isCaptureEnabled) {
                                    withCamera { ok ->
                                        if (ok) {
                                            startCamera()
                                        } else {
                                            notice = tr("Ohne Zugriff auf die Kamera bitte ein Bild aus der Galerie wählen.")
                                            pickImage.launch("image/*")
                                        }
                                    }
                                } else {
                                    pickImage.launch("image/*")
                                }
                                return true
                            }
                        }
                        loadUrl(session.url)
                        web = this
                    }
                },
            )
        }
    }

    pendingLeave?.let { count ->
        AlertDialog(
            onDismissRequest = { pendingLeave = null },
            title = { Text(tr("Eingaben noch nicht übertragen")) },
            text = {
                Text(
                    trn(
                        "Eine Eingabe wartet noch auf die Übertragung. Sie bleibt bis zu zwölf Stunden auf dem Telefon und wird beim nächsten Öffnen des Standmodus mit Empfang übertragen.",
                        "{n} Eingaben warten noch auf die Übertragung. Sie bleiben bis zu zwölf Stunden auf dem Telefon und werden beim nächsten Öffnen des Standmodus mit Empfang übertragen.",
                        count,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { pendingLeave = null }) { Text(tr("Weiter erfassen")) } },
            dismissButton = { TextButton(onClick = { pendingLeave = null; onClose() }) { Text(tr("Trotzdem schließen")) } },
        )
    }
}
