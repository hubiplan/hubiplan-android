package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.hubiplan.app.tr

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

/** Einheit mit Verwaltungsrechten (Schritt A8). */
@Serializable
data class AdminUnit(
    val id: Int,
    val name: String,
    val type: String = "",
    val permissions: List<String> = emptyList(),
    /** "u": Einheit mit Rollen dort · "d": Untereinheit mit Rollen nur dort (Zugführung, B4) */
    val scope: String = "u",
    /** Rechte als Bereiche mit Stufen (Server ab November 2026): neue Codes gelten */
    val stufen: Boolean = false,
    /** Eingeschaltete Module (Server ab 08.11.2026; ältere: leer) */
    val modules: List<String> = emptyList(),
) {
    val key: String get() = "$scope:$id"
    val isDepartment: Boolean get() = scope == "d"
}

/** Untereinheit mit Rollen nur dort (z. B. Zugführung), Schritt B4. */
@Serializable
data class AdminDepartment(
    val id: Int,
    val name: String,
    @SerialName("club_name") val clubName: String = "",
    val events: Boolean = true,
    val permissions: List<String> = emptyList(),
    val stufen: Boolean = false,
) {
    fun asUnit(): AdminUnit = AdminUnit(id, name, "department", permissions, "d", stufen)
}

@Serializable
data class AdminOverview(
    val units: List<AdminUnit> = emptyList(),
    val departments: List<AdminDepartment> = emptyList(),
    @SerialName("two_factor") val twoFactor: Boolean = false,
    val unlocked: Boolean = false,
    val until: String? = null,
) {
    /** Einheiten und Untereinheiten (Zugführung) zusammen, für die Auswahl. */
    val allUnits: List<AdminUnit> get() = units + departments.map { it.asUnit() }
}

@Serializable
data class AdminUnlock(val token: String, val until: String? = null, val signed: Boolean = false)

/** Offene Aufgabe (Änderungsmeldung, Foto, Wechsel, Termin mit Rückmeldungen …). */
@Serializable
data class AdminTask(
    val kind: String = "",
    @SerialName("kind_label") val kindLabel: String = "",
    val title: String = "",
    val detail: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val link: String = "",
    /** Änderungsmeldung bzw. Foto (B2): zum Bearbeiten in der App */
    val ref: Int? = null,
    val membership: Int? = null,
)

@Serializable
data class AdminTasks(val items: List<AdminTask> = emptyList())

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
    // Härtung H7: null = Server kennt keine Schlüsselpaare (ältere Instanz)
    val key: Boolean? = null,
    @SerialName("renew_due") val renewDue: Boolean = false,
)

/** Neuer Geräteschlüssel nach der Erneuerung (H7). */
@Serializable
data class Renewed(val token: String, val device: DeviceInfo)

@Serializable
data class RenewChallenge(val challenge: String)

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
    /** Modul Wettbewerbe an: „Meine Ergebnisse“ im Profil (ältere HUBIplan-Stände: aus) */
    @SerialName("competitions_enabled") val competitionsEnabled: Boolean = false,
    /** Modul Helferdienste an: Umschalter „Helferdienste“ im Reiter Aufgaben (ältere Stände: aus) */
    @SerialName("helpers_enabled") val helpersEnabled: Boolean = false,
    /** "organization": Zugang als Ansprechpartner einer Organisation (ältere Stände: fehlt) */
    @SerialName("member_kind") val memberKind: String = "person",
    @SerialName("member_name") val memberName: String = "",
    /** Eingeschaltete Module der Einheit (Modulverzeichnis; ältere Stände: fehlt) */
    val modules: List<String>? = null,
    /** Aktives Musikerprofil in diesem Verein (Modul Musik an; ältere Stände: fehlt) */
    val musician: Boolean = false,
    @SerialName("meetings_enabled") val meetingsEnabled: Boolean = false,
    @SerialName("docs_enabled") val docsEnabled: Boolean = false,
) {
    val title: String get() = clubDisplayName.ifBlank { clubName }

    /** Bereich „Noten“: Musiker, Module Musik und Notenarchiv an (Konzept Noten in der App, E2) */
    val hasScores: Boolean get() = musician && moduleOn("music", false) && moduleOn("scores", false)

    /**
     * Ist das Modul eingeschaltet? Ältere HUBIplan-Stände liefern keine Modulliste; dann gilt
     * der einzelne Schalter ([fallback]).
     */
    fun moduleOn(id: String, fallback: Boolean = true): Boolean = modules?.contains(id) ?: fallback

    /** „Meine Ergebnisse“ im Profil */
    val hasResults: Boolean get() = moduleOn("competitions", competitionsEnabled)

    /** Umschalter „Helferdienste“ im Reiter Aufgaben */
    val hasHelpers: Boolean get() = moduleOn("helpers", helpersEnabled)

    /** Aufgaben im Mitgliederbereich */
    val hasTodos: Boolean get() = moduleOn("todos")

    /** Mitgliedsausweis oben im Reiter „Ich“ */
    val hasCard: Boolean get() = moduleOn("cards")

    /** „Rechnungen“ unter „Ich“; wie im Browser immer da (ohne Modul Rechnungen leer) */
    val hasInvoices: Boolean get() = moduleOn("billing")

    /** „Versammlungen“ unter „Ich“ */
    val hasMeetings: Boolean get() = moduleOn("meetings", meetingsEnabled)

    /** „Dokumente“ unter „Ich“ (nur lesen) */
    val hasDocs: Boolean get() = moduleOn("docs", docsEnabled)

    val isContact: Boolean get() = memberKind == ORGANIZATION && memberName.isNotBlank()

    /** Für die Auswahl: bei Ansprechpartnern auch die Organisation. */
    val label: String
        get() = if (isContact) tr("{club}: {member} (Ansprechpartner)", "club" to title, "member" to memberName) else title
}

