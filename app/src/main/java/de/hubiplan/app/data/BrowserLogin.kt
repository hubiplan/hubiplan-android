package de.hubiplan.app.data

import android.content.Context
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Anmelden im Browser mit Prüfwert (Gesamtaudit 15.11., APP-01; docs/app.md in hubiplan).
 *
 * Der Browser gibt den Einmalcode über hubiplan://verbinden an „die“ App. Das Schema gehört
 * keiner App allein; eine fremde App könnte den Rückruf abfangen. Die App erzeugt deshalb vor
 * dem Öffnen von /app/anmelden ein Geheimnis ([newVerifier]) und schickt nur dessen Prüfwert
 * ([challenge], SHA-256 wie PKCE, RFC 7636) mit. Einlösen kann den Code dann nur, wer das
 * Geheimnis kennt.
 */
object BrowserLogin {
    /** Höchstens so lange wartet die App auf den Rückruf (der Code gilt 10 Minuten). */
    const val MAX_AGE_MS = 15 * 60 * 1000L

    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** 32 Zufallsbytes, base64url ohne „=“: 43 Zeichen aus A–Z a–z 0–9 - _. */
    fun newVerifier(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** Seite zum Anmelden mit Prüfwert; null bei ungültiger Adresse. */
    fun url(serverInput: String, challenge: String): String? =
        normalizeServer(serverInput)?.let { "$it/app/anmelden?challenge=$challenge" }

    /**
     * Knopf „In der HUBIplan-App öffnen“ ohne Code: hubiplan://anmelden?server=<Origin>. Liefert
     * die Adresse der Instanz (nur HTTPS, ohne Pfad); die App fragt vor dem Öffnen nach.
     */
    fun parseStart(text: String): String? {
        val uri = try {
            URI(text.trim())
        } catch (e: Exception) {
            return null
        }
        if (uri.scheme != PairingLink.SCHEME || uri.isOpaque || uri.host != "anmelden") return null
        if (!uri.path.isNullOrEmpty() && uri.path != "/") return null
        val serverText = uri.rawQuery?.split('&')
            ?.firstOrNull { it.startsWith("server=") }
            ?.removePrefix("server=")
            ?.let { try { URLDecoder.decode(it, "UTF-8") } catch (e: IllegalArgumentException) { null } }
            ?: return null
        val server = try {
            URI(serverText)
        } catch (e: Exception) {
            return null
        }
        if (server.scheme != "https" || server.host.isNullOrBlank()) return null
        if (!server.path.isNullOrEmpty() && server.path != "/") return null
        if (server.rawQuery != null || server.rawFragment != null || server.rawUserInfo != null) return null
        return httpsOrigin(server)
    }

    /** Geheimnis zum Rückruf dieser Adresse, wenn er rechtzeitig kommt; sonst null. */
    fun verifierFor(pending: PendingBrowserLogin?, server: String, now: Long): String? =
        pending?.takeIf { it.server == server && now - it.createdAt in 0..MAX_AGE_MS }?.verifier
}

/** Offene Anmeldung im Browser (übersteht, dass Android die App im Hintergrund beendet). */
data class PendingBrowserLogin(val server: String, val verifier: String, val createdAt: Long)

class BrowserLoginStore(context: Context) {
    private val prefs = context.getSharedPreferences("browser-anmeldung", Context.MODE_PRIVATE)

    fun save(pending: PendingBrowserLogin) {
        prefs.edit()
            .putString("server", pending.server)
            .putString("verifier", pending.verifier)
            .putLong("created", pending.createdAt)
            .apply()
    }

    fun load(): PendingBrowserLogin? {
        val server = prefs.getString("server", null) ?: return null
        val verifier = prefs.getString("verifier", null) ?: return null
        return PendingBrowserLogin(server, verifier, prefs.getLong("created", 0))
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
