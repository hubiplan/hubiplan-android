package de.hubiplan.app

import de.hubiplan.app.data.Occurrence
import de.hubiplan.app.data.StartSeite
import de.hubiplan.app.data.StartTodo
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.monatTitel
import de.hubiplan.app.ui.startAusTerminen
import de.hubiplan.app.ui.startBereich
import de.hubiplan.app.ui.startZeilen
import de.hubiplan.app.ui.EINFUEHRUNG_STAND
import de.hubiplan.app.ui.einfuehrungZeilen
import de.hubiplan.app.ui.zeigtEinfuehrung
import de.hubiplan.app.ui.terminGruppen
import java.time.ZoneId
import kotlinx.serialization.json.Json
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

    private fun termin(
        key: String,
        rsvp: Boolean = false,
        antwort: String? = null,
        abgesagt: Boolean = false,
        start: String = "2027-06-12T15:00:00+02:00",
    ) = Occurrence(
        key = key, eventId = key.hashCode(), title = "Sommerfest", startsAt = start, endsAt = start,
        isCancelled = abgesagt, rsvpEnabled = rsvp, myResponse = antwort,
    )

    /** Etappe U3: „Start“ kommt in einem Abruf vom Server; ältere Stände liefern die Terminliste. */
    @Test
    fun startseiteVomServerUndAusTerminen() {
        val json = Json { ignoreUnknownKeys = true }
        val seite = json.decodeFromString(
            StartSeite.serializer(),
            """{"next_event":{"key":"7-20270612T130000Z","event_id":7,"title":"Sommerfest","starts_at":"2027-06-12T15:00:00+02:00",
               "ends_at":"2027-06-12T22:00:00+02:00","rsvp_enabled":true,"my_response":null,"neu":1},
               "todo":[{"key":"rsvp","title":"2 Termine warten auf deine Rückmeldung","count":2,"target":"events"},
                       {"key":"surveys","title":"1 Umfrage wartet auf deine Antwort","count":1,"target":"surveys"},
                       {"key":"spaeter","title":"Etwas Neues","count":3,"target":"unbekannt"}],
               "tiles":[{"key":"pinnwand","title":"Pinnwand","shown":true}],"mehr":true}""",
        )
        assertEquals("Sommerfest", seite.nextEvent?.title)
        assertEquals(3, seite.todo.size)
        assertEquals("pinnwand", seite.tiles.single().key)
        assertNull(json.decodeFromString(StartSeite.serializer(), """{"next_event":null,"todo":[],"tiles":[]}""").nextEvent)
        // Unbekannte Ziele und Bereiche, die der Zugang nicht hat, zeigt die App nicht
        assertEquals(listOf("rsvp", "surveys"), startZeilen(seite.todo, listOf(IchBereich.UMFRAGEN)).map { it.key })
        assertEquals(listOf("rsvp"), startZeilen(seite.todo, emptyList()).map { it.key })
        assertEquals(emptyList<StartTodo>(), startZeilen(listOf(StartTodo("rsvp", "x", 0, "events")), emptyList()))
        assertEquals(IchBereich.PINNWAND, startBereich("pinboard"))
        assertEquals(IchBereich.NACHWEISE, startBereich("fee_proofs"))
        assertNull(startBereich("events"))
        // Älterer Stand: nächster Termin und offene Rückmeldungen aus der Terminliste
        val liste = listOf(termin("a", abgesagt = true), termin("b", rsvp = true), termin("c", rsvp = true))
        val alt = startAusTerminen(liste)
        assertEquals("b", alt.naechster?.key)
        assertEquals(StartTodo("rsvp", "2 Termine warten auf deine Rückmeldung", 2, "events"), alt.todo.single())
        assertEquals(emptyList<StartTodo>(), startAusTerminen(listOf(termin("d"))).todo)
    }

    @Test
    fun terminlisteNachMonaten() {
        val berlin = ZoneId.of("Europe/Berlin")
        assertEquals("November 2026", monatTitel("2026-11-14T19:00:00+01:00", allDay = false, zone = berlin, english = false))
        assertEquals("März 2027", monatTitel("2027-03-03T10:00:00+01:00", allDay = false, zone = berlin, english = false))
        assertEquals("March 2027", monatTitel("2027-03-03T10:00:00+01:00", allDay = false, zone = berlin, english = true))
        // 23:30 UTC am Monatsletzten ist in Berlin schon der nächste Monat
        assertEquals("Dezember 2026", monatTitel("2026-11-30T23:30:00Z", allDay = false, zone = berlin, english = false))
        assertEquals("", monatTitel("kein Datum", allDay = false))
        val gruppen = terminGruppen(
            listOf(
                termin("a", start = "2027-06-12T15:00:00+02:00"),
                termin("b", start = "2027-06-20T15:00:00+02:00"),
                termin("c", start = "2027-07-01T15:00:00+02:00"),
            ),
        )
        assertEquals(listOf(2, 1), gruppen.map { it.second.size })
        assertEquals(listOf("a", "b"), gruppen[0].second.map { it.key })
    }

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

    @Test
    fun einfuehrungErscheintEinmalJeStand() {
        // solange die Einstellungen nicht geladen sind, blitzt nichts auf
        assertFalse(zeigtEinfuehrung(null))
        assertTrue(zeigtEinfuehrung(0))
        assertFalse(zeigtEinfuehrung(EINFUEHRUNG_STAND))
        // ein höherer Stand zeigt sie allen noch einmal
        assertTrue(zeigtEinfuehrung(1, stand = 2))
        assertEquals(3, einfuehrungZeilen().size)
    }
}
