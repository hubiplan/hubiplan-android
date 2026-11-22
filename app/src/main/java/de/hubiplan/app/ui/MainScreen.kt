package de.hubiplan.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import de.hubiplan.app.tr
import de.hubiplan.app.trn

/**
 * Hauptziele der App. [short] steht in der Navigationsleiste: einzeilig, ohne Umbruch im Wort
 * (UX-Audit 08.11. A-14). „Mein Konto“ steht nicht in der Navigationsleiste, sondern als Symbol im
 * Kopf; so bleiben höchstens fünf Ziele (UX-Audit 05.11. A-01). „Ich“ statt „Ausweis“: oben der
 * Ausweis, darunter alles Persönliche wie „Mein Bereich“ im Browser (Konzept app-ich, S-06/X-03).
 */
// Texte erst beim Anzeigen übersetzen, damit ein Sprachwechsel sofort wirkt (UX-Audit 16.11. AND-11)
internal enum class Tab(private val labelText: () -> String, private val shortText: () -> String = labelText) {
    START({ tr("Start") }), TERMINE({ tr("Termine") }), MITTEILUNGEN({ tr("Mitteilungen") }, { tr("Neues") }), ICH({ tr("Ich") }),
    VERWALTUNG({ tr("Verwaltung") }, { tr("Verwalten") }), NOTEN({ tr("Noten") }), AUFGABEN({ tr("Aufgaben") }), KONTO({ tr("Mein Konto") });

    val label: String get() = labelText()
    val short: String get() = shortText()

    /** Symbol aus dem gemeinsamen Symbolsatz beider Apps. */
    val symbol: Strich
        get() = when (this) {
            START -> Strich.START
            TERMINE -> Strich.TERMINE
            MITTEILUNGEN -> Strich.NEUES
            ICH -> Strich.ICH
            VERWALTUNG -> Strich.VERWALTUNG
            NOTEN -> Strich.NOTEN
            AUFGABEN -> Strich.AUFGABEN
            KONTO -> Strich.KONTO
        }
}

/** Die vier festen Reiter, in beiden Apps gleich (Gestaltung „Frisch mobil“). */
internal val FESTE_REITER = listOf(Tab.START, Tab.TERMINE, Tab.MITTEILUNGEN, Tab.ICH)

/**
 * Wer den fünften Platz der Leiste bekommen kann, in der Reihenfolge der Vorgabe: Verwaltung
 * vor Noten vor Aufgaben. Nur was dieses Konto überhaupt hat.
 */
internal fun fuenfteReiter(adminVisible: Boolean, tasksVisible: Boolean, notesVisible: Boolean): List<Tab> = buildList {
    if (adminVisible) add(Tab.VERWALTUNG)
    if (notesVisible) add(Tab.NOTEN)
    if (tasksVisible) add(Tab.AUFGABEN)
}

/** Der fünfte Reiter: die eigene Wahl aus „Mein Konto“, wenn es sie (noch) gibt, sonst die Vorgabe. */
internal fun fuenfterReiter(moeglich: List<Tab>, wahl: String): Tab? = moeglich.firstOrNull { it.name == wahl } ?: moeglich.firstOrNull()

/**
 * Ziele der Navigationsleiste: Start, Termine, Neues, Ich und ein fünfter Reiter je Rolle
 * ([fuenfterReiter], [wahl] aus „Mein Konto“). Auf dem Telefon nie mehr als fünf, „Mein Konto“
 * nie (A-01). Was nicht in der Leiste steht, ist von „Start“ aus erreichbar; „Verwaltung“
 * zusätzlich als Symbol im Kopf. Die seitliche Leiste ([wide]) zeigt alle.
 */
internal fun barTabs(
    adminVisible: Boolean,
    tasksVisible: Boolean,
    notesVisible: Boolean = false,
    wide: Boolean = false,
    wahl: String = "",
): List<Tab> {
    val moeglich = fuenfteReiter(adminVisible, tasksVisible, notesVisible)
    return FESTE_REITER + if (wide) moeglich else listOfNotNull(fuenfterReiter(moeglich, wahl))
}

