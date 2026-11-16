package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Mein Bereich wie im Browser: Belegung anfragen, Kündigung, Meldungen zu Wettbewerben und
 * Abwesenheiten in der Musik (`/api/portal/<Zugang>/…`). Die App speichert nichts davon.
 */

// --- Belegung (portal/<Zugang>/bookings/) ---

/** Objekt, das Mitglieder anfragen dürfen (Vereinsheim, Räume, Material). */
@Serializable
data class BookableItem(
    val id: Int,
    val kind: String = "",
    val title: String = "",
    @SerialName("horizon_days") val horizonDays: Int = 0,
)

/** Belegte Zeit eines Objekts; fremde Buchungen heißen nur „Belegt“. */
@Serializable
data class BusyTime(
    val item: Int,
    val title: String = "",
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("all_day") val allDay: Boolean = false,
    val own: Boolean = false,
)

/** Eigene Anfrage oder Buchung. */
@Serializable
data class OwnBooking(
    val id: Int,
    val item: Int = 0,
    @SerialName("item_title") val itemTitle: String = "",
    val title: String = "",
    val note: String = "",
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("all_day") val allDay: Boolean = false,
    val status: String = "",
    val version: Int = 0,
) {
    /** Stornieren geht, solange die Anfrage offen oder bestätigt ist. */
    val cancellable: Boolean get() = status == "requested" || status == "confirmed"
}

@Serializable
data class BookingWeek(
    val items: List<BookableItem> = emptyList(),
    val occurrences: List<BusyTime> = emptyList(),
    val own: List<OwnBooking> = emptyList(),
)

object Belegung {
    /** Der Kalendertag gilt in deutscher Zeit, wie auf dem Server. */
    val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    private val ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /** Montag der Woche von [day]. */
    fun monday(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    /**
     * Ganztägig speichert der Server das Ende als 0:00 des Folgetags; für die Anzeige zählt der
     * letzte Tag (wie im Browser).
     */
    fun shownEnd(endsAt: String, allDay: Boolean): String =
        if (!allDay) endsAt else runCatching { OffsetDateTime.parse(endsAt).minusSeconds(1).format(ISO) }.getOrDefault(endsAt)

    /**
     * Beginn und Ende einer Anfrage als ISO mit Zeitzone. Ganztägig: erster und letzter Tag je
     * 0:00 deutscher Zeit (der Server rechnet das Ende selbst auf den Folgetag). Ohne Enddatum gilt
     * der Starttag; liegt das Ende dann vor dem Beginn, ist der nächste Tag gemeint.
     * Ungültig oder Ende vor Beginn → null.
     */
    fun requestTimes(
        from: LocalDate,
        until: LocalDate?,
        start: LocalTime?,
        end: LocalTime?,
        allDay: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<String, String>? {
        if (allDay) {
            val last = until ?: from
            if (last.isBefore(from)) return null
            return from.atStartOfDay(BERLIN).format(ISO) to last.atStartOfDay(BERLIN).format(ISO)
        }
        if (start == null || end == null) return null
        val begin = from.atTime(start).atZone(zone)
        var finish = (until ?: from).atTime(end).atZone(zone)
        if (until == null && !finish.isAfter(begin)) finish = finish.plusDays(1)
        if (!finish.isAfter(begin)) return null
        return begin.format(ISO) to finish.format(ISO)
    }
}

// --- Kündigung (portal/<Zugang>/cancellation/, Angaben in „Meine Daten“) ---

@Serializable
data class CancellationItem(
    val id: Int,
    @SerialName("exit_date") val exitDate: String? = null,
    val message: String = "",
    /** pending, accepted, rejected, withdrawn */
    val status: String = "",
    @SerialName("status_label") val statusLabel: String = "",
    val response: String = "",
    @SerialName("created_at") val createdAt: String? = null,
) {
    val pending: Boolean get() = status == "pending"
}

@Serializable
data class CancellationInfo(
    /** Verein erlaubt Kündigen im Mitgliederbereich */
    val enabled: Boolean = false,
    /** Jetzt kündbar: eingeschaltet, kein Austritt eingetragen, keine offene Kündigung */
    val possible: Boolean = false,
    @SerialName("earliest_date") val earliestDate: String? = null,
    /** Regel in Worten, z. B. „mit einer Frist von 3 Monaten zum Ende des Geschäftsjahres“ */
    val rule: String = "",
    @SerialName("planned_exit") val plannedExit: String? = null,
    val requests: List<CancellationItem> = emptyList(),
) {
    val pending: CancellationItem? get() = requests.firstOrNull { it.pending }

    /** Abschnitt zeigen: eingeschaltet oder schon Kündigungen da (wie im Browser). */
    val shown: Boolean get() = enabled || requests.isNotEmpty()
}

// --- Meldungen (portal/<Zugang>/entries/) ---

/** Wofür das Mitglied gemeldet ist (nur abgegebene oder bestätigte Meldungen). */
@Serializable
data class MyEntry(
    val id: Int,
    val title: String = "",
    val organizer: String = "",
    val date: String? = null,
    val location: String = "",
    val group: String = "",
    val position: String = "",
    val substitute: Boolean = false,
    val confirmed: Boolean = false,
) {
    /** Liegt das Datum vor [today]? Ohne Datum: kommend. */
    fun past(today: LocalDate = LocalDate.now()): Boolean =
        date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true
}

// --- Musik: Abwesenheiten (portal/<Zugang>/music/absences/) ---

@Serializable
data class MusicAbsence(
    val id: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
)

@Serializable
data class OwnAbsences(
    /** Aktives Musikerprofil vorhanden */
    val enabled: Boolean = false,
    val items: List<MusicAbsence> = emptyList(),
)
