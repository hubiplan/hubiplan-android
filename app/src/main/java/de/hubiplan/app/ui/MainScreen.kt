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
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
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

/**
 * Hauptziele der App. [short] steht in der Navigationsleiste: einzeilig, ohne Umbruch im Wort
 * (UX-Audit 08.11. A-14). „Mein Konto“ steht nicht in der Navigationsleiste, sondern als Symbol im
 * Kopf; so bleiben höchstens fünf Ziele (UX-Audit 05.11. A-01). „Ausweis“ statt „Profil“: dort
 * liegen Ausweis, Foto und eigene Daten (A-02, wie „Mein Bereich › Ausweis“ im Browser).
 */
internal enum class Tab(val label: String, val short: String = label) {
    TERMINE(tr("Termine")), AUFGABEN(tr("Aufgaben")), MITTEILUNGEN(tr("Mitteilungen"), tr("Neues")), AUSWEIS(tr("Ausweis")),
    VERWALTUNG(tr("Verwaltung"), tr("Verwalten")), KONTO(tr("Mein Konto")),
}

/** Ziele der Navigationsleiste: nie mehr als fünf, „Mein Konto“ nie (A-01). */
internal fun barTabs(adminVisible: Boolean, tasksVisible: Boolean): List<Tab> =
    Tab.entries.filter { it != Tab.KONTO && (it != Tab.VERWALTUNG || adminVisible) && (it != Tab.AUFGABEN || tasksVisible) }

/**
 * Wohin System-Zurück auf oberster Ebene führt (A-04): aus „Mein Konto“ zum vorherigen Reiter,
 * aus jedem anderen Reiter zum Startreiter „Termine“; nur dort beendet Zurück die App.
 */
