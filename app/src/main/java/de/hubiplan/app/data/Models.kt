package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Antworten von HUBIplan (/api/app/v1/ und Mitgliederbereich /api/portal/…).
// Unbekannte Felder werden ignoriert, damit neue Server-Versionen die App nicht stören.

@Serializable
data class Instance(
    val product: String,
    @SerialName("api_version") val apiVersion: Int,
    @SerialName("min_app_version") val minAppVersion: Int = 1,
    val url: String = "",
    val push: List<PushWay> = emptyList(),
)

/** Push-Weg der Instanz (UnifiedPush mit VAPID-Schlüssel, später Gateway für Play). */
@Serializable
data class PushWay(
    val kind: String,
    @SerialName("vapid_public_key") val vapidPublicKey: String? = null,
    val gateway: String? = null,
)

@Serializable
data class AppUser(val email: String, val display: String)

@Serializable
data class DeviceInfo(
    val id: Int,
    val name: String,
    val platform: String = "android",
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    val current: Boolean = false,
)

@Serializable
data class Paired(val token: String, val device: DeviceInfo, val user: AppUser)

@Serializable
data class Theme(
    val primary: String,
    @SerialName("on_primary") val onPrimary: String,
    val accent: String,
    @SerialName("on_accent") val onAccent: String,
    @SerialName("logo_url") val logoUrl: String? = null,
)

/** Zugang zum Mitgliederbereich einer Einheit. */
@Serializable
data class PortalAccess(
    val id: Int,
    @SerialName("club_name") val clubName: String,
    @SerialName("club_display_name") val clubDisplayName: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val theme: Theme? = null,
    @SerialName("events_enabled") val eventsEnabled: Boolean = true,
    @SerialName("imprint_key") val imprintKey: String? = null,
) {
    val title: String get() = clubDisplayName.ifBlank { clubName }
}

@Serializable
data class Me(val user: AppUser, val device: DeviceInfo, val portal: List<PortalAccess>)

@Serializable
data class Category(val name: String = "", val color: String? = null)

/** Einzeltermin (bei Serien eine Wiederholung). */
@Serializable
data class Occurrence(
    val key: String,
    @SerialName("event_id") val eventId: Int,
    val title: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("all_day") val allDay: Boolean = false,
    val location: String = "",
    val address: String = "",
    val category: Category? = null,
    @SerialName("is_cancelled") val isCancelled: Boolean = false,
    val note: String = "",
    val owner: String = "",
    @SerialName("rsvp_enabled") val rsvpEnabled: Boolean = false,
    @SerialName("my_response") val myResponse: String? = null,
    @SerialName("rsvp_deadline") val rsvpDeadline: String? = null,
)

@Serializable
data class EventList(val items: List<Occurrence>)

@Serializable
data class MyResponse(val status: String, val guests: Int = 0, val note: String = "")

@Serializable
data class Rsvp(
    val enabled: Boolean = false,
    val deadline: String? = null,
    val capacity: Int? = null,
    @SerialName("guests_allowed") val guestsAllowed: Boolean = false,
    @SerialName("max_guests") val maxGuests: Int = 10,
    val free: Int? = null,
    val mine: MyResponse? = null,
    @SerialName("can_respond") val canRespond: Boolean = false,
    val reason: String? = null,
)

@Serializable
data class EventDetail(
    val id: Int,
    val title: String,
    val description: String = "",
    val location: String = "",
    val address: String = "",
    @SerialName("all_day") val allDay: Boolean = false,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("is_cancelled") val isCancelled: Boolean = false,
    @SerialName("cancel_note") val cancelNote: String = "",
    val owner: String = "",
    val rsvp: Rsvp = Rsvp(),
)

@Serializable
data class CircularItem(
    val id: Int,
    val subject: String,
    val sender: String = "",
    @SerialName("kind_label") val kindLabel: String = "",
    @SerialName("sent_at") val sentAt: String? = null,
    val body: String? = null,
    // Versandweg: app, mail, letter, portal
    val channel: String = "",
)

/** Einheit, an deren Mitglieder dieses Konto Kurzmitteilungen senden darf. */
@Serializable
data class Sender(
    val key: String,
    val name: String,
    val type: String,
    val id: Int,
    val club: String = "",
)

@Serializable
data class Senders(val senders: List<Sender> = emptyList(), @SerialName("max_body") val maxBody: Int = 500)

/** Antwort auf Vorschau (counts) bzw. Senden (sent). */
@Serializable
data class SendResult(
    val counts: Map<String, Int> = emptyMap(),
    val sent: Map<String, Int> = emptyMap(),
    val id: Int? = null,
)

@Serializable
data class Contact(
    val email: String = "",
    val phone: String = "",
    val street: String = "",
    @SerialName("postal_code") val postalCode: String = "",
    val city: String = "",
    val country: String = "",
)

@Serializable
data class ChangeRequestItem(
    val id: Int,
    val data: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val message: String = "",
    val status: String = "",
    @SerialName("status_label") val statusLabel: String = "",
    val response: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

/** Eigene Daten im Mitgliederbereich. */
@Serializable
data class Profile(
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("club_number") val clubNumber: String = "",
    @SerialName("member_type") val memberType: String? = null,
    @SerialName("entry_date") val entryDate: String? = null,
    val departments: List<String> = emptyList(),
    val contact: Contact? = null,
    val status: String = "",
    @SerialName("contact_changes_need_approval") val changesNeedApproval: Boolean = false,
    @SerialName("change_requests") val changeRequests: List<ChangeRequestItem> = emptyList(),
)

@Serializable
data class PhotoVersion(val url: String, @SerialName("thumbnail_url") val thumbnailUrl: String = "")

@Serializable
data class PhotoState(
    @SerialName("consent_at") val consentAt: String? = null,
    val approved: PhotoVersion? = null,
    val pending: PhotoVersion? = null,
)
