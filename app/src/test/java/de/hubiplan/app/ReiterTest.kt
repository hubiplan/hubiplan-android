package de.hubiplan.app

import de.hubiplan.app.data.Occurrence
import de.hubiplan.app.ui.FESTE_REITER
import de.hubiplan.app.ui.Tab
import de.hubiplan.app.ui.barTabs
import de.hubiplan.app.ui.fuenfteReiter
import de.hubiplan.app.ui.fuenfterReiter
import de.hubiplan.app.ui.kopfInVereinsfarbe
import de.hubiplan.app.ui.naechsterTermin
import de.hubiplan.app.ui.offeneRueckmeldungen
import de.hubiplan.app.ui.wartetAufAntwort
import de.hubiplan.app.ui.weitereReiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gestaltung „Frisch mobil“, Etappe U2: Reiter wie in der iOS-App, fünfter Reiter je Rolle, „Start“. */
class ReiterTest {
    @Test
    fun vierFesteReiterUndEinFuenfterJeRolle() {
        assertEquals(listOf(Tab.START, Tab.TERMINE, Tab.MITTEILUNGEN, Tab.ICH), FESTE_REITER)
        // Vorgabe: Verwaltung vor Noten vor Aufgaben
        assertEquals(listOf(Tab.VERWALTUNG, Tab.NOTEN, Tab.AUFGABEN), fuenfteReiter(adminVisible = true, tasksVisible = true, notesVisible = true))
        assertEquals(Tab.VERWALTUNG, fuenfterReiter(listOf(Tab.VERWALTUNG, Tab.NOTEN, Tab.AUFGABEN), ""))
        assertEquals(Tab.NOTEN, fuenfterReiter(listOf(Tab.NOTEN, Tab.AUFGABEN), ""))
        assertEquals(Tab.AUFGABEN, fuenfterReiter(listOf(Tab.AUFGABEN), ""))
        assertNull(fuenfterReiter(emptyList(), "NOTEN"))
        // Eigene Wahl gilt, solange es den Bereich gibt
        assertEquals(Tab.AUFGABEN, fuenfterReiter(listOf(Tab.VERWALTUNG, Tab.AUFGABEN), "AUFGABEN"))
        assertEquals(Tab.VERWALTUNG, fuenfterReiter(listOf(Tab.VERWALTUNG, Tab.AUFGABEN), "NOTEN"))
        for (admin in listOf(false, true)) for (tasks in listOf(false, true)) for (notes in listOf(false, true)) {
            val tabs = barTabs(admin, tasks, notes)
            assertTrue("höchstens fünf", tabs.size <= 5)
            assertEquals(FESTE_REITER, tabs.take(4))
            assertFalse(Tab.KONTO in tabs)
            // Nichts geht verloren: Leiste und „Start“ zusammen zeigen alle Bereiche
            assertEquals(
                fuenfteReiter(admin, tasks, notes).toSet(),
                (tabs.drop(4) + weitereReiter(admin, tasks, notes, tabs)).toSet(),
            )
        }
        assertEquals(listOf(Tab.NOTEN, Tab.AUFGABEN), weitereReiter(true, true, true, barTabs(true, true, true)))
        assertEquals(emptyList<Tab>(), weitereReiter(true, true, true, barTabs(true, true, true, wide = true)))
    }

    @Test
    fun kopfNurAufStartInVereinsfarbe() {
        assertTrue(kopfInVereinsfarbe(Tab.START, detail = false))
        assertFalse(kopfInVereinsfarbe(Tab.START, detail = true))
        Tab.entries.filter { it != Tab.START }.forEach { assertFalse(kopfInVereinsfarbe(it, detail = false)) }
    }

    private fun termin(key: String, rsvp: Boolean = false, antwort: String? = null, abgesagt: Boolean = false) = Occurrence(
        key = key, eventId = key.hashCode(), title = "Sommerfest", startsAt = "2027-06-12T15:00:00+02:00", endsAt = "2027-06-12T22:00:00+02:00",
        isCancelled = abgesagt, rsvpEnabled = rsvp, myResponse = antwort,
    )

    @Test
    fun startZeigtDenNaechstenTerminUndWasWartet() {
        val liste = listOf(termin("a", abgesagt = true), termin("b", rsvp = true), termin("c", rsvp = true), termin("d", rsvp = true, antwort = "yes"))
        assertEquals("b", naechsterTermin(liste)?.key)
        assertNull(naechsterTermin(listOf(termin("a", abgesagt = true))))
        assertTrue(wartetAufAntwort(liste[1]))
        assertFalse(wartetAufAntwort(liste[3]))
        assertFalse(wartetAufAntwort(termin("x", rsvp = true, abgesagt = true)))
        assertFalse(wartetAufAntwort(termin("y")))
        assertEquals(2, offeneRueckmeldungen(liste))
        // Der groß gezeigte Termin zählt nicht noch einmal
        assertEquals(1, offeneRueckmeldungen(liste, ohne = liste[1]))
    }
}
