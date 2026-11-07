package de.hubiplan.app

import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modulliste des Mitgliederbereichs (Modulverzeichnis M7); alle Daten erfunden. */
class ModuleTest {
    private fun access(extra: String) = json.decodeFromString<PortalAccess>(
        """{"id":1,"club_name":"Schützenverein Musterstadt"$extra}""",
    )

    @Test
    fun modullisteEntscheidet() {
        val a = access(""","modules":["events","helpers"],"competitions_enabled":true""")
        assertTrue(a.hasHelpers)
        assertFalse(a.hasTodos)
        assertFalse(a.hasCard)
        // Die Liste gilt vor dem einzelnen Schalter
        assertFalse(a.hasResults)
    }

    @Test
    fun aeltereStaendeOhneListe() {
        val a = access(""","competitions_enabled":true""")
        assertTrue(a.hasResults)
        assertFalse(a.hasHelpers)
        assertTrue(a.hasTodos)
        assertTrue(a.hasCard)
    }
}
