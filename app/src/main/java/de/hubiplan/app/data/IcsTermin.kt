package de.hubiplan.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Ein Termin aus einer geteilten Kalenderdatei (.ics), etwa aus Outlook, Thunderbird oder der
 * Kalender-App des Telefons (hubiplan konzepte/termine-aus-dem-mailprogramm.md, W4). Die App
 * füllt damit nur „Neuer Termin“ vor; angelegt wird erst nach dem Prüfen.
 */
data class IcsTermin(
    val title: String,
    val day: LocalDate,
    /** Beginn und Ende in der Zeitzone des Telefons; bei ganztägigen Terminen null. */
    val start: LocalTime?,
    val end: LocalTime?,
    val allDay: Boolean,
    val location: String,
    val description: String,
    val videoUrl: String,
    /** Serien legt die App nicht an: Hinweis zeigen, nur der erste Termin wird übernommen. */
    val repeats: Boolean,
)

object IcsLeser {
    /** Größer ist keine einzelne Einladung. */
    const val MAX_BYTES = 1_000_000

    private val VIDEO_LINK = Regex(
        "https://[^\\s<>\"'\\\\]*?(?:/call/[A-Za-z0-9]+|/meet/[^\\s<>\"'\\\\]+|teams\\.microsoft\\.com/" +
            "[^\\s<>\"'\\\\]+|teams\\.live\\.com/[^\\s<>\"'\\\\]+|zoom\\.us/[jw]/[^\\s<>\"'\\\\]+|meet\\.jit\\.si/" +
            "[^\\s<>\"'\\\\]+|meet\\.google\\.com/[^\\s<>\"'\\\\]+|/b/[a-z0-9-]{3,}|/rooms/[^\\s<>\"'\\\\]+/join)",
        RegexOption.IGNORE_CASE,
    )

    /** Windows-Zeitzonen aus Outlook, die in Vereinen vorkommen. */
    private val WINDOWS_ZONES = mapOf(
        "W. Europe Standard Time" to "Europe/Berlin",
        "Central Europe Standard Time" to "Europe/Budapest",
        "Romance Standard Time" to "Europe/Paris",
        "Central European Standard Time" to "Europe/Warsaw",
        "GMT Standard Time" to "Europe/London",
        "UTC" to "UTC",
    )

    private class Zeile(val name: String, val params: Map<String, String>, val value: String)

    /**
     * Den ersten Termin der Datei lesen; null, wenn die Datei keinen brauchbaren Termin enthält
     * (auch bei einer Absage, METHOD:CANCEL).
     */
    fun lesen(text: String, zone: ZoneId = ZoneId.systemDefault()): IcsTermin? {
        val lines = entfalten(text)
        if (lines.any { it.name == "METHOD" && it.value.equals("CANCEL", ignoreCase = true) }) return null
        val begin = lines.indexOfFirst { it.name == "BEGIN" && it.value.equals("VEVENT", ignoreCase = true) }
        if (begin < 0) return null
        val event = mutableListOf<Zeile>()
        var depth = 0
        for (line in lines.drop(begin + 1)) {
            if (line.name == "BEGIN") depth++
            if (line.name == "END") {
                if (depth == 0) break
                depth--
                continue
            }
            if (depth == 0) event += line
        }
        fun first(name: String) = event.firstOrNull { it.name == name }
        if (first("STATUS")?.value.equals("CANCELLED", ignoreCase = true)) return null
        val startLine = first("DTSTART") ?: return null
        val allDay = startLine.params["VALUE"].equals("DATE", ignoreCase = true) || startLine.value.length == 8
        val from = zeit(startLine, zone) ?: return null
        val to = first("DTEND")?.let { zeit(it, zone) }
            ?: first("DURATION")?.let { dauer(it.value) }?.let { from.plusSeconds(it) }
        val title = text(first("SUMMARY")?.value).ifBlank { return null }
        val location = text(first("LOCATION")?.value)
        val description = text(first("DESCRIPTION")?.value)
        val conference = event.filter { it.name == "CONFERENCE" }.map { it.value.trim() }
            .firstOrNull { it.startsWith("https://") && it.length <= 500 }
        val video = conference ?: listOf(location, description).firstNotNullOfOrNull { videoLink(it) } ?: ""
        val endTime = if (allDay) null else to?.takeIf { it.toLocalDate() == from.toLocalDate() && it.isAfter(from) }?.toLocalTime()
        return IcsTermin(
            title = title.take(200),
            day = from.toLocalDate(),
            start = if (allDay) null else from.toLocalTime(),
            end = endTime,
            allDay = allDay,
            location = location.take(200),
            description = description,
            videoUrl = video,
            repeats = event.any { it.name == "RRULE" || it.name == "RDATE" },
        )
    }

    /** Videolink (Nextcloud Talk, Jitsi, Teams, Zoom, Meet, BigBlueButton) im Text. */
    fun videoLink(text: String): String? =
        VIDEO_LINK.find(text)?.value?.trimEnd('.', ',', ';', ')')?.takeIf { it.length <= 500 }

    private fun entfalten(text: String): List<Zeile> {
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val joined = mutableListOf<String>()
        for (line in raw) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && joined.isNotEmpty()) {
                joined[joined.lastIndex] = joined.last() + line.substring(1)
            } else if (line.isNotBlank()) {
                joined += line
            }
        }
        return joined.mapNotNull(::zeile)
    }

    /** „NAME;PARAM=WERT;…:Wert“; Doppelpunkte in Anführungszeichen gehören zum Parameter. */
    private fun zeile(line: String): Zeile? {
        var quoted = false
        var colon = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"')
        }.toMap()
        return Zeile(head[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun text(value: String?): String {
        if (value == null) return ""
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val n = value[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString().trim()
    }

    private val BASIC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** Zeitpunkt in der Zone des Telefons; ganztägig: 0:00 des Tags. */
    private fun zeit(line: Zeile, zone: ZoneId): LocalDateTime? = runCatching {
        val v = line.value.trim()
        if (v.length == 8) return@runCatching LocalDate.parse(v, DATE).atStartOfDay()
        if (v.endsWith("Z", ignoreCase = true)) {
            return@runCatching LocalDateTime.parse(v.dropLast(1), BASIC).atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(zone).toLocalDateTime()
        }
        val local = LocalDateTime.parse(v, BASIC)
        val tzid = line.params["TZID"] ?: return@runCatching local
        val source = zoneOf(tzid) ?: return@runCatching local
        local.atZone(source).withZoneSameInstant(zone).toLocalDateTime()
    }.getOrNull()

    private fun zoneOf(tzid: String): ZoneId? {
        val name = tzid.trim().removePrefix("/")
        WINDOWS_ZONES[name]?.let { return ZoneId.of(it) }
        return runCatching { ZoneId.of(name) }.getOrNull()
            // Manche Programme schreiben „/freeassociation.sourceforge.net/Europe/Berlin“
            ?: Regex("[A-Za-z]+/[A-Za-z_]+$").find(name)?.value?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    }

    /** RFC-5545-Dauer wie „PT1H30M“ oder „P1D“ in Sekunden. */
    private fun dauer(value: String): Long? {
        val m = Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").find(value.trim()) ?: return null
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0
        val seconds = g(2) * 604800 + g(3) * 86400 + g(4) * 3600 + g(5) * 60 + g(6)
        return if (m.groupValues[1] == "-") null else seconds
    }
}
