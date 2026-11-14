package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Reiter „Ich“ (Konzept app-ich, S-06/X-03): Rechnungen, Lastschriftmandat, Versammlungen und
 * Dokumente aus dem Mitgliederbereich (`/api/portal/<Zugang>/…`), wie „Mein Bereich“ im Browser.
 * Nichts davon speichert die App; PDFs liegen nur zum Ansehen kurz im Cache.
 */

/** Eigene Rechnung (`portal/<Zugang>/invoices/`). Beträge als Text („48.00“). */
@Serializable
data class MyInvoice(
    val id: Int,
    val number: String = "",
    val status: String = "",
    @SerialName("status_label") val statusLabel: String = "",
    val date: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val subject: String = "",
    val total: String = "0",
    val outstanding: String = "0",
    val overdue: Boolean = false,
    @SerialName("issuer_name") val issuerName: String = "",
    @SerialName("department_name") val departmentName: String? = null,
    @SerialName("has_cancellation_pdf") val hasCancellationPdf: Boolean = false,
) {
    val open: Boolean get() = status == "open"
}

/** Beitrag, der über die Sammelrechnung einer Untereinheit läuft. */
@Serializable
data class CollectiveLine(
    val id: Int,
    val number: String = "",
    val date: String? = null,
    val subject: String = "",
    @SerialName("department_name") val departmentName: String = "",
    val amount: String = "0",
    val status: String = "",
    @SerialName("status_label") val statusLabel: String = "",
)

@Serializable
data class MyInvoices(
    val invoices: List<MyInvoice> = emptyList(),
    val collective: List<CollectiveLine> = emptyList(),
) {
    /** Offene zuerst, sonst wie geliefert (neueste oben). */
    val sorted: List<MyInvoice> get() = invoices.filter { it.open } + invoices.filterNot { it.open }

    val openCount: Int get() = invoices.count { it.open }

    /** Summe der offenen Beträge als Text mit zwei Nachkommastellen, null ohne offene. */
    val openSum: String?
        get() = invoices.filter { it.open }
            .mapNotNull { it.outstanding.toBigDecimalOrNull() }
            .takeIf { it.isNotEmpty() }
            ?.fold(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
            ?.setScale(2)
            ?.toPlainString()
}

@Serializable
data class MyMandate(
    val reference: String = "",
    @SerialName("scheme_label") val schemeLabel: String = "",
    @SerialName("status_label") val statusLabel: String = "",
    val holder: String = "",
    @SerialName("iban_masked") val ibanMasked: String = "",
    @SerialName("signed_on") val signedOn: String? = null,
)

/** Lastschrift im Mitgliederbereich (`portal/<Zugang>/mandate/`); die App zeigt nur an. */
@Serializable
data class MandateState(
    val available: Boolean = false,
    @SerialName("club_name") val clubName: String = "",
    @SerialName("creditor_id") val creditorId: String = "",
    val mandate: MyMandate? = null,
)

@Serializable
data class AgendaPoint(val position: Int = 0, val title: String = "", val description: String = "")

@Serializable
data class OwnMotion(
    val id: Int,
    val title: String = "",
    val text: String = "",
    val status: String = "",
    val reply: String = "",
    @SerialName("created_at") val createdAt: String? = null,
) {
    /** Zurückziehen geht nur, solange der Vorstand noch nicht entschieden hat. */
    val withdrawable: Boolean get() = status == "submitted"
}

@Serializable
data class UpcomingMeeting(
    val id: Int,
    val title: String = "",
    @SerialName("kind_name") val kindName: String = "",
    val unit: String = "",
    @SerialName("starts_at") val startsAt: String? = null,
    val location: String = "",
    val address: String = "",
    /** Letzter Zeitpunkt für Anträge; null, wenn die Art keine Anträge vorsieht. */
    @SerialName("motion_until") val motionUntil: String? = null,
    /** Anmeldung per App offen bzw. Teilnahme aus der Ferne erlaubt (HUBIplan V7; ältere Stände: aus) */
    @SerialName("checkin_open") val checkinOpen: Boolean = false,
    val remote: Boolean = false,
    val motions: List<OwnMotion> = emptyList(),
    val agenda: List<AgendaPoint> = emptyList(),
) {
    fun motionsOpen(now: java.time.Instant = java.time.Instant.now()): Boolean =
        motionUntil?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() }?.isAfter(now) == true
}

@Serializable
data class PastMeeting(
    val id: Int,
    val title: String = "",
    @SerialName("kind_name") val kindName: String = "",
    val unit: String = "",
    @SerialName("starts_at") val startsAt: String? = null,
)

@Serializable
data class MyMeetings(
    val upcoming: List<UpcomingMeeting> = emptyList(),
    val minutes: List<PastMeeting> = emptyList(),
)

@Serializable
data class DocVersion(
    val id: Int,
    val name: String = "",
    val size: Long = 0,
    @SerialName("content_type") val contentType: String = "",
    val url: String = "",
)

@Serializable
data class MyDocument(
    val id: Int,
    val title: String = "",
    val date: String? = null,
    val note: String = "",
    val current: DocVersion? = null,
)

@Serializable
data class DocFolder(
    val id: Int,
    val name: String = "",
    val department: String? = null,
    val documents: List<MyDocument> = emptyList(),
)

@Serializable
data class MyDocuments(val folders: List<DocFolder> = emptyList()) {
    val count: Int get() = folders.sumOf { f -> f.documents.count { it.current != null } }
}

/**
 * Nur Pfade des eigenen Mitgliederbereichs laden (Dokumentlinks kommen vom Server, werden aber
 * nicht blind übernommen): `/api/portal/<Zugang>/documents/versions/<Nr>/`.
 */
fun documentPathOk(accessId: Int, path: String): Boolean =
    Regex("^/api/portal/$accessId/documents/versions/\\d+/$").matches(path)

/** Mitglied, für das die Firma zahlt (Mein Bereich › Mitarbeiter; nur Name und Zeitraum). */
@Serializable
data class CompanyMember(
    val link: Int,
    val name: String = "",
    val since: String? = null,
    val until: String? = null,
    @SerialName("pays_via") val paysVia: Boolean = false,
)

@Serializable
data class CompanyEntry(
    val id: Int,
    val name: String = "",
    val members: List<CompanyMember> = emptyList(),
)

/** `GET /api/portal/<Zugang>/company/` */
@Serializable
data class CompanyOverview(
    @SerialName("can_report") val canReport: Boolean = false,
    val companies: List<CompanyEntry> = emptyList(),
)