/** Reiter dieses Kontos, die nicht in der Leiste stehen: „Start“ führt dorthin. */
internal fun weitereReiter(adminVisible: Boolean, tasksVisible: Boolean, notesVisible: Boolean, tabs: List<Tab>): List<Tab> =
    fuenfteReiter(adminVisible, tasksVisible, notesVisible).filter { it !in tabs }

/** Zugang für den Bereich „Noten“: online aus den Modulen, ohne Netz aus dem Speicher. */
@Composable
private fun notesAccessId(main: Screen.Main, context: android.content.Context): Int? {
    val access = main.access
    if (access != null) return access.id.takeIf { access.hasScores }
    val selected = main.account.selectedAccess ?: return null
    if (!main.offline) return null
    val stored = remember(main.account.server, main.account.deviceId, selected) {
        de.hubiplan.app.data.NotenStore(context, de.hubiplan.app.data.LocalAccountData.key(main.account.server, main.account.deviceId))
            .hasFolders(selected)
    }
    return selected.takeIf { stored }
}

/** Kopf in Vereinsfarbe nur auf „Start“ und nicht über einer Detailansicht. */
internal fun kopfInVereinsfarbe(tab: Tab, detail: Boolean): Boolean = tab == Tab.START && !detail

/** Steht „Verwaltung“ als Symbol in der Kopfleiste statt unten? */
internal fun adminInHeader(adminVisible: Boolean, tabs: List<Tab>): Boolean = adminVisible && Tab.VERWALTUNG !in tabs

/** Seitliche Leiste statt unterer: breiter Bildschirm, der nicht flach ist (Tablet). */
internal fun wideNavigation(widthDp: Int, heightDp: Int): Boolean = widthDp >= 600 && heightDp >= 480

/**
 * Wohin System-Zurück auf oberster Ebene führt (A-04): aus „Mein Konto“ zum vorherigen Reiter,
 * aus jedem anderen Reiter zum Reiter „Start“; nur dort beendet Zurück die App.
 */
internal fun backTarget(tab: Tab, previous: Tab): Tab? = when (tab) {
    Tab.START -> null
    Tab.KONTO -> previous.takeIf { it != Tab.KONTO } ?: Tab.START
    else -> Tab.START
}

