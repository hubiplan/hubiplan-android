package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
    /** Stufe A5: Verpflegung, Minderjährige, Zusatzleistungen und Zusatzfragen */
    val catering: Boolean = false,
    val minors: Boolean = false,
    @SerialName("health_allowed") val healthAllowed: Boolean = false,
    val addons: List<AddOn> = emptyList(),
    val questions: List<Question> = emptyList(),
) {
    /** Zusatzleistungen je Person ("person") oder je Anmeldung ("registration") */
    fun addonsFor(per: String): List<AddOn> = addons.filter { it.isActive && it.per == per }

    /** Zusatzfragen je Person oder Anmeldung; Allergien nur, wenn es Essen gibt (F11) */
    fun questionsFor(per: String): List<Question> =
        questions.filter { it.isActive && it.per == per && (it.kind != "health" || healthAllowed) }

    /** Fragt die Anmeldung nach Allergien und Unverträglichkeiten? */
    val asksHealth: Boolean get() = questionsFor("person").any { it.kind == "health" }

    /** Tarife für ein Mitglied bzw. einen Gast, die sich gerade buchen lassen. */
    fun pricesFor(member: Boolean): List<RegistrationPrice> = prices.filter { it.bookable && it.fits(member) }

    /** Anmelden geht: offen, oder voll mit Warteliste (dann auf die Warteliste). */
    val canJoin: Boolean get() = state == "open" || (state == "full" && waitlist)

    /** Neue Anmeldungen kommen auf die Warteliste. */
    val toWaitlist: Boolean get() = state == "full" && waitlist
}

/** Zusatzleistung mit Auswahl, z. B. Busfahrt oder Menü (Stufe A5). */
@Serializable
data class AddOn(
    val id: Int = 0,
    val name: String = "",
    @SerialName("name_en") val nameEn: String = "",
    /** meal, other */
    val kind: String = "other",
    /** person, registration */
    val per: String = "person",
    val required: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    val choices: List<AddOnChoice> = emptyList(),
) {
    fun label(english: Boolean): String = if (english && nameEn.isNotBlank()) nameEn else name
}

@Serializable
data class AddOnChoice(
    val id: Int = 0,
    val name: String = "",
    @SerialName("name_en") val nameEn: String = "",
    val amount: String = "0",
    val capacity: Int? = null,
    val free: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true,
) {
    fun label(english: Boolean): String = if (english && nameEn.isNotBlank()) nameEn else name

    val full: Boolean get() = free == 0

    val price: BigDecimal get() = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
}

/** Zusatzfrage; "health" sind Allergien und Unverträglichkeiten (freiwillig, mit Einwilligung). */
@Serializable
data class Question(
    val id: Int = 0,
    val text: String = "",
    @SerialName("text_en") val textEn: String = "",
    /** text, yesno, choice, health */
    val kind: String = "text",
    val choices: List<String> = emptyList(),
    val required: Boolean = false,
    val per: String = "registration",
    @SerialName("is_active") val isActive: Boolean = true,
) {
    fun label(english: Boolean): String = if (english && textEn.isNotBlank()) textEn else text
}

@Serializable
data class Booking(
    val id: Int = 0,
    @SerialName("choice_id") val choiceId: Int = 0,
    @SerialName("addon_id") val addonId: Int = 0,
    val name: String = "",
    val amount: String = "0",
)

/** Erziehungsberechtigte Person bei Teilnehmenden unter 18 (F13). */
@Serializable
data class Guardian(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
) {
    val complete: Boolean get() = name.isNotBlank() && phone.isNotBlank() && email.isNotBlank()
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
    val bookings: List<Booking> = emptyList(),
    val guardians: List<Guardian> = emptyList(),
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
    /** Eintrittskarten mit QR-Code als PDF (A6; ältere Server: keine) */
    @SerialName("has_tickets") val hasTickets: Boolean = false,
    /** Zusatzleistungen je Anmeldung */
    val bookings: List<Booking> = emptyList(),
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
    /** unter 18 am Veranstaltungstag (nur mit „Minderjährige möglich“) */
    val minor: Boolean = false,
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
    val extras: RegistrationExtras = RegistrationExtras(),
    /** Erziehungsberechtigte nötig (unter 18 bei „Minderjährige möglich“) */
    val minor: Boolean = false,
)

/**
 * Zusatzangaben einer Person (oder der ganzen Anmeldung): Zusatzleistung → Auswahl,
 * Frage → Antwort (Text, Ja/Nein oder Auswahl), Allergien, Erziehungsberechtigte.
 */
data class RegistrationExtras(
    val addons: Map<Int, Int> = emptyMap(),
    val answers: Map<Int, JsonPrimitive> = emptyMap(),
    val health: String = "",
    val guardians: List<Guardian> = emptyList(),
)

private fun extrasJson(x: RegistrationExtras, minor: Boolean, health: Boolean): Map<String, JsonElement> = buildMap {
    if (x.addons.isNotEmpty()) {
        put("addons", JsonObject(x.addons.map { (k, v) -> k.toString() to JsonPrimitive(v) }.toMap()))
    }
    val answers = x.answers.filterValues { !(it.isString && it.content.isBlank()) }
    if (answers.isNotEmpty()) {
        put("answers", JsonObject(answers.map { (k, v) -> k.toString() to (if (v.isString) JsonPrimitive(v.content.trim()) else v) }.toMap()))
    }
    if (health && x.health.isNotBlank()) put("health", JsonPrimitive(x.health.trim()))
    if (minor) {
        put(
            "guardians",
            JsonArray(
                x.guardians.filter { it.name.isNotBlank() || it.phone.isNotBlank() }.map { g ->
                    JsonObject(
                        mapOf(
                            "name" to JsonPrimitive(g.name.trim()),
                            "phone" to JsonPrimitive(g.phone.trim()),
                            "email" to JsonPrimitive(g.email.trim()),
                            "address" to JsonPrimitive(g.address.trim()),
                        ),
                    )
                },
            ),
        )
    }
}

