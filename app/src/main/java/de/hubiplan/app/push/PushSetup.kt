package de.hubiplan.app.push

import android.content.Context
import de.hubiplan.app.HubiplanApp
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.PushWay
import de.hubiplan.app.sync.Notifications
import de.hubiplan.app.sync.SyncWorker
import kotlinx.coroutines.flow.first

/** Push einrichten, Endpunkt an HUBIplan melden und Nachrichten anzeigen. */
object PushSetup {

    private fun store(context: Context) = (context.applicationContext as HubiplanApp).store

    /**
     * Bei der Verteiler-App [distributor] anmelden; der Endpunkt kommt per Broadcast zurück.
     * [GooglePush.ID] (nur Play-Fassung): über Google und das Push-Gateway der Instanz.
     */
    suspend fun enable(context: Context, distributor: String) {
        val store = store(context)
        val account = store.account.first() ?: return
        val previous = store.settings.first().push.distributor
        if (previous != null && previous != distributor) {
            leave(context, previous)
            store.setPushDistributor(null)
        }
        store.setPushOff(false)
        val ways = runCatching { Api(account.server).instance().push }.getOrNull()
        if (distributor == GooglePush.ID) {
            store.setPushDistributor(distributor)
            enableGoogle(context, ways)
            return
        }
        val vapid = ways?.firstOrNull { it.kind == "unifiedpush" }?.vapidPublicKey
        store.setPushDistributor(distributor)
        UnifiedPush.register(context, distributor, store.pushToken(), vapid)
    }

    private suspend fun enableGoogle(context: Context, ways: List<PushWay>?, fcmToken: String? = null) {
        val store = store(context)
        if (ways == null) {
            store.setPushEndpoint(null, "HUBIplan ist gerade nicht erreichbar.")
            return
        }
        val gateway = ways.firstOrNull { it.kind == "fcm" }?.gateway
        if (gateway.isNullOrBlank()) {
            store.setPushEndpoint(null, "Dein Verein bietet Push über Google nicht an. Eine UnifiedPush-App wie „ntfy“ geht immer.")
            return
        }
        val token = fcmToken ?: GooglePush.token(context)
        if (token == null) {
            store.setPushEndpoint(null, "Google-Push ist auf diesem Gerät nicht verfügbar.")
            return
        }
        onNewEndpoint(context, store.pushToken(), GooglePush.endpoint(gateway, token), kind = "fcm")
    }

    /** Beim bisherigen Weg abmelden (Verteiler-App bzw. Google-Kennung löschen). */
    private suspend fun leave(context: Context, distributor: String) {
        if (distributor == GooglePush.ID) {
            GooglePush.delete(context)
        } else {
            store(context).storedPushToken()?.let { UnifiedPush.unregister(context, distributor, it) }
        }
    }

    /** Push abschalten: beim Verteiler und bei HUBIplan abmelden (dann nur Abruf). */
    suspend fun disable(context: Context, server: String? = null, deviceToken: String? = null) {
        val store = store(context)
        store.settings.first().push.distributor?.let { leave(context, it) }
        val account = store.account.first()
        val api = when {
            server != null && deviceToken != null -> Api(server, deviceToken)
            account != null -> Api(account.server, account.token)
            else -> null
        }
        runCatching { api?.unregisterPush() }
        store.setPushDistributor(null)
    }

    /** Selbst abgeschaltet: merken, damit die Play-Fassung nicht von allein Google einrichtet. */
    suspend fun turnOff(context: Context) {
        disable(context)
        store(context).setPushOff(true)
    }

    /** Nach dem Start: bei der Verteiler-App erneut anmelden (sie bestätigt den Endpunkt). */
    suspend fun refresh(context: Context) {
        val push = store(context).settings.first().push
        val distributor = push.distributor
        if (distributor == null) {
            // Play-Fassung ohne UnifiedPush-App: von allein über Google, außer selbst abgeschaltet
            if (!push.off && GooglePush.available(context) && UnifiedPush.distributors(context).isEmpty()) {
                enable(context, GooglePush.ID)
            }
            return
        }
        if (distributor == GooglePush.ID) {
            if (GooglePush.available(context)) enable(context, distributor) else disable(context)
            return
        }
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
        leave(context, distributor)
    }

    /** Google hat dem Gerät eine neue Kennung gegeben: Endpunkt neu melden. */
    suspend fun onGoogleToken(context: Context, fcmToken: String) {
        val store = store(context)
        if (store.settings.first().push.distributor != GooglePush.ID) return
        val account = store.account.first() ?: return
        enableGoogle(context, runCatching { Api(account.server).instance().push }.getOrNull(), fcmToken)
    }

    /** Nachricht über Google (Play-Fassung): derselbe verschlüsselte Web-Push wie bei UnifiedPush. */
    suspend fun onGoogleMessage(context: Context, bytes: ByteArray) {
        val store = store(context)
        if (store.settings.first().push.distributor != GooglePush.ID) return
        val message = try {
            PushMessage.parse(String(WebPushCrypto.decrypt(bytes, store.pushKeys())))
        } catch (e: PushDecryptException) {
            null
        } ?: return
        handle(context, message)
    }

    suspend fun onNewEndpoint(context: Context, token: String?, endpoint: String?, kind: String = "unifiedpush") {
        val store = store(context)
        if (token == null || endpoint == null || token != store.storedPushToken()) return
        val account = store.account.first() ?: return
        val keys = store.pushKeys()
        try {
            Api(account.server, account.token).registerPush(endpoint, keys.p256dh, keys.authB64, kind)
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