/** Ab dieser Breite stehen Liste und Detail nebeneinander (Tablet, Querformat). */
val TWO_PANE_MIN_WIDTH = 720.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(main: Screen.Main, vm: AppViewModel) {
    FormLeaveGuardHost { leave -> MainScreenContent(main, vm, leave) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(main: Screen.Main, vm: AppViewModel, leave: FormLeaveGuard) {
    // Einmal nach dem Verbinden: Erlaubnis für Benachrichtigungen mit Begründung (AND-01)
    NotificationPermissionAsk()
    var tab by rememberSaveable { mutableStateOf(Tab.START) }
    // Reiter vor „Mein Konto“, damit Zurück dorthin führt
    var previousTab by rememberSaveable { mutableStateOf(Tab.START) }
    // „Termin ansehen“ auf „Start“ öffnet genau diesen Termin im Reiter „Termine“
    var terminRequest by rememberSaveable { mutableStateOf<String?>(null) }
    // „Für dich zu tun“ auf „Start“ öffnet den Bereich unter „Ich“
    var ichRequest by rememberSaveable { mutableStateOf<String?>(null) }
    val openRequest by vm.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest, leave.busy) { if (openRequest != null && tab != Tab.MITTEILUNGEN) leave.request(action = { tab = Tab.MITTEILUNGEN }, onStay = vm::openHandled) }
    // App-Verknüpfung „Ausweis zeigen“: Reiter „Ich“; den großen Ausweis öffnet der Reiter selbst
    val openCard by vm.openCard.collectAsStateWithLifecycle()
    LaunchedEffect(openCard, leave.busy) { if (openCard && tab != Tab.ICH) leave.request(action = { tab = Tab.ICH }, onStay = vm::cardHandled) }
    // „Helferdienste“ unter „Ich“ öffnet die Helferdienste im Reiter „Aufgaben“
    var helpersRequest by rememberSaveable { mutableStateOf(false) }
    // „Verwaltung“ nur für Konten mit Verwaltungsrechten (Schritt A8)
    val admin by vm.admin.collectAsStateWithLifecycle()
    val pendingTodo by vm.openTodo.collectAsStateWithLifecycle()
    val hasDraft = leave.hasDraft || leave.busy
    androidx.compose.runtime.SideEffect { vm.todoFormActive.value = hasDraft }
    androidx.compose.runtime.DisposableEffect(vm) { onDispose { vm.todoFormActive.value = false } }
    val selectedTodo by vm.selectedTodo.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTodo, leave.busy) {
        val pending = pendingTodo ?: return@LaunchedEffect
        if (!pending.ready) {
            leave.request(action = {
                vm.todoFormActive.value = false
                vm.todoHandled()
                vm.openTodoNotification(pending.accountId, pending.target)
            }, onStay = vm::todoHandled)
        } else if (pending.target.scope == "p") {
            if (pending.accountId != de.hubiplan.app.sync.RsvpActionReceiver.accountId(main.account)) {
                vm.todoHandled(); return@LaunchedEffect
            }
            leave.request(action = {
                main.me?.portal?.firstOrNull { it.id == pending.target.unitId }?.let { vm.selectAccess(it) }
                vm.selectTodo(pending.target)
                vm.todoHandled()
                tab = Tab.AUFGABEN
            }, onStay = vm::todoHandled)
        } else if (tab != Tab.VERWALTUNG) leave.request(action = { tab = Tab.VERWALTUNG }, onStay = vm::todoHandled)
    }
    val deferredCircular by vm.deferredCircular.collectAsStateWithLifecycle()
    LaunchedEffect(deferredCircular, leave.busy) {
        val request = deferredCircular ?: return@LaunchedEffect
        leave.request(action = {
            vm.todoFormActive.value = false
            vm.resumeCircular(request)
        }, onStay = vm::clearDeferredCircular)
    }
    val adminVisible = admin.visible || pendingTodo?.target?.scope?.let { it != "p" } == true
    val tasksVisible = main.me?.portal?.any { it.hasTodos || it.hasHelpers } == true || selectedTodo?.scope == "p"
    // Ohne Netz zählt der zuletzt gespeicherte Stand: das Notenpult muss offline gehen
    val context = androidx.compose.ui.platform.LocalContext.current
    val notesAccess = notesAccessId(main, context)
    val notesVisible = notesAccess != null
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = wideNavigation(configuration.screenWidthDp, configuration.screenHeightDp)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tabs = barTabs(adminVisible, tasksVisible, notesVisible, wide, settings.fifthTab)
    val weitere = weitereReiter(adminVisible, tasksVisible, notesVisible, tabs)
    val adminIcon = adminInHeader(adminVisible, tabs)
    LaunchedEffect(adminVisible) { if (tab == Tab.VERWALTUNG && !adminVisible) tab = Tab.START }
    LaunchedEffect(notesVisible) { if (tab == Tab.NOTEN && !notesVisible) tab = Tab.START }
    // Zurück auf oberster Ebene: zum Startreiter statt die App zu schließen (A-04). Hier vor dem
    // Inhalt angelegt, damit Zurück in Listen, Details und Formularen zuerst dort wirkt.
    val back = backTarget(tab, previousTab)
    androidx.activity.compose.BackHandler(enabled = back != null) { back?.let { target -> leave.request { tab = target } } }
    val stand by vm.stand.collectAsStateWithLifecycle()
    // Standmodus nur zur Instanz dieses Kontos; beim Kontowechsel schließen
    LaunchedEffect(stand, main.account.server) {
        if (stand?.server?.let { it != main.account.server } == true) vm.closeStand()
    }
    // Standmodus im Vollbild statt der Reiter: so wirkt Zurück sicher dort, und die Sperre der
    // Verwaltung nach 5 Minuten Pause schließt ihn nicht (er arbeitet mit dem Standzugang)
    val openStand = stand
    if (openStand != null) {
        StandScreen(openStand, onClose = vm::closeStand)
        return
    }
    // Notenpult ebenso im Vollbild; beim Kontowechsel schließen
    val pult by vm.pult.collectAsStateWithLifecycle()
    LaunchedEffect(pult, main.account.server, main.account.deviceId) {
        if (pult?.accountKey?.let { it != accountKey(main.account) } == true) vm.closePult()
    }
    val openPult = pult
    if (openPult != null && openPult.accountKey == accountKey(main.account)) {
        NotenPult(openPult, vm, onClose = vm::closePult)
        return
    }
    // Aktiver Reiter deutlich markiert: Fläche in der (kontrastgeprüften) Primärfarbe, mind. 3 : 1
    // zur Leiste in Hell und Dunkel bei jeder Vereinsfarbe (UX-Audit 08.11. A-19)
    val scheme = MaterialTheme.colorScheme
    val barColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = scheme.onPrimary,
            selectedTextColor = scheme.onSurface,
            indicatorColor = scheme.primary,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = scheme.onPrimary,
            selectedTextColor = scheme.onSurface,
            indicatorColor = scheme.primary,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant,
        ),
    )
    // Detailansichten melden Pfeil zurück und Titel für die Kopfleiste an (A-01)
    val backHost = remember { BackHost() }
    val detail = backHost.current
    // Navigationsleiste unten auf dem Telefon, seitlich auf dem Tablet (passt sich selbst an)
    val unreadCount = vm.unreadCirculars.collectAsStateWithLifecycle().value.size
    // Rückmeldungen nach Aktionen unten im sichtbaren Bereich (UX-Audit 16.11. AND-08)
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val messageScope = androidx.compose.runtime.rememberCoroutineScope()
    val actionMessages = remember(snackbar, messageScope) { ActionMessages(snackbar, messageScope) }
    // Geteilte Kalenderdatei (W4): in der Verwaltung „Neuer Termin“ vorausgefüllt öffnen
    val sharedEvent by vm.sharedEvent.collectAsStateWithLifecycle()
    LaunchedEffect(sharedEvent, admin.visible, leave.busy) {
        if (sharedEvent == null || tab == Tab.VERWALTUNG) return@LaunchedEffect
        if (admin.visible) {
            leave.request(action = { tab = Tab.VERWALTUNG }, onStay = vm::sharedEventHandled)
        } else if (admin.overview != null || admin.error != null) {
            vm.sharedEventHandled()
            snackbar.showSnackbar(tr("Termine anlegen können nur Konten mit Verwaltungsrechten."))
        }
    }
    val sharedEventError by vm.sharedEventError.collectAsStateWithLifecycle()
    LaunchedEffect(sharedEventError) {
        val message = sharedEventError ?: return@LaunchedEffect
        vm.sharedEventErrorShown()
        snackbar.showSnackbar(message)
    }
    CompositionLocalProvider(LocalBackHost provides backHost, LocalActionMessages provides actionMessages) {
    // Ein gemeinsamer Faktor für alle Beschriftungen der Leiste: keine sichtbar kleiner als die
    // anderen (UX-Audit 16.11., AND-29)
    val barScale = remember(tabs.map { it.short }) { mutableFloatStateOf(1f) }
    NavigationSuiteScaffold(
        layoutType = if (wide) {
            androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType.NavigationRail
        } else {
            androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType.NavigationBar
        },
        navigationSuiteItems = {
            tabs.forEach { t ->
                item(
                    selected = tab == t,
                    onClick = { if (t != tab) leave.request { tab = t } },
                    icon = {
                        // Plakette mit der Zahl ungelesener Mitteilungen (UX-Audit 16.11. AND-15)
                        val count = if (t == Tab.MITTEILUNGEN) unreadCount else 0
                        androidx.compose.material3.BadgedBox(
                            badge = {
                                if (count > 0) {
                                    val text = trn("{n} neue Mitteilung", "{n} neue Mitteilungen", count)
                                    androidx.compose.material3.Badge(Modifier.semantics { contentDescription = text }) {
                                        Text(if (count > 99) "99+" else count.toString())
                                    }
                                }
                            },
                        ) {
                        Icon(strichsymbol(t.symbol), contentDescription = null)
                        }
                    },
                    label = { BarLabel(t.short, barScale) },
                    colors = barColors,
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            val inBrand = kopfInVereinsfarbe(tab, detail != null)
            TopAppBar(
                // In der Verwaltung nicht den persönlichen Zugang als Kopf zeigen; welche Einheit
                // verwaltet wird, steht darunter (UX-Audit 31.10. A04)
                title = {
                    when {
                        detail != null -> Text(
                            detail.title ?: if (tab == Tab.VERWALTUNG || tab == Tab.KONTO) tab.label else main.access?.label ?: "HUBIplan",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.asHeading(),
                        )
                        tab == Tab.VERWALTUNG -> Text(tr("Verwaltung"))
                        tab == Tab.KONTO -> Text(tr("Mein Konto"))
                        else -> AccessPicker(main, vm, onBrand = inBrand)
                    }
                },
                // Wappen der gewählten Einheit vor dem Namen (Schmuck; der Name steht daneben)
                navigationIcon = {
                    val logo by vm.logo.collectAsStateWithLifecycle()
                    val image = remember(logo) { logo?.asImageBitmap() }
                    if (detail != null) {
                        IconButton(onClick = detail.onBack, enabled = detail.enabled) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Zurück"))
                        }
                    } else if (tab == Tab.KONTO) {
                        IconButton(onClick = { backTarget(tab, previousTab)?.let { target -> leave.request { tab = target } } }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Zurück"))
                        }
                    } else if (image != null && tab != Tab.VERWALTUNG) {
                        Image(image, contentDescription = null, modifier = Modifier.padding(start = Abstand.mittel).size(36.dp))
                    }
                },
                // „Mein Konto“ oben rechts wie im Browser (A-01)
                actions = {
                    // Sechs Ziele auf dem Telefon: „Verwaltung“ hier statt unten (Noten, E2)
                    if (adminIcon && tab != Tab.KONTO) {
                        IconButton(onClick = { if (tab != Tab.VERWALTUNG) leave.request { tab = Tab.VERWALTUNG } }) {
                            Icon(strichsymbol(Strich.VERWALTUNG), contentDescription = tr("Verwaltung"))
                        }
                    }
                    if (tab != Tab.KONTO) {
                        IconButton(onClick = { leave.request { previousTab = tab; tab = Tab.KONTO } }) {
                            Icon(strichsymbol(Strich.KONTO), contentDescription = tr("Mein Konto"))
                        }
                    }
                },
                // Vereinsfarbe nur auf „Start“; überall sonst ein ruhiger Kopf, damit der Inhalt
                // vorn steht (Gestaltung „Frisch mobil“)
                colors = if (inBrand) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = LocalBrand.current.brand,
                        titleContentColor = LocalBrand.current.onBrand,
                        navigationIconContentColor = LocalBrand.current.onBrand,
                        actionIconContentColor = LocalBrand.current.onBrand,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            // Offline als neutrale Fläche, nicht in Fehlerrot (UX-Audit 08.11. A-03)
            // Offline als neutrale Fläche, ein Serverfehler nennt HUBIplan statt des Internets (AND-07)
            if (main.offline) {
                val problem = main.problem
                if (problem != null && !problem.offline) ProblemNotice(problem, onRetry = vm::reload) else OfflineBanner(since = null, onRetry = vm::reload)
                ReloadWhenOnline(vm::reload)
            }
            val access = main.access
            val signedOut by vm.signedOut.collectAsStateWithLifecycle()
            signedOut?.let { SignedOutDialog(it, onReconnect = { vm.signedOutSeen(); vm.addAccount() }, onDismiss = vm::signedOutSeen) }
            val offer by vm.shortcutOffer.collectAsStateWithLifecycle()
            // Nur in der Terminliste, nie über einem geöffneten Termin (A-15)
            if (offer && access != null && tab == Tab.START && detail == null) ShortcutOffer(vm)
            Box(Modifier.fillMaxSize()) {
                when {
                    tab == Tab.KONTO -> AccountPane(main, vm)
                    tab == Tab.VERWALTUNG -> AdminPane(main, vm)
                    tab == Tab.AUFGABEN -> MemberTodosPane(main, vm, openHelpers = helpersRequest, onHelpersOpened = { helpersRequest = false })
                    tab == Tab.NOTEN && notesAccess != null -> NotenPane(notesAccess, main, vm)
                    main.me == null && !main.offline -> Loading()
                    access == null && main.me != null -> Message(
                        tr("Für dieses Konto ist kein Mitgliederbereich freigeschaltet. Bitte wende dich an deinen Verein."),
                    )
                    access != null && tab == Tab.START -> StartPane(
                        access, main, vm, weitere,
                        onTab = { ziel -> leave.request { tab = ziel } },
                        onTermin = { key -> leave.request { terminRequest = key; tab = Tab.TERMINE } },
                        onBereich = { bereich -> leave.request { ichRequest = bereich.name; tab = Tab.ICH } },
                    )
                    access != null && tab == Tab.TERMINE -> EventsPane(access, vm, open = terminRequest, onOpened = { terminRequest = null })
                    access != null && tab == Tab.MITTEILUNGEN -> CircularsPane(access, vm)
                    access != null && tab == Tab.ICH -> IchPane(
                        access, main, vm,
                        onHelpers = { leave.request { helpersRequest = true; tab = Tab.AUFGABEN } },
                        request = ichRequest,
                        onRequestHandled = { ichRequest = null },
                    )
                    // Ohne Netz: wenigstens den gespeicherten Ausweis zeigen
                    main.offline && tab == Tab.ICH -> OfflineCardPane(main, vm)
                    // Ohne Netz beim Kaltstart: Hinweis statt leerer Fläche, dazu wartende
                    // Kassenvorgänge dieses Telefons (UX-Audit 31.10. A15, F03)
                    main.offline -> OfflinePane(main, events = tab == Tab.TERMINE || tab == Tab.START, vm)
                }
                androidx.compose.material3.SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    }
}

/**
 * Beschriftung der Navigationsleiste: immer eine Zeile. Passt sie bei großer Schrift nicht in die
 * Breite, wird sie schrittweise kleiner, höchstens bis zur Standardgröße (A-14).
 */
@Composable
private fun BarLabel(text: String, shared: androidx.compose.runtime.MutableFloatState) {
    val base = LocalTextStyle.current
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val min = barLabelMinScale(fontScale)
    var scale by shared
    var fits by remember(text, base, fontScale) { mutableStateOf(false) }
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        style = base.copy(fontSize = base.fontSize * scale, hyphens = Hyphens.None),
        modifier = Modifier.drawWithContent { if (fits) drawContent() },
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && scale > min) scale = (scale - 0.05f).coerceAtLeast(min) else fits = true
        },
    )
}

/** Kleinster Faktor für [BarLabel]: nie kleiner als bei Standardschrift, höchstens auf 60 %. */
internal fun barLabelMinScale(fontScale: Float): Float = (1f / fontScale.coerceAtLeast(1f)).coerceIn(0.6f, 1f)

/**
 * Einmaliges Angebot nach dem Verbinden: Verknüpfung mit Wappen und Namen anlegen. Kompakt in
 * einer Zeile, damit auch bei großer Schrift der erste Termin sichtbar bleibt (UX-Audit 08.11. A-15);
 * wer es gesehen hat, bekommt es beim nächsten Start nicht noch einmal („Mein Konto“ hat den Knopf).
 */
@Composable
private fun ShortcutOffer(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.shortcutOfferSeen() }
    Card(Modifier.fillMaxWidth().padding(horizontal = Abstand.rand, vertical = Abstand.klein)) {
        Row(
            Modifier.padding(start = Abstand.rand, end = Abstand.eng),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Abstand.klein),
        ) {
            Text(tr("Auf den Startbildschirm legen?"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.pinShortcut() }) { Text(tr("Hinzufügen")) }
            IconButton(onClick = vm::dismissShortcutOffer) {
                Icon(Icons.Filled.Close, contentDescription = tr("Nicht jetzt"))
            }
        }
    }
}

