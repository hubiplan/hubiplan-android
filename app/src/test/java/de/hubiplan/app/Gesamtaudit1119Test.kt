package de.hubiplan.app

import de.hubiplan.app.ui.Sichtschutz
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gesamtaudit 19.11.2026, S-09: Sichtschutz in der ganzen App, außer am groß gezeigten Ausweis. */
class Gesamtaudit1119Test {
    @Test
    fun standardmaessigGeschuetzt() {
        assertTrue(Sichtschutz.secure(allowed = false, forced = 0, exempt = 0))
    }

    @Test
    fun ausweisIstAusgenommen() {
        assertFalse(Sichtschutz.secure(allowed = false, forced = 0, exempt = 1))
    }

    @Test
    fun abschaltbarInDenEinstellungen() {
        assertFalse(Sichtschutz.secure(allowed = true, forced = 0, exempt = 0))
    }

    @Test
    fun verwaltungImmerGeschuetzt() {
        assertTrue(Sichtschutz.secure(allowed = true, forced = 1, exempt = 0))
        assertTrue(Sichtschutz.secure(allowed = true, forced = 1, exempt = 1))
    }
}
