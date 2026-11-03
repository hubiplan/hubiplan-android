package de.hubiplan.app.data

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Inhalt des QR-Codes „App verbinden“: https://<Instanz>/app/verbinden#code=<Code>. */
data class PairingLink(val server: String, val code: String) {
    companion object {
        fun parse(text: String): PairingLink? {
            val uri = try {
                URI(text.trim())
            } catch (e: Exception) {
                return null
            }
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
    }
}

/** Eingegebene Serveradresse vereinheitlichen; nur HTTPS. */
fun normalizeServer(input: String): String? {
    var text = input.trim().trimEnd('/')
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
