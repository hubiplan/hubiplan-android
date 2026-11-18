package de.hubiplan.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.ExpenseClaim
import de.hubiplan.app.data.ExpenseDraft
import de.hubiplan.app.data.ExpenseOverview
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.fields
import de.hubiplan.app.photo.PhotoPrep
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import androidx.compose.ui.text.input.ImeAction

/** Kurzstand rechts neben „Auslagen“. */
internal fun expenseSummary(overview: ExpenseOverview): String {
    val asks = overview.claims.count { it.asksMe }
    val open = overview.claims.count { it.status in setOf("submitted", "confirmed", "approved") }
    return when {
        asks > 0 -> trn("1 Rückfrage der Kasse", "{n} Rückfragen der Kasse", asks)
        open > 0 -> trn("1 in Bearbeitung", "{n} in Bearbeitung", open)
        else -> tr("Selbst Bezahltes erstattet bekommen")
    }
}

/** Zweite Zeile einer Auslage: Betrag, Stand und Datum. */
internal fun expenseLine(c: ExpenseClaim): String = listOfNotNull(
    formatMoney(c.amountDue),
    c.statusLabel.ifBlank { null },
    c.submittedAt?.let { tr("eingereicht am {day}", "day" to formatDate(it)) },
).joinToString(" · ")

/**
 * Auslagen unter „Ich“ (HUBIplan Auslagen erstatten): selbst bezahlte Ausgaben mit Foto des
 * Belegs melden, Rückfragen beantworten, Stand sehen. Fotos werden auf dem Gerät verkleinert,
 * ohne EXIF gesendet und danach aus dem Cache gelöscht.
 */
