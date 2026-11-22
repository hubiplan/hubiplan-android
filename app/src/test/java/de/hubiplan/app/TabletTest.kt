package de.hubiplan.app

import androidx.compose.ui.unit.dp
import de.hubiplan.app.ui.LESEBREITE
import de.hubiplan.app.ui.TWO_PANE_MIN_WIDTH
import de.hubiplan.app.ui.cardStatusText
import de.hubiplan.app.ui.listenBreite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tablets: Zweiteilung und Lesebreite. Breiten neben der Seitenleiste (rund 80 dp):
 * 7 Zoll (1200×1920, 320 dpi) quer 880 dp, 10 Zoll (1600×2560, 320 dpi) hochkant 720 dp, quer 1200 dp.
 */
class TabletTest {
    private val katalog = Texte.parse(File("src/main/assets/texte/en.json").readText())

    @After
    fun deutsch() = Texte.setzeFuerTest("de", katalog)

    @Test
    fun zehn_zoll_hochkant_ohne_zweiteilung() {
        assertFalse(720.dp >= TWO_PANE_MIN_WIDTH)
    }

    @Test
    fun sieben_zoll_quer_bleibt_wie_bisher() {
        // Vorher 40 % von 880 dp = 352 dp; das bleibt so
        assertTrue(880.dp >= TWO_PANE_MIN_WIDTH)
        assertEquals(352.dp, listenBreite(880.dp))
    }

    @Test
    fun liste_nie_zu_schmal_und_nie_zu_breit() {
        assertEquals(340.dp, listenBreite(TWO_PANE_MIN_WIDTH))
        assertEquals(420.dp, listenBreite(1200.dp))
        // Detail daneben behält mindestens Lesebreite minus Rand
        assertTrue(TWO_PANE_MIN_WIDTH - listenBreite(TWO_PANE_MIN_WIDTH) >= 480.dp)
        assertTrue(LESEBREITE <= 1200.dp - listenBreite(1200.dp))
    }

    @Test
    fun ausweis_gueltigkeit_englisch() {
        Texte.setzeFuerTest("en", katalog)
        assertEquals("Valid membership", cardStatusText("active", "Gültige Mitgliedschaft"))
        assertEquals("Membership starts later", cardStatusText("future", "Mitgliedschaft beginnt später"))
        assertEquals("No valid membership", cardStatusText("inactive", "Keine gültige Mitgliedschaft"))
        // Unbekannter Zustand: Text des Servers
        assertEquals("Gesperrt", cardStatusText("revoked", "Gesperrt"))
    }

    @Test
    fun ausweis_gueltigkeit_deutsch() {
        Texte.setzeFuerTest("de", katalog)
        assertEquals("Gültige Mitgliedschaft", cardStatusText("active", "egal"))
    }
}
