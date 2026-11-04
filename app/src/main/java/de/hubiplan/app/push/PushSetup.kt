package de.hubiplan.app.push

import android.content.Context
import de.hubiplan.app.HubiplanApp
import de.hubiplan.app.data.AccountStore
import de.hubiplan.app.data.Account
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ClubLogo
import de.hubiplan.app.data.PushWay
import de.hubiplan.app.sync.Notifications
import de.hubiplan.app.sync.SyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import de.hubiplan.app.tr

/**
 * Push einrichten, Endpunkt an HUBIplan melden und Nachrichten anzeigen. Jedes Konto (je
 * Instanz) meldet sich einzeln an, mit eigener Kennung beim Verteiler und eigenen Schlüsseln;
 * daran erkennt die App, für welches Konto eine Nachricht ist.
 */
object PushSetup {
    // Firebase-Kennung gilt für die App, nicht für einen Kontoplatz. Ihre netzgebundene
    // Erneuerung/Löschung getrennt von den kurzen Kontosperren serialisieren.
    private val googleLifecycle = Mutex()

    private fun accounts(context: Context) = (context.applicationContext as HubiplanApp).accounts

    /**
     * Bei der Verteiler-App [distributor] anmelden; der Endpunkt kommt per Broadcast zurück.
     * [GooglePush.ID] (nur Play-Fassung): über Google und das Push-Gateway der Instanz.
     */
    suspend fun enable(context: Context, store: AccountStore, distributor: String, expected: Account? = null) {
        val account = expected ?: store.currentAccount() ?: return
        val previous = store.ifCurrent(account) { store.settings.first().push } ?: return
        if (previous.distributor != null && previous.distributor != distributor) {
            val cleanup = store.ifCurrent(account) {
                val cleanup = detach(context, store)
                store.setPushDistributor(null)
                cleanup
            } ?: return
            cleanup()
        }
        store.ifCurrent(account) { store.setPushOff(false) } ?: return
        val ways = runCatching { Api(account.server).instance().push }.getOrNull()
        if (distributor == GooglePush.ID) {
            store.ifCurrent(account) { store.setPushDistributor(distributor) } ?: return
            enableGoogle(context, store, account, ways)
            return
        }
        val vapid = ways?.firstOrNull { it.kind == "unifiedpush" }?.vapidPublicKey
        store.ifCurrent(account) {
            store.setPushDistributor(distributor)
            UnifiedPush.register(context, distributor, store.pushToken(), vapid)
        }
    }

    private suspend fun enableGoogle(context: Context, store: AccountStore, account: Account, ways: List<PushWay>?, fcmToken: String? = null) = googleLifecycle.withLock {
        if (ways == null) {
            store.ifCurrent(account) { store.setPushEndpoint(null, tr("HUBIplan ist gerade nicht erreichbar.")) }
            return@withLock
        }
        val fcmWay = ways.firstOrNull { it.kind == "fcm" }
        val gateway = fcmWay?.gateway
        if (gateway.isNullOrBlank()) {
            store.ifCurrent(account) { store.setPushEndpoint(null, tr("Dein Verein bietet Push über Google nicht an. Eine UnifiedPush-App wie „ntfy“ geht immer.")) }
            return@withLock
        }
        val token = fcmToken ?: GooglePush.token(context)
        if (token == null) {
            store.ifCurrent(account) { store.setPushEndpoint(null, tr("Google-Push ist auf diesem Gerät nicht verfügbar.")) }
            return@withLock
        }
        // An den VAPID-Schlüssel der Instanz binden (Gateway nimmt nur deren Nachrichten an)
        val vapid = fcmWay.vapidPublicKey ?: ways.firstOrNull { it.kind == "unifiedpush" }?.vapidPublicKey
        register(store, account, GatewayEndpoint.of(gateway, token, vapid), kind = "fcm")
    }

    /** Push abschalten: beim Verteiler und bei HUBIplan abmelden (dann nur Abruf). */
    suspend fun disable(context: Context, store: AccountStore, expected: Account? = null) {
        val account = expected ?: store.currentAccount() ?: return
        val cleanup = store.ifCurrent(account) {
            val cleanup = detach(context, store)
            store.setPushDistributor(null)
            cleanup
        } ?: return
        cleanup()
        runCatching { Api(account.server, account.token).unregisterPush() }
    }