internal fun backTarget(tab: Tab, previous: Tab): Tab? = when (tab) {
    Tab.TERMINE -> null
    Tab.KONTO -> previous.takeIf { it != Tab.KONTO } ?: Tab.TERMINE
    else -> Tab.TERMINE
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
    var tab by rememberSaveable { mutableStateOf(Tab.TERMINE) }
    // Reiter vor „Mein Konto“, damit Zurück dorthin führt
    var previousTab by rememberSaveable { mutableStateOf(Tab.TERMINE) }
    val openRequest by vm.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest, leave.busy) { if (openRequest != null && tab != Tab.MITTEILUNGEN) leave.request(action = { tab = Tab.MITTEILUNGEN }, onStay = vm::openHandled) }
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
    val tabs = barTabs(adminVisible, tasksVisible)
    LaunchedEffect(adminVisible) { if (tab == Tab.VERWALTUNG && !adminVisible) tab = Tab.TERMINE }
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
    CompositionLocalProvider(LocalBackHost provides backHost) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            tabs.forEach { t ->
                item(
                    selected = tab == t,
                    onClick = { if (t != tab) leave.request { tab = t } },
                    icon = {
                        Icon(
                            when (t) {
                                Tab.AUFGABEN -> Icons.Filled.CheckCircle
                                Tab.TERMINE -> Icons.Filled.DateRange
                                Tab.MITTEILUNGEN -> Icons.Filled.Email
                                Tab.AUSWEIS -> Icons.Filled.AccountBox
                                Tab.VERWALTUNG -> Icons.Filled.Build
                                Tab.KONTO -> Icons.Filled.AccountCircle
                            },
                            contentDescription = null,
                        )
                    },
                    label = { BarLabel(t.short) },
                    colors = barColors,
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
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
                        else -> AccessPicker(main, vm)
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
                    if (tab != Tab.KONTO) {
                        IconButton(onClick = { leave.request { previousTab = tab; tab = Tab.KONTO } }) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = tr("Mein Konto"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalBrand.current.brand,
                    titleContentColor = LocalBrand.current.onBrand,
                    navigationIconContentColor = LocalBrand.current.onBrand,
                    actionIconContentColor = LocalBrand.current.onBrand,
                ),
            )
            if (main.offline) {
                Text(
                    tr("Keine Verbindung zu HUBIplan. Bitte später erneut versuchen."),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.announce().padding(Abstand.rand),
                )
                TextButton(onClick = vm::reload, modifier = Modifier.padding(horizontal = Abstand.klein)) { Text(tr("Erneut laden")) }
            }
            val access = main.access
            val offer by vm.shortcutOffer.collectAsStateWithLifecycle()
            if (offer && access != null && tab == Tab.TERMINE) ShortcutOffer(access.title, vm)
            Box(Modifier.fillMaxSize()) {
                when {
                    tab == Tab.KONTO -> AccountPane(main, vm)
                    tab == Tab.VERWALTUNG -> AdminPane(main, vm)
                    tab == Tab.AUFGABEN -> MemberTodosPane(main, vm)
                    main.me == null && !main.offline -> Loading()
                    access == null && main.me != null -> Message(
                        tr("Für dieses Konto ist kein Mitgliederbereich freigeschaltet. Bitte wende dich an den Vorstand."),
                    )
                    access != null && tab == Tab.TERMINE -> EventsPane(access, vm)
                    access != null && tab == Tab.MITTEILUNGEN -> CircularsPane(access, vm)
                    access != null && tab == Tab.AUSWEIS -> ProfilePane(access, main, vm)
                    // Ohne Netz: wenigstens den gespeicherten Ausweis zeigen
                    main.offline && tab == Tab.AUSWEIS -> OfflineCardPane(main)
                    // Ohne Netz beim Kaltstart: Hinweis statt leerer Fläche, dazu wartende
                    // Kassenvorgänge dieses Telefons (UX-Audit 31.10. A15, F03)
                    main.offline -> OfflinePane(main, events = tab == Tab.TERMINE)
                }
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
private fun BarLabel(text: String) {
    val base = LocalTextStyle.current
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val min = barLabelMinScale(fontScale)
    var scale by remember(text, base, fontScale) { mutableFloatStateOf(1f) }
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

/** Einmaliges Angebot nach dem Verbinden: Verknüpfung mit Wappen und Namen anlegen. */
@Composable
private fun ShortcutOffer(name: String, vm: AppViewModel) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Abstand.rand, vertical = Abstand.klein)) {
        Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
            Text(tr("„{name}“ auf den Startbildschirm legen?", "name" to name), style = MaterialTheme.typography.titleSmall)
            Text(
                tr("Eine Verknüpfung mit Wappen und Namen öffnet die App direkt hier. Im App-Menü heißt die App weiter HUBIplan."),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                TextButton(onClick = { vm.pinShortcut() }) { Text(tr("Ja, ablegen")) }
                TextButton(onClick = vm::dismissShortcutOffer) { Text(tr("Nein, danke")) }
            }
        }
    }
}

@Composable
private fun AccessPicker(main: Screen.Main, vm: AppViewModel) {
    val leave = LocalFormLeaveGuard.current
    val accesses = main.me?.portal.orEmpty()
    val current = main.access
    var open by rememberSaveable { mutableStateOf(false) }
    if (accesses.size <= 1) {
        Text(current?.label ?: "HUBIplan", maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.dropdown(tr("Einheit"), current?.label ?: tr("keine gewählt"), open)) {
            Text((current?.label ?: tr("Einheit wählen")) + " ▾", color = LocalBrand.current.onBrand, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accesses.forEach { a ->
                // Ansprechpartner sehen die Organisation dazu (J7)
                DropdownMenuItem(text = { Text(a.label) }, onClick = {
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
private fun OfflineCardPane(main: Screen.Main) {
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
            MemberCardSection(store, accessId, null)
        }
    }
}

@Composable
private fun OfflinePane(main: Screen.Main, events: Boolean) {
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
                tr("Sie liegen verschlüsselt auf diesem Telefon und werden gesendet, sobald Verbindung besteht und du die Verwaltung öffnest. Einzelheiten stehen dort."),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** „1 Kassenvorgang wartet auf Versand“ / „3 Kassenvorgänge warten auf Versand“. */
fun pendingCountText(count: Int): String =
    if (count == 1) tr("1 Kassenvorgang wartet auf Versand") else tr("{count} Kassenvorgänge warten auf Versand", "count" to count)
