package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * Verbindliche Anmeldung mit Teilnahmegebühr am Termin (Modul „Anmeldungen“,
 * GET/POST /api/portal/<Zugang>/events/<id>/registration/, Beschreibung in hubiplan/docs/app.md).
 * Nichts davon wird auf dem Gerät gespeichert.
 */
@Serializable
data class RegistrationBrief(
    val enabled: Boolean = false,
    /** not_yet, open, closed, full, cancelled */
    val state: String? = null,
    @SerialName("state_text") val stateText: String = "",
    @SerialName("closes_at") val closesAt: String? = null,
    val free: Int? = null,
    @SerialName("can_register") val canRegister: Boolean = false,
)

@Serializable
data class RegistrationPriceNow(
    val id: Int = 0,
    val name: String = "",
    val amount: String = "0",
    @SerialName("until_date") val untilDate: String? = null,
    val remaining: Int? = null,
)

@Serializable
data class RegistrationPriceNext(
    val name: String = "",
    val amount: String = "0",
    @SerialName("until_date") val untilDate: String? = null,
)

@Serializable
data class RegistrationPrice(
    val id: Int,
    val name: String = "",
    @SerialName("name_en") val nameEn: String = "",
    /** all, members, guests */
    val applies: String = "all",
    @SerialName("age_from") val ageFrom: Int? = null,
    @SerialName("age_to") val ageTo: Int? = null,
    val free: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    val current: RegistrationPriceNow? = null,
    val next: RegistrationPriceNext? = null,
) {
    /** Name in der Sprache der App; ohne englischen Namen der deutsche. */
    fun label(english: Boolean): String = if (english && nameEn.isNotBlank()) nameEn else name

    val hasAge: Boolean get() = ageFrom != null || ageTo != null

    /** Gerade buchbar: aktiv, mit aktueller Preisstufe und nicht ausgebucht. */
    val bookable: Boolean get() = isActive && current != null && (free == null || free > 0)

    fun fits(member: Boolean): Boolean = when (applies) {
        "members" -> member
        "guests" -> !member
        else -> true
    }
}

@Serializable
data class RegistrationOffer(
    val id: Int = 0,
    val state: String = "open",
    @SerialName("state_text") val stateText: String = "",
    @SerialName("opens_at") val opensAt: String? = null,
    @SerialName("closes_at") val closesAt: String? = null,
    @SerialName("is_open") val isOpen: Boolean = true,
    val capacity: Int? = null,
    val free: Int? = null,
    val taken: Int = 0,
    @SerialName("max_persons") val maxPersons: Int = 1,
    @SerialName("guests_allowed") val guestsAllowed: Boolean = false,
    val terms: String = "",
    val prices: List<RegistrationPrice> = emptyList(),
    /** Warteliste, wenn alle Plätze vergeben sind (Stufe A3) */
    val waitlist: Boolean = false,
    /** Anmeldungen auf der Warteliste */
    val waiting: Int = 0,
    @SerialName("substitute_allowed") val substituteAllowed: Boolean = false,
    @SerialName("cancel_rules") val cancelRules: List<CancelRule> = emptyList(),
    @SerialName("cancel_fee_fixed") val cancelFeeFixed: String? = null,
) {
    /** Tarife für ein Mitglied bzw. einen Gast, die sich gerade buchen lassen. */
    fun pricesFor(member: Boolean): List<RegistrationPrice> = prices.filter { it.bookable && it.fits(member) }

    /** Anmelden geht: offen, oder voll mit Warteliste (dann auf die Warteliste). */
    val canJoin: Boolean get() = state == "open" || (state == "full" && waitlist)

    /** Neue Anmeldungen kommen auf die Warteliste. */
    val toWaitlist: Boolean get() = state == "full" && waitlist
}

/** Stornostaffel: „weniger als [days] Tage vorher: [percent] %“ */
@Serializable
data class CancelRule(val days: Int = 0, val percent: Int = 0)

/** Vorschau der Stornogebühr (GET …/cancel/?participants=…). */
@Serializable
data class FeeQuote(
    val base: String = "0",
    val percent: Int = 0,
    val fee: String = "0",
    /** warum kostenfrei: "", waitlist, moved_up, event_cancelled */
    @SerialName("free_reason") val freeReason: String = "",
) {
    val hasFee: Boolean get() = (fee.toBigDecimalOrNull()?.signum() ?: 0) > 0
}

@Serializable
data class RegistrationParticipant(
    val id: Int = 0,
    val name: String = "",
    @SerialName("membership_id") val membershipId: Int? = null,
    @SerialName("price_id") val priceId: Int = 0,
    @SerialName("price_name") val priceName: String = "",
    val amount: String = "0",
    /** active, cancelled */
    val status: String = "active",
)

@Serializable
data class RegistrationInvoice(
    val id: Int = 0,
    val number: String = "",
    val status: String = "",
    val total: String = "0",
    val paid: String = "0",
    @SerialName("due_date") val dueDate: String? = null,
)

