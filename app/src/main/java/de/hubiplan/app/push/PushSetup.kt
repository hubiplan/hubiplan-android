package de.hubiplan.app.push

import android.content.Context
import de.hubiplan.app.HubiplanApp
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.sync.Notifications
import de.hubiplan.app.sync.SyncWorker
import kotlinx.coroutines.flow.first

/** Push einrichten, Endpunkt an HUBIplan melden und Nachrichten anzeigen. */
object PushSetup {

    private fun store(context: Context) = (context.applicationContext as HubiplanApp).store

    /** Bei der Verteiler-App [distributor] anmelden; der Endpunkt kommt per Broadcast zurück. */
    suspend fun enable(context: Context, distributor: String) {
        val store = store(context)
        val account = store.account.first() ?: return
        val previous = store.settings.first().push.distributor
        if (previous != null && previous != distributor) {
            store.storedPushToken()?.let { UnifiedPush.unregister(context, previous, it) }
            store.setPushDistributor(null)
        }
        val vapid = runCatching { Api(account.server).instance().push.firstOrNull { it.kind == "unifiedpush" }?.vapidPublicKey }
            .getOrNull()
        store.setPushDistributor(distributor)
        UnifiedPush.register(context, distributor, store.pushToken(), vapid)
    }

    /** Push abschalten: beim Verteiler und bei HUBIplan abmelden (dann nur Abruf). */
    suspend fun disable(context: Context, server: String? = null, deviceToken: String? = null) {
        val store = store(context)
        val distributor = store.settings.first().push.distributor
        val token = store.storedPushToken()
        if (distributor != null && token != null) UnifiedPush.unregister(context, distributor, token)
        val account = store.account.first()
        val api = when {
            server != null && deviceToken != null -> Api(server, deviceToken)
            account != null -> Api(account.server, account.token)
            else -> null
        }
        runCatching { api?.unregisterPush() }
        store.setPushDistributor(null)
    }

    /** Nach dem Start: bei der Verteiler-App erneut anmelden (sie bestätigt den Endpunkt). */
    suspend fun refresh(context: Context) {
        val distributor = store(context).settings.first().push.distributor ?: return
        if (UnifiedPush.distributors(context).none { it.packageName == distributor }) {
            // Verteiler-App deinstalliert: nur noch Abruf
            disable(context)
        } else {
            enable(context, distributor)
        }
    }

    /** Gerät abgemeldet: nur beim Verteiler abmelden (HUBIplan kennt das Gerät nicht mehr). */
    suspend fun forget(context: Context) {
        val store = store(context)
        val distributor = store.settings.first().push.distributor ?: return
        store.storedPushToken()?.let { UnifiedPush.unregister(context, distributor, it) }
    }

    suspend fun onNewEndpoint(context: Context, token: String?, endpoint: String?) {
        val store = store(context)
        if (token == null || endpoint == null || token != store.storedPushToken()) return
        val account = store.account.first() ?: return
        val keys = store.pushKeys()
        try {
            Api(account.server, account.token).registerPush(endpoint, keys.p256dh, keys.authB64)
            store.setPushEndpoint(endpoint)
        } catch (e: ApiException) {
            store.setPushEndpoint(null, e.message)
        }
    }

    suspend fun onFailed(context: Context, token: String?, reason: String?) {
        val store = store(context)
        if (token == null || token != store.storedPushToken()) return
        store.setPushEndpoint(null, "Die Push-App hat die Anmeldung abgelehnt" + (reason?.let { " ($it)" } ?: "") + ".")
    }

    suspend fun onUnregistered(context: Context, token: String?) {
        val store = store(context)
        if (token == null || token != store.storedPushToken()) return
        val account = store.account.first()
        runCatching { account?.let { Api(it.server, it.token).unregisterPush() } }
        store.setPushDistributor(null)
    }

    suspend fun onMessage(context: Context, token: String?, bytes: ByteArray?, id: String?) {
        val store = store(context)
        if (token == null || bytes == null || token != store.storedPushToken()) return
        store.settings.first().push.distributor?.let { UnifiedPush.acknowledge(context, it, token, id) }
        val message = try {
            PushMessage.parse(String(WebPushCrypto.decrypt(bytes, store.pushKeys())))
        } catch (e: PushDecryptException) {
            // Nicht von HUBIplan oder für einen alten Schlüssel: verwerfen
            null
        } ?: return
        handle(context, message)
    }

    suspend fun handle(context: Context, message: PushMessage) {
        val store = store(context)
        when (message.type) {
            "circular" -> {
                val access = message.access ?: return
                val id = message.id ?: return
                val key = circularKey(access, id)
                if (key in store.notifiedKeys()) return
                Notifications.showCircular(context, access, id, message.title, message.text, message.from, message.kind == "short")
                store.addNotified(setOf(key))
            }
            "info" -> {
                val access = message.access ?: return
                Notifications.showInfo(context, access, message.title, message.text)
            }
            else -> SyncWorker.now(context)
        }
    }

    fun circularKey(access: Int, id: Int) = "r:$access:$id"
}
