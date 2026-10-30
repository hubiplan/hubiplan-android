package de.hubiplan.app.ui

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("EE, d. MMMM yyyy", Locale.GERMAN)
private val SHORT_DAY = DateTimeFormatter.ofPattern("EE, d.M.", Locale.GERMAN)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

private fun local(iso: String, zone: ZoneId): java.time.ZonedDateTime =
    OffsetDateTime.parse(iso).atZoneSameInstant(zone)

/** „Fr, 14. November 2026, 19:00 – 22:00 Uhr“ bzw. ganztägig nur Datum. */
fun formatWhen(start: String, end: String, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val s = local(start, zone)
    val e = local(end, zone)
    if (allDay) {
        // Ganztägig: Ende ist der letzte Tag 0:00
        return if (s.toLocalDate() == e.toLocalDate()) s.format(DAY) else "${s.format(SHORT_DAY)} – ${e.format(DAY)}"
    }
    return if (s.toLocalDate() == e.toLocalDate()) {
        "${s.format(DAY)}, ${s.format(TIME)} – ${e.format(TIME)} Uhr"
    } else {
        "${s.format(SHORT_DAY)} ${s.format(TIME)} – ${e.format(SHORT_DAY)} ${e.format(TIME)} Uhr"
    }
}

fun formatDate(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String =
    iso?.let { runCatching { local(it, zone).format(DAY) }.getOrNull() } ?: ""
