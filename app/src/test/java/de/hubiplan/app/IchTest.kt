package de.hubiplan.app

import de.hubiplan.app.data.Ansicht
import de.hubiplan.app.data.MyDocuments
import de.hubiplan.app.data.MyInvoices
import de.hubiplan.app.data.MyMeetings
import de.hubiplan.app.data.MandateState
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.decodeApi
import de.hubiplan.app.data.documentPathOk
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.ichBereiche
import de.hubiplan.app.ui.invoiceSummary
import de.hubiplan.app.ui.meetingSummary
import de.hubiplan.app.ui.motionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Reiter „Ich“ (Konzept app-ich, S-06/X-03): Liste, Kurzstand, Rechnungen, Versammlungen. Demodaten. */
class IchTest {
    private fun access(vararg modules: String, musician: Boolean = false) =
        PortalAccess(5, "Schützenverein Musterstadt", modules = modules.toList(), musician = musician)

    @Test
    fun reihenfolgeWieImKonzept() {
        val all = ichBereiche(access("billing", "meetings", "competitions", "helpers", "docs"))
        assertEquals(
            listOf(IchBereich.DATEN, IchBereich.RECHNUNGEN, IchBereich.VERSAMMLUNGEN, IchBereich.ERGEBNISSE, IchBereich.HELFERDIENSTE, IchBereich.DOKUMENTE),
            all,
        )
        assertEquals("Meine Daten", IchBereich.DATEN.label)
    }

    @Test
    fun nurEingeschalteteModule() {
        assertEquals(listOf(IchBereich.DATEN), ichBereiche(access()))
        assertEquals(listOf(IchBereich.DATEN, IchBereich.VERSAMMLUNGEN), ichBereiche(access("meetings")))
        // Ältere HUBIplan-Stände ohne Modulliste: Rechnungen wie im Browser, der Rest nach Schalter
        val old = PortalAccess(5, "Musterstadt", meetingsEnabled = true)
        assertEquals(listOf(IchBereich.DATEN, IchBereich.RECHNUNGEN, IchBereich.VERSAMMLUNGEN), ichBereiche(old))
    }

    private val invoicesJson = """
        {"invoices": [
          {"id": 3, "number": "2026-0003", "status": "paid", "status_label": "bezahlt", "date": "2026-02-01",
           "due_date": "2026-02-15", "subject": "Beitrag 2025", "total": "48.00", "outstanding": "0.00", "overdue": false,
           "issuer_name": "Schützenverein Musterstadt", "has_cancellation_pdf": false, "unknown": 1},
          {"id": 9, "number": "2026-0009", "status": "open", "status_label": "offen", "date": "2026-11-01",
           "due_date": "2026-11-15", "subject": "Beitrag 2026", "total": "48.00", "outstanding": "48.00", "overdue": false,
           "issuer_name": "Schützenverein Musterstadt", "department_name": null, "has_cancellation_pdf": false}
        ],
        "collective": [{"id": 1, "number": "S-1", "date": "2026-03-01", "subject": "Zugbeitrag", "department_name": "Jägerzug",
           "amount": "12.50", "status": "open", "status_label": "offen"}]}
    """.trimIndent()

    @Test
    fun rechnungenOffeneZuerst() {
        val list = decodeApi<MyInvoices>(invoicesJson)
        assertEquals(listOf(9, 3), list.sorted.map { it.id })
        assertEquals(1, list.openCount)
        assertEquals("48.00", list.openSum)
        assertEquals("48,00 € offen", invoiceSummary(list))
        assertEquals("Alles bezahlt", invoiceSummary(list.copy(invoices = list.invoices.filterNot { it.open })))
        assertEquals("Keine Rechnungen", invoiceSummary(MyInvoices()))
    }

