package de.hubiplan.app

import de.hubiplan.app.ui.RADIUS
import de.hubiplan.app.ui.RADIUS_DIALOG
import de.hubiplan.app.ui.RADIUS_GROSS
import de.hubiplan.app.ui.Zustand
import de.hubiplan.app.ui.contrast
import de.hubiplan.app.ui.datumKaestchen
import de.hubiplan.app.ui.zustandFarben
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

/** Design „Frisch“: Formen, Abzeichen und Datumskästchen wie in der Weboberfläche. */
class DesignFrischTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun abzeichenSindHellUndDunkelLesbar() {
        for (zustand in Zustand.values()) for (dark in listOf(false, true)) {
            val farben = zustandFarben(zustand, dark)
            assertTrue("$zustand, dunkel=$dark", contrast(farben.schrift, farben.flaeche) >= 4.5)
        }
    }

    @Test
    fun zustandsfarbenWieInDerWeboberflaeche() {
        assertEquals(0xFF1D6B46L, zustandFarben(Zustand.GUT, false).schrift)
        assertEquals(0xFFFBE9E7L, zustandFarben(Zustand.FEHLER, false).flaeche)
        assertEquals(0xFFF4CF86L, zustandFarben(Zustand.WARNUNG, true).schrift)
    }

    @Test
    fun rundungenWieInDerWeboberflaeche() {
        assertEquals(6, RADIUS)
        assertEquals(8, RADIUS_GROSS)
        assertTrue(RADIUS_DIALOG <= 12)
        val theme = File("src/main/java/de/hubiplan/app/ui/Theme.kt").readText()
        assertTrue("shapes = HubiShapes" in theme)
    }

    @Test
    fun datumskaestchenJeSprache() {
        assertEquals("14" to "NOV", datumKaestchen("2026-11-14T19:00:00+01:00", berlin, english = false))
        assertEquals("3" to "MÄR", datumKaestchen("2027-03-03T10:00:00+01:00", berlin, english = false))
        assertEquals("3" to "MAR", datumKaestchen("2027-03-03T10:00:00+01:00", berlin, english = true))
        // Zeitzone des Geräts zählt: 23:30 UTC ist in Berlin schon der nächste Tag
        assertEquals("1" to "DEZ", datumKaestchen("2026-11-30T23:30:00Z", berlin, english = false))
        assertNull(datumKaestchen("kein Datum", berlin, english = false))
    }

    @Test
    fun termineZeigenKaestchenUndAbzeichen() {
        val events = File("src/main/java/de/hubiplan/app/ui/EventsPane.kt").readText()
        assertTrue("DatumKaestchen(occ.startsAt" in events)
        assertTrue("Abzeichen(tr(\"Abgesagt\"), Zustand.FEHLER" in events)
        assertTrue("Abzeichen(tr(\"Zugesagt\"), Zustand.GUT" in events)
    }
}
