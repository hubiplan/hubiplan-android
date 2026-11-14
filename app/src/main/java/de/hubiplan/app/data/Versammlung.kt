package de.hubiplan.app.data

import java.net.URI
import java.net.URLDecoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Anmelden per App und Online-Abstimmung in Versammlungen (HUBIplan V7, `docs/versammlungen.md`).
 *
 * Die Leitung zeigt einen QR-Code mit
 * `https://<Instanz>/mein-bereich/versammlungen/da/<Versammlung>?v=<Verein>&c=<Code>`.
 * Der Code wechselt alle 30 Sekunden; die App schickt ihn sofort nach dem Scannen.
 */
data class MeetingLink(val server: String, val meetingId: Int, val clubId: Int, val code: String) {
    companion object {
        private val PATH = Regex("^/mein-bereich/versammlungen/da/(\\d{1,10})/?$")
        private val CODE = Regex("^\\d{1,12}-[0-9a-f]{16}$")

        fun parse(text: String): MeetingLink? {
            val uri = try {
                URI(text.trim())
            } catch (e: Exception) {
                return null
            }
            if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
            val match = PATH.find(uri.rawPath ?: return null) ?: return null
            val query = (uri.rawQuery ?: return null).split("&").mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to URLDecoder.decode(part.substring(i + 1), "UTF-8")
            }.toMap()
            val club = query["v"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            val code = query["c"]?.takeIf { CODE.matches(it) } ?: return null
            val server = httpsOrigin(uri) ?: return null
            val meeting = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return MeetingLink(server, meeting, club, code)
        }
    }
}

@Serializable
data class PollOption(val id: Int, val role: String = "", val label: String = "")

@Serializable
data class PollResult(val id: Int, val role: String = "", val label: String = "", val votes: Int = 0)

/** Eine Stimme, die das Mitglied abgeben darf: eigene, Vollmacht oder für eine Organisation. */
@Serializable
data class PollVote(val attendance: Int, val name: String = "", val how: String = "own", val done: Boolean = false)

@Serializable
data class Poll(
    val id: Int,
    @SerialName("agenda_position") val agendaPosition: Int = 0,
    @SerialName("agenda_number") val agendaNumber: String = "",
    @SerialName("agenda_title") val agendaTitle: String = "",
    val kind: String = "resolution",
    val question: String = "",
    val office: String = "",
    val candidate: String = "",
    val secret: Boolean = false,
    val status: String = "",
    val options: List<PollOption> = emptyList(),
    val result: List<PollResult>? = null,
    val votes: List<PollVote> = emptyList(),
) {
    val running: Boolean get() = status == "open"
    val openVotes: List<PollVote> get() = votes.filter { !it.done }
}

@Serializable
data class MeetingLive(
    val id: Int,
    val title: String = "",
    @SerialName("kind_name") val kindName: String = "",
    val unit: String = "",
    val location: String = "",
    @SerialName("checkin_open") val checkinOpen: Boolean = false,
    val remote: Boolean = false,
    val voter: Boolean = false,
    @SerialName("is_organization") val isOrganization: Boolean = false,
    val status: String = "",
    val mode: String = "",
    val votes: Int = 0,
    val polls: List<Poll> = emptyList(),
) {
    val present: Boolean get() = status == "present"
    /** „Online teilnehmen“ geht nur für Stimmberechtigte, wenn die Leitung es erlaubt. */
    val canJoinRemote: Boolean get() = !present && !isOrganization && remote && voter
}
