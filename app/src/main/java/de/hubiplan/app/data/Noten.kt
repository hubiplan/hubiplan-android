package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Noten in der App (Konzept „Noten in der App“, Schritte N3–N8): Antworten von
// /api/portal/<Zugang>/music/folders/ und /music/live/<Ziel>/.

/** Eine Notendatei der eigenen Stimme (neueste Fassung). */
@Serializable
data class ScoreFile(
    val id: Int,
    val title: String = "",
    val instrument: String = "",
    val register: String = "",
    @SerialName("part_kind") val partKind: String = "",
    @SerialName("paper_format") val paperFormat: String = "",
    @SerialName("usage_note") val usageNote: String = "",
    val position: Int = 0,
    /** Fassungs-ID: ändert sie sich, lädt die App neu */
    val version: Int = 0,
    val filename: String = "",
    val size: Long = 0,
    @SerialName("content_type") val contentType: String = "",
    /** "musicxml", "mei", "abc", "midi" oder leer (PDF, Bild) */
    val notation: String = "",
    /** Zusätzliche Formate, die der Server setzt: musicxml, pdf, midi */
    val formats: List<String> = emptyList(),
    val url: String = "",
) {
    /** Hörbeispiel (MIDI-Datei), nie als Notenbild. */
    val isAudio: Boolean get() = notation == "midi"

    /** Notenbild: PDF/Bild direkt, MusicXML/MEI/ABC als vom Server gesetztes PDF. */
    val isSheet: Boolean get() = !isAudio && (contentType == PDF || contentType.startsWith("image/") || "pdf" in formats)

    /** Aus MusicXML/MEI/ABC lässt sich ein MIDI zum Anhören setzen. */
    val canPlay: Boolean get() = isAudio || "midi" in formats

    /** Format, in dem die App das Notenbild lädt ("" = Original). */
    val sheetFormat: String get() = if (contentType == PDF || contentType.startsWith("image/")) "" else "pdf"

    companion object {
        const val PDF = "application/pdf"
    }
}

/** Ein Eintrag einer Mappe: Stück, Pause oder Ansage. */
@Serializable
data class FolderItem(
    /** "piece", "pause", "encore" (Zugabe) oder andere Programmpunkte */
    val kind: String = "piece",
    val arrangement: Int? = null,
    val title: String = "",
    val composer: String = "",
    @SerialName("arrangement_name") val arrangementName: String = "",
    val arranger: String = "",
    @SerialName("license_note") val licenseNote: String = "",
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    val scores: List<ScoreFile> = emptyList(),
    val position: Int = 0,
    /** Nummer in der Mappe (Marschbuch „27“, „27a“); leer bei Terminen */
    val number: String = "",
    val note: String = "",
    @SerialName("entry_kind") val entryKind: String = "",
) {
    val isPiece: Boolean get() = arrangement != null
    val sheets: List<ScoreFile> get() = scores.filter { it.isSheet }.sortedWith(compareBy({ it.position }, { it.id }))
    val audio: ScoreFile? get() = scores.firstOrNull { it.isAudio } ?: scores.firstOrNull { it.canPlay }
}

/** Feste Mappe (Marschbuch, Repertoire, …). */
@Serializable
data class ScoreFolder(
    val id: Int,
    val key: String,
    @SerialName("can_lead") val canLead: Boolean = false,
    /** "marchbook", "repertoire", "other" */
    val kind: String = "other",
    val name: String = "",
    val note: String = "",
    val items: List<FolderItem> = emptyList(),
)

/** Mappe eines Termins (Auftritt oder Probe) in Programmreihenfolge. */
@Serializable
data class ScoreEvent(
    val key: String,
    @SerialName("live_key") val liveKey: String = "",
    @SerialName("can_lead") val canLead: Boolean = false,
    /** "performance" oder "rehearsal" */
    val type: String = "rehearsal",
    val title: String = "",
    @SerialName("starts_at") val startsAt: String = "",
    @SerialName("ends_at") val endsAt: String = "",
    val location: String = "",
    val cancelled: Boolean = false,
    val focus: String = "",
    val items: List<FolderItem> = emptyList(),
)

@Serializable
data class ScoreFolders(
    val folders: List<ScoreFolder> = emptyList(),
    val events: List<ScoreEvent> = emptyList(),
) {
    /** Alle Mappen einheitlich: feste zuerst, dann Termine (Konzept 4.1). */
    val mappen: List<Mappe>
        get() = folders.map { Mappe.of(it) } + events.map { Mappe.of(it) }
}

/** Einheitliche Sicht auf feste Mappe und Termin-Mappe für Liste, Suche und Notenpult. */
data class Mappe(
    val key: String,
    /** Ziel für die Live-Vorgabe (folder-…, performance-…, rehearsal-…) */
    val liveKey: String,
    val name: String,
    val kind: String,
    val canLead: Boolean,
    val startsAt: String = "",
    val location: String = "",
    val cancelled: Boolean = false,
    val note: String = "",
    val items: List<FolderItem>,
) {
    val isEvent: Boolean get() = kind == "performance" || kind == "rehearsal"
    val isMarchbook: Boolean get() = kind == "marchbook"
    val pieces: List<FolderItem> get() = items.filter { it.isPiece }

    companion object {
        fun of(f: ScoreFolder) = Mappe(f.key, f.key, f.name, f.kind, f.canLead, note = f.note, items = f.items)
        fun of(e: ScoreEvent) = Mappe(
            e.key, e.liveKey.ifBlank { e.key }, e.title, e.type, e.canLead, e.startsAt, e.location, e.cancelled, e.focus, e.items,
        )
    }
}

/** Vorgabe der Leitung: nächstes Stück für alle Geräte derselben Mappe. */
@Serializable
data class LiveCue(
    val seq: Long,
    val arrangement: Int,
    val number: String = "",
    val title: String = "",
    val sender: String = "",
    @SerialName("sent_at") val sentAt: String = "",
)

@Serializable
data class LiveState(
    val cue: LiveCue? = null,
    @SerialName("can_send") val canSend: Boolean = false,
    @SerialName("poll_seconds") val pollSeconds: Int = 2,
)

@Serializable
data class LiveSent(val cue: LiveCue? = null)

/** Ergebnis eines Datei-Abrufs: [notModified] bei 304 (ETag passt). */
class ScoreDownload(val notModified: Boolean, val body: ByteArray, val etag: String?, val contentType: String?)
