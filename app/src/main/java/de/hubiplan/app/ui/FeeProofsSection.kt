package de.hubiplan.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.FEE_PROOF_MAX_BYTES
import de.hubiplan.app.data.FeeProof
import de.hubiplan.app.data.FeeProofOverview
import de.hubiplan.app.data.FeeYearState
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.photo.PhotoPrep
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Kurzstand rechts neben „Beitragsnachweise“. */
internal fun feeProofSummary(due: Int): String =
    if (due > 0) trn("1 Nachweis fehlt", "{n} Nachweise fehlen", due) else tr("Nachweis für den ermäßigten Beitrag")

/** Stand eines Beitragsjahres in Worten (wie im Mitgliederbereich im Browser). */
internal fun feeStateLabel(state: String): String = when (state) {
    "accepted" -> tr("Nachweis anerkannt")
    "pending" -> tr("Nachweis wird geprüft")
    "question" -> tr("Rückfrage")
    "missing" -> tr("Kein Nachweis, normaler Beitrag")
    else -> tr("Nachweis fehlt noch")
}

internal fun feeStateSentence(st: FeeYearState): String = when (st.state) {
    "accepted" -> st.proof?.validUntil?.let { tr("Anerkannt, gültig bis {day}.", "day" to formatPlainDate(it)) } ?: tr("Anerkannt.")
    "pending" -> tr("Eingegangen, dein Verein prüft ihn noch. Bis dahin gilt die Ermäßigung.")
    "question" -> st.proof?.note?.takeIf { it.isNotBlank() }?.let { tr("Rückfrage deines Vereins: {note}", "note" to it) }
        ?: tr("Dein Verein hat eine Rückfrage.")
    "missing" -> tr("Bis {day} ist kein Nachweis eingegangen; für dieses Jahr gilt der normale Beitrag.", "day" to formatPlainDate(st.final))
    else -> if (st.final != st.due) {
        tr("Bitte bis {due} einreichen (spätestens {final}).", "due" to formatPlainDate(st.due), "final" to formatPlainDate(st.final))
    } else {
        tr("Bitte bis {due} einreichen.", "due" to formatPlainDate(st.due))
    }
}

internal fun feeProofStatusLabel(p: FeeProof): String = when (p.status) {
    "accepted" -> tr("anerkannt")
    "rejected" -> tr("abgelehnt")
    "question" -> tr("Rückfrage")
    else -> tr("eingegangen")
}

/** Höchstens [limit] Bytes lesen (größere Dateien lehnt die App ab, ohne alles zu laden). */
internal fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** Gewählte Datei bis zum Senden: Name, Art und Inhalt */
private class ProofFile(val name: String, val mime: String, val data: ByteArray)

/**
 * Beitragsnachweise unter „Ich“: je Beitragsjahr Stand und Frist, Nachweis als Foto oder PDF
 * einreichen. Fotos werden auf dem Gerät verkleinert und ohne EXIF gesendet.
 */
@Composable
internal fun FeeProofsSection(access: PortalAccess, vm: AppViewModel) {
    var overview by remember(access.id) { mutableStateOf<FeeProofOverview?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            overview = api.memberFeeProofs(access.id)
            error = null
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        val o = overview
        if (o == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        Text(tr("Für einen ermäßigten Beitrag braucht der Verein jedes Jahr einen aktuellen Nachweis, zum Beispiel eine Immatrikulationsbescheinigung. Ohne Nachweis gilt der normale Beitrag."))
        if (o.states.isEmpty()) Text(tr("Gerade ist kein Nachweis nötig."))
        o.states.forEach { st ->
            FeeYearCard(access, vm, st, onDone = { overview = it })
        }
        if (o.proofs.isNotEmpty()) {
            Text(tr("Eingereichte Nachweise"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            o.proofs.forEach { p ->
                Column {
                    Text(listOf(p.discountName, p.yearLabel, formatPlainDate(p.receivedOn), feeProofStatusLabel(p)).filter { it.isNotBlank() }.joinToString(" · "))
                    if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeeYearCard(access: PortalAccess, vm: AppViewModel, st: FeeYearState, onDone: (FeeProofOverview) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember(st.discount, st.year) { mutableStateOf<ProofFile?>(null) }
    var kind by remember(st.discount, st.year) { mutableStateOf("") }
    var note by remember(st.discount, st.year) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    fun takePhoto(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                runCatching { PhotoPrep.load(context, uri)?.let(PhotoPrep::jpeg) }.getOrNull()
            }
            withContext(Dispatchers.IO) {
                File(context.cacheDir, "fotos").listFiles { f -> f.name.startsWith("nachweis-") }?.forEach { it.delete() }
            }
            if (jpeg == null) message = tr("Das Foto ließ sich nicht lesen.") else file = ProofFile("nachweis.jpg", "image/jpeg", jpeg)
        }
    }
    fun takePdf(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input -> readAtMost(input, FEE_PROOF_MAX_BYTES + 1) }
                }.getOrNull()
            }
            message = null
            when {
                data == null -> message = tr("Die Datei ließ sich nicht lesen.")
                data.size > FEE_PROOF_MAX_BYTES -> message = tr("Die Datei ist größer als 10 MB.")
                else -> file = ProofFile("nachweis.pdf", "application/pdf", data)
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) takePhoto(photoUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> takePhoto(uri) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> takePdf(uri) }
    fun startCamera() {
        val dir = File(context.cacheDir, "fotos").apply { mkdirs() }
        val f = File(dir, "nachweis-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.dateien", f)
        photoUri = uri
        camera.launch(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else message = tr("Ohne Kamerafreigabe bitte ein Foto aus der Galerie wählen.")
    }
    fun send() {
        val chosen = file ?: run {
            message = tr("Bitte eine Datei wählen.")
            return
        }
        scope.launch {
            val api = vm.api() ?: return@launch
            busy = true
            message = null
            try {
                val next = api.submitFeeProof(access.id, st.discount, st.year, kind, note.trim(), chosen.name, chosen.mime, chosen.data)
                file = null
                note = ""
                notice = tr("Danke! Dein Nachweis ist eingegangen.")
                onDone(next)
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else message = e.problem().text
            } finally {
                busy = false
            }
        }
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Text("${st.discountName} · ${st.yearLabel}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            Text(
                feeStateLabel(st.state),
                color = if (st.state == "missing" || st.state == "question") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(feeStateSentence(st))
            ActionNotice(notice) { notice = null }
            if (st.canSubmit) {
                if (st.kinds.isNotEmpty()) {
                    Text(tr("Art des Nachweises"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        st.kinds.forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = if (kind == k) "" else k }, label = { Text(k) })
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    OutlinedButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            startCamera()
                        } else {
                            permission.launch(Manifest.permission.CAMERA)
                        }
                    }, enabled = !busy) { Text(tr("Foto aufnehmen")) }
                    OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !busy) { Text(tr("Aus der Galerie")) }
                    OutlinedButton(onClick = { pdf.launch("application/pdf") }, enabled = !busy) { Text(tr("PDF wählen")) }
                }
                file?.let { f ->
                    Text(if (f.mime == "application/pdf") tr("PDF gewählt") else tr("Foto gewählt"), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { file = null }, enabled = !busy) { Text(tr("Entfernen")) }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text(tr("Bemerkung (freiwillig)")) },
                    keyboardOptions = FREITEXT,
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { send() }, enabled = !busy && file != null) { Text(tr("Nachweis einreichen")) }
            }
        }
    }
}