@Composable
private fun AccessPicker(main: Screen.Main, vm: AppViewModel, onBrand: Boolean = true) {
    val leave = LocalFormLeaveGuard.current
    val accesses = main.me?.portal.orEmpty()
    val current = main.access
    var open by rememberSaveable { mutableStateOf(false) }
    if (accesses.size <= 1) {
        Text(current?.label ?: "HUBIplan", maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.dropdown(tr("Verein oder Gruppe"), current?.label ?: tr("keine gewählt"), open)) {
            DropdownText(
                current?.label ?: tr("Verein oder Gruppe wählen"),
                color = if (onBrand) LocalBrand.current.onBrand else MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accesses.forEach { a ->
                // Ansprechpartner sehen die Organisation dazu (J7)
                // Häkchen am gewählten Zugang (AND-26)
                DropdownMenuItem(text = { Text(a.label) }, leadingIcon = { MenuCheck(a.id == current?.id) }, onClick = {
                    open = false
                    if (a.id != current?.id) { if (leave == null) vm.selectAccess(a) else leave.request { vm.selectAccess(a) } }
                })
            }
        }
    }
}

/**
 * Liste und Detail: auf schmalen Bildschirmen nacheinander, auf breiten nebeneinander.
 * [detail] ist null, solange nichts gewählt ist.
 */
@Composable
fun ListDetail(
    hasDetail: Boolean,
    onBack: () -> Unit,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    emptyDetail: String,
) {
    val leave = LocalFormLeaveGuard.current
    androidx.activity.compose.BackHandler(enabled = hasDetail) { if (leave == null) onBack() else leave.request(onBack) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        if (boxWidth >= TWO_PANE_MIN_WIDTH) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(boxWidth * 0.4f).fillMaxHeight()) { list() }
                VerticalDivider()
                Box(Modifier.fillMaxSize()) {
                    if (hasDetail) detail() else Message(emptyDetail)
                }
            }
        } else {
            if (hasDetail) {
                detail()
            } else {
                list()
            }
        }
    }
}

