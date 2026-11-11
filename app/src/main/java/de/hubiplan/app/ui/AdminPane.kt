package de.hubiplan.app.ui

import androidx.compose.ui.autofill.ContentType
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.hubiplan.app.data.AdminClient
import de.hubiplan.app.data.AdminTask
import de.hubiplan.app.data.AdminUnit
import de.hubiplan.app.data.LastUnitStore
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.CardCheck
import de.hubiplan.app.data.DraftStore
import de.hubiplan.app.data.ReceiptStore
import kotlinx.coroutines.launch
import de.hubiplan.app.tr

/** Derselbe Zustandshalter für die produktive Sicherheitssperre und deren Gerätetest. */
@Composable
internal fun rememberAdminDraftState(key: String): androidx.compose.runtime.saveable.SaveableStateHolder {
    val retained = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    rememberFormDraft(Unit, onDiscard = { retained.removeState(key) })
    return retained
}

/**
 * Reiter „Verwaltung“ (Schritt A8): nur für Konten mit Verwaltungsrechten. Erst nach dem
 * Freischalten (Passwort + 2FA) und jedes Mal nach Fingerabdruck/PIN; keine Bildschirmfotos.
 */
@Composable
fun AdminPane(main: Screen.Main, vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.admin.collectAsStateWithLifecycle()
    val retainedKey = "${main.account.server}-${main.account.deviceId}"
    val retained = rememberAdminDraftState(retainedKey)
    // Im Verwaltungsbereich keine Bildschirmfotos und keine Vorschau im App-Wechsler
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    // Beim Öffnen und nach Rückkehr in die App: nach 5 Minuten Pause neu entsperren
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.checkAdminIdle()
                resumed++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Auch bei offener Ansicht im Vordergrund nach 5 Minuten Pause sperren (AND-S01)
    LaunchedEffect(Unit) {
        while (true) {
            vm.checkAdminIdle()
            kotlinx.coroutines.delay(15_000)
        }
    }
    val overview = state.overview
    if (overview == null || overview.allUnits.isEmpty()) {
        Notice(tr("Die Aufgabe oder der Verwaltungsbereich ist nicht verfügbar. Bitte Verbindung und Berechtigung prüfen."), tr("Erneut laden"), vm::reload)
        return
    }
    // Nach der Rückkehr aus den Einstellungen erneut prüfen (UX-Audit 08.11. A-17)
    val screenLock = remember(resumed) { DeviceUnlock.available(context) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            !overview.twoFactor -> Notice(
                tr("Für die Verwaltung in der App ist die Zwei-Faktor-Anmeldung nötig. Bitte im Browser unter „Mein Konto › Anmeldung & Sicherheit“ einrichten, danach hier freischalten."),
                tr("Mein Konto öffnen"),
            ) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${main.account.server}/mein-konto/anmeldung"))) }
            !screenLock -> Notice(
                tr("Für die Verwaltung braucht das Telefon eine Bildschirmsperre (PIN, Muster oder Fingerabdruck)."),
                tr("Bildschirmsperre einrichten"),
            ) { DeviceUnlock.openSetup(context) }
            !state.sealed -> UnlockForm(state, vm)
            state.token == null -> Column(
                Modifier.widthIn(max = 520.dp).padding(Abstand.gross),
                verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
            ) {
                Text(tr("Verwaltung"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
                Text(tr("Zum Öffnen mit Fingerabdruck, Gesicht oder Geräte-PIN entsperren."))
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
                Button(onClick = {
                    DeviceUnlock.ask(context, tr("Verwaltung öffnen"), onSuccess = vm::openAdmin, onError = {})
                }) { Text(tr("Entsperren")) }
                LaunchedEffect(Unit) {
                    DeviceUnlock.ask(context, tr("Verwaltung öffnen"), onSuccess = vm::openAdmin, onError = {})
                }
            }
            else -> retained.SaveableStateProvider(retainedKey) { AdminHome(overview.allUnits, overview.until, main, vm) }
        }
    }
}

@Composable
private fun Notice(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.widthIn(max = 520.dp).padding(Abstand.gross), verticalArrangement = Arrangement.spacedBy(Abstand.mittel)) {
        Text(tr("Verwaltung"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
        Text(text)
        action?.let { OutlinedButton(onClick = onAction) { Text(it) } }
    }
}

@Composable
private fun UnlockForm(state: AdminState, vm: AppViewModel) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Abstand.gross),
        verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
    ) {
        Text(tr("Verwaltung freischalten"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
        Text(
            tr("Für Mitglieder, Termine und Kasse in der App bitte mit Passwort und Zwei-Faktor-Code bestätigen. Die Freischaltung gilt 30 Tage auf diesem Telefon; geöffnet wird jedes Mal mit Fingerabdruck oder PIN."),
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(tr("Passwort")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().autofill(ContentType.Password),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(8) },
            label = { Text(tr("Code aus der Authenticator-App")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                error = null
                // Erst das Telefon entsperren: Der Tresor für den Schlüssel braucht es
                DeviceUnlock.ask(
                    context,
                    tr("Verwaltung freischalten"),
                    onSuccess = { vm.unlockAdmin(password, code) },
                    onError = { error = it },
                )
            },
            enabled = !state.busy && password.isNotEmpty() && code.length >= 6,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr("Freischalten")) }
    }
}

/**
 * Bereiche der Verwaltung. Sie stehen untereinander in einer Startliste statt als waagerecht
 * scrollende Filterchips (UX-Audit 05.11. A-03); „Zu erledigen“ hebt sich von „Aufgaben“ ab (Begriff wie auf der Web-Startseite).
 */
internal enum class Section(val label: String, val hint: String) {
    TASKS(tr("Zu erledigen"), tr("Datenänderungen, Fotos und Termine, die auf dich warten")),
    // „Aufgaben verteilen“: der Reiter „Aufgaben“ zeigt die eigenen (UX-Audit 08.11. A-04)
    TODOS(tr("Aufgaben verteilen"), tr("Aufgaben anlegen, verteilen und erledigen")),
    MEMBERS(tr("Mitglieder"), tr("Suchen, anrufen, Mitgliedsakte öffnen")),
    EVENTS(tr("Termine"), tr("Termine anlegen, Zu- und Absagen eintragen")),
    FINANCE(tr("Kassenbuch"), tr("Buchungen und Quittungen")),
    BILLING(tr("Rechnungen"), tr("Offene und bezahlte Rechnungen der Mitglieder")),
    OCCASIONS(tr("Jubiläen und Geburtstage"), tr("Geburtstage und Jubiläen der nächsten Wochen")),
    COMPETITIONS(tr("Wettbewerbe"), tr("Schießen im Standmodus erfassen, auch ohne Empfang")),
    PARTNERS(tr("Geschäftspartner"), tr("Lieferanten, Sponsoren und Ansprechpartner nachschlagen")),
}

/**
 * Bereiche je nach Rechten; Untereinheiten (Zugführung) haben Aufgaben, Mitglieder und Termine,
 * aber keine gemeinsame Liste offener Punkte.
 */
internal fun sectionsFor(unit: AdminUnit): List<Section> {
    val p = unit.permissions
    if (unit.isDepartment) {
        return listOfNotNull(
            Section.TODOS.takeIf { showsTodos(unit) },
            Section.MEMBERS.takeIf { showsMembers(unit) },
            Section.EVENTS.takeIf { showsEvents(unit) },
        )
    }
    val members = showsMembers(unit)
    return listOfNotNull(
        Section.TASKS,
        Section.TODOS.takeIf { showsTodos(unit) },
        Section.MEMBERS.takeIf { members },
        Section.EVENTS.takeIf { showsEvents(unit) },
        Section.FINANCE.takeIf { FINANCE_VIEW in p || FINANCE_EDIT in p },
        Section.BILLING.takeIf { BILLING_VIEW in p || BILLING_EDIT in p },
        Section.OCCASIONS.takeIf { members },
        Section.COMPETITIONS.takeIf { showsCompetitions(unit) },
        Section.PARTNERS.takeIf { showsPartners(unit) },
    )
}

@Composable
private fun AdminHome(units: List<AdminUnit>, until: String?, main: Screen.Main, vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val accountKey = "${main.account.server}-${main.account.deviceId}"
    // Start in der zuletzt gewählten Einheit dieses Kontos, sonst in der ersten
    var unitKey by rememberSaveable(accountKey) {
        mutableStateOf(LastUnitStore.startUnit(units, LastUnitStore.get(context, accountKey)).key)
    }
    val unit = units.firstOrNull { it.key == unitKey } ?: units.first()
    val client = remember(unit.key, main.account.server, main.account.deviceId) { vm.adminApi()?.let { AdminClient(it, unit.id, vm::useAdmin, unit.scope, "${main.account.server}-${main.account.deviceId}") } } ?: return
    val errors = remember { AdminErrors(onLocked = vm::adminRejected) }
    val sections = sectionsFor(unit)
    val hasMembers = !unit.isDepartment && showsMembers(unit)
    // Entwürfe der Kasse: je Konto (Server und Gerät)
    val drafts = remember(main.account.server, main.account.deviceId) {
        DraftStore(context, "${main.account.server}-${main.account.deviceId}")
    }
    val receiptStore = remember(main.account.server, main.account.deviceId) {
        ReceiptStore(context, "${main.account.server}-${main.account.deviceId}")
    }
    // null = Startliste der Verwaltung
    var section by rememberSaveable(unit.key) { mutableStateOf<Section?>(null) }
    var todoId by remember(unit.key) { mutableStateOf<Int?>(null) }
    var creatingTodo by remember(unit.key) { mutableStateOf(false) }
    var member by rememberSaveable(unit.key) { mutableStateOf<Int?>(null) }
    var requests by rememberSaveable(unit.key) { mutableStateOf(false) }
    var admitting by rememberSaveable(unit.key) { mutableStateOf(false) }
    var eventId by rememberSaveable(unit.key) { mutableStateOf<Int?>(null) }
    var eventStart by rememberSaveable(unit.key) { mutableStateOf<String?>(null) }
    var creatingEvent by rememberSaveable(unit.key) { mutableStateOf<Boolean?>(null) }
    var bookingId by rememberSaveable(unit.key) { mutableStateOf<Int?>(null) }
    var newBooking by rememberSaveable(unit.key) { mutableStateOf(false) }
    var newReceipt by rememberSaveable(unit.key) { mutableStateOf(false) }
    var invoiceId by rememberSaveable(unit.key) { mutableStateOf<Int?>(null) }
    var memberSearch by rememberSaveable(unit.key) { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var card by remember { mutableStateOf<CardCheck?>(null) }
    var cardError by remember { mutableStateOf<String?>(null) }

    // Nach jeder Änderung: Hinweis mit „Rückgängig“ (wie im Browser)
    val onChanged: OnChanged = { text, changeSet ->
        scope.launch {
            val result = snackbar.showSnackbar(
                text,
                actionLabel = if (changeSet != null) tr("Rückgängig") else null,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed && changeSet != null) {
                try {
                    client.undo(changeSet)
                    reload++
                    snackbar.showSnackbar(tr("Rückgängig gemacht"))
                } catch (e: ApiException) {
                    snackbar.showSnackbar(errors.message(e))
                }
            }
        }
    }
    fun openStandLink(code: String): Boolean {
        val link = de.hubiplan.app.data.StandLink.parse(code) ?: return false
        if (link.server != main.account.server) {
            cardError = tr("Der QR-Code gehört zu einer anderen HUBIplan-Instanz.")
        } else {
            vm.openStand(StandSession(link.url, link.server, tr("Standmodus")))
        }
        return true
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents ?: return@rememberLauncherForActivityResult
        // Stand-QR (auch beim Ausweis prüfen erkannt): Standmodus öffnen
        if (openStandLink(code)) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                card = client.checkCard(code)
            } catch (e: ApiException) {
                cardError = errors.message(e)
            }
        }
    }

    // Android-Zurück geht eine Ebene zurück wie der Pfeil in der Kopfleiste (UX-Audit 31.10. A01); aus einem
    // Formular erst nach Rückfrage, damit Eingaben nicht unbemerkt verloren gehen.
    val leave = LocalFormLeaveGuard.current
    fun requestLeave(action: () -> Unit) { if (leave == null) action() else leave.request(action) }
    val inForm = newBooking || newReceipt || admitting || creatingEvent != null || creatingTodo
    val nested = inForm || invoiceId != null || bookingId != null || eventId != null || member != null || requests || todoId != null
    // Aus einem Bereich führt Zurück zur Startliste, erst von dort zum Startreiter (A-03/A-04).
    androidx.activity.compose.BackHandler(enabled = nested || section != null) {
        requestLeave {
            when {
                creatingTodo -> creatingTodo = false
                todoId != null -> { todoId = null; reload++ }
                invoiceId != null -> { invoiceId = null; reload++ }
                newReceipt -> { newReceipt = false; reload++ }
                newBooking -> newBooking = false
                bookingId != null -> bookingId = null
                creatingEvent != null -> creatingEvent = null
                eventId != null -> { eventId = null; eventStart = null }
                admitting -> admitting = false
                member != null -> member = null
                requests -> requests = false
                else -> section = null
            }
        }
    }
    val pendingTodo by vm.openTodo.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTodo, leave?.busy) {
        val pending = pendingTodo ?: return@LaunchedEffect
        if (!pending.ready) return@LaunchedEffect // Entscheidung vor einem Kontowechsel gehört dem MainScreen.
        if (pending.target.scope == "p") return@LaunchedEffect

        if (pending.accountId != de.hubiplan.app.sync.RsvpActionReceiver.accountId(main.account)) {
            vm.todoHandled()
            return@LaunchedEffect
        }
        val targetUnit = units.firstOrNull { pending.target.permittedBy(it) }
        if (targetUnit == null) {
            vm.todoHandled()
            snackbar.showSnackbar(tr("Die Aufgabe ist nicht mehr verfügbar oder nicht freigegeben."))
        } else {
            val navigate = { creatingTodo = false; unitKey = targetUnit.key; vm.todoHandled(); vm.selectTodo(pending.target) }
            if (leave == null) navigate() else leave.request(action = navigate, onStay = vm::todoHandled)
        }
    }
    val selectedTodo by vm.selectedTodo.collectAsStateWithLifecycle()
    LaunchedEffect(selectedTodo, unit.key, leave?.busy) {
        val target = selectedTodo ?: return@LaunchedEffect
        if (target.unitKey == unit.key) {
            val navigate = {
                member = null; requests = false; admitting = false; eventId = null; eventStart = null; creatingEvent = null
                bookingId = null; newBooking = false; newReceipt = false; invoiceId = null; creatingTodo = false
                section = Section.TODOS; todoId = target.todoId; vm.selectTodo(null)
            }
            if (leave == null) navigate() else leave.request(action = navigate, onStay = { vm.selectTodo(null) })
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            UnitPicker(units, unit) { picked ->
                if (picked.key != unit.key) {
                    requestLeave {
                        unitKey = picked.key
                        LastUnitStore.put(context, accountKey, picked.key)
                    }
                }
            }
            val current = section
            // Bereich: Pfeil zurück zur Übersicht und Name des Bereichs in der Kopfleiste (A-01)
            if (!nested && current != null) BackHead(current.label) { requestLeave { section = null } }
            HorizontalDivider()
            cardError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true).padding(Abstand.rand)) }
            val open = member
            val openEvent = eventId
            val rsvpAllowed = creatingEvent
            val openBooking = bookingId
            val openInvoice = invoiceId
            val openTodo = todoId
            // Listen der Verwaltung lassen sich herunterziehen (UX-Audit 08.11. A-02); Formulare
            // und Akten nicht, damit eine Geste keine Eingaben verwirft
            val views: @Composable () -> Unit = {
                when {
                    creatingTodo && showsTodos(unit) -> TodoCreateScreen(client, errors, onBack = { requestLeave { creatingTodo = false } }) { id, changeSet ->
                        creatingTodo = false
                        todoId = id
                        onChanged(tr("Aufgabe angelegt"), changeSet)
                    }
                    openTodo != null && showsTodos(unit) -> TodoScreen(client, openTodo, errors, reload, onBack = { todoId = null; reload++ }, onChanged = onChanged)
                    current == null && !nested -> AdminStart(
                        client = client,
                        sections = sections,
                        until = until,
                        vm = vm,
                        reloadKey = reload,
                        onScan = if (hasMembers) {
                            {
                                scanner.launch(
                                    ScanOptions()
                                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        .setPrompt(tr("Mitgliedsausweis scannen"))
                                        .setBeepEnabled(false)
                                        .setOrientationLocked(false),
                                )
                            }
                        } else {
                            null
                        },
                        onOpen = { section = it },
                    )
                    section == Section.TODOS && showsTodos(unit) -> TodoListScreen(client, errors, reload, onNew = { creatingTodo = true }, onLock = vm::lockAdmin, onOpen = { todoId = it })
                    openInvoice != null -> InvoiceScreen(client, openInvoice, BILLING_EDIT in unit.permissions, errors, onBack = {
                        invoiceId = null
                        reload++
                    }, onChanged = onChanged)
                    newReceipt -> ReceiptScreen(client, unit.name, receiptStore, errors, onBack = { requestLeave { newReceipt = false; reload++ } }, onChanged = onChanged)
                    newBooking -> BookingForm(client, unit.name, drafts, errors, onBack = { requestLeave { newBooking = false } }) { text ->
                        newBooking = false
                        reload++
                        onChanged(text, null)
                    }
                    openBooking != null -> BookingScreen(client, openBooking, FINANCE_EDIT in unit.permissions, errors, onBack = { bookingId = null }, onChanged = onChanged)
                    rsvpAllowed != null -> EventCreateScreen(client, errors, rsvpAllowed, onBack = { requestLeave { creatingEvent = null } }) { id, text, changeSet ->
                        creatingEvent = null
                        eventId = id
                        eventStart = null
                        onChanged(text, changeSet)
                    }
                    openEvent != null -> EventScreen(client, openEvent, eventStart, errors, onBack = {
                        eventId = null
                        eventStart = null
                    }, onChanged = onChanged, reload = reload)
                    admitting -> AdmitScreen(client, errors, onBack = { requestLeave { admitting = false } }) { id, text ->
                        admitting = false
                        member = id
                        onChanged(text, null)
                    }
                    open != null && unit.isDepartment -> DeptMemberScreen(client, unit.permissions, open, errors, onBack = { member = null }, onChanged = onChanged, reload = reload)
                    open != null -> MemberScreen(client, unit.permissions, open, errors, onBack = { member = null }, onChanged = onChanged, reload = reload)
                    requests -> ChangeRequestsScreen(client, errors, onBack = { requests = false }, onOpen = {
                        requests = false
                        member = it
                    }, onChanged = onChanged)
                    section == Section.MEMBERS && section in sections -> MemberList(
                        client,
                        errors,
                        onAdmit = if (!unit.isDepartment && MEMBERS_EDIT in unit.permissions) {
                            { admitting = true }
                        } else {
                            null
                        },
                        initialSearch = memberSearch,
                        onSearch = { memberSearch = it },
                    ) { member = it }
                    section == Section.OCCASIONS && hasMembers -> OccasionsSection(client, errors, reload) { member = it }
                    section == Section.FINANCE && section in sections -> FinanceSection(
                        client,
                        canEdit = FINANCE_EDIT in unit.permissions,
                        store = drafts,
                        receiptStore = receiptStore,
                        errors = errors,
                        reload = reload,
                        onNew = { newBooking = true },
                        onReceipt = { newReceipt = true },
                        onOpen = { bookingId = it },
                        onChanged = onChanged,
                    )
                    section == Section.BILLING && section in sections -> BillingSection(client, errors, reload) { invoiceId = it }
                    section == Section.COMPETITIONS && section in sections -> CompetitionsSection(
                        client,
                        server = main.account.server,
                        deviceName = android.os.Build.MODEL.take(60),
                        errors = errors,
                        reload = reload,
                        onScan = {
                            scanner.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt(tr("Stand-QR scannen"))
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false),
                            )
                        },
                        onOpenStand = vm::openStand,
                    )
                    section == Section.PARTNERS && section in sections -> PartnersSection(client, errors, reload)
                    section == Section.EVENTS -> EventsSection(client, errors, reload, onCreate = { creatingEvent = it }) { o ->
                        eventId = o.eventId
                        eventStart = if (o.isSeries) o.originalStart ?: o.startsAt else null
                    }
                    else -> TaskList(client, errors, until, vm, reload) { task ->
                        when {
                            task.kind == "change_request" -> requests = true
                            task.kind == "todo" -> { if (showsTodos(unit)) todoId = task.ref }
                            task.kind == "photo" && task.membership != null -> member = task.membership
                            task.kind == "event" && task.ref != null -> {
                                eventId = task.ref
                                eventStart = null
                            }
                            // Wechselanfragen: im Browser
                            else -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(main.account.server + task.link)))
                        }
                    }
                }
            }
            if (nested) {
                views()
            } else {
                Refreshable(refreshing, onRefresh = {
                    refreshing = true
                    reload++
                    // Die Bereiche zeigen ihr eigenes Laden; die Geste bestätigt nur kurz
                    scope.launch { kotlinx.coroutines.delay(600); refreshing = false }
                }) { views() }
            }
        }
    }
    card?.let { check ->
        val server = main.account.server
        CardResultDialog(
            check,
            loadPublic = { path -> Api(server).image(path) },
            onOpen = {
                card = null
                member = it
            },
            onDismiss = { card = null },
        )
    }
}

