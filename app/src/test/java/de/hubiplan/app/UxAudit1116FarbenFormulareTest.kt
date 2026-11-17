package de.hubiplan.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import de.hubiplan.app.data.ExpenseDraft
import de.hubiplan.app.data.Theme
import de.hubiplan.app.ui.brandFor
import de.hubiplan.app.ui.colorSchemeFor
import de.hubiplan.app.ui.contrast
import de.hubiplan.app.ui.keyboardTypeFor
import de.hubiplan.app.ui.withIsoDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX-Audit 16.11.2026: Farben gegen die wirksame Fläche (X-03: AND-12, AND-19, AND-23) und
 * Formulare (AND-09, AND-10). Gemeinsame Prüfpaletten wie in der Weboberfläche: hell, dunkel,
 * Gelb, Rot, sehr hell, sehr dunkel.
 */
class UxAudit1116FarbenFormulareTest {
    private val palette = listOf("#0a5a4a", "#ffd500", "#d62828", "#7ec8e3", "#101820", "#f6f6f0", "#ff7a00", "#ffffff")

    private fun argb(c: Color) = c.toArgb().toLong() and 0xFFFFFFFFL
    private fun theme(hex: String) = Theme(primary = hex, onPrimary = "#ffffff", accent = hex, onAccent = "#000000")

    @Test
    fun gewaehlte_zeile_ist_in_jeder_vereinsfarbe_lesbar() {
        for (hex in palette) for (dark in listOf(false, true)) {
            val s = colorSchemeFor(theme(hex), dark)
            // Titel, Zusatztext und Balken der gewählten Zeile stehen in onPrimaryContainer
            assertTrue("$hex dunkel=$dark", contrast(argb(s.onPrimaryContainer), argb(s.primaryContainer)) >= 4.5)
        }
    }

    @Test
    fun aktion_in_der_snackbar_ist_lesbar() {
        for (hex in palette) for (dark in listOf(false, true)) {
            val s = colorSchemeFor(theme(hex), dark)
            assertTrue("$hex dunkel=$dark", contrast(argb(s.inversePrimary), argb(s.inverseSurface)) >= 4.5)
        }
    }

    @Test
    fun ausweiskopf_prueft_die_schriftfarbe() {
        for (hex in palette) {
            // Server liefert Weiß, auch wenn es auf der Farbe nicht lesbar wäre
            val brand = brandFor(theme(hex))
            assertTrue(hex, contrast(argb(brand.onBrand), argb(brand.brand)) >= 4.5)
        }
    }

    @Test
    fun auslage_uebertraegt_das_datum_als_iso() {
        assertEquals("2099-01-02", ExpenseDraft(date = "02.01.2099").withIsoDate().date)
        assertEquals("2099-01-02", ExpenseDraft(date = "2099-01-02").withIsoDate().date)
        // Unlesbares bleibt stehen, der Server meldet es am Feld
        assertEquals("morgen", ExpenseDraft(date = "morgen").withIsoDate().date)
    }

    @Test
    fun jedes_feld_oeffnet_die_passende_tastatur() {
        assertEquals(KeyboardType.Email, keyboardTypeFor("email"))
        assertEquals(KeyboardType.Phone, keyboardTypeFor("phone"))
        assertEquals(KeyboardType.Number, keyboardTypeFor("postal_code"))
        assertEquals(KeyboardType.Text, keyboardTypeFor("city"))
    }
}
