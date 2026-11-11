package de.hubiplan.app

import de.hubiplan.app.data.AdminDepartment
import de.hubiplan.app.data.AdminUnit
import de.hubiplan.app.ui.canEditDeptMembers
import de.hubiplan.app.ui.canEditPhotos
import de.hubiplan.app.ui.showsEvents
import de.hubiplan.app.ui.showsMembers
import de.hubiplan.app.ui.showsPartners
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rechte als Bereiche mit Stufen (HUBIplan ab November 2026) und frühere Codes. */
class RechteTest {
    @Test
    fun termineMitStufenNurMitLesen() {
        assertTrue(showsEvents(AdminUnit(1, "Verein", permissions = listOf("events.view"), stufen = true)))
        assertFalse(showsEvents(AdminUnit(1, "Verein", permissions = listOf("finance.view"), stufen = true)))
        // Ältere Server: Termine sah jede Rolle
        assertTrue(showsEvents(AdminUnit(1, "Verein", permissions = listOf("finance.view"))))
    }

    @Test
    fun zugMitgliederAltUndNeu() {
        val neu = AdminDepartment(3, "1. Zug", permissions = listOf("members.view"), stufen = true).asUnit()
        assertTrue(showsMembers(neu))
        val alt = AdminDepartment(3, "1. Zug", permissions = listOf("dept.members.view")).asUnit()
        assertTrue(showsMembers(alt))
        val ohne = AdminDepartment(3, "1. Zug", permissions = listOf("events.edit"), stufen = true).asUnit()
        assertFalse(showsMembers(ohne))
    }

    @Test
    fun bearbeitenAltUndNeu() {
        assertTrue(canEditPhotos(listOf("photos.edit")))
        assertTrue(canEditPhotos(listOf("photos.manage")))
        assertFalse(canEditPhotos(listOf("photos.view")))
        assertTrue(canEditDeptMembers(listOf("members.edit")))
        assertTrue(canEditDeptMembers(listOf("dept.members.edit")))
        assertFalse(canEditDeptMembers(listOf("members.view")))
    }

    @Test
    fun geschaeftspartnerNurMitEigenemRecht() {
        assertTrue(showsPartners(AdminUnit(1, "Verein", permissions = listOf("partners.view"), stufen = true)))
        // Ältere Server kennen keine Geschäftspartner: Kasse allein zeigt den Bereich nicht
        assertFalse(showsPartners(AdminUnit(1, "Verein", permissions = listOf("finance.view"), stufen = true)))
        assertFalse(showsPartners(AdminUnit(1, "Verein", permissions = listOf("events.view"), stufen = true)))
        val zug = AdminDepartment(3, "1. Zug", permissions = listOf("partners.view"), stufen = true).asUnit()
        assertFalse(showsPartners(zug))
    }
}
