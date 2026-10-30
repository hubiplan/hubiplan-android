package de.hubiplan.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.PhotoState
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.Profile
import de.hubiplan.app.photo.PhotoPrep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Eigene Daten: ansehen, Änderung melden, Foto aufnehmen (wartet auf Freigabe). */
@Composable
fun ProfilePane(access: PortalAccess, vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember(access.id) { mutableStateOf<Profile?>(null) }
    var photo by remember(access.id) { mutableStateOf<PhotoState?>(null) }
    var image by remember(access.id) { mutableStateOf<Bitmap?>(null) }
    var error by remember(access.id) { mutableStateOf<String?>(null) }
    var notice by remember(access.id) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    // Ausgewähltes Foto vor dem Hochladen (Vorschau, drehen, Einwilligung)
    var draft by remember { mutableStateOf<Bitmap?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun fail(e: ApiException) {
        if (e.status == 401) vm.deviceRevoked(e.message) else error = e.message
    }

    LaunchedEffect(access.id, reload) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            profile = api.profile(access.id)
            val state = api.photo(access.id)
            photo = state
            val path = (state.pending ?: state.approved)?.url
            image = path?.let { p -> api.image(p)?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) } } }
        } catch (e: ApiException) {
            fail(e)
        }
    }

    // Kamera: Foto in eine Datei im Cache, danach gleich wieder löschen
    var cameraFile by remember { mutableStateOf<File?>(null) }
    fun load(uri: Uri) {
        scope.launch {
            draft = withContext(Dispatchers.IO) { runCatching { PhotoPrep.load(context, uri) }.getOrNull() }
            if (draft == null) error = "Das Bild ließ sich nicht öffnen."
            cameraFile?.delete()
            cameraFile = null
        }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) load(Uri.fromFile(file)) else file?.delete()
    }
    fun startCamera() {
        val dir = File(context.cacheDir, "fotos").apply { mkdirs() }
        val file = File(dir, "aufnahme.jpg")
        cameraFile = file
        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.dateien", file))
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else error = "Ohne Zugriff auf die Kamera bitte ein Bild aus der Galerie wählen."
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) load(uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        val p = profile
        if (p == null) {
            if (error == null) Loading()
            return@Column
        }
        Text("${p.firstName} ${p.lastName}", style = MaterialTheme.typography.headlineSmall)
        val facts = buildList {
            p.memberType?.let { add(it) }
            if (p.departments.isNotEmpty()) add(p.departments.joinToString(", "))
            p.entryDate?.let { add("Mitglied seit ${formatPlainDate(it)}") }
            if (p.clubNumber.isNotBlank()) add("Nr. ${p.clubNumber}")
        }
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)

        // --- Foto ---
        Text("Foto", style = MaterialTheme.typography.titleMedium)
        val state = photo
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            image?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = "Dein Foto",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(105.dp).height(135.dp),
                )
            }
            Text(
                when {
                    state?.pending != null -> "Neues Foto wartet auf Freigabe durch den Vorstand."
                    state?.approved != null -> "Dieses Foto steht in deinem Mitgliedsausweis."
                    else -> "Noch kein Foto. Es erscheint nach der Freigabe im Mitgliedsausweis."
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                error = null
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }) { Text("Foto aufnehmen") }
            OutlinedButton(onClick = {
                error = null
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Aus der Galerie") }
        }
        if (state?.consentAt != null || state?.approved != null || state?.pending != null) {
            TextButton(onClick = { confirmDelete = true }) { Text("Einwilligung widerrufen und Foto löschen") }
        }

        HorizontalDivider()
        // --- Kontaktdaten ---
        Text("Kontakt", style = MaterialTheme.typography.titleMedium)
        val c = p.contact
        if (c != null) {
            listOf(c.street, "${c.postalCode} ${c.city}".trim(), c.country, c.email, c.phone)
                .filter { it.isNotBlank() }
                .forEach { Text(it) }
        }
        p.changeRequests.firstOrNull { it.status == "pending" }?.let {
            Text("Deine Änderung wartet auf Bestätigung durch den Vorstand.", style = MaterialTheme.typography.bodySmall)
        }
        if (!editing) {
            OutlinedButton(onClick = { editing = true }) { Text("Änderung melden") }
        } else {
            ChangeForm(
                p,
                onCancel = { editing = false },
                onSend = { values, message ->
                    val api = vm.api() ?: return@ChangeForm
                    scope.launch {
                        try {
                            val waits = api.reportChange(access.id, values, message)
                            notice = if (waits) {
                                "Danke! Der Vorstand prüft die Änderung, du bekommst Bescheid."
                            } else {
                                "Gespeichert."
                            }
                            editing = false
                            reload++
                        } catch (e: ApiException) {
                            fail(e)
                        }
                    }
                },
            )
        }
    }

    draft?.let { bitmap ->
        PhotoUploadDialog(
            bitmap,
            organization = access.title,
            onRotate = { draft = PhotoPrep.rotate(bitmap, 90) },
            onCancel = { draft = null },
            onUpload = {
                val api = vm.api() ?: return@PhotoUploadDialog
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.Default) { PhotoPrep.jpeg(bitmap) }
                        photo = api.uploadPhoto(access.id, bytes)
                        notice = "Foto gesendet. Es erscheint, sobald der Vorstand es freigibt."
                        draft = null
                        reload++
                    } catch (e: ApiException) {
                        draft = null
                        fail(e)
                    }
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Foto löschen?") },
            text = { Text("Deine Einwilligung wird widerrufen und alle deine Fotos werden gelöscht, auch im Mitgliedsausweis.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val api = vm.api() ?: return@TextButton
                    scope.launch {
                        try {
                            photo = api.deletePhoto(access.id)
                            image = null
                            notice = "Foto gelöscht und Einwilligung widerrufen."
                        } catch (e: ApiException) {
                            fail(e)
                        }
                    }
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun PhotoUploadDialog(
    bitmap: Bitmap,
    organization: String,
    onRotate: () -> Unit,
    onCancel: () -> Unit,
    onUpload: () -> Unit,
) {
    var consent by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Foto senden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = "Vorschau",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
                TextButton(onClick = onRotate) { Text("Drehen") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text(
                        "Ich willige ein, dass $organization dieses Foto für meinen Mitgliedsausweis und die " +
                            "Mitgliederverwaltung verwendet. Ich kann die Einwilligung jederzeit widerrufen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onUpload, enabled = consent) { Text("Senden") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

@Composable
private fun ChangeForm(profile: Profile, onCancel: () -> Unit, onSend: (Map<String, String>, String) -> Unit) {
    val c = profile.contact
    val start = mapOf(
        "email" to (c?.email ?: ""),
        "phone" to (c?.phone ?: ""),
        "street" to (c?.street ?: ""),
        "postal_code" to (c?.postalCode ?: ""),
        "city" to (c?.city ?: ""),
        "last_name" to profile.lastName,
    )
    val labels = listOf(
        "email" to "E-Mail",
        "phone" to "Telefon",
        "street" to "Straße und Hausnummer",
        "postal_code" to "PLZ",
        "city" to "Ort",
        "last_name" to "Nachname",
    )
    var values by remember { mutableStateOf(start) }
    var message by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { (key, label) ->
            OutlinedTextField(
                value = values[key] ?: "",
                onValueChange = { values = values + (key to it) },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(500) },
            label = { Text("Nachricht an den Vorstand (freiwillig)") },
            modifier = Modifier.fillMaxWidth(),
        )
        val changed = changedFields(start, values)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSend(changed, message) }, enabled = changed.isNotEmpty()) { Text("Senden") }
            TextButton(onClick = onCancel) { Text("Abbrechen") }
        }
    }
}