@Composable
fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(Abstand.gross), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(
            Modifier.semantics { contentDescription = tr("Lädt") }.announce(),
        )
    }
}

@Composable
private fun OfflineCardPane(main: Screen.Main, vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(main.account.server, main.account.deviceId) {
        de.hubiplan.app.data.CardStore(context, de.hubiplan.app.data.CardStore.key(main.account.server, main.account.deviceId))
    }
    val accessId = main.account.selectedAccess
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(Abstand.rand),
        verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
    ) {
        if (accessId == null) {
            Text(tr("Ohne Netz ist noch kein Ausweis gespeichert."))
        } else {
            // Verknüpfung „Ausweis zeigen“ ohne Netz: gespeicherten Ausweis groß
            val openCard by vm.openCard.collectAsStateWithLifecycle()
            var bigRequest by remember { mutableIntStateOf(0) }
            LaunchedEffect(openCard) { if (openCard) { bigRequest++; vm.cardHandled() } }
            MemberCardSection(store, accessId, null, bigRequest = bigRequest)
        }
    }
}

/**
 * Ohne Netz beim Start: zuletzt geladene Termine bzw. Mitteilungen mit Standzeit, nur zum Lesen
 * (UX-Audit 16.11. AND-07). Ohne gespeicherten Stand der bisherige Hinweis.
 */
