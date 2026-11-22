package de.hubiplan.app

import de.hubiplan.app.data.AdminUnit
import de.hubiplan.app.ui.NeuAktion
import de.hubiplan.app.ui.Section
import de.hubiplan.app.ui.VerwaltungGruppe
import de.hubiplan.app.ui.neuAktionen
import de.hubiplan.app.ui.sectionGroup
import de.hubiplan.app.ui.sectionGroups
import de.hubiplan.app.ui.sectionSearch
import de.hubiplan.app.ui.sectionSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Gestaltung „Frisch mobil“, Etappe U5: Startliste der Verwaltung als Arbeitsplatz (wie in der iOS-App). */
class VerwaltungStartTest {
    private val verein = AdminUnit(
        1,
        "Verein",
        permissions = listOf("todos.view", "members.view", "events.view", "finance.view", "finance.edit", "billing.view"),
        stufen = true,
    )

    @Test
    fun bereicheInVierGruppen() {
        val gruppen = sectionGroups(Section.entries)
        assertEquals(
            listOf(VerwaltungGruppe.PLANEN, VerwaltungGruppe.VEREIN, VerwaltungGruppe.VEREINSLEBEN, VerwaltungGruppe.VERWALTUNG),
            gruppen.map { it.first },
        )
        // „Zu erledigen“ steht für sich; alles andere hat genau eine Gruppe
        assertNull(sectionGroup(Section.TASKS))
        assertEquals(Section.entries.size - 1, gruppen.sumOf { it.second.size })
        assertEquals(listOf(Section.FINANCE, Section.BILLING, Section.PARTNERS), gruppen[3].second)
        // Leere Gruppen fallen weg, die Reihenfolge der Bereiche bleibt
        assertEquals(
            listOf(VerwaltungGruppe.PLANEN to listOf(Section.EVENTS, Section.TODOS), VerwaltungGruppe.VEREIN to listOf(Section.MEMBERS)),
            sectionGroups(listOf(Section.TASKS, Section.EVENTS, Section.TODOS, Section.MEMBERS)),
        )
        // Jeder Bereich hat ein eigenes Symbol
        assertEquals(Section.entries.size, Section.entries.map(::sectionSymbol).toSet().size)
    }

    @Test
    fun sucheNachNameUndBeschreibung() {
        val alle = Section.entries.toList()
        assertEquals(alle, sectionSearch(alle, "  "))
        assertEquals(listOf(Section.FINANCE), sectionSearch(alle, "kassenbuch"))
        // Beschreibung zählt mit, Groß- und Kleinschreibung nicht
        assertEquals(listOf(Section.FINANCE), sectionSearch(alle, "QUITTUNGEN"))
        // Alle Wörter müssen vorkommen
        assertEquals(listOf(Section.OCCASIONS), sectionSearch(alle, "geburtstage jubiläen"))
        assertEquals(emptyList<Section>(), sectionSearch(alle, "gibt es nicht"))
    }

    @Test
    fun neuJeNachRechten() {
        assertEquals(listOf(NeuAktion.QUITTUNG, NeuAktion.BUCHUNG, NeuAktion.TERMIN, NeuAktion.AUFGABE), neuAktionen(verein))
        // Ohne „Kasse bearbeiten“ keine Quittung und keine Buchung
        val ohneKasse = verein.copy(permissions = listOf("todos.view", "events.view", "finance.view"))
        assertEquals(listOf(NeuAktion.TERMIN, NeuAktion.AUFGABE), neuAktionen(ohneKasse))
        // Untereinheit: nie Kasse
        val zug = AdminUnit(4, "Zug", scope = "d", permissions = listOf("todos.view", "events.view", "finance.edit"), stufen = true)
        assertEquals(listOf(NeuAktion.TERMIN, NeuAktion.AUFGABE), neuAktionen(zug))
        assertEquals(emptyList<NeuAktion>(), neuAktionen(AdminUnit(2, "Verein", permissions = listOf("members.view"), stufen = true)))
    }
}