    /** Selbst abgeschaltet: merken, damit die Play-Fassung nicht von allein Google einrichtet. */
    suspend fun turnOff(context: Context, store: AccountStore) {
        val account = store.currentAccount() ?: return
        disable(context, store, account)
        store.ifCurrent(account) { store.setPushOff(true) }
    }

    /** Nach dem Start: bei der Verteiler-App erneut anmelden (sie bestätigt den Endpunkt). */
    suspend fun refresh(context: Context, store: AccountStore) {
        val account = store.currentAccount() ?: return
        val push = store.ifCurrent(account) { store.settings.first().push } ?: return
        val distributor = push.distributor
        if (distributor == null) {
            // Play-Fassung ohne UnifiedPush-App: von allein über Google, außer selbst abgeschaltet
            if (!push.off && GooglePush.available(context) && UnifiedPush.distributors(context).isEmpty()) {
                enable(context, store, GooglePush.ID, account)
            }
            return
        }
        if (distributor == GooglePush.ID) {
            if (GooglePush.available(context)) enable(context, store, distributor, account) else disable(context, store, account)
            return
        }
        if (UnifiedPush.distributors(context).none { it.packageName == distributor }) {
            // Verteiler-App deinstalliert: nur noch Abruf
            disable(context, store, account)
        } else {
            enable(context, store, distributor, account)
        }
    }

    /** Gerät abgemeldet: nur beim Verteiler abmelden (HUBIplan kennt das Gerät nicht mehr). */
    suspend fun forget(context: Context, store: AccountStore) {
        val account = store.currentAccount() ?: return
        val cleanup = store.ifCurrent(account) { detach(context, store) } ?: return
        cleanup()
    }

    /** Unter Kontosperre nur alte lokale Kennungen erfassen/IPC abmelden. Der
     * zurückgegebene Netzanteil läuft erst nach dem Entfernen außerhalb der Sperre.
     */
    suspend fun detach(context: Context, store: AccountStore): suspend () -> Unit {
        val distributor = store.settings.first().push.distributor
        if (distributor == GooglePush.ID) {
            return {
                googleLifecycle.withLock {
                    val inUse = accounts(context).allStores().any {
                        it.settings.first().push.distributor == GooglePush.ID
                    }
                    if (!inUse) GooglePush.delete(context)
                }
            }
        }
        val token = store.storedPushToken()
        if (distributor != null && token != null) UnifiedPush.unregister(context, distributor, token)
        return {}
    }

    /** Konto, zu dem eine Kennung beim Verteiler gehört. */
    private suspend fun storeFor(context: Context, token: String?): AccountStore? {
        if (token == null) return null
        return accounts(context).allStores().firstOrNull { it.storedPushToken() == token }
    }

    private suspend fun accountForToken(store: AccountStore, token: String?): Account? {
        if (token == null) return null
        val account = store.currentAccount() ?: return null
        return store.ifCurrent(account) {
            account.takeIf { store.storedPushToken() == token }
        }
    }

    /** Google hat dem Gerät eine neue Kennung gegeben: Endpunkt bei allen Konten mit Google neu melden. */
    suspend fun onGoogleToken(context: Context, fcmToken: String) {
        for (store in accounts(context).allStores()) {
            val account = store.currentAccount() ?: continue
            if (store.ifCurrent(account) { store.settings.first().push.distributor == GooglePush.ID } != true) continue
            enableGoogle(context, store, account, runCatching { Api(account.server).instance().push }.getOrNull(), fcmToken)
        }
    }

    /**
     * Nachricht über Google (Play-Fassung): derselbe verschlüsselte Web-Push wie bei UnifiedPush.
     * Welches Konto gemeint ist, zeigt der Schlüssel, mit dem sie sich entschlüsseln lässt.
     */
    suspend fun onGoogleMessage(context: Context, bytes: ByteArray) {
        for (store in accounts(context).allStores()) {
            val account = store.currentAccount() ?: continue
            if (store.ifCurrent(account) { store.settings.first().push.distributor == GooglePush.ID } != true) continue
            val message = store.ifCurrent(account) { decrypt(store, bytes) } ?: continue
            handle(context, store, message, account)
            return
        }
    }

