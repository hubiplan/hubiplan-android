package de.hubiplan.app.data

import de.hubiplan.app.tr
import java.net.URI
import java.net.URLDecoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Training und Anwesenheit im Mitgliederbereich (GET /api/portal/<Zugang>/training/, Beschreibung
 * in hubiplan/docs/app.md und docs/training.md): eigene Anwesenheit, Einchecken per QR-Code und mit
 * Schießsport das eigene Schießbuch mit Stand nach § 14 WaffG. Nichts wird gespeichert.
 *
 * Die Übungsleitung zeigt einen QR-Code mit
 * `https://<Instanz>/mein-bereich/training/da/<Training>?v=<Verein>&c=<Code>`. Der Code wechselt
 * alle 30 Sekunden; die App schickt ihn sofort nach dem Scannen.
 */
data class TrainingLink(val server: String, val sessionId: Int, val clubId: Int, val code: String) {
    companion object {
        private val PATH = Regex("^/mein-bereich/training/da/(\\d{1,10})/?$")
        private val CODE = Regex("^\\d{1,12}-[0-9a-f]{16}$")

        fun parse(text: String): TrainingLink? {
            val uri = try {
                URI(text.trim())
            } catch (e: Exception) {
                return null
            }
            if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
            val match = PATH.find(uri.rawPath ?: return null) ?: return null
            val query = (uri.rawQuery ?: return null).split("&").mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to URLDecoder.decode(part.substring(i + 1), "UTF-8")
            }.toMap()
            val club = query["v"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            val code = query["c"]?.takeIf { CODE.matches(it) } ?: return null
            val server = httpsOrigin(uri) ?: return null
            val session = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return TrainingLink(server, session, club, code)
        }

        /** Inhalt des QR-Codes wie im Browser, aus dem aktuellen Code der Übungsleitung. */
        fun build(server: String, code: TrainingCode): String {
            val c = java.net.URLEncoder.encode(code.code, "UTF-8")
            return "${server.trimEnd('/')}/mein-bereich/training/da/${code.session}?v=${code.club}&c=$c"
        }
    }
}

@Serializable
data class TrainingAttendance(
    val session: Int,
    val date: String = "",
    val title: String = "",
    val kind: String = "",
    @SerialName("kind_label") val kindLabel: String = "",
    @SerialName("department_name") val departmentName: String = "",
    val source: String = "hand",
)

@Serializable
data class TrainingSessionInfo(
    val id: Int,
    val department: Int? = null,
    @SerialName("department_name") val departmentName: String = "",
    val date: String = "",
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    val title: String = "",
    val kind: String = "",
    @SerialName("kind_label") val kindLabel: String = "",
    val location: String = "",
    val leader: String = "",
    val shooting: Boolean = false,
    val discipline: String = "",
    @SerialName("weapon_class") val weaponClass: String = "",
    @SerialName("checkin_open") val checkinOpen: Boolean = false,
    val count: Int = 0,
    val version: Int = 1,
    /** Nur in der Liste zum Einchecken: schon eingecheckt */
    val present: Boolean = false,
)

@Serializable
data class Discipline(
    val name: String,
    @SerialName("weapon_class") val weaponClass: String = "short",
    val caliber: String = "",
    @SerialName("permit_required") val permitRequired: Boolean = true,
)

@Serializable
data class ShootingEntry(
    val id: Int,
    val date: String = "",
    val discipline: String = "",
    @SerialName("weapon_class") val weaponClass: String = "short",
    @SerialName("weapon_class_label") val weaponClassLabel: String = "",
    val caliber: String = "",
    @SerialName("permit_required") val permitRequired: Boolean = true,
    val competition: Boolean = false,
    val place: String = "",
    val external: Boolean = false,
    val note: String = "",
    val status: String = "confirmed",
    @SerialName("status_label") val statusLabel: String = "",
    @SerialName("from_training") val fromTraining: Boolean = false,
) {
    /** Nur gemeldete, noch nicht bestätigte Einträge kann das Mitglied zurückziehen. */
    val canWithdraw: Boolean get() = status == "reported"
}

@Serializable
data class TrainingSpan(val start: String, val end: String, val count: Int = 0)

@Serializable
data class ShootingAcquisition(
    val ok: Boolean = false,
    @SerialName("member_12_months") val member12Months: Boolean = false,
    @SerialName("by_months") val byMonths: Boolean = false,
    @SerialName("by_count") val byCount: Boolean = false,
    val count: Int = 0,
    val needed: Int = 18,
    val months: List<TrainingSpan> = emptyList(),
    @SerialName("longest_gap_days") val longestGapDays: Int = 0,
    @SerialName("gap_ok") val gapOk: Boolean = true,
    val external: Int = 0,
    val internal: Int = 0,
    @SerialName("mostly_own_club") val mostlyOwnClub: Boolean = true,
)

@Serializable
data class ShootingPossession(
    @SerialName("weapon_class") val weaponClass: String = "short",
    val label: String = "",
    val count: Int = 0,
    val ok: Boolean = false,
    @SerialName("by_quarters") val byQuarters: Boolean = false,
    @SerialName("by_six") val bySix: Boolean = false,
    val quarters: List<TrainingSpan> = emptyList(),
    val years: List<TrainingSpan> = emptyList(),
)

@Serializable
data class ShootingEvaluation(
    val on: String = "",
    @SerialName("member_since") val memberSince: String? = null,
    val acquisition: ShootingAcquisition = ShootingAcquisition(),
    val possession: List<ShootingPossession> = emptyList(),
    val pending: Int = 0,
)