@Composable
private fun TaskList(
    client: AdminClient,
    errors: AdminErrors,
    until: String?,
    vm: AppViewModel,
    reloadKey: Int,
    onOpen: (AdminTask) -> Unit,
) {
    var tasks by remember(client) { mutableStateOf<List<AdminTask>?>(null) }
    var error by remember(client) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(client, reload, reloadKey) {
        tasks = null
        error = null
        val api = vm.adminApi() ?: return@LaunchedEffect
        // Untereinheiten nutzen den eigenen Bereich „Aufgaben verteilen“, keine gemischte Übersicht.
        if (client.scope == "d") {
            tasks = emptyList()
            return@LaunchedEffect
        }
        try {
            tasks = api.adminTasks(client.unitId).items
            error = null
        } catch (e: ApiException) {
            error = errors.message(e)
        }
    }
    Column(Modifier.fillMaxSize()) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true).padding(Abstand.rand)) }
        val list = tasks
        when {
            list == null && error == null -> Loading()
            // Fehler ohne Liste ist nicht „keine Aufgaben“ (UX-Audit 31.10. A07)
            list == null -> Column(Modifier.padding(horizontal = Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                TextButton(onClick = { reload++ }) { Text(tr("Erneut laden")) }
                Footer(until, vm) { reload++ }
            }
            list.isEmpty() -> Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                Text(tr("Nichts zu erledigen."))
                Footer(until, vm) { reload++ }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list) { task ->
                    ListItem(
                        overlineContent = { Text(task.kindLabel) },
                        headlineContent = { Text(task.title) },
                        supportingContent = { Text(task.detail) },
                        modifier = Modifier.openRow { onOpen(task) },
                    )
                    HorizontalDivider()
                }
                item { Box(Modifier.padding(Abstand.rand)) { Footer(until, vm) { reload++ } } }
            }
        }
    }
}

