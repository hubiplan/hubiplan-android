package de.hubiplan.app

import de.hubiplan.app.data.PinboardList
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.json
import de.hubiplan.app.push.PushMessage
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.ichBereiche
import de.hubiplan.app.ui.pinboardSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pinnwand unter „Ich“ (HUBIplan Pinnwand); alle Daten erfunden. */
class PinnwandTest {
    private val text = """
        {"notices":[
          {"id":2,"text":"Bilder vom Sommerfest","unit":"","department":null,"from":"Musikverein Musterdorf",
           "author":"Erika Beispiel","pinned":true,"visible_until":null,"created_at":"2099-06-20T08:00:00Z",
           "edited_at":null,"push":false,"pushed":false,"expired":false,"version":1,"can_edit":false,
           "photos":[{"id":5,"url":"/api/portal/9/pinboard/2/photos/5/","width":40,"height":30}],"new":true},
          {"id":1,"text":"Der Bus fährt 10 Minuten später ab.","unit":"Jägerzug","department":4,
           "from":"Jägerzug","author":"","pinned":false,"created_at":"2099-06-19T08:00:00Z","new":false}],
         "targets":[{"department":4,"name":"Jägerzug"}],"max_photos":6,"max_text":2000}
    """.trimIndent()

    @Test
    fun antwortLesen() {
        val list = json.decodeFromString<PinboardList>(text)
        assertEquals(2, list.notices.size)
        assertEquals(1, list.newCount)
        assertEquals("/api/portal/9/pinboard/2/photos/5/", list.notices[0].photos[0].url)
        assertEquals(4, list.targets.single().department)
        assertEquals("1 neuer Beitrag", pinboardSummary(list))
        assertEquals("Kurze Neuigkeiten aus dem Verein", pinboardSummary(list.copy(notices = list.notices.map { it.copy(new = false) })))
        assertEquals("Noch keine Beiträge", pinboardSummary(PinboardList()))
    }

    @Test
    fun nurMitModulOben() {
        val on = PortalAccess(5, "Musikverein Musterdorf", modules = listOf("pinboard"), pinboardEnabled = true)
        assertEquals(IchBereich.PINNWAND, ichBereiche(on).first())
        assertFalse(IchBereich.PINNWAND in ichBereiche(on.copy(pinboardEnabled = false)))
        // Ältere HUBIplan-Stände ohne Pinnwand: kein Eintrag
        assertFalse(IchBereich.PINNWAND in ichBereiche(PortalAccess(5, "Musterdorf")))
    }

    @Test
    fun pushLesen() {
        val m = PushMessage.parse("""{"v":1,"t":"pinboard","id":3,"title":"Pinnwand: Jägerzug","from":"Jägerzug","text":"Treffpunkt Kirche","access":9}""")!!
        assertEquals("pinboard", m.type)
        assertEquals(9, m.access)
        assertTrue(m.text.startsWith("Treffpunkt"))
    }
}