/** Wert von `kind` für Organisationsmitglieder (Firma, Verein, Behörde …). */
const val ORGANIZATION = "organization"

/** „Meine Ergebnisse“: eigene Starts und Titel (GET /api/portal/<Zugang>/results/). */
@Serializable
data class MyResults(
    val starts: List<MyStart> = emptyList(),
    val grants: List<MyGrant> = emptyList(),
)

@Serializable
data class MyStart(
    val competition: Int,
    val title: String,
    val date: String,
    val status: String = "",
    @SerialName("status_label") val statusLabel: String = "",
    @SerialName("unit_name") val unitName: String = "",
    val results: List<MyPlacing> = emptyList(),
)

@Serializable
data class MyPlacing(
    val scoring: String,
    val place: Int? = null,
    @SerialName("value_text") val valueText: String = "",
    val field: Int? = null,
    /** Einheit des Werts („Ringe“, „Teiler“); ältere Server senden sie nicht. */
    @SerialName("unit_label") val unitLabel: String = "",
)

@Serializable
data class MyGrant(
    val id: Int,
    val year: Int,
    @SerialName("full_title") val fullTitle: String = "",
    @SerialName("scoring_name") val scoringName: String = "",
    val place: Int? = null,
    @SerialName("competition_title") val competitionTitle: String = "",
    @SerialName("trophy_name") val trophyName: String = "",
)

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
data class MyResponse(val status: String, val guests: Int = 0, val note: String = "", val source: String = "")

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
    // Je Termin einschaltbar: „Vielleicht“ und Bemerkung (mit eigener Frage)
    @SerialName("maybe_allowed") val maybeAllowed: Boolean = false,
    @SerialName("note_allowed") val noteAllowed: Boolean = false,
    @SerialName("note_question") val noteQuestion: String = "",
)

/** Routenlinks zum Ort (OpenStreetMap, Google Maps). */
@Serializable
data class MapLinks(val osm: String? = null, val google: String? = null)

/** Anhang eines Termins. */
@Serializable
data class EventFile(
    val id: Int,
    val name: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String = "",
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
    val directions: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("map_links") val mapLinks: MapLinks = MapLinks(),
    val attachments: List<EventFile> = emptyList(),
)

@Serializable
data class CircularItem(
    val id: Int,
    val subject: String,
    val sender: String = "",
    @SerialName("kind_label") val kindLabel: String = "",
    @SerialName("sent_at") val sentAt: String? = null,
    val body: String? = null,
    val attachments: List<CircularFile> = emptyList(),
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
    val kind: String = "person",
    @SerialName("display_name") val displayName: String = "",
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
) {
    val isOrganization: Boolean get() = kind == ORGANIZATION

    val name: String get() = if (isOrganization) displayName.ifBlank { lastName } else "$firstName $lastName".trim()
}

@Serializable
data class PhotoVersion(val url: String, @SerialName("thumbnail_url") val thumbnailUrl: String = "")

@Serializable
data class PhotoState(
    @SerialName("consent_at") val consentAt: String? = null,
    val approved: PhotoVersion? = null,
    val pending: PhotoVersion? = null,
)

/** Eigener Mitgliedsausweis (`/api/portal/<Zugang>/card/`). */
@Serializable
data class CardState(
    /** "none", "active" oder "revoked" */
    val state: String = "none",
    /** Inhalt des QR-Codes (Prüfseite des Ausweises) */
    val url: String? = null,
    val display: CardDisplay? = null,
    /** Welche Wallet der Betreiber eingerichtet und die Einheit eingeschaltet hat */
    val wallet: CardWallet? = null,
)

@Serializable
data class CardWallet(
    val google: Boolean = false,
    val apple: Boolean = false,
)

@Serializable
data class CardDisplay(
    /** "organization": Ausweis einer Organisation, Name ganz in [lastName], Logo statt Foto */
    val kind: String = "person",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    /** "active", "future" oder "inactive" */
    val status: String = "inactive",
    @SerialName("status_label") val statusLabel: String = "",
    @SerialName("member_type") val memberType: String? = null,
    val club: String = "",
    val federation: String? = null,
    @SerialName("department_label") val departmentLabel: String = "",
    val departments: List<String> = emptyList(),
    @SerialName("club_number") val clubNumber: String = "",
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    val theme: CardTheme? = null,
) {
    val isOrganization: Boolean get() = kind == ORGANIZATION

    val name: String get() = "$firstName $lastName".trim()
}

@Serializable
data class CardTheme(
    val primary: String = "",
    @SerialName("on_primary") val onPrimary: String = "",
)

/** Sprache des Kontos und auf dem Server wählbare Sprachen (/api/app/v1/me/language/). */
@Serializable
data class LanguageOption(val code: String, val name: String)

@Serializable
data class LanguageState(
    val languages: List<LanguageOption> = emptyList(),
    val default: String = "de",
    val language: String? = null,
)

@Serializable
data class CircularFile(val id: Int, val name: String, val size: Long = 0)