@Composable
private fun OfflinePane(main: Screen.Main, events: Boolean, vm: AppViewModel) {
    val accessId = main.account.selectedAccess
    var savedEvents by remember(accessId) { mutableStateOf<de.hubiplan.app.data.OfflineStore.Saved<List<de.hubiplan.app.data.Occurrence>>?>(null) }
    var savedCirculars by remember(accessId) { mutableStateOf<de.hubiplan.app.data.OfflineStore.Saved<List<de.hubiplan.app.data.CircularItem>>?>(null) }
    LaunchedEffect(accessId) {
        if (accessId == null) return@LaunchedEffect
        val store = vm.offlineStore() ?: return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            savedEvents = store.events(accessId)
            savedCirculars = store.circulars(accessId)
        }
    }
    val saved = if (events) savedEvents else savedCirculars
    if (saved != null) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    tr("Gespeicherter Stand von {time}. Zusagen und Antworten gehen erst wieder mit Verbindung.", "time" to formatSince(saved.savedAt, System.currentTimeMillis())),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Abstand.rand, vertical = Abstand.klein),
                )
            }
            if (events) {
                val list = savedEvents?.items.orEmpty()
                if (list.isEmpty()) item { Text(tr("Keine anstehenden Termine."), Modifier.padding(Abstand.rand)) }
                list.forEach { occ ->
                    item(key = occ.key) {
                        EventRow(occ, false, null)
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            } else {
                val list = savedCirculars?.items.orEmpty()
                if (list.isEmpty()) item { Text(tr("Noch keine Mitteilungen."), Modifier.padding(Abstand.rand)) }
                list.forEach { c ->
                    item(key = c.id) {
                        CircularRow(c, false, onClick = null)
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
            item { Column(Modifier.padding(Abstand.rand)) { PendingLocalCard(main) } }
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(Abstand.rand),
        verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
    ) {
        Text(
            if (events) {
                tr("Ohne Verbindung lassen sich die Termine nicht laden. Ist der Abgleich mit dem Gerätekalender eingeschaltet, stehen sie dort.")
            } else {
                tr("Ohne Verbindung lassen sich die Mitteilungen nicht laden. Sie erscheinen, sobald wieder Verbindung besteht.")
            },
        )
        PendingLocalCard(main)
    }
}

/** Kommt die Verbindung zurück, selbst neu laden statt auf „Erneut laden“ zu warten (AND-07). */
@Composable
private fun ReloadWhenOnline(reload: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val current by androidx.compose.runtime.rememberUpdatedState(reload)
    androidx.compose.runtime.DisposableEffect(context) {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                main.post { current() }
            }
        }
        val registered = runCatching { cm?.registerDefaultNetworkCallback(callback) }.isSuccess && cm != null
        onDispose { if (registered) runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
}

/**
 * Noch nicht gesendete Buchungen und Quittungen dieses Kontos, auch ohne Netz sichtbar (F03).
 * Außerhalb der entsperrten Verwaltung nur die Anzahl, ohne Entschlüsseln (GA-S10).
 */
@Composable
fun PendingLocalCard(main: Screen.Main) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val key = de.hubiplan.app.data.LocalAccountData.key(main.account.server, main.account.deviceId)
    var count by remember(key) { mutableStateOf(0) }
    LaunchedEffect(key) {
        count = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                de.hubiplan.app.data.DraftStore(context, key).count() + de.hubiplan.app.data.ReceiptStore(context, key).count()
            }.getOrDefault(0)
        }
    }
    if (count == 0) return
    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Text(
                pendingCountText(count),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.asHeading(),
            )
            Text(
                tr("Sie liegen verschlüsselt auf diesem Gerät und werden gesendet, sobald Verbindung besteht und du die Verwaltung öffnest. Einzelheiten stehen dort."),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** „1 Kassenvorgang wartet auf Versand“ / „3 Kassenvorgänge warten auf Versand“. */
fun pendingCountText(count: Int): String =
    if (count == 1) tr("1 Kassenvorgang wartet auf Versand") else tr("{count} Kassenvorgänge warten auf Versand", "count" to count)

/** Ein Konto wurde auf diesem Gerät abgemeldet; die App zeigt jetzt ein anderes (AND-16). */
@Composable
private fun SignedOutDialog(info: AppViewModel.SignedOut, onReconnect: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Konto abgemeldet")) },
        text = {
            Text(
                tr(
                    "{dropped} wurde auf diesem Gerät abgemeldet. Du siehst jetzt {now}.",
                    "dropped" to info.dropped,
                    "now" to info.now,
                ),
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(tr("OK")) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onReconnect) { Text(tr("Neu verbinden")) } },
    )
}