@Serializable
data class MyRegistration(
    val id: Int,
    val number: String = "",
    /** registered, cancelled */
    val status: String = "registered",
    val total: String = "0",
    /** free, open, partly, paid, cancelled */
    @SerialName("paid_state") val paidState: String = "open",
    val participants: List<RegistrationParticipant> = emptyList(),
    val invoice: RegistrationInvoice? = null,
    @SerialName("waitlist_position") val waitlistPosition: Int? = null,
    @SerialName("moved_up_at") val movedUpAt: String? = null,
    @SerialName("free_cancel_until") val freeCancelUntil: String? = null,
    @SerialName("cancel_fee") val cancelFee: String = "0",
    @SerialName("fee_invoice") val feeInvoice: RegistrationInvoice? = null,
    /** Ältere Server ohne Angabe: dann gilt `can_cancel` des Angebots */
    @SerialName("can_cancel") val canCancel: Boolean? = null,
    @SerialName("can_substitute") val canSubstitute: Boolean = false,
) {
    /** Angemeldet oder auf der Warteliste (nicht abgemeldet) */
    val active: Boolean get() = status == "registered" || status == "waiting"

    val waiting: Boolean get() = status == "waiting"

    val activeParticipants: List<RegistrationParticipant> get() = participants.filter { it.status == "active" }
}

@Serializable
data class RegistrationPerson(
    @SerialName("membership_id") val membershipId: Int,
    val name: String = "",
    val self: Boolean = false,
    val registered: Boolean = false,
    @SerialName("has_birth_date") val hasBirthDate: Boolean = false,
)

@Serializable
data class MemberRegistration(
    @SerialName("event_id") val eventId: Int = 0,
    val offer: RegistrationOffer = RegistrationOffer(),
    @SerialName("can_cancel") val canCancel: Boolean = false,
    val persons: List<RegistrationPerson> = emptyList(),
    val mine: List<MyRegistration> = emptyList(),
)

/** Eine Person im Anmeldeformular: Mitglied (mit [membershipId]) oder Gast (mit [name]). */
data class RegistrationChoice(
    val membershipId: Int?,
    val name: String,
    val priceId: Int,
    /** ISO-Datum, nur bei Tarifen mit Altersgrenze ohne bekanntes Geburtsdatum */
    val birthDate: String? = null,
)

/** Inhalt von POST …/registration/ (Kennzeichen `app`: angemeldet über die App). */
fun registrationPayload(persons: List<RegistrationChoice>, note: String, termsAccepted: Boolean): JsonObject =
    JsonObject(
        mapOf(
            "persons" to JsonArray(
                persons.map { p ->
                    JsonObject(
                        buildMap {
                            put("membership_id", p.membershipId?.let { JsonPrimitive(it) } ?: JsonNull)
                            if (p.membershipId == null) put("name", JsonPrimitive(p.name.trim()))
                            put("price_id", JsonPrimitive(p.priceId))
                            p.birthDate?.let { put("birth_date", JsonPrimitive(it)) }
                        },
                    )
                },
            ),
            "note" to JsonPrimitive(note.trim()),
            "terms_accepted" to JsonPrimitive(termsAccepted),
            "app" to JsonPrimitive(true),
        ),
    )

/**
 * Voraussichtliche Summe nach den aktuellen Preisstufen. Der Server rechnet verbindlich; reicht
 * eine Early-Bird-Stufe nicht für alle, kann die Rechnung etwas höher ausfallen.
 */
fun registrationSum(offer: RegistrationOffer, persons: List<RegistrationChoice>): BigDecimal =
    persons.fold(BigDecimal.ZERO) { sum, p ->
        val amount = offer.prices.firstOrNull { it.id == p.priceId }?.current?.amount?.toBigDecimalOrNull()
        sum + (amount ?: BigDecimal.ZERO)
    }

/** Bitte die Lücke im Formular nennen, sonst null (Prüfung wie auf dem Server, vorab). */
fun registrationMissing(
    offer: RegistrationOffer,
    persons: List<RegistrationChoice>,
    needsBirthDate: (RegistrationChoice) -> Boolean,
    termsAccepted: Boolean,
): RegistrationGap? = when {
    persons.isEmpty() -> RegistrationGap.NOBODY
    persons.size > offer.maxPersons -> RegistrationGap.TOO_MANY
    persons.any { it.membershipId == null && it.name.isBlank() } -> RegistrationGap.NAME
    persons.any { needsBirthDate(it) && it.birthDate == null } -> RegistrationGap.BIRTH_DATE
    offer.terms.isNotBlank() && !termsAccepted -> RegistrationGap.TERMS
    else -> null
}

enum class RegistrationGap { NOBODY, TOO_MANY, NAME, BIRTH_DATE, TERMS }

/** Inhalt von POST …/cancel/: leer für alle, sonst nur die genannten Personen. */
fun cancelPayload(participants: List<Int>): JsonObject =
    JsonObject(if (participants.isEmpty()) emptyMap() else mapOf("participants" to JsonArray(participants.map { JsonPrimitive(it) })))

/** Abfrage der Gebührenvorschau: „?participants=4,5“ oder leer für alle. */
fun cancelQuery(participants: List<Int>): String =
    if (participants.isEmpty()) "" else "?participants=" + participants.joinToString(",")

/** Inhalt von POST …/participants/<id>/: Mitglied der Familie oder Gast mit Namen. */
fun substitutePayload(membershipId: Int?, name: String, birthDate: String?): JsonObject =
    JsonObject(
        buildMap {
            put("membership_id", membershipId?.let { JsonPrimitive(it) } ?: JsonNull)
            if (membershipId == null) put("name", JsonPrimitive(name.trim()))
            birthDate?.let { put("birth_date", JsonPrimitive(it)) }
        },
    )