/**
 * Startliste der Verwaltung (A-03): „Ausweis prüfen“ ganz oben, weil es am Einlass schnell gehen
 * muss, darunter „Zu erledigen“ mit Anzahl und alle Bereiche als große Zeilen, ohne seitliches
 * Wischen. TalkBack sagt „Schaltfläche“ und „Öffnen“, nicht „Filter“.
 */
@Composable
private fun AdminStart(
    client: AdminClient,
    sections: List<Section>,
    until: String?,
    vm: AppViewModel,
    reloadKey: Int,
    onScan: (() -> Unit)?,
    onOpen: (Section) -> Unit,
) {
    var reload by remember { mutableIntStateOf(0) }
    var openCount by remember(client) { mutableStateOf<Int?>(null) }
    LaunchedEffect(client, reload, reloadKey) {
        if (Section.TASKS !in sections) return@LaunchedEffect
        val api = vm.adminApi() ?: return@LaunchedEffect
        // Die Anzahl ist nur ein Hinweis; ein Fehler zeigt sich beim Öffnen der Liste.
        openCount = try { api.adminTasks(client.unitId).items.size } catch (_: ApiException) { null }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (onScan != null) {
            item {
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Abstand.rand, vertical = Abstand.mittel).heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Filled.AccountBox, contentDescription = null, modifier = Modifier.padding(end = Abstand.klein))
                    Text(tr("Ausweis prüfen"))
                }
            }
        }
        item {
            Text(
                tr("Was möchtest du tun?"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.asHeading().padding(start = Abstand.rand, top = Abstand.mittel, end = Abstand.rand, bottom = Abstand.eng),
            )
        }
        if (sections.isEmpty()) {
            item { Text(tr("Für diese Einheit sind in der App keine Bereiche freigegeben."), modifier = Modifier.padding(Abstand.rand)) }
        }
        items(sections) { s ->
            val count = openCount
            ListItem(
                leadingContent = { Icon(sectionIcon(s), contentDescription = null) },
                headlineContent = {
                    Text(if (s == Section.TASKS && count != null) tr("Zu erledigen ({count})", "count" to count) else s.label)
                },
                supportingContent = { Text(s.hint) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.openRow { onOpen(s) },
            )
            HorizontalDivider()
        }
        item { Box(Modifier.padding(Abstand.rand)) { Footer(until, vm) { reload++ } } }
    }
}

