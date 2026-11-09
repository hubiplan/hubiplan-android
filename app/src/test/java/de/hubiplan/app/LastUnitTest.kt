package de.hubiplan.app

import de.hubiplan.app.data.AdminUnit
import de.hubiplan.app.data.LastUnitStore
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verwaltung startet in der zuletzt gewählten Einheit (Ralf 09.11.). */
class LastUnitTest {
    private val verein = AdminUnit(1, "Verein")
    private val zug = AdminUnit(4, "Zug", scope = "d")
    private val bezirk = AdminUnit(9, "Bezirk")
    private val units = listOf(verein, zug, bezirk)

    @Test
    fun startsInSavedUnit() {
        assertEquals(bezirk, LastUnitStore.startUnit(units, "u:9"))
        assertEquals(zug, LastUnitStore.startUnit(units, "d:4"))
    }

    @Test
    fun fallsBackToFirstUnit() {
        assertEquals(verein, LastUnitStore.startUnit(units, null))
        // Einheit nicht mehr verfügbar
        assertEquals(verein, LastUnitStore.startUnit(units, "u:42"))
        // gleiche Nummer, aber andere Art (Einheit statt Untereinheit)
        assertEquals(verein, LastUnitStore.startUnit(units, "u:4"))
    }
}
