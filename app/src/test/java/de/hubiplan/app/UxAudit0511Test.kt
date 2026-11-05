package de.hubiplan.app

import de.hubiplan.app.data.AdminUnit
import de.hubiplan.app.ui.Section
import de.hubiplan.app.ui.Tab
import de.hubiplan.app.ui.backTarget
import de.hubiplan.app.ui.barTabs
import de.hubiplan.app.ui.sectionsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** UX-Audit 05.11.2026, Android A-01 bis A-05. */
class UxAudit0511Test {
    @Test
    fun hoechstensFuenfZieleOhneKonto() {
        val all = barTabs(adminVisible = true, tasksVisible = true)
        assertEquals(listOf(Tab.TERMINE, Tab.AUFGABEN, Tab.MITTEILUNGEN, Tab.AUSWEIS, Tab.VERWALTUNG), all)
        assertEquals(listOf(Tab.TERMINE, Tab.MITTEILUNGEN, Tab.AUSWEIS), barTabs(adminVisible = false, tasksVisible = false))
        assertFalse(Tab.KONTO in all)
    }

    @Test
    fun ausweisStattProfil() {
        assertEquals("Ausweis", Tab.AUSWEIS.label)
        assertEquals("Mein Konto", Tab.KONTO.label)
    }

    @Test
    fun zurueckFuehrtZumStartreiter() {
        assertNull(backTarget(Tab.TERMINE, Tab.TERMINE))
        Tab.entries.filter { it != Tab.TERMINE && it != Tab.KONTO }.forEach {
            assertEquals(Tab.TERMINE, backTarget(it, Tab.TERMINE))
        }
        assertEquals(Tab.AUSWEIS, backTarget(Tab.KONTO, Tab.AUSWEIS))
        assertEquals(Tab.TERMINE, backTarget(Tab.KONTO, Tab.KONTO))
    }

    @Test
    fun verwaltungAlsStartlisteOhneFilterchips() {
        val src = File("src/main/java/de/hubiplan/app/ui/AdminPane.kt").readText()
        assertFalse("Bereiche nicht als FilterChip", "FilterChip(" in src)
        assertFalse("keine waagerecht scrollende Bereichszeile", "horizontalScroll" in src)
        assertEquals("Offene Punkte", Section.TASKS.label)
        assertTrue(Section.entries.all { it.hint.isNotBlank() })
    }

    @Test
    fun untereinheitOhneOffenePunkte() {
        val dept = AdminUnit(id = 1, name = "Zug", scope = "d", permissions = listOf("members.view", "events.view"), stufen = true)
        assertFalse(Section.TASKS in sectionsFor(dept))
    }

    @Test
    fun antippbareZeilenMitRolleUndAktion() {
        val dir = File("src/main/java/de/hubiplan/app/ui")
        val bare = dir.listFiles()!!.filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex()
                .filter { (_, l) -> "Modifier.clickable" in l && "onClickLabel" !in l && "role" !in l }
                .map { (i, _) -> "${f.name}:${i + 1}" }
        }
        assertEquals("clickable ohne Rolle und Aktionsbeschriftung", emptyList<String>(), bare)
    }
}
