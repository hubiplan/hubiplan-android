package de.hubiplan.app

import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.IchGruppe
import de.hubiplan.app.ui.NeuesTeil
import de.hubiplan.app.ui.ichGruppe
import de.hubiplan.app.ui.ichGruppen
import de.hubiplan.app.ui.ichSymbol
import de.hubiplan.app.ui.neuesBeschriftung
import de.hubiplan.app.ui.neuesTeil
import de.hubiplan.app.ui.neuesTeile
import de.hubiplan.app.ui.neuesWahl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Gestaltung „Frisch mobil“, Etappe U4: Reiter „Neues“ und „Ich“ in vier Gruppen (wie in der iOS-App). */
class NeuesIchTest {
    @Test
    fun neuesFasstMitteilungenPinnwandUndUmfragenZusammen() {
        assertEquals(listOf(NeuesTeil.MITTEILUNGEN), neuesTeile(PortalAccess(5, "Musterdorf")))
        val alles = PortalAccess(5, "Musikverein Musterdorf", modules = listOf("pinboard", "surveys"), pinboardEnabled = true, surveysEnabled = true)
        assertEquals(listOf(NeuesTeil.MITTEILUNGEN, NeuesTeil.PINNWAND, NeuesTeil.UMFRAGEN), neuesTeile(alles))
        // „Start“ führt bei Pinnwand und Umfragen nach „Neues“, sonst nach „Ich“
        assertEquals(NeuesTeil.PINNWAND, neuesTeil(IchBereich.PINNWAND))
        assertEquals(NeuesTeil.UMFRAGEN, neuesTeil(IchBereich.UMFRAGEN))
        assertNull(neuesTeil(IchBereich.RECHNUNGEN))
        // Ohne Wahl oder ohne das Modul: Mitteilungen
        assertEquals(NeuesTeil.MITTEILUNGEN, neuesWahl(neuesTeile(alles), null))
        assertEquals(NeuesTeil.UMFRAGEN, neuesWahl(neuesTeile(alles), "UMFRAGEN"))
        assertEquals(NeuesTeil.MITTEILUNGEN, neuesWahl(listOf(NeuesTeil.MITTEILUNGEN), "UMFRAGEN"))
        assertEquals("Pinnwand (2)", neuesBeschriftung(NeuesTeil.PINNWAND, 2))
        assertEquals("Umfragen", neuesBeschriftung(NeuesTeil.UMFRAGEN, 0))
        assertEquals("Mitteilungen (99+)", neuesBeschriftung(NeuesTeil.MITTEILUNGEN, 120))
    }

    @Test
    fun ichInVierGruppen() {
        val gruppen = ichGruppen(IchBereich.entries)
        assertEquals(listOf(IchGruppe.GELD, IchGruppe.MITMACHEN, IchGruppe.VEREIN, IchGruppe.DATEN), gruppen.map { it.first })
        assertEquals(
            listOf(IchBereich.RECHNUNGEN, IchBereich.NACHWEISE, IchBereich.BESTELLUNGEN, IchBereich.AUSLAGEN),
            gruppen[0].second,
        )
        assertEquals(listOf(IchBereich.VERSAMMLUNGEN, IchBereich.DOKUMENTE), gruppen[2].second)
        assertEquals(listOf(IchBereich.DATEN, IchBereich.MITARBEITER), gruppen[3].second)
        // Pinnwand und Umfragen stehen unter „Neues“; alles andere hat genau eine Gruppe
        val ohne = IchBereich.entries.filter { ichGruppe(it) == null }
        assertEquals(listOf(IchBereich.PINNWAND, IchBereich.UMFRAGEN), ohne)
        assertEquals(IchBereich.entries.size - 2, gruppen.sumOf { it.second.size })
        // Leere Gruppen fallen weg
        assertEquals(listOf(IchGruppe.DATEN), ichGruppen(listOf(IchBereich.PINNWAND, IchBereich.DATEN)).map { it.first })
        // Jeder Bereich hat ein eigenes Symbol
        assertEquals(IchBereich.entries.size, IchBereich.entries.map(::ichSymbol).toSet().size)
    }
}
