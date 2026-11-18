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
) {
    /** Tarife für ein Mitglied bzw. einen Gast, die sich gerade buchen lassen. */
    fun pricesFor(member: Boolean): List<RegistrationPrice> = prices.filter { it.bookable && it.fits(member) }
}

@Serializable
data class RegistrationParticipant(
    val id: Int = 0,
    val name: String = "",
    @SerialName("membership_id") val membershipId: Int? = null,
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
) {
    val active: Boolean get() = status == "registered"
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
