package de.hubiplan.app

import de.hubiplan.app.data.ExpenseDraft
import de.hubiplan.app.data.ExpenseOverview
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.cleanIban
import de.hubiplan.app.data.fields
import de.hubiplan.app.data.json
import de.hubiplan.app.data.parseKm
import androidx.compose.runtime.saveable.SaverScope
import de.hubiplan.app.ui.ExpenseDraftSaver
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.parseAmount
import de.hubiplan.app.ui.expenseSummary
import de.hubiplan.app.ui.ichBereiche
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auslagen unter „Ich“ (HUBIplan Auslagen erstatten); alle Daten erfunden. */
class AuslagenTest {
    private val text = """
        {"claims":[
          {"id":4,"number":"A2099-0001","kind":"expense","status":"question","status_label":"Rückfrage",
           "purpose":"Hemden für die Jugend","total":"45.90","amount_due":"45.90","version":2,
           "lines":[{"id":1,"amount":"45.90","text":""}],"files":[{"id":9,"name":"beleg.jpg"}],
           "events":[{"kind":"submitted","label":"eingereicht","who":"Anna Muster","text":"","at":"2099-01-02T10:00:00Z"},
                     {"kind":"question","label":"Rückfrage","who":"Kasse","text":"Für welche Gruppe?","at":"2099-01-03T10:00:00Z"}],
           "can_edit":true,"can_withdraw":true,"unknown_field":1},
          {"id":5,"number":"","kind":"expense","status":"draft","purpose":"","total":"0","amount_due":"0"}
        ],
        "options":{"may_submit":true,"mileage_enabled":true,"km_rate":"0.30",
                   "payee":{"holder":"Anna Muster","iban":"DE89370400440532013000"}}}
    """.trimIndent()

    @Test
    fun liestDieListe() {
        val o = json.decodeFromString<ExpenseOverview>(text)
        val c = o.claims[0]
        assertTrue(c.asksMe)
        assertEquals("Für welche Gruppe?", c.question)
        assertTrue(c.canAddFile)
        assertFalse(c.isLooseDraft)
        assertTrue(o.claims[1].isLooseDraft)
        assertEquals("DE89370400440532013000", o.options.payee?.iban)
        assertEquals("1 Rückfrage der Kasse", expenseSummary(o))
        assertEquals("Selbst Bezahltes erstattet bekommen", expenseSummary(ExpenseOverview()))
    }

    @Test
    fun ichZeigtAuslagenNurMitModul() {
        val on = PortalAccess(5, "Schützenverein Musterstadt", expensesEnabled = true)
        assertTrue(IchBereich.AUSLAGEN in ichBereiche(on))
        assertFalse(IchBereich.AUSLAGEN in ichBereiche(on.copy(expensesEnabled = false)))
    }

    @Test
    fun kilometerUndIban() {
        assertEquals("12.5", parseKm("12,5"))
        assertEquals("120", parseKm(" 120 "))
        assertNull(parseKm("0"))
        assertNull(parseKm("abc"))
        assertNull(parseKm("9000"))
        assertEquals("DE89370400440532013000", cleanIban("de89 3704 0044 0532 0130 00"))
    }

    @Test
    fun formularfelderEinerAuslage() {
        val d = ExpenseDraft(
            purpose = " Hemden für die Jugend ",
            date = "2099-01-02",
            amounts = listOf("45,90", "", "x"),
            holder = "Anna Muster",
            iban = "de89 3704 0044 0532 0130 00",
        )
        val f = d.fields(submit = true, ::parseAmount)
        assertEquals("expense", f["kind"])
        assertEquals("Hemden für die Jugend", f["purpose"])
        assertEquals("app", f["source"])
        assertEquals("true", f["submit"])
        assertEquals("2099-01-02", f["purchase_date"])
        assertTrue(f["lines"]!!.contains("45.9"))
        assertTrue(f["payee"]!!.contains("DE89370400440532013000"))
        val bar = d.copy(payout = "cash").fields(submit = false) { null }
        assertEquals("", bar["payee"])
        assertNull(bar["submit"])
        assertEquals("[]", bar["lines"])
    }

    @Test
    fun formularfelderEinerFahrt() {
        val f = ExpenseDraft(kind = "mileage", purpose = "Fahrt", date = "2099-01-02", route = "Musterstadt – Beispielhausen", km = "42,0")
            .fields(submit = false) { null }
        assertEquals("42", f["km"])
        assertEquals("Musterstadt – Beispielhausen", f["route"])
        assertNull(f["lines"])
    }

    @Test
    fun entwurf_uebersteht_gespeicherten_zustand() {
        // AND-03: Angaben bleiben bei Drehung und Prozessende erhalten
        val d = ExpenseDraft(
            kind = "mileage", purpose = "Fahrt zum Kreistag", date = "2099-01-02", amounts = listOf("12,50", "3,00"),
            route = "Musterstadt – Beispielhausen", km = "42,0", payout = "cash", holder = "Erika Muster", iban = "",
        )
        val saved = with(ExpenseDraftSaver) { SaverScope { true }.save(d) }!!
        assertEquals(d, ExpenseDraftSaver.restore(saved))
        // Leerer Entwurf behält seine eine leere Position
        val leer = with(ExpenseDraftSaver) { SaverScope { true }.save(ExpenseDraft()) }!!
        assertEquals(ExpenseDraft(), ExpenseDraftSaver.restore(leer))
    }
}
