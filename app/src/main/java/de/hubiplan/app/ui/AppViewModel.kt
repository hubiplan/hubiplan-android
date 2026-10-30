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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as HubiplanApp).store

    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen

    private val _connect = MutableStateFlow(ConnectState())
    val connect: StateFlow<ConnectState> = _connect

    private val _theme = MutableStateFlow<Theme?>(null)
    val theme: StateFlow<Theme?> = _theme

    init {
        viewModelScope.launch {
            _theme.value = store.cachedTheme.first()
            val account = store.account.first()
            if (account == null) _screen.value = Screen.Connect else refresh(account)
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
        } catch (e: ApiException) {
            if (e.status == 401) {
                // Gerät abgemeldet (im Browser, Passwort geändert, lange nicht genutzt)
                store.clear()
                _connect.value = ConnectState(error = e.message, server = account.server)
                _screen.value = Screen.Connect
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
                    store.save(server, paired)
                    _connect.value = ConnectState()
                    refresh(store.account.first() ?: return@launch)
                } else {
                    _connect.value = _connect.value.copy(busy = false)
                }
            } catch (e: ApiException) {
                _connect.value = _connect.value.copy(busy = false, error = e.message)
            }
        }
    }

    fun logout() {
        val main = screen.value as? Screen.Main ?: return
        viewModelScope.launch {
            // Auch ohne Netz lokal abmelden; der Server lässt den Schlüssel sonst nach 180 Tagen verfallen
            runCatching { Api(main.account.server, main.account.token).logout(main.account.deviceId) }
            store.clear()
            _theme.value = null
            _connect.value = ConnectState()
            _screen.value = Screen.Connect
        }
    }

    /** Geräteschlüssel ungültig geworden (z. B. im Browser abgemeldet). */
    fun deviceRevoked(message: String?) {
        viewModelScope.launch {
            store.clear()
            _connect.value = ConnectState(error = message)
            _screen.value = Screen.Connect
        }
    }

    private val appVersion: String get() = BuildConfig.VERSION_CODE.toString()
}