@Composable
internal fun ExpensesSection(access: PortalAccess, vm: AppViewModel) {
    var overview by remember(access.id) { mutableStateOf<ExpenseOverview?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    var notice by remember(access.id) { mutableStateOf<String?>(null) }
    var creating by rememberSaveable(access.id) { mutableStateOf(false) }
    var open by rememberSaveable(access.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            overview = api.memberExpenses(access.id)
            error = null
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    val o = overview
    val selected = open
    val leave = LocalFormLeaveGuard.current
    if (selected != null) {
        val back: () -> Unit = {
            open = null
            attempt++
        }
        // Zurück führt eine Ebene hoch zur Liste der Auslagen, nicht aus dem Bereich (AND-04)
        androidx.activity.compose.BackHandler { if (leave == null) back() else leave.request(back) }
        ExpenseDetail(access, vm, selected, onBack = { if (leave == null) back() else leave.request(back) })
        return
    }
    // Zurück im Formular schließt nur das Formular und fragt bei Eingaben nach (AND-04)
    val closeForm: () -> Unit = { creating = false }
    androidx.activity.compose.BackHandler(enabled = creating) { if (leave == null) closeForm() else leave.request(closeForm) }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        ActionNotice(notice) { notice = null }
        if (o == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        if (!o.options.maySubmit) {
            Text(tr("Auslagen melden hier nur Personen mit Amt oder Rolle im Verein."))
        }
        if (creating) {
            ExpenseForm(access, vm, o, onDone = { claim ->
                creating = false
                if (claim != null) {
                    notice = if (claim.status == "draft") {
                        tr("Entwurf gespeichert.")
                    } else {
                        tr("Auslage {number} ist eingereicht. Du bekommst Bescheid, sobald sie geprüft ist.", "number" to claim.number)
                    }
                }
                attempt++
            })
        } else if (o.options.maySubmit) {
            Button(onClick = {
                notice = null
                creating = true
            }) { Text(tr("Auslage melden")) }
        }
        if (o.claims.isEmpty() && !creating) {
            Text(tr("Hast du etwas für den Verein selbst bezahlt? Melde es hier mit einem Foto des Belegs, die Kasse erstattet dir den Betrag."))
        }
        o.claims.forEach { c ->
            OutlinedCard(onClick = { open = c.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    Text(
                        listOf(c.number.ifBlank { tr("Entwurf") }, c.purpose.ifBlank { tr("ohne Zweck") }).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.asHeading(),
                    )
                    Text(expenseLine(c), style = MaterialTheme.typography.bodySmall)
                    if (c.asksMe) Text(tr("Die Kasse hat eine Rückfrage."), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Entwurf einer Auslage im gespeicherten Zustand der Ansicht (Drehen, Prozessende). */
/** Datum des Formulars (Landesschreibweise) für die Schnittstelle als JJJJ-MM-TT; Unlesbares bleibt, der Server meldet es. */
internal fun ExpenseDraft.withIsoDate(): ExpenseDraft = copy(date = parseGermanDate(date) ?: date)

internal val ExpenseDraftSaver = listSaver<ExpenseDraft, String>(
    save = { listOf(it.kind, it.purpose, it.date, it.route, it.km, it.payout, it.holder, it.iban) + it.amounts },
    restore = {
        ExpenseDraft(
            kind = it[0], purpose = it[1], date = it[2], route = it[3], km = it[4], payout = it[5],
            holder = it[6], iban = it[7], amounts = it.drop(8).ifEmpty { listOf("") },
        )
    },
)

/** Fotos des Belegs: Kamera oder Galerie, verkleinert als JPEG im Speicher. */
@Composable
private fun rememberReceiptPhotos(onPhoto: (ByteArray) -> Unit, onFail: (String) -> Unit): Pair<() -> Unit, () -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    fun take(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                runCatching { PhotoPrep.load(context, uri)?.let(PhotoPrep::jpeg) }.getOrNull()
            }
            // Kamerafoto sofort aus dem Cache löschen
            withContext(Dispatchers.IO) {
                File(context.cacheDir, "fotos").listFiles { f -> f.name.startsWith("auslage-") }?.forEach { it.delete() }
            }
            if (jpeg == null) onFail(tr("Das Foto ließ sich nicht lesen.")) else onPhoto(jpeg)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) take(photoUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> take(uri) }
    fun startCamera() {
        val dir = File(context.cacheDir, "fotos").apply { mkdirs() }
        val file = File(dir, "auslage-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.dateien", file)
        photoUri = uri
        camera.launch(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else onFail(tr("Ohne Kamerafreigabe bitte ein Foto aus der Galerie wählen."))
    }
    val shoot = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }
    val pick = { gallery.launch("image/*") }
    return shoot to pick
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseForm(access: PortalAccess, vm: AppViewModel, overview: ExpenseOverview, onDone: (ExpenseClaim?) -> Unit) {
    val options = overview.options
    // Die Angaben überstehen Drehen und Prozessende (AND-03); Fotos liegen nur im Speicher
    var draft by rememberSaveable(access.id, stateSaver = ExpenseDraftSaver) {
        mutableStateOf(
            ExpenseDraft(
                // Datum in Landesschreibweise, übertragen wird weiter ISO (AND-10)
                date = LocalDate.now().let { germanDate(it.year, it.monthValue, it.dayOfMonth) },
                holder = options.payee?.holder.orEmpty(),
                iban = options.payee?.iban.orEmpty(),
            ),
        )
    }
    val photos = remember(access.id) { mutableStateListOf<ByteArray>() }
    // Ein Schlüssel je Formular: erneutes Senden nach einem Abbruch legt nichts doppelt an
    val key = remember(access.id) { UUID.randomUUID().toString() }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Problem?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    // Eingaben und Belegfotos zählen als Entwurf: Zurück, Reiterwechsel und Abbrechen fragen nach
    val saved = rememberFormDraft(draft.toString(), photos.size, busy = busy, retainOnDispose = false)
    val leave = LocalFormLeaveGuard.current
    val (shoot, pick) = rememberReceiptPhotos(
        onPhoto = { if (photos.size < 10) photos.add(it) },
        onFail = { problem = Problem(it) },
    )
    fun send(submit: Boolean) {
        val api = vm.api() ?: return
        if (busy) return
        busy = true
        problem = null
        scope.launch {
            try {
                val claim = api.createExpense(access.id, draft.withIsoDate().fields(submit, ::parseAmount), photos.toList(), key)
                photos.clear()
                saved()
                onDone(claim)
            } catch (e: ApiException) {
                if (e.status == 401) {
                    vm.deviceRevoked(e)
                } else {
                    fieldErrors = e.fieldErrors
                    problem = if (e.network != null) {
                        Problem(tr("Ohne Verbindung: Die Angaben bleiben hier stehen. Bitte später erneut senden."), offline = true)
                    } else {
                        e.problem()
                    }
                }
            } finally {
                busy = false
            }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Text(tr("Neue Auslage"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            if (options.mileageEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    FilterChip(draft.kind == "expense", { draft = draft.copy(kind = "expense") }, { Text(tr("Auslage mit Beleg")) })
                    FilterChip(draft.kind == "mileage", { draft = draft.copy(kind = "mileage") }, { Text(tr("Fahrtkosten")) })
                }
            }
            OutlinedTextField(
                draft.purpose, { draft = draft.copy(purpose = it.take(1000)) },
                label = { Text(tr("Wofür, für wen?")) },
                isError = "purpose" in fieldErrors,
                supportingText = fieldErrors["purpose"]?.let { { Text(it) } },
                keyboardOptions = FREITEXT,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                draft.date, { draft = draft.copy(date = it.take(10)) },
                label = { Text(if (draft.kind == "mileage") tr("Datum der Fahrt") else tr("Datum des Kaufs")) },
                isError = "purchase_date" in fieldErrors || (draft.date.isNotBlank() && parseGermanDate(draft.date) == null),
                supportingText = { Text(fieldErrors["purchase_date"] ?: tr("TT.MM.JJJJ")) },
                trailingIcon = { DatePickButton(draft.date) { draft = draft.copy(date = it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.kind == "mileage") {
                OutlinedTextField(
                    draft.route, { draft = draft.copy(route = it.take(300)) },
                    label = { Text(tr("Start, Ziel und Anlass")) },
                    isError = "route" in fieldErrors,
                    keyboardOptions = FREITEXT,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    draft.km, { draft = draft.copy(km = it.take(8)) },
                    label = { Text(tr("Gefahrene Kilometer (hin und zurück)")) },
                    supportingText = { Text(tr("{rate} je km", "rate" to formatMoney(options.kmRate))) },
                    isError = "km" in fieldErrors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                draft.amounts.forEachIndexed { i, amount ->
                    OutlinedTextField(
                        amount,
                        { v -> draft = draft.copy(amounts = draft.amounts.mapIndexed { j, a -> if (j == i) v.take(14) else a }) },
                        label = { Text(tr("Betrag in €")) },
                        isError = "lines" in fieldErrors,
                        supportingText = if (i == draft.amounts.lastIndex) fieldErrors["lines"]?.let { { Text(it) } } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (draft.amounts.size < 20) {
                    TextButton(onClick = { draft = draft.copy(amounts = draft.amounts + "") }) { Text(tr("Weitere Position")) }
                }
                Text(tr("Belege"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
                Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    Button(onClick = shoot, enabled = photos.size < 10) { Text(tr("Foto aufnehmen")) }
                    OutlinedButton(onClick = pick, enabled = photos.size < 10) { Text(tr("Aus der Galerie")) }
                }
                PhotoStrip(photos)
                fieldErrors["files"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Text(tr("Auszahlung"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                FilterChip(draft.payout == "bank", { draft = draft.copy(payout = "bank") }, { Text(tr("Überweisung")) })
                FilterChip(draft.payout == "cash", { draft = draft.copy(payout = "cash") }, { Text(tr("bar")) })
            }
            if (draft.payout == "bank") {
                OutlinedTextField(
                    draft.holder, { draft = draft.copy(holder = it.take(70)) },
                    label = { Text(tr("Kontoinhaber")) },
                    keyboardOptions = keyboardFor("holder"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    draft.iban, { draft = draft.copy(iban = it.uppercase().take(42)) },
                    label = { Text(tr("IBAN")) },
                    isError = "payee" in fieldErrors,
                    supportingText = fieldErrors["payee"]?.let { { Text(it) } },
                    keyboardOptions = IBAN_TASTATUR,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            problem?.let { ProblemNotice(it, onRetry = null, inset = false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Button(onClick = { send(true) }, enabled = !busy) { Text(tr("Einreichen")) }
                OutlinedButton(onClick = { send(false) }, enabled = !busy) { Text(tr("Als Entwurf speichern")) }
                TextButton(onClick = { if (leave == null) onDone(null) else leave.request { onDone(null) } }, enabled = !busy) { Text(tr("Abbrechen")) }
            }
        }
    }
}

// Umbrechend statt einer festen Zeile: alle zehn Belege samt „Entfernen“ bleiben erreichbar (AND-13)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoStrip(photos: MutableList<ByteArray>) {
    if (photos.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
        photos.forEachIndexed { i, jpeg ->
            val bitmap = remember(jpeg) { android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) }
            Column {
                bitmap?.let { Thumb(it, tr("Beleg {n}", "n" to i + 1)) }
                TextButton(onClick = { photos.removeAt(i) }) { Text(tr("Entfernen")) }
            }
        }
    }
}

@Composable
private fun Thumb(bitmap: Bitmap, description: String) {
    Image(bitmap.asImageBitmap(), contentDescription = description, modifier = Modifier.size(72.dp))
}

@Composable
private fun ExpenseDetail(access: PortalAccess, vm: AppViewModel, id: Int, onBack: () -> Unit) {
    var claim by remember(id) { mutableStateOf<ExpenseClaim?>(null) }
    var error by remember(id) { mutableStateOf<Problem?>(null) }
    var notice by remember(id) { mutableStateOf<String?>(null) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var answer by rememberSaveable(id) { mutableStateOf("") }
    var confirmWithdraw by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            claim = api.memberExpense(access.id, id)
            error = null
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    fun act(done: String, leave: Boolean = false, action: suspend (de.hubiplan.app.data.Api) -> Unit) {
        val api = vm.api() ?: return
        if (busy) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                action(api)
                notice = done
                if (leave) onBack()
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
            } finally {
                busy = false
                attempt++
            }
        }
    }
    val (shoot, pick) = rememberReceiptPhotos(
        onPhoto = { jpeg -> act(tr("Beleg hinzugefügt.")) { it.addExpensePhoto(access.id, id, jpeg) } },
        onFail = { error = Problem(it) },
    )
    SectionColumn {
        TextButton(onClick = onBack) { Text(tr("Zurück zu den Auslagen")) }
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        ActionNotice(notice) { notice = null }
        val c = claim
        if (c == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        Text(listOf(c.number.ifBlank { tr("Entwurf") }, c.statusLabel).joinToString(" · "), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        Text(c.purpose)
        Text(tr("Summe: {amount}", "amount" to formatMoney(c.total)))
        if (c.approvedAmount != null && c.approvedAmount != c.total) {
            Text(tr("Freigegeben: {amount} ({reason})", "amount" to formatMoney(c.approvedAmount), "reason" to c.reduceReason))
        }
        if (c.rejectReason.isNotBlank()) Text(tr("Grund der Ablehnung: {reason}", "reason" to c.rejectReason))
        c.purchaseDate?.let { Text(tr("Datum: {day}", "day" to formatDate(it))) }
        Text(trn("1 Beleg", "{n} Belege", c.files.size), style = MaterialTheme.typography.bodySmall)
        if (c.asksMe) {
            c.question?.let { Text(tr("Rückfrage der Kasse: {text}", "text" to it), color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(answer, { answer = it.take(2000) }, label = { Text(tr("Deine Antwort")) }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { act(tr("Antwort gesendet. Die Kasse prüft erneut.")) { it.expenseAction(access.id, id, "answer", answer.trim()) }.also { answer = "" } },
                enabled = !busy && answer.isNotBlank(),
            ) { Text(tr("Antwort senden")) }
        }
        if (c.status == "draft" && c.problems.isNotEmpty()) {
            Text(tr("Zum Einreichen fehlt noch: {list}", "list" to c.problems.values.joinToString(" ")), style = MaterialTheme.typography.bodySmall)
        }
        if (c.canAddFile) {
            Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                OutlinedButton(onClick = shoot, enabled = !busy) { Text(tr("Foto aufnehmen")) }
                OutlinedButton(onClick = pick, enabled = !busy) { Text(tr("Aus der Galerie")) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            if (c.status == "draft") {
                Button(onClick = { act(tr("Auslage eingereicht.")) { it.expenseAction(access.id, id, "submit") } }, enabled = !busy) { Text(tr("Einreichen")) }
            }
            if (c.canWithdraw) {
                TextButton(onClick = { confirmWithdraw = true }, enabled = !busy) { Text(if (c.isLooseDraft) tr("Löschen") else tr("Zurückziehen")) }
            }
        }
        if (c.events.isNotEmpty()) {
            Text(tr("Verlauf"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
            c.events.forEach { e ->
                Text(listOf(formatDate(e.at), e.label, e.who, e.text).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (confirmWithdraw) {
            AlertDialog(
                onDismissRequest = { confirmWithdraw = false },
                title = { Text(if (c.isLooseDraft) tr("Entwurf löschen?") else tr("Auslage zurückziehen?")) },
                text = { Text(c.purpose) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmWithdraw = false
                        if (c.isLooseDraft) {
                            act(tr("Entwurf gelöscht."), leave = true) { it.deleteExpense(access.id, id) }
                        } else {
                            act(tr("Auslage zurückgezogen.")) { it.expenseAction(access.id, id, "withdraw") }
                        }
                    }) { Text(if (c.isLooseDraft) tr("Löschen") else tr("Zurückziehen")) }
                },
                dismissButton = { TextButton(onClick = { confirmWithdraw = false }) { Text(tr("Abbrechen")) } },
            )
        }
    }
}
