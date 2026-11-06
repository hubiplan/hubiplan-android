package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Helferdienste im Mitgliederbereich (GET /api/portal/<Zugang>/helpers/). */
@Serializable
data class HelperOverview(
    val plans: List<HelperPlan> = emptyList(),
    val mine: List<MyShift> = emptyList(),
)

@Serializable
data class HelperPlan(
    val id: Int,
    val title: String = "",
    val unit: String = "",
    @SerialName("event_title") val eventTitle: String = "",
    /** „self“: Mitglieder tragen sich selbst ein; sonst teilt nur die Verwaltung ein. */
    val signup: String = "self",
    @SerialName("cancel_days") val cancelDays: Int = 0,
    @SerialName("max_shifts") val maxShifts: Int? = null,
    val note: String = "",
    val duties: List<HelperDuty> = emptyList(),
)

@Serializable
data class HelperDuty(
    val id: Int,
    val name: String = "",
    val description: String = "",
    val location: String = "",
    @SerialName("min_age") val minAge: Int? = null,
    val shifts: List<HelperShift> = emptyList(),
)

@Serializable
data class HelperShift(
    val id: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    val needed: Int = 0,
    val taken: Int = 0,
    val note: String = "",
    val names: List<String> = emptyList(),
    /** Eigene Eintragung in dieser Schicht (Kennung zum Absagen). */
    val mine: Int? = null,
    @SerialName("can_cancel") val canCancel: Boolean = false,
    val past: Boolean = false,
) {
    val free: Int get() = (needed - taken).coerceAtLeast(0)
}

/** Eigener Dienst („Meine Dienste“). */
@Serializable
data class MyShift(
    val id: Int,
    val plan: String = "",
    val duty: String = "",
    val location: String = "",
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
)

/** Eintragen ist möglich: Plan zum Selbsteintragen, Schicht noch nicht begonnen, frei, nicht schon dabei. */
fun HelperPlan.canSignup(shift: HelperShift): Boolean =
    signup == "self" && !shift.past && shift.mine == null && shift.free > 0

/** Darf das Mitglied diesen eigenen Dienst noch selbst absagen (Frist des Plans)? */
fun HelperOverview.canCancel(assignment: Int): Boolean =
    plans.any { plan -> plan.duties.any { duty -> duty.shifts.any { it.mine == assignment && it.canCancel } } }
