package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

// Einlass vor Ort bei Terminen mit Anmeldung (HUBIplan A6/A7): Liste aller Angemeldeten,
// Karte scannen oder von Hand einlassen, Abendkasse über die Barzahlung zur Rechnung.
// GET/POST /api/app/v1/admin/u/<Einheit>/events/<id>/registration/checkin/ (hubiplan docs/app.md)

@Serializable
data class CheckInBooking(
    val name: String = "",
    @SerialName("redeemed_at") val redeemedAt: String? = null,
)

@Serializable
data class CheckInRow(
    val id: Int,
    val name: String = "",
    @SerialName("price_name") val priceName: String = "",
    val number: String = "",
    val contact: String = "",
    @SerialName("paid_state") val paidState: String = "",
    @SerialName("invoice_id") val invoiceId: Int? = null,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    val outstanding: String = "0.00",
    @SerialName("checked_in_at") val checkedInAt: String? = null,
    @SerialName("checked_in_via") val checkedInVia: String = "",
    val minor: Boolean = false,
    val bookings: List<CheckInBooking> = emptyList(),
) {
    val checkedIn: Boolean get() = checkedInAt != null

    /** Noch etwas zu zahlen (Abendkasse). */
    val owes: Boolean get() = invoiceId != null && (outstanding.toBigDecimalOrNull() ?: BigDecimal.ZERO).signum() > 0
}

@Serializable
data class CheckInList(
    @SerialName("event_id") val eventId: Int = 0,
    val title: String = "",
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_cash") val canCash: Boolean = false,
    val participants: List<CheckInRow> = emptyList(),
    val count: Int = 0,
    @SerialName("checked_in") val checkedIn: Int = 0,
)

@Serializable
data class CheckInResult(
    val result: String = "",
    val message: String = "",
    val participant: CheckInRow? = null,
) {
    /** Grün nur bei erfolgreichem Einlass, alles andere fällt auf. */
    val good: Boolean get() = result == "ok" || result == "undone"
}

/** Suche nach Name, Anmeldenummer oder Anmeldendem, ohne Groß- und Kleinschreibung. */
fun filterCheckIn(rows: List<CheckInRow>, query: String): List<CheckInRow> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return rows
    return rows.filter { r -> listOf(r.name, r.number, r.contact).any { it.lowercase().contains(q) } }
}

/** Liste mit einer geänderten Zeile und neu gezählten Eingelassenen. */
fun CheckInList.withRow(row: CheckInRow): CheckInList {
    val rows = participants.map { if (it.id == row.id) row else it }
    return copy(participants = rows, checkedIn = rows.count { it.checkedIn })
}

/** Ohne Netz abgehakt: wird beim nächsten Kontakt nachgereicht (Reihenfolge bleibt). */
data class PendingCheckIn(val participant: Int, val undo: Boolean)

/** Neuer Schritt ersetzt einen älteren für dieselbe Person; Einlassen und Zurücknehmen heben sich auf. */
fun queueCheckIn(queue: List<PendingCheckIn>, step: PendingCheckIn): List<PendingCheckIn> {
    val old = queue.firstOrNull { it.participant == step.participant }
    val rest = queue.filter { it.participant != step.participant }
    return if (old != null && old.undo != step.undo) rest else rest + step
}

/** Zeile so zeigen, wie sie nach den wartenden Schritten aussieht. */
fun CheckInList.withPending(queue: List<PendingCheckIn>, now: String): CheckInList =
    queue.fold(this) { list, step ->
        val row = list.participants.firstOrNull { it.id == step.participant } ?: return@fold list
        list.withRow(row.copy(checkedInAt = if (step.undo) null else row.checkedInAt ?: now))
    }
