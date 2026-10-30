package de.hubiplan.app.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val GERMAN_DATE = DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT)

/** „31.12.2026“ (auch „1.2.2026“) → „2026-12-31“; ungültig → null. */
fun parseGermanDate(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return runCatching { LocalDate.parse(t, GERMAN_DATE).toString() }.getOrNull()
        ?: runCatching { LocalDate.parse(t).toString() }.getOrNull()
}

/** Felder einer Änderungsmeldung bzw. der Stammdaten in Worten. */
val FIELD_LABELS = mapOf(
    "first_name" to "Vorname",
    "last_name" to "Nachname",
    "birth_date" to "Geburtsdatum",
    "gender" to "Geschlecht",
    "club_number" to "Mitgliedsnummer",
    "email" to "E-Mail",
    "phone" to "Telefon",
    "street" to "Straße",
    "postal_code" to "PLZ",
    "city" to "Ort",
    "country" to "Land",
)

val GENDERS = listOf("" to "keine Angabe", "männlich" to "männlich", "weiblich" to "weiblich", "divers" to "divers")

fun genderLabel(value: String): String = GENDERS.firstOrNull { it.first == value }?.second ?: value

/**
 * Geänderte Stammdaten für PATCH: Text wie eingegeben (am Rand gekürzt), Geburtsdatum als ISO.
 * Gibt null zurück, wenn ein Datum ungültig ist.
 */
fun masterDataChanges(before: Map<String, String>, after: Map<String, String>): Map<String, String>? {
    val out = mutableMapOf<String, String>()
    for ((key, raw) in after) {
        val value = raw.trim()
        val old = (before[key] ?: "").trim()
        if (key == "birth_date") {
            if (value.isEmpty()) continue
            val iso = parseGermanDate(value) ?: return null
            if (iso != old) out[key] = iso
        } else if (value != old) {
            out[key] = value
        }
    }
    return out
}

/** Wert aus einer Änderungsmeldung lesbar machen (Datum, Geschlecht). */
fun displayValue(key: String, value: String?): String = when {
    value.isNullOrBlank() -> "–"
    key == "birth_date" -> formatPlainDate(value)
    key == "gender" -> genderLabel(value)
    else -> value
}

/** Beginn und Ende des Zeitraums für Geburtstage und Jubiläen: heute bis in [days] Tagen. */
fun upcomingRange(today: LocalDate, days: Long = 30): Pair<String, String> =
    today.toString() to today.plusDays(days).toString()
