package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

// Schießen in der App (hubiplan konzepte/schiessen-android.md): Wettbewerbe in der Verwaltung,
// Standmodus eingebettet, Ranglisten für Mitglieder wie auf der Webseite.

/** Wettbewerb in der Verwaltung (GET /api/app/v1/admin/u/<Einheit>/competitions/). */
@Serializable
data class AdminCompetition(
    val id: Int,
    val title: String,
    val date: String = "",
    val status: String = "",
    val form: String = "",
    val own: Boolean = true,
    val location: String = "",
    @SerialName("starter_count") val starterCount: Int? = null,
    /** nur in der Einzelansicht; ältere Server: fehlt */
    @SerialName("can_stand_access") val canStandAccess: Boolean? = null,
    @SerialName("can_manage") val canManage: Boolean? = null,
) {
    val isFinal: Boolean get() = status == "endgueltig"
    val isBird: Boolean get() = form == "vogel"
}

/** Standzugang; [token] nur direkt nach dem Anlegen. */
@Serializable
data class StandAccess(
    val id: Int,
    val label: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    val active: Boolean = true,
    val token: String? = null,
)

/** Wettbewerbe für den Bereich „Wettbewerbe“: eigene, nicht abgeschlossene, heute zuerst. */
fun openCompetitions(all: List<AdminCompetition>, today: String): List<AdminCompetition> =
    all.filter { it.own && !it.isFinal }
        .sortedWith(compareBy<AdminCompetition> { it.date < today }.thenBy { if (it.date >= today) it.date else "" }.thenByDescending { it.date })

/** Adresse des Standmodus mit Standzugang. */
fun standUrl(server: String, token: String): String = "${server.trimEnd('/')}/stand/zugang/$token"

/** Inhalt eines Stand-QR-Codes: https://<Instanz>/stand/zugang/<Schlüssel>. */
data class StandLink(val server: String, val token: String) {
    val url: String get() = standUrl(server, token)

    companion object {
        private val TOKEN = Regex("[A-Za-z0-9_-]{16,200}")

        fun parse(text: String): StandLink? {
            val uri = try {
                URI(text.trim())
            } catch (e: Exception) {
                return null
            }
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawQuery != null) return null
            val path = uri.rawPath ?: return null
            if (!path.startsWith("/stand/zugang/")) return null
            val token = path.removePrefix("/stand/zugang/").trimEnd('/')
            if (!TOKEN.matches(token)) return null
            val server = httpsOrigin(uri) ?: return null
            return StandLink(server, token)
        }
    }
}

/** Darf die eingebettete Ansicht diese Adresse laden? Nur die eigene Instanz über HTTPS. */
fun sameInstance(server: String, url: String): Boolean {
    val a = try { URI(server) } catch (e: Exception) { return false }
    val b = try { URI(url) } catch (e: Exception) { return false }
    return b.scheme == "https" && a.scheme == "https" &&
        a.host.equals(b.host, ignoreCase = true) && portOf(a) == portOf(b)
}

private fun portOf(u: URI): Int = if (u.port == -1) 443 else u.port

// --- Ranglisten für Mitglieder (GET /api/portal/<Zugang>/rankings/…) ---

@Serializable
data class Rankings(
    val competitions: List<RankingSummary> = emptyList(),
    @SerialName("poll_seconds") val pollSeconds: Int = 15,
)

@Serializable
data class RankingSummary(
    val id: String,
    val title: String,
    val date: String = "",
    val location: String = "",
    val own: Boolean = true,
    val organizer: String = "",
    val live: Boolean = false,
    val provisional: Boolean = false,
    val final: Boolean = false,
)

@Serializable
data class RankingDetail(
    val id: String,
    val title: String,
    val date: String = "",
    val live: Boolean = false,
    val provisional: Boolean = false,
    val final: Boolean = false,
    val scorings: List<RankingScoring> = emptyList(),
    val prizes: List<RankingPrize> = emptyList(),
    val titles: List<RankingTitle> = emptyList(),
    val version: String = "",
    @SerialName("poll_seconds") val pollSeconds: Int? = null,
)

@Serializable
data class RankingScoring(
    val name: String,
    val kind: String = "",
    val field: Int? = null,
    val rows: List<RankingRow> = emptyList(),
    val complete: Boolean = true,
)

@Serializable
data class RankingRow(
    val place: Int? = null,
    val name: String = "",
    val value: String = "",
    val unit: String = "",
    val club: String = "",
    val prize: Boolean = false,
    val note: String = "",
    @SerialName("out_of_competition") val outOfCompetition: Boolean = false,
)

@Serializable
data class RankingPrize(
    val name: String,
    val fallen: Boolean = false,
    val winner: String = "",
    val unit: String = "",
    val shot: Int? = null,
)

@Serializable
data class RankingTitle(val title: String, val name: String = "")

@Serializable
data class RankingVersion(val version: String = "", val final: Boolean = false)
