package de.hubiplan.app.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.hubiplan.app.BuildConfig
import de.hubiplan.app.HubiplanApp
import de.hubiplan.app.data.Account
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.LoginStep
import de.hubiplan.app.data.Me
import de.hubiplan.app.data.PairingLink
import de.hubiplan.app.data.Paired
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.Theme
import de.hubiplan.app.data.normalizeServer
import de.hubiplan.app.calendar.DeviceCalendar
import de.hubiplan.app.data.AppSettings
import de.hubiplan.app.push.PushSetup
import de.hubiplan.app.sync.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Loading : Screen
    data object Connect : Screen
    data class Main(val account: Account, val me: Me?, val access: PortalAccess?, val offline: Boolean) : Screen
}

/** Schritte beim Verbinden (QR-Code oder Passwort mit 2FA). */
data class ConnectState(
    val busy: Boolean = false,
    val error: String? = null,
    val server: String = "",
    // Zweiter Faktor: Sitzung der Anmeldung, bis der Code eingegeben ist
    val pendingSession: String? = null,
    // Weiteres Konto verbinden (zurück zum bisherigen möglich)
    val adding: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val accounts = (app as HubiplanApp).accounts
    private val store get() = accounts.activeStore()

    /** Alle Konten auf dem Gerät (Platz, Konto), für den Wechsel unter „Konto“. */
    private val _accountList = MutableStateFlow<List<Pair<Int, Account>>>(emptyList())
    val accountList: StateFlow<List<Pair<Int, Account>>> = _accountList

    val activeSlot: StateFlow<Int> = accounts.active

    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen

    private val _connect = MutableStateFlow(ConnectState())
    val connect: StateFlow<ConnectState> = _connect

    private val _theme = MutableStateFlow<Theme?>(null)
    val theme: StateFlow<Theme?> = _theme

    val settings: StateFlow<AppSettings> = accounts.active
        .flatMapLatest { accounts.store(it).settings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val context get() = getApplication<Application>()

    /** Darf dieses Konto Kurzmitteilungen senden (Vorstand, Zugführung)? */
    private val _canSend = MutableStateFlow(false)
    val canSend: StateFlow<Boolean> = _canSend

    /** Mitteilung, die aus einer Benachrichtigung geöffnet werden soll (Zugang, Nummer). */
    private val _openRequest = MutableStateFlow<Pair<Int, Int>?>(null)
    val openRequest: StateFlow<Pair<Int, Int>?> = _openRequest

    fun openCircular(slot: Int, accessId: Int, circularId: Int) {
        _openRequest.value = accessId to circularId
        if (slot != accounts.active.value) {
            // Mitteilung eines anderen Kontos: dorthin wechseln, mit dem Zugang der Mitteilung
            viewModelScope.launch {
                accounts.store(slot).currentAccount() ?: return@launch
                accounts.store(slot).selectAccess(accessId, null)
                switchTo(slot)
            }
            return
        }
        val main = screen.value as? Screen.Main ?: return
        main.me?.portal?.firstOrNull { it.id == accessId }?.let { if (main.access?.id != it.id) selectAccess(it) }
    }

    fun openHandled() {
        _openRequest.value = null
    }

    /** Push über eine UnifiedPush-App oder Google einschalten oder (null) abschalten. */
    fun setPushDistributor(pkg: String?) {
        viewModelScope.launch {
            if (pkg == null) PushSetup.turnOff(context, store) else PushSetup.enable(context, store, pkg)
        }
    }

    /** Nach Änderungen (Rückmeldung, Einstellung) Kalender und Hinweise aktualisieren. */
    fun syncSoon() = SyncWorker.now(context)

    fun setCalendarOn(on: Boolean) {
        viewModelScope.launch {
            store.setCalendarOn(on)
            if (!on) store.currentAccount()?.let { DeviceCalendar(context).removeCalendarsExcept(emptySet(), it.server) }
            syncSoon()
        }
    }

    fun setReminderMinutes(minutes: Int?) {
        viewModelScope.launch {
            store.setReminderMinutes(minutes)
            syncSoon()
        }
    }

    fun setRsvpHints(on: Boolean) {
        viewModelScope.launch { store.setRsvpHints(on) }
    }

    init {
        viewModelScope.launch {
            // Aktives Konto fehlt (abgemeldet), aber andere sind da: zum ersten wechseln
            if (store.currentAccount() == null) accounts.list().firstOrNull()?.let { accounts.setActive(it.first) }
            _theme.value = store.cachedTheme.first()
            loadAccounts()
            val account = store.account.first()
            if (account == null) _screen.value = Screen.Connect else refresh(account)
        }
    }

    private suspend fun loadAccounts() {
        _accountList.value = accounts.list()
    }

    private suspend fun switchTo(slot: Int) {
        accounts.setActive(slot)
        _canSend.value = false
        _theme.value = store.cachedTheme.first()
        loadAccounts()
        val account = store.currentAccount()
        if (account == null) _screen.value = Screen.Connect else refresh(account)
    }

    /** Zu einem anderen Konto (andere Instanz) wechseln. */
    fun switchAccount(slot: Int) {
        if (slot == accounts.active.value) return
        viewModelScope.launch { switchTo(slot) }
    }

    /** Weiteres Konto verbinden (QR-Code oder Anmeldung einer anderen Instanz). */
    fun addAccount() {
        _connect.value = ConnectState(adding = true)
        _screen.value = Screen.Connect
    }

    /** Verbinden abbrechen: zurück zum bisherigen Konto. */
    fun cancelAdd() {
        viewModelScope.launch {
            _connect.value = ConnectState()
            val account = store.currentAccount()
            if (account == null) _screen.value = Screen.Connect else refresh(account)
        }
    }

    /** Konto ist weg (abgemeldet, ungültig): zum nächsten wechseln oder Verbinden zeigen. */
    private suspend fun dropActive(error: String?) {
        val slot = accounts.active.value
        val server = store.currentAccount()?.server
        runCatching { PushSetup.forget(context, store) }
        runCatching { server?.let { DeviceCalendar(context).removeCalendarsExcept(emptySet(), it) } }
        accounts.remove(slot)
        val next = accounts.list().firstOrNull()
        if (next != null) {
            switchTo(next.first)
        } else {
            SyncWorker.cancel(context)
            loadAccounts()
            _theme.value = null
            _connect.value = ConnectState(error = error, server = server ?: "")
            _screen.value = Screen.Connect
        }
    }

    fun api(): Api? = (screen.value as? Screen.Main)?.account?.let { Api(it.server, it.token) }

    private val deviceName: String
        get() = listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
            .distinct().joinToString(" ").take(100)

    private suspend fun refresh(account: Account) {
        _screen.value = Screen.Main(account, null, null, offline = false)
        try {
            val me = Api(account.server, account.token).me()
            val access = me.portal.firstOrNull { it.id == account.selectedAccess } ?: me.portal.firstOrNull()
            _screen.value = Screen.Main(account, me, access, offline = false)
            access?.let { applyAccess(it) }
            SyncWorker.schedule(context)
            SyncWorker.now(context)
            for (s in accounts.allStores()) runCatching { PushSetup.refresh(context, s) }
            _canSend.value = runCatching { Api(account.server, account.token).senders().senders.isNotEmpty() }.getOrDefault(false)
        } catch (e: ApiException) {
            if (e.status == 401) {
                // Gerät abgemeldet (im Browser, Passwort geändert, lange nicht genutzt)
                dropActive(e.message)
            } else {
                _screen.value = Screen.Main(account, null, null, offline = true)
            }
        }
    }

    fun reload() {
        val main = screen.value as? Screen.Main ?: return
        viewModelScope.launch { refresh(main.account) }
    }

    private suspend fun applyAccess(access: PortalAccess) {
        store.selectAccess(access.id, access.theme)
        _theme.value = access.theme
    }

    fun selectAccess(access: PortalAccess) {
        val main = screen.value as? Screen.Main ?: return
        _screen.value = main.copy(access = access)
        viewModelScope.launch { applyAccess(access) }
    }

    fun clearError() {
        _connect.value = _connect.value.copy(error = null)
    }

    fun scanned(text: String) {
        val link = PairingLink.parse(text)
        if (link == null) {
            _connect.value = _connect.value.copy(
                error = "Das ist kein QR-Code von HUBIplan. Bitte den Code unter „Mein Konto › App auf dem Telefon“ scannen.",
            )
            return
        }
        run(link.server) { api -> api.pair(code = link.code, deviceName = deviceName, appVersion = appVersion) }
    }

    fun login(serverInput: String, email: String, password: String) {
        val server = normalizeServer(serverInput)
        if (server == null) {
            _connect.value = _connect.value.copy(error = "Bitte die Adresse von HUBIplan angeben, z. B. verein.example.de.")
            return
        }
        _connect.value = _connect.value.copy(server = server)
        run(server) { api ->
            when (val step = api.login(email.trim(), password)) {
                is LoginStep.Done -> api.createDevice(step.sessionToken, deviceName, appVersion)
                is LoginStep.NeedsCode -> {
                    _connect.value = _connect.value.copy(pendingSession = step.sessionToken)
                    null
                }
            }
        }
    }

    fun secondFactor(code: String) {
        val state = _connect.value
        val session = state.pendingSession ?: return
        run(state.server) { api ->
            when (val step = api.secondFactor(session, code.trim())) {
                is LoginStep.Done -> api.createDevice(step.sessionToken, deviceName, appVersion)
                is LoginStep.NeedsCode -> throw ApiException(400, "Der Code stimmt nicht. Bitte erneut versuchen.")
            }
        }
    }

    fun cancelSecondFactor() {
        _connect.value = _connect.value.copy(pendingSession = null, error = null)
    }

    private fun run(server: String, action: suspend (Api) -> Paired?) {
        _connect.value = _connect.value.copy(busy = true, error = null, server = server)
        viewModelScope.launch {
            try {
                val api = Api(server)
                val instance = api.instance()
                if (instance.product != "HUBIplan") throw ApiException(0, "Unter dieser Adresse läuft kein HUBIplan.")
                if (instance.minAppVersion > BuildConfig.VERSION_CODE) {
                    throw ApiException(0, "Bitte die App aktualisieren, diese Version wird von HUBIplan nicht mehr unterstützt.")
                }
                val paired = action(api)
                if (paired != null) {
                    val slot = accounts.slotFor(server)
                    val target = accounts.store(slot)
                    target.currentAccount()?.let { old ->
                        // Dieselbe Instanz neu verbunden: altes Gerät abmelden
                        runCatching { PushSetup.disable(context, target) }
                        runCatching { Api(old.server, old.token).logout(old.deviceId) }
                    }
                    target.save(server, paired)
                    accounts.setActive(slot)
                    _connect.value = ConnectState()
                    _theme.value = null
                    loadAccounts()
                    refresh(target.currentAccount() ?: return@launch)
                } else {
                    _connect.value = _connect.value.copy(busy = false)
                }
            } catch (e: ApiException) {
                _connect.value = _connect.value.copy(busy = false, error = e.message)
            }
        }
    }

    /** Dieses Konto vom Gerät abmelden; andere Konten bleiben. */
    fun logout() {
        val main = screen.value as? Screen.Main ?: return
        viewModelScope.launch {
            // Auch ohne Netz lokal abmelden; der Server lässt den Schlüssel sonst nach 180 Tagen verfallen
            runCatching { PushSetup.disable(context, store) }
            runCatching { Api(main.account.server, main.account.token).logout(main.account.deviceId) }
            dropActive(null)
        }
    }

    /** Geräteschlüssel ungültig geworden (z. B. im Browser abgemeldet). */
    fun deviceRevoked(message: String?) {
        viewModelScope.launch { dropActive(message) }
    }

    private val appVersion: String get() = BuildConfig.VERSION_CODE.toString()
}