/** Aufpreis der gewählten Zusatzleistungen. */
fun extrasSum(offer: RegistrationOffer, x: RegistrationExtras): BigDecimal =
    x.addons.entries.fold(BigDecimal.ZERO) { sum, (addon, choice) ->
        sum + (offer.addons.firstOrNull { it.id == addon }?.choices?.firstOrNull { it.id == choice }?.price ?: BigDecimal.ZERO)
    }

/** Fehlt eine Pflichtangabe (Pflicht-Zusatzleistung, Pflichtfrage, Erziehungsberechtigte)? */
fun extrasGap(offer: RegistrationOffer, x: RegistrationExtras, per: String, minor: Boolean): RegistrationGap? = when {
    offer.addonsFor(per).any { it.required && x.addons[it.id] == null } -> RegistrationGap.ADDON
    offer.questionsFor(per).any { q ->
        q.required && q.kind != "health" && x.answers[q.id].let { it == null || (it.isString && it.content.isBlank()) }
    } -> RegistrationGap.ANSWER
    minor && x.guardians.firstOrNull()?.complete != true -> RegistrationGap.GUARDIAN
    else -> null
}

/** Inhalt von POST …/registration/ (Kennzeichen `app`: angemeldet über die App). */
fun registrationPayload(
    persons: List<RegistrationChoice>,
    note: String,
    termsAccepted: Boolean,
    extras: RegistrationExtras = RegistrationExtras(),
    healthConsent: Boolean = false,
    asksHealth: Boolean = false,
): JsonObject =
    JsonObject(
        buildMap {
            put(
                "persons",
                JsonArray(
                    persons.map { p ->
                        JsonObject(
                            buildMap {
                                put("membership_id", p.membershipId?.let { JsonPrimitive(it) } ?: JsonNull)
                                if (p.membershipId == null) put("name", JsonPrimitive(p.name.trim()))
                                put("price_id", JsonPrimitive(p.priceId))
                                p.birthDate?.let { put("birth_date", JsonPrimitive(it)) }
                                putAll(extrasJson(p.extras, p.minor, asksHealth))
                            },
                        )
                    },
                ),
            )
            put("note", JsonPrimitive(note.trim()))
            put("terms_accepted", JsonPrimitive(termsAccepted))
            putAll(extrasJson(extras, minor = false, health = false))
            if (healthConsent) put("health_consent", JsonPrimitive(true))
            put("app", JsonPrimitive(true))
        },
    )

/**
 * Voraussichtliche Summe nach den aktuellen Preisstufen. Der Server rechnet verbindlich; reicht
 * eine Early-Bird-Stufe nicht für alle, kann die Rechnung etwas höher ausfallen.
 */
fun registrationSum(
    offer: RegistrationOffer,
    persons: List<RegistrationChoice>,
    extras: RegistrationExtras = RegistrationExtras(),
): BigDecimal =
    persons.fold(extrasSum(offer, extras)) { sum, p ->
        val amount = offer.prices.firstOrNull { it.id == p.priceId }?.current?.amount?.toBigDecimalOrNull()
        sum + (amount ?: BigDecimal.ZERO) + extrasSum(offer, p.extras)
    }

/** Bitte die Lücke im Formular nennen, sonst null (Prüfung wie auf dem Server, vorab). */
fun registrationMissing(
    offer: RegistrationOffer,
    persons: List<RegistrationChoice>,
    needsBirthDate: (RegistrationChoice) -> Boolean,
    termsAccepted: Boolean,
    extras: RegistrationExtras = RegistrationExtras(),
    healthConsent: Boolean = false,
): RegistrationGap? = when {
    persons.isEmpty() -> RegistrationGap.NOBODY
    persons.size > offer.maxPersons -> RegistrationGap.TOO_MANY
    persons.any { it.membershipId == null && it.name.isBlank() } -> RegistrationGap.NAME
    persons.any { needsBirthDate(it) && it.birthDate == null } -> RegistrationGap.BIRTH_DATE
    else ->
        persons.firstNotNullOfOrNull { extrasGap(offer, it.extras, "person", it.minor) }
            ?: extrasGap(offer, extras, "registration", minor = false)
            ?: when {
                offer.asksHealth && persons.any { it.extras.health.isNotBlank() } && !healthConsent -> RegistrationGap.CONSENT
                offer.terms.isNotBlank() && !termsAccepted -> RegistrationGap.TERMS
                else -> null
            }
}

enum class RegistrationGap { NOBODY, TOO_MANY, NAME, BIRTH_DATE, ADDON, ANSWER, GUARDIAN, CONSENT, TERMS }

/** Alter am ersten Tag unter 18? [birth] und [start] als ISO-Datum („2012-05-04“). */
fun isMinor(birth: String?, start: String?): Boolean {
    if (birth == null || start == null) return false
    val b = runCatching { java.time.LocalDate.parse(birth.take(10)) }.getOrNull() ?: return false
    val s = runCatching { java.time.LocalDate.parse(start.take(10)) }.getOrNull() ?: return false
    return java.time.Period.between(b, s).years < 18
}

/** Gebuchte Zusatzleistungen als Zeile: „Busfahrt (12,00 €), Menü: Lachs (3,00 €)“. */
fun bookingLine(bookings: List<Booking>, money: (String) -> String): String =
    bookings.joinToString(", ") { b ->
        if ((b.amount.toBigDecimalOrNull()?.signum() ?: 0) > 0) "${b.name} (${money(b.amount)})" else b.name
    }

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