@Serializable
data class OwnShootingLog(
    val entries: List<ShootingEntry> = emptyList(),
    val evaluation: ShootingEvaluation = ShootingEvaluation(),
    val disciplines: List<Discipline> = emptyList(),
)

@Serializable
data class TrainingTarget(val department: Int? = null, val name: String = "")

@Serializable
data class MemberTraining(
    val attendance: List<TrainingAttendance> = emptyList(),
    @SerialName("count_year") val countYear: Int = 0,
    @SerialName("count_12") val count12: Int = 0,
    val checkin: List<TrainingSessionInfo> = emptyList(),
    @SerialName("shooting_enabled") val shootingEnabled: Boolean = false,
    /** Wo dieses Konto Trainings leiten darf (Anwesenheit erfassen, QR-Code zeigen) */
    val targets: List<TrainingTarget> = emptyList(),
    val shooting: OwnShootingLog? = null,
)

/** Schießtermin melden (Mitglied) */
data class ShootingDraft(
    val date: String,
    val discipline: String,
    val weaponClass: String = "short",
    val caliber: String = "",
    val permitRequired: Boolean = true,
    val competition: Boolean = false,
    val place: String = "",
    val external: Boolean = false,
    val note: String = "",
)

// --- Übungsleitung in der App ---

@Serializable
data class TrainingKind(val value: String, val label: String = "")

@Serializable
data class ManageTrainings(
    val sessions: List<TrainingSessionInfo> = emptyList(),
    val targets: List<TrainingTarget> = emptyList(),
    val kinds: List<TrainingKind> = emptyList(),
    @SerialName("shooting_enabled") val shootingEnabled: Boolean = false,
    val disciplines: List<Discipline> = emptyList(),
)

@Serializable
data class TrainingRow(
    val membership: Int,
    val name: String = "",
    val present: Boolean = false,
    val source: String? = null,
    val shooting: Boolean = false,
)

@Serializable
data class TrainingDetail(
    val id: Int,
    @SerialName("department_name") val departmentName: String = "",
    val date: String = "",
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    val title: String = "",
    @SerialName("kind_label") val kindLabel: String = "",
    val location: String = "",
    val leader: String = "",
    val shooting: Boolean = false,
    val discipline: String = "",
    @SerialName("checkin_open") val checkinOpen: Boolean = false,
    @SerialName("checkin_possible") val checkinPossible: Boolean = false,
    val attendance: List<TrainingRow> = emptyList(),
    val version: Int = 1,
) {
    val presentCount: Int get() = attendance.count { it.present }
}

@Serializable
data class TrainingCode(
    val code: String,
    @SerialName("refresh_in") val refreshIn: Int = 30,
    val session: Int,
    val club: Int,
    val count: Int = 0,
)

/** „19:00–21:00“ bzw. „19:00“ oder leer */
fun trainingTime(startsAt: String?, endsAt: String?): String = when {
    startsAt.isNullOrBlank() -> ""
    endsAt.isNullOrBlank() -> startsAt
    else -> "$startsAt–$endsAt"
}

/** Kurzzeile unter einem Eintrag im Schießbuch. */
fun shootingDetails(e: ShootingEntry): String = listOf(
    e.weaponClassLabel,
    e.caliber,
    e.place,
    if (e.competition) tr("Wettkampf") else "",
    if (e.external) tr("anderer Verein") else "",
    if (e.permitRequired) "" else tr("erlaubnisfrei"),
).filter { it.isNotBlank() }.joinToString(" · ")

fun okText(ok: Boolean): String = if (ok) tr("erfüllt") else tr("nicht erfüllt")

/** Stand nach § 14 WaffG in Zeilen, wie die Karte im Browser. */
fun evaluationLines(e: ShootingEvaluation): List<String> {
    val a = e.acquisition
    val out = mutableListOf<String>()
    out += tr("Erwerb (Abs. 3): letzte 12 Monate") + ": " + okText(a.ok)
    out += if (a.member12Months) {
        tr("Seit mindestens zwölf Monaten Mitglied.")
    } else {
        tr("Noch keine zwölf Monate Mitglied.")
    }
    out += tr("In jedem Monat geschossen: {state}.", "state" to okText(a.byMonths))
    out += tr("Termine insgesamt: {n} von {needed}.", "n" to a.count.toString(), "needed" to a.needed.toString())
    if (!a.gapOk) {
        out += tr(
            "Längere Pause: {days} Tage ohne Schießtermin. Der BSSB erlaubt höchstens vier Monate ohne Begründung.",
            "days" to a.longestGapDays.toString(),
        )
    }
    if (!a.mostlyOwnClub) {
        out += tr(
            "Mehr Termine in anderen Vereinen ({external}) als im eigenen ({internal}).",
            "external" to a.external.toString(),
            "internal" to a.internal.toString(),
        )
    }
    e.possession.forEach { p ->
        out += tr("Besitz (Abs. 4), {label}: letzte 24 Monate", "label" to p.label) + ": " + okText(p.ok)
        out += tr(
            "Jedes Quartal: {q}. Sechsmal je zwölf Monate: {six}.",
            "q" to okText(p.byQuarters),
            "six" to okText(p.bySix),
        )
    }
    if (e.pending > 0) {
        out += tr("{n} gemeldete Termine warten auf die Bestätigung des Vereins.", "n" to e.pending.toString())
    }
    return out
}
