package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Auslagen erstatten im Mitgliederbereich (GET /api/portal/<Zugang>/expenses/, Beschreibung in
 * hubiplan/docs/auslagen.md): selbst bezahlte Ausgaben mit Foto des Belegs melden. Nichts davon
 * bleibt dauerhaft auf dem Gerät; Fotos liegen nur bis zum Senden im Cache.
 */
@Serializable
data class ExpenseOverview(
    val claims: List<ExpenseClaim> = emptyList(),
    val options: ExpenseOptions = ExpenseOptions(),
)

@Serializable
data class ExpenseOptions(
    @SerialName("may_submit") val maySubmit: Boolean = false,
    @SerialName("mileage_enabled") val mileageEnabled: Boolean = false,
    @SerialName("km_rate") val kmRate: String = "0.30",
    val payee: ExpensePayee? = null,
)

@Serializable
data class ExpensePayee(val holder: String = "", val iban: String = "")

@Serializable
data class ExpenseLine(val id: Int? = null, val amount: String = "0", val text: String = "")

@Serializable
data class ExpenseFile(val id: Int, val name: String = "")

@Serializable
data class ExpenseEvent(val kind: String = "", val label: String = "", val who: String = "", val text: String = "", val at: String = "")

@Serializable
data class ExpenseClaim(
    val id: Int,
    val number: String = "",
    /** expense, mileage, advance */
    val kind: String = "expense",
    /** draft, submitted, question, confirmed, approved, paid, rejected, withdrawn, waived */
    val status: String = "draft",
    @SerialName("status_label") val statusLabel: String = "",
    val purpose: String = "",
    @SerialName("purchase_date") val purchaseDate: String? = null,
    val payout: String = "bank",
    val route: String = "",
    val km: String? = null,
    val total: String = "0",
    @SerialName("amount_due") val amountDue: String = "0",
    @SerialName("approved_amount") val approvedAmount: String? = null,
    @SerialName("reduce_reason") val reduceReason: String = "",
    @SerialName("reject_reason") val rejectReason: String = "",
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val version: Int = 0,
    val lines: List<ExpenseLine> = emptyList(),
    val payee: ExpensePayee? = null,
    val files: List<ExpenseFile> = emptyList(),
    val events: List<ExpenseEvent> = emptyList(),
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_withdraw") val canWithdraw: Boolean = false,
    val problems: Map<String, String> = emptyMap(),
) {
    /** Wartet auf eine Antwort des Mitglieds (Plakette unter „Ich“). */
    val asksMe: Boolean get() = status == "question"

    /** Letzte Rückfrage der Kasse */
    val question: String? get() = events.lastOrNull { it.kind == "question" }?.text

    /** Belege lassen sich nur bei Entwurf und Rückfrage ergänzen. */
    val canAddFile: Boolean get() = canEdit && kind == "expense"

    /** Ein Entwurf ohne Nummer wird beim Zurückziehen gelöscht. */
    val isLooseDraft: Boolean get() = status == "draft" && number.isBlank()
}

/** Angaben des Formulars, wie sie die App vor dem Senden hält. */
data class ExpenseDraft(
    val kind: String = "expense",
    val purpose: String = "",
    val date: String = "",
    /** Beträge wie eingegeben, je Position („23,40“) */
    val amounts: List<String> = listOf(""),
    val route: String = "",
    val km: String = "",
    val payout: String = "bank",
    val holder: String = "",
    val iban: String = "",
)

/** Kilometer „12,5“ → „12.5“; sonst null. */
fun parseKm(text: String): String? {
    val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0 || value > java.math.BigDecimal("5000")) return null
    return value.stripTrailingZeros().toPlainString()
}

/** IBAN ohne Leerzeichen, groß geschrieben. */
fun cleanIban(text: String): String = text.filterNot { it.isWhitespace() }.uppercase()

/**
 * Formularfelder für POST /api/portal/<Zugang>/expenses/ (mehrteilig: Positionen und
 * Bankverbindung als JSON-Text, Belege als „file“). [parse] liest einen Betrag wie
 * `parseAmount`; ungültige Beträge fehlen, der Server meldet dann „Bitte den Betrag angeben“.
 */
fun ExpenseDraft.fields(submit: Boolean, parse: (String) -> String?): Map<String, String> {
    val out = linkedMapOf(
        "kind" to kind,
        "purpose" to purpose.trim(),
        "payout" to payout,
        "source" to "app",
    )
    if (kind != "advance") out["purchase_date"] = date
    if (kind == "mileage") {
        out["route"] = route.trim()
        out["km"] = parseKm(km).orEmpty()
    } else {
        val lines = amounts.mapNotNull(parse).map { JsonObject(mapOf("amount" to JsonPrimitive(it), "text" to JsonPrimitive(""))) }
        out["lines"] = JsonArray(lines).toString()
    }
    out["payee"] = if (payout == "bank") {
        JsonObject(mapOf("holder" to JsonPrimitive(holder.trim()), "iban" to JsonPrimitive(cleanIban(iban)))).toString()
    } else {
        ""
    }
    if (submit) out["submit"] = "true"
    return out
}