    suspend fun onNewEndpoint(context: Context, token: String?, endpoint: String?) {
        val store = storeFor(context, token) ?: return
        val account = accountForToken(store, token) ?: return
        if (endpoint == null) return
        register(store, account, endpoint, kind = "unifiedpush")
    }

    private suspend fun register(store: AccountStore, account: Account, endpoint: String, kind: String) {
        registerAccountPush(
            scope = store, expected = account, endpoint = endpoint,
            keys = { store.pushKeys() },
            send = { captured, keys -> Api(captured.server, captured.token).registerPush(endpoint, keys.p256dh, keys.authB64, kind) },
            save = { value, error -> store.setPushEndpoint(value, error) },
        )
    }

    suspend fun onFailed(context: Context, token: String?, reason: String?) {
        val store = storeFor(context, token) ?: return
        val account = accountForToken(store, token) ?: return
        store.ifCurrent(account) {
            if (store.storedPushToken() != token) return@ifCurrent
            store.setPushEndpoint(null, tr("Die Push-App hat die Anmeldung abgelehnt") + (reason?.let { " ($it)" } ?: "") + ".")
        }
    }

    suspend fun onUnregistered(context: Context, token: String?) {
        val store = storeFor(context, token) ?: return
        val account = accountForToken(store, token) ?: return
        runCatching { Api(account.server, account.token).unregisterPush() }
        store.ifCurrent(account) {
            if (store.storedPushToken() == token) store.setPushDistributor(null)
        }
    }

    suspend fun onMessage(context: Context, token: String?, bytes: ByteArray?, id: String?) {
        val store = storeFor(context, token) ?: return
        if (bytes == null || token == null) return
        val account = accountForToken(store, token) ?: return
        store.ifCurrent(account) {
            store.settings.first().push.distributor?.let { UnifiedPush.acknowledge(context, it, token, id) }
        }
        // Nicht von HUBIplan oder für einen alten Schlüssel: verwerfen
        val message = store.ifCurrent(account) { decrypt(store, bytes) } ?: return
        handle(context, store, message, account)
    }

    private suspend fun decrypt(store: AccountStore, bytes: ByteArray): PushMessage? = try {
        PushMessage.parse(String(WebPushCrypto.decrypt(bytes, store.pushKeys())))
    } catch (e: PushDecryptException) {
        null
    }

    suspend fun handle(context: Context, store: AccountStore, message: PushMessage, expected: de.hubiplan.app.data.Account? = null) {
        val account = expected ?: store.currentAccount() ?: return
        val accountId = de.hubiplan.app.sync.RsvpActionReceiver.accountId(account)
        when (message.type) {
            "todo" -> {
                if (message.v != 1) return
                val target = de.hubiplan.app.data.TodoTarget.fromPush(message.org, message.department, message.todo) ?: return
                store.ifCurrent(account) {
                    Notifications.showTodo(context, store.slot, accountId, target, message.title, message.text)
                }
            }
            "circular" -> {
                val access = message.access ?: return
                val id = message.id ?: return
                val key = circularKey(access, id)
                if (key in store.notifiedKeys()) return
                val logo = ClubLogo.load(context, account, access)
                store.ifCurrent(account) {
                    Notifications.showCircular(context, store.slot, accountId, access, id, message.title, message.text, message.from, message.kind == "short", logo)
                    store.addNotified(setOf(key))
                }
            }
            "info" -> {
                val access = message.access ?: return
                val logo = ClubLogo.load(context, account, access)
                store.ifCurrent(account) {
                    Notifications.showInfo(context, store.slot, accountId, access, message.title, message.text, logo)
                }
            }
            else -> SyncWorker.now(context)
        }
    }

    fun circularKey(access: Int, id: Int) = "r:$access:$id"
}
