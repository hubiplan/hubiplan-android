package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// Vorstandsaufgaben in der App (hubiplan docs/app.md, „Vorstandsaufgaben“): Beitrittsanträge
// entscheiden, Belegungsanfragen freigeben, Versammlung leiten. Dieselben Antworten wie im
// Browser unter /api/app/v1/admin/<u|d>/<Einheit>/…, nur mit dem Vorstandsschlüssel.

// --- Beitrittsanträge ---

@Serializable
data class ApplicationRow(
    val id: Int,
    val number: String = "",
    val status: String = "",
    @SerialName("status_label") val statusLabel: String = "",
    val kind: String = "person",
    val name: String = "",
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("member_type") val memberType: String = "",
    @SerialName("member_type_id") val memberTypeId: Int? = null,
    val unit: String = "",
    val minor: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("approvals_met") val approvalsMet: Boolean? = null,
    val disputed: Boolean = false,
    val missing: List<String> = emptyList(),
) {
    val isOpen: Boolean get() = status in OPEN_APPLICATION_STATES
}

val OPEN_APPLICATION_STATES = setOf("unsigned", "open", "query")

@Serializable
data class ApplicationList(
    val results: List<ApplicationRow> = emptyList(),
    val enabled: Boolean = false,
    @SerialName("rule_text") val ruleText: String = "",
    @SerialName("may_approve") val mayApprove: Boolean = false,
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class ApplicationApproval(
    val id: Int = 0,
    @SerialName("user_name") val userName: String = "",
    val roles: String = "",
    @SerialName("for_unit") val forUnit: Boolean = false,
    val decision: String = "",
    val note: String = "",
)

@Serializable
data class ApplicationMatch(
    val name: String = "",
    @SerialName("birth_date") val birthDate: String? = null,
    val here: Boolean = false,
    val former: Boolean = false,
    @SerialName("active_elsewhere") val activeElsewhere: String = "",
)

@Serializable
data class ApplicationMatches(val persons: List<ApplicationMatch> = emptyList())

@Serializable
data class ApplicationDetail(
    val id: Int,
    val number: String = "",
    val status: String = "",
    @SerialName("status_label") val statusLabel: String = "",
    val name: String = "",
    @SerialName("member_type") val memberType: String = "",
    @SerialName("member_type_id") val memberTypeId: Int? = null,
    @SerialName("approvals_met") val approvalsMet: Boolean? = null,
    val disputed: Boolean = false,
    val missing: List<String> = emptyList(),
    val summary: List<String> = emptyList(),
    val approvals: List<ApplicationApproval> = emptyList(),
    val matches: ApplicationMatches = ApplicationMatches(),
    @SerialName("rule_text") val ruleText: String = "",
    @SerialName("entry_date") val entryDate: String? = null,
    @SerialName("may_approve") val mayApprove: Boolean = false,
    @SerialName("may_decide") val mayDecide: Boolean = false,
    @SerialName("own_decision") val ownDecision: String? = null,
    @SerialName("decision_note") val decisionNote: String = "",
    @SerialName("decided_by") val decidedBy: String = "",
) {
    val isOpen: Boolean get() = status in OPEN_APPLICATION_STATES
    /** Annehmen geht bei eingegangenen Anträgen, wenn alle Zustimmungen da sind und niemand abgelehnt hat. */
    val canAccept: Boolean get() = mayDecide && status == "open" && approvalsMet != false && !disputed
    val canApprove: Boolean get() = mayApprove && isOpen && ownDecision == null
}

suspend fun AdminClient.applications(): ApplicationList = decodeApi(send("GET", "admission/?status=offen"))

suspend fun AdminClient.application(id: Int): ApplicationDetail {
    require(id > 0)
    return decodeApi(send("GET", "admission/$id/"))
}

/** Zustimmen oder ablehnen als Zustimmende (Regel des Formulars). */
suspend fun AdminClient.approveApplication(id: Int, approve: Boolean, note: String): ApplicationDetail =
    decodeApi(send("POST", "admission/$id/approve/", obj("decision" to if (approve) "approve" else "reject", "note" to note.trim())))

/** Aufnehmen mit Eintrittsdatum (ISO); Mitgliedsart aus dem Antrag. */
suspend fun AdminClient.acceptApplication(id: Int, entryDate: String?, note: String, welcome: Boolean): ApplicationDetail =
    decodeApi(
        send(
            "POST",
            "admission/$id/accept/",
            obj("entry_date" to entryDate?.ifBlank { null }, "note" to note.trim(), "welcome" to welcome),
        ),
    )

suspend fun AdminClient.rejectApplication(id: Int, note: String, sendMail: Boolean): ApplicationDetail =
    decodeApi(send("POST", "admission/$id/reject/", obj("note" to note.trim(), "send_mail" to sendMail)))

/** Rückfrage an die antragstellende Person (Mail). */
suspend fun AdminClient.askApplication(id: Int, text: String): ApplicationDetail =
    decodeApi(send("POST", "admission/$id/ask/", obj("text" to text.trim())))

// --- Belegung freigeben ---

@Serializable
data class BookingRequest(
    val id: Int,
    val item: Int = 0,
    @SerialName("item_title") val itemTitle: String = "",
    val title: String = "",
    val note: String = "",
    @SerialName("starts_at") val startsAt: String = "",
    @SerialName("ends_at") val endsAt: String = "",
    @SerialName("all_day") val allDay: Boolean = false,
    val repeat: String = "",
    val status: String = "",
    @SerialName("member_name") val memberName: String = "",
    @SerialName("booked_by") val bookedBy: String = "",
    val contact: JsonObject = JsonObject(emptyMap()),
    val version: Int = 1,
    @SerialName("can_manage") val canManage: Boolean = false,
) {
    /** Wer angefragt hat: Mitglied, sonst Konto, sonst Kontakt (Gast). */
    val requester: String get() = memberName.ifBlank { bookedBy }.ifBlank { contactName }
    private val contactName: String get() = (contact["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
}

@Serializable
data class BookingRequests(val requests: List<BookingRequest> = emptyList())

/** Offene Anfragen ab heute (Server: Zeitraum für die Belegung, Anfragen unabhängig davon). */
suspend fun AdminClient.bookingRequests(today: String): List<BookingRequest> =
    decodeApi<BookingRequests>(send("GET", "bookings/?start=$today")).requests

/** [action]: "confirm" oder "reject"; 409, wenn die Buchung inzwischen geändert wurde. */
suspend fun AdminClient.decideBooking(id: Int, version: Int, action: String): BookingRequest {
    require(action == "confirm" || action == "reject")
    return decodeApi(send("POST", "bookings/$id/decision/", obj("action" to action, "version" to version)))
}

// --- Versammlung leiten ---

@Serializable
data class MeetingItem(
    val id: Int,
    @SerialName("kind_name") val kindName: String = "",
    val title: String = "",
    @SerialName("starts_at") val startsAt: String = "",
    val location: String = "",
    val status: String = "",
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_attendance") val canAttendance: Boolean = false,
    @SerialName("online_voting") val onlineVoting: Boolean = false,
    @SerialName("remote_allowed") val remoteAllowed: Boolean = false,
    @SerialName("checkin_open") val checkinOpen: Boolean = false,
    val remote: Boolean = false,
    val agenda: List<AgendaEntry> = emptyList(),
) {
    /** Leiten lässt sich, was noch nicht abgeschlossen ist (Entwurf, eingeladen, gehalten). */
    val isLive: Boolean get() = status in LIVE_MEETING_STATES
}

val LIVE_MEETING_STATES = setOf("draft", "invited", "held")

@Serializable
data class AgendaEntry(
    val id: Int,
    val number: String = "",
    val title: String = "",
    val type: String = "",
) {
    /** Abgestimmt wird nur bei Beschlüssen und Wahlen. */
    val votable: Boolean get() = type == "resolution" || type == "election"
}

@Serializable
data class AttendanceRow(
    val id: Int? = null,
    val membership: Int? = null,
    val name: String = "",
    val voter: Boolean = false,
    val status: String = "",
    val mode: String = "",
) {
    val present: Boolean get() = status == "present" || status == "delegate"
}

@Serializable
data class AttendanceList(
    val voters: Int = 0,
    val present: Int = 0,
    val represented: Int = 0,
    val guests: Int = 0,
    val online: Int = 0,
    val quorum: JsonObject? = null,
    val editable: Boolean = false,
    val rows: List<AttendanceRow> = emptyList(),
)

@Serializable
data class LeadPollOption(val id: Int, val role: String = "", val label: String = "")

@Serializable
data class LeadPollResult(val id: Int, val label: String = "", val votes: Int = 0)

@Serializable
data class LeadPoll(
    val id: Int,
    @SerialName("agenda_number") val agendaNumber: String = "",
    @SerialName("agenda_title") val agendaTitle: String = "",
    val kind: String = "",
    val question: String = "",
    val office: String = "",
    val candidate: String = "",
    val secret: Boolean = false,
    val status: String = "",
    val cast: Int = 0,
    val entitled: Int = 0,
    val options: List<LeadPollOption> = emptyList(),
    val result: List<LeadPollResult>? = null,
    val resolution: Int? = null,
)

@Serializable
data class LeadLive(
    val meeting: Int = 0,
    val organization: Int = 0,
    val status: String = "",
    @SerialName("online_voting") val onlineVoting: Boolean = false,
    @SerialName("remote_allowed") val remoteAllowed: Boolean = false,
    @SerialName("checkin_open") val checkinOpen: Boolean = false,
    val remote: Boolean = false,
    val live: Boolean = false,
    val voters: Int = 0,
    @SerialName("present_app") val presentApp: Int = 0,
    @SerialName("present_online") val presentOnline: Int = 0,
    val entitled: Int = 0,
    val polls: List<LeadPoll> = emptyList(),
    /** Nur bei offener Anmeldung: wechselnder Code für den QR-Code und Sekunden bis zum Wechsel */
    val code: String? = null,
    @SerialName("refresh_in") val refreshIn: Int = 30,
)

suspend fun AdminClient.meetings(): List<MeetingItem> = decodeApi(send("GET", "meetings/"))

suspend fun AdminClient.meeting(id: Int): MeetingItem {
    require(id > 0)
    return decodeApi(send("GET", "meetings/$id/"))
}

suspend fun AdminClient.attendance(meeting: Int): AttendanceList = decodeApi(send("GET", "meetings/$meeting/attendance/"))

/** Mitglied ([membership]) oder Gast ([name]) eintragen; [status] z. B. "present" oder "excused". */
suspend fun AdminClient.setAttendance(meeting: Int, membership: Int?, name: String?, status: String): AttendanceList =
    decodeApi(
        send(
            "POST",
            "meetings/$meeting/attendance/",
            obj("membership" to membership, "name" to name?.trim().orEmpty(), "status" to status),
        ),
    )

suspend fun AdminClient.removeAttendance(meeting: Int, entry: Int): AttendanceList =
    decodeApi(send("DELETE", "meetings/$meeting/attendance/$entry/"))

suspend fun AdminClient.meetingLive(meeting: Int): LeadLive = decodeApi(send("GET", "meetings/$meeting/live/"))

/** Anmeldung per App öffnen oder schließen, Teilnahme aus der Ferne erlauben. */
suspend fun AdminClient.setCheckin(meeting: Int, open: Boolean, remote: Boolean? = null): LeadLive =
    decodeApi(
        send(
            "POST",
            "meetings/$meeting/live/",
            if (remote == null) obj("open" to open) else obj("open" to open, "remote" to remote),
        ),
    )

/** Abstimmung zu einem Beschluss anlegen; bei Wahlen mit Amt und Kandidaten. */
suspend fun AdminClient.createPoll(
    meeting: Int,
    agendaItem: Int,
    question: String,
    secret: Boolean,
    office: String = "",
    candidates: List<String> = emptyList(),
): LeadLive = decodeApi(
    send(
        "POST",
        "meetings/$meeting/polls/",
        obj(
            "agenda_item" to agendaItem,
            "question" to question.trim(),
            "secret" to secret,
            "office" to office.trim(),
            "candidates" to JsonArray(candidates.map { it.trim() }.filter { it.isNotEmpty() }.map { obj("name" to it) }),
        ),
    ),
)

/** [action]: "open" (starten), "close" (beenden) oder "apply" (Ergebnis als Beschluss übernehmen). */
suspend fun AdminClient.pollAction(meeting: Int, poll: Int, action: String): LeadLive {
    require(action in setOf("open", "close", "apply"))
    return decodeApi(send("POST", "meetings/$meeting/polls/$poll/", obj("action" to action)))
}

suspend fun AdminClient.deletePoll(meeting: Int, poll: Int): LeadLive =
    decodeApi(send("DELETE", "meetings/$meeting/polls/$poll/"))

/**
 * Inhalt des QR-Codes für die Anmeldung per App, wie im Browser:
 * `https://<Instanz>/mein-bereich/versammlungen/da/<Versammlung>?v=<Verein>&c=<Code>` (liest [MeetingLink]).
 */
fun checkinLink(server: String, live: LeadLive): String? {
    val code = live.code?.takeIf { live.checkinOpen && it.isNotBlank() } ?: return null
    val c = java.net.URLEncoder.encode(code, "UTF-8")
    return "${server.trimEnd('/')}/mein-bereich/versammlungen/da/${live.meeting}?v=${live.organization}&c=$c"
}
