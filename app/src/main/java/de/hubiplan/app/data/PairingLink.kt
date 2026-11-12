package de.hubiplan.app.data

import java.net.URI
import java.net.URLDecoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Inhalt des QR-Codes „App verbinden“: https://<Instanz>/app/verbinden#code=<Code>.
 * Auf demselben Telefon öffnet der Knopf „In der HUBIplan-App öffnen“ stattdessen
 * hubiplan://verbinden?server=<Origin, URL-kodiert>&code=<Code> (UX-Audit 08.11. X-04).
 */
data class PairingLink(val server: String, val code: String) {
    companion object {
        fun parse(text: String): PairingLink? {
            val uri = try {
                URI(text.trim())
            } catch (e: Exception) {
                return null
            }
            if (uri.scheme == SCHEME) return parseAppLink(uri)
            if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
            if (uri.path?.trimEnd('/') != "/app/verbinden") return null
            val code = uri.rawFragment
                ?.split('&')
                ?.firstOrNull { it.startsWith("code=") }
                ?.removePrefix("code=")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val server = httpsOrigin(uri) ?: return null
            return PairingLink(server, code)
        }

        /** Eigenes Schema der App; die Adresse der Instanz steht im Link, nur HTTPS. */
        const val SCHEME = "hubiplan"

        private fun parseAppLink(uri: URI): PairingLink? {
            if (uri.isOpaque || uri.host != "verbinden") return null
            if (!uri.path.isNullOrEmpty() && uri.path != "/") return null
            val params = uri.rawQuery?.split('&').orEmpty().mapNotNull { part ->
                val key = part.substringBefore('=', "")
                if (key.isEmpty()) null else key to decode(part.substringAfter('='))
            }.toMap()
            // Auch hubiplan://verbinden?link=<QR-Adresse, URL-kodiert>: dieselbe Prüfung wie beim QR-Code
            params["link"]?.let { inner -> return inner.takeIf { it.startsWith("https://") }?.let(::parse) }
            val code = params["code"]?.takeIf { it.isNotBlank() && it.none(Char::isWhitespace) } ?: return null
            val serverText = params["server"] ?: return null
            val server = try {
                URI(serverText)
            } catch (e: Exception) {
                return null
            }
            if (server.scheme != "https" || server.host.isNullOrBlank()) return null
            if (!server.path.isNullOrEmpty() && server.path != "/") return null
            if (server.rawQuery != null || server.rawFragment != null || server.rawUserInfo != null) return null
            return PairingLink(httpsOrigin(server) ?: return null, code)
        }

        private fun decode(text: String): String? = try {
            URLDecoder.decode(text, "UTF-8")
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** Eingegebene Serveradresse vereinheitlichen; nur HTTPS. */
fun normalizeServer(input: String): String? {
    var text = input.trim()
    if (text.isEmpty()) return null
    if (!text.contains("://")) text = "https://$text"
    val uri = try {
        URI(text)
    } catch (e: Exception) {
        return null
    }
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
    return httpsOrigin(uri)
}

/** https://Host[:Port], nur wenn okhttp die Adresse auch annimmt (Port 1–65535). */
internal fun httpsOrigin(uri: URI): String? {
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    if (uri.port != -1 && uri.port !in 1..65535) return null
    val origin = "https://$host" + if (uri.port == -1) "" else ":${uri.port}"
    return origin.takeIf { it.toHttpUrlOrNull() != null }
}

/** Vorhandene Web-Wiederherstellung derselben Instanz; keine Anmeldedaten im Link. */
fun passwordResetUrl(serverInput: String): String? =
    normalizeServer(serverInput)?.let { "$it/passwort-vergessen" }

/**
 * Pfad aus einer Serverantwort als Adresse auf derselben Instanz (Gesamtaudit 11.11.2026, H-08).
 * Nur Pfade mit genau einem führenden „/“; ohne Schrägstrich würde aus `https://verein.example.de`
 * und `.boese.example` eine fremde Adresse, „//“ und „\“ wären ebenfalls ein fremder Host.
 * Das Ergebnis muss Schema, Host und Port der Instanz behalten.
 */
fun serverLink(server: String, path: String): String? {
    if (!path.startsWith("/") || path.startsWith("//")) return null
    if (path.any { it == '\\' || it.isWhitespace() || it.isISOControl() }) return null
    val base = server.toHttpUrlOrNull() ?: return null
    val url = (server.trimEnd('/') + path).toHttpUrlOrNull() ?: return null
    if (url.scheme != "https" || url.scheme != base.scheme || url.host != base.host || url.port != base.port) return null
    return url.toString()
}
