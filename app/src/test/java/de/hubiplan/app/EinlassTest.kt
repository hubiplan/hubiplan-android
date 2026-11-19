package de.hubiplan.app

import de.hubiplan.app.data.CheckInList
import de.hubiplan.app.data.CheckInResult
import de.hubiplan.app.data.PendingCheckIn
import de.hubiplan.app.data.filterCheckIn
import de.hubiplan.app.data.json
import de.hubiplan.app.data.queueCheckIn
import de.hubiplan.app.data.withPending
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Einlass bei Terminen mit Anmeldung (HUBIplan A6/A7); alle Daten erfunden. */
class EinlassTest {
    private val antwort = """
        {"event_id":5,"title":"Sommerfest","can_edit":true,"can_cash":true,"count":3,"checked_in":1,
         "participants":[
          {"id":1,"name":"Anna Muster","price_name":"Erwachsene","registration_id":3,"number":"A2099-0001",
           "contact":"Anna Muster","paid_state":"paid","invoice_id":9,"invoice_number":"2099-0042","outstanding":"0.00",
           "checked_in_at":"2099-07-03T14:05:00+02:00","checked_in_via":"Eingang Nord","minor":false,
           "bookings":[{"name":"Mittagessen","redeemed_at":null}]},
          {"id":2,"name":"Kim Muster","price_name":"Kinder","registration_id":3,"number":"A2099-0001",
           "contact":"Anna Muster","paid_state":"open","invoice_id":10,"invoice_number":"2099-0043","outstanding":"15.00",
           "checked_in_at":null,"minor":true},
          {"id":3,"name":"Gerd Gast","price_name":"Gäste","registration_id":4,"number":"A2099-0002",
           "contact":"Gerd Gast","paid_state":"free","invoice_id":null,"invoice_number":"","outstanding":"0.00",
           "checked_in_at":null,"minor":false,"neu":"wird ignoriert"}]}
    """.trimIndent()

    private fun liste() = json.decodeFromString(CheckInList.serializer(), antwort)

    @Test fun listeLesen() {
        val l = liste()
        assertEquals(3, l.participants.size)
        assertTrue(l.canCash)
        val anna = l.participants[0]
        assertTrue(anna.checkedIn)
        assertEquals("Eingang Nord", anna.checkedInVia)
        assertNull(anna.bookings.single().redeemedAt)
        assertFalse(anna.owes)
        assertTrue(l.participants[1].owes)
        // Kostenlos: keine Rechnung, nichts zu kassieren
        assertFalse(l.participants[2].owes)
    }

    @Test fun sucheNachNameNummerUndAnmeldendem() {
        val rows = liste().participants
        assertEquals(listOf(1, 2), filterCheckIn(rows, "muster").map { it.id })
        assertEquals(listOf(3), filterCheckIn(rows, "0002").map { it.id })
        assertEquals(listOf(1, 2), filterCheckIn(rows, " anna ").map { it.id })
        assertEquals(3, filterCheckIn(rows, "").size)
    }

    @Test fun ohneNetzWartenSchritteUndHebenSichAuf() {
        var q = queueCheckIn(emptyList(), PendingCheckIn(2, undo = false))
        q = queueCheckIn(q, PendingCheckIn(3, undo = false))
        assertEquals(listOf(2, 3), q.map { it.participant })
        // Einlassen und gleich wieder zurücknehmen: nichts nachzureichen
        q = queueCheckIn(q, PendingCheckIn(2, undo = true))
        assertEquals(listOf(3), q.map { it.participant })
        val l = liste().withPending(q, "2099-07-03T15:00:00+02:00")
        assertEquals(2, l.checkedIn)
        assertEquals("2099-07-03T15:00:00+02:00", l.participants[2].checkedInAt)
    }

    @Test fun ergebnisGutOderAuffaellig() {
        val ok = json.decodeFromString(CheckInResult.serializer(), """{"result":"ok","message":"Eingelassen.","participant":null}""")
        val schon = json.decodeFromString(CheckInResult.serializer(), """{"result":"already","message":"Schon eingelassen."}""")
        assertTrue(ok.good)
        assertFalse(schon.good)
    }
}