/** Symbole nach `docs/begriffe.md` (UX-Audit 08.11. A-04): keine Glocke, kein Einkaufswagen. */
internal fun sectionIcon(s: Section) = when (s) {
    Section.TASKS -> Symbole.ZuErledigen
    Section.TODOS -> Icons.Filled.CheckCircle
    Section.MEMBERS -> Icons.Filled.Person
    Section.EVENTS -> Icons.Filled.DateRange
    Section.FINANCE -> Symbole.Kassenbuch
    Section.BILLING -> Symbole.Rechnungen
    Section.OCCASIONS -> Icons.Filled.Star
    Section.COMPETITIONS -> Icons.Filled.LocationOn
    Section.PARTNERS -> Icons.Filled.AccountBox
}

@Composable
private fun Footer(until: String?, vm: AppViewModel, onReload: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
        TextButton(onClick = onReload) { Text(tr("Aktualisieren")) }
        until?.let { Text(tr("Freigeschaltet bis {x1}", "x1" to formatDate(it)), style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = vm::lockAdmin) { Text(tr("Verwaltung auf diesem Telefon sperren")) }
    }
}

@Composable
private fun UnitPicker(units: List<AdminUnit>, current: AdminUnit, onPick: (AdminUnit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = Abstand.klein)) {
        // Welche Einheit verwaltet wird, steht ausdrücklich da (UX-Audit 31.10. A04)
        if (units.size == 1) {
            Text(tr("Verwaltung · {name}", "name" to current.name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Abstand.klein))
        } else {
            TextButton(onClick = { open = true }, modifier = Modifier.dropdown(tr("Verwaltete Einheit"), current.name, open)) {
                DropdownText(tr("Verwaltung · {name}", "name" to current.name), style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                units.forEach { u ->
                    DropdownMenuItem(text = { Text(u.name) }, onClick = {
                        open = false
                        onPick(u)
                    })
                }
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
