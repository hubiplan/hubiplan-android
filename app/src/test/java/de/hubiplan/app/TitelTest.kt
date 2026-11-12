package de.hubiplan.app

import de.hubiplan.app.data.MemberDetail
import de.hubiplan.app.data.json
import de.hubiplan.app.ui.FIELD_LABELS
import de.hubiplan.app.ui.TITLES
import de.hubiplan.app.ui.displayValue
import de.hubiplan.app.ui.isFreeTitle
import de.hubiplan.app.ui.masterDataChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Titel (Dr., Prof. …) in der Mitgliedsakte: Auswahlliste oder frei (erfundene Daten). */
class TitelTest {
    @Test
    fun auswahlUndFrei() {
        assertEquals(listOf("Dr.", "Dr. Dr.", "Prof.", "Prof. Dr."), TITLES)
        assertFalse(isFreeTitle(""))
        assertFalse(isFreeTitle("Prof. Dr."))
        assertTrue(isFreeTitle("Dipl.-Ing."))
    }

    @Test
    fun akteLiestTitel() {
        val d = json.decodeFromString(MemberDetail.serializer(), """{"id": 3, "last_name": "Muster", "first_name": "Max", "title": "Dr."}""")
        assertEquals("Dr.", d.title)
        // ältere Server ohne Titel
        assertEquals("", json.decodeFromString(MemberDetail.serializer(), """{"id": 3}""").title)
    }

    @Test
    fun geaenderterTitelGehtMit() {
        val before = mapOf("last_name" to "Muster", "title" to "")
        val changes = masterDataChanges(before, before + ("title" to "Prof."))
        assertEquals(mapOf("title" to "Prof."), changes)
        assertEquals("Titel", FIELD_LABELS["title"])
        assertEquals("Dr.", displayValue("title", "Dr."))
    }
}
