package de.hubiplan.app

import de.hubiplan.app.data.AdminUnit
import de.hubiplan.app.ui.Section
import de.hubiplan.app.ui.sectionsFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verwaltungsbereiche in der Reihenfolge der Gruppen des Hauptmenüs im Browser (Menüprüfung 3). */
class HauptmenueReihenfolgeTest {
    @Test
    fun bereicheFolgenDenMenuegruppen() {
        val unit = AdminUnit(
            1,
            "Verein",
            permissions = listOf(
                "todos.view", "members.view", "events.view", "finance.view", "billing.view",
                "competitions.results", "partners.view", "obligations.view",
            ),
            stufen = true,
            modules = listOf("competitions", "qualifications", "obligations"),
        )
        assertEquals(
            listOf(
                Section.TASKS,
                Section.EVENTS, Section.TODOS, Section.OBLIGATIONS,
                Section.MEMBERS, Section.OCCASIONS, Section.QUALIFICATIONS,
                Section.COMPETITIONS,
                Section.FINANCE, Section.BILLING, Section.PARTNERS,
            ),
            sectionsFor(unit),
        )
    }

    @Test
    fun untereinheitTermineZuerst() {
        val zug = AdminUnit(4, "Zug", scope = "d", permissions = listOf("todos.view", "members.view", "events.view"), stufen = true)
        assertEquals(listOf(Section.EVENTS, Section.TODOS, Section.MEMBERS), sectionsFor(zug))
    }
}
