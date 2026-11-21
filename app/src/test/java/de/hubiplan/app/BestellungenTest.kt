package de.hubiplan.app

import de.hubiplan.app.data.OrderCampaign
import de.hubiplan.app.data.OrderCampaignList
import de.hubiplan.app.data.OrderInput
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.json
import de.hubiplan.app.push.PushMessage
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.ichBereiche
import de.hubiplan.app.ui.orderInputs
import de.hubiplan.app.ui.orderKey
import de.hubiplan.app.ui.orderLimitText
import de.hubiplan.app.ui.orderLinesText
import de.hubiplan.app.ui.orderTotal
import de.hubiplan.app.ui.ordersSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sammelbestellungen unter „Ich“ (HUBIplan Bestellungen); alle Daten erfunden. */
class BestellungenTest {
    private val list = """
        {"campaigns":[
          {"id":7,"title":"Vereinskleidung 2027","deadline":"2099-06-30","state":"open","from":"Musikverein Musterdorf",
           "club_pays":false,"ordered":false,"can_order":false,"order":null},
          {"id":5,"title":"Größen für Jacken","deadline":"2099-01-31","state":"closed","from":"Musikverein Musterdorf",
           "club_pays":true,"ordered":true,"can_order":false,
           "order":{"id":3,"lines":[{"article":1,"name":"Jacke","variant":"50","quantity":1,"price":null,"amount":null}],
                    "note":"","total":null,"handed_out":true,"handed_out_at":"2099-02-10T10:00:00Z",
                    "updated_at":"2099-01-20T10:00:00Z","invoice":null}}]}
    """.trimIndent()

    private val detail = """
        {"id":7,"title":"Vereinskleidung 2027","deadline":"2099-06-30","state":"open","from":"Musikverein Musterdorf",
         "club_pays":false,"ordered":false,"can_order":true,"order":null,"text":"Ausgabe beim Sommerfest",
         "articles":[
           {"id":1,"name":"Poloshirt","text":"","price":"25.00","variants":["S","M","L"],"variant_label":"Größe",
            "max_per_member":3,"max_total":null},
           {"id":2,"name":"Festschrift","text":"","price":"10.00","variants":[],"variant_label":"",
            "max_per_member":null,"max_total":50,"left":12}]}
    """.trimIndent()

    @Test
    fun listeLesen() {
        val l = json.decodeFromString<OrderCampaignList>(list)
        assertEquals(2, l.campaigns.size)
        assertEquals(1, l.openCount)
        assertEquals("1 offen, jetzt bestellen", ordersSummary(l))
        assertEquals("Gerade keine Sammelbestellung", ordersSummary(OrderCampaignList()))
        val own = l.campaigns[1].order!!
        assertTrue(own.handedOut)
        assertEquals("1 × Jacke (50)", orderLinesText(own))
    }

    @Test
    fun bestellungZusammenstellen() {
        val c = json.decodeFromString<OrderCampaign>(detail)
        assertTrue(c.canOrder)
        assertEquals("höchstens 3 je Mitglied", orderLimitText(c.articles[0]))
        assertEquals("noch 12 von 50 verfügbar", orderLimitText(c.articles[1]))
        val values = mapOf(orderKey(1, "M") to 2, orderKey(1, "L") to 0, orderKey(2, "") to 1)
        val inputs = orderInputs(c.articles, values)
        assertEquals(listOf(OrderInput(1, "M", 2), OrderInput(2, "", 1)), inputs)
        assertEquals("60.00", orderTotal(c.articles, inputs))
        // Größenabfrage ohne Preis: keine Summe
        val free = c.articles.map { it.copy(price = null) }
        assertNull(orderTotal(free, inputs))
    }

    @Test
    fun nurMitModul() {
        val on = PortalAccess(5, "Musikverein Musterdorf", modules = listOf("orders"), ordersEnabled = true)
        assertTrue(IchBereich.BESTELLUNGEN in ichBereiche(on))
        assertFalse(IchBereich.BESTELLUNGEN in ichBereiche(on.copy(ordersEnabled = false)))
        assertFalse(IchBereich.BESTELLUNGEN in ichBereiche(PortalAccess(5, "Musterdorf")))
    }

    @Test
    fun pushLesen() {
        val m = PushMessage.parse("""{"v":1,"t":"order","id":7,"title":"Bestellung: Vereinskleidung 2027","from":"Musterdorf","text":"Bestellen bis 30.06.2099","access":9}""")!!
        assertEquals("order", m.type)
        assertEquals(9, m.access)
    }
}