    @Test
    fun mandatOhneVolleIban() {
        val state = decodeApi<MandateState>(
            """{"available": true, "club_name": "Musterstadt", "creditor_id": "DE98ZZZ09999999999",
               "mandate": {"reference": "M-1", "scheme_label": "Basislastschrift", "status_label": "aktiv",
               "holder": "Anke Annahmer", "iban": "DE02120300000000202051", "iban_masked": "DE02 **** 2051", "signed_on": "2026-01-05"},
               "text": "…"}""",
        )
        assertEquals("DE02 **** 2051", state.mandate?.ibanMasked)
        assertTrue(state.available)
    }

    private fun meetings(until: String?) = decodeApi<MyMeetings>(
        """{"upcoming": [{"id": 4, "title": "Jahreshauptversammlung 2026", "kind_name": "Jahreshauptversammlung", "unit": "",
            "starts_at": "2026-12-06T18:00:00+01:00", "location": "Vereinsheim", "address": "",
            "motion_until": ${until?.let { "\"$it\"" } ?: "null"},
            "motions": [{"id": 2, "title": "Neue Fahne", "text": "…", "status": "submitted", "reply": "", "created_at": "2026-11-01T10:00:00+01:00"}],
            "agenda": [{"position": 1, "title": "Begrüßung", "description": ""},
                       {"position": 2, "level": 0, "number": "2", "title": "Wahlen", "description": ""},
                       {"position": 3, "level": 1, "number": "2.1", "title": "Wahl des Vorsitzenden", "description": ""}]}],
           "minutes": [{"id": 1, "title": "Mitgliederversammlung 2025", "kind_name": "MV", "unit": "", "starts_at": "2025-12-07T18:00:00+01:00"}]}""",
    )

    @Test
    fun versammlungenUndAntragsfrist() {
        val list = meetings("2026-11-22T18:00:00+01:00")
        val m = list.upcoming.single()
        assertTrue(m.motionsOpen(Instant.parse("2026-11-09T12:00:00Z")))
        assertFalse(m.motionsOpen(Instant.parse("2026-11-23T12:00:00Z")))
        assertFalse(meetings(null).upcoming.single().motionsOpen(Instant.parse("2026-11-09T12:00:00Z")))
        assertTrue(m.motions.single().withdrawable)
        // Unterpunkte: Nummer vom Server, ältere Server ohne Nummer zählen nach Position
        assertEquals(listOf("1", "2", "2.1"), m.agenda.map { it.shownNumber })
        assertEquals(1, m.agenda.last().level)
        assertEquals("eingereicht", motionStatus("submitted"))
        assertTrue(meetingSummary(list).startsWith("Jahreshauptversammlung 2026 "))
        assertEquals("1 Protokoll", meetingSummary(list.copy(upcoming = emptyList())))
        assertEquals("Keine Einladung", meetingSummary(MyMeetings()))
    }

    @Test
    fun dokumenteNurAusDemEigenenBereich() {
        val docs = decodeApi<MyDocuments>(
            """{"folders": [{"id": 1, "name": "Satzung", "visibility": "members", "documents": [
                {"id": 7, "title": "Satzung", "current": {"id": 11, "name": "satzung.pdf", "size": 1000,
                 "content_type": "application/pdf", "url": "/api/portal/5/documents/versions/11/"}},
                {"id": 8, "title": "Leer", "current": null}]}], "can_manage": false}""",
        )
        assertEquals(1, docs.count)
        assertTrue(documentPathOk(5, "/api/portal/5/documents/versions/11/"))
        assertFalse(documentPathOk(5, "/api/portal/6/documents/versions/11/"))
        assertFalse(documentPathOk(5, "https://example.org/api/portal/5/documents/versions/11/"))
        assertFalse(documentPathOk(5, "/api/portal/5/documents/versions/11/../../../me/"))
    }

    @Test
    fun ansichtMitSicheremNamen() {
        assertEquals("rechnung-2026-0009.pdf", Ansicht.safeName("rechnung-2026-0009.pdf"))
        assertEquals("passwd", Ansicht.safeName("../../etc/passwd"))
        assertEquals("Beitragsordnung-2026.pdf", Ansicht.safeName("Beitragsordnung 2026.pdf"))
        assertEquals("datei.pdf", Ansicht.safeName(".."))
    }
}
