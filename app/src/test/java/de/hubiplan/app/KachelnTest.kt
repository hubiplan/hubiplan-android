package de.hubiplan.app

import de.hubiplan.app.data.HomeTiles
import de.hubiplan.app.data.TileState
import de.hubiplan.app.data.json
import de.hubiplan.app.ui.tileStateLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Kacheln der Übersicht (HUBIplan backend/hometiles); alle Daten erfunden. */
class KachelnTest {
    private val text = """
        {"scope":"p9","tiles":[
          {"key":"umfragen","title":"Offene Umfragen","description":"Umfragen, die noch auf deine Antwort warten",
           "state":"auto","urgent":true,"shown":true,
           "content":{"figures":[],"rows":[{"title":"Sommerfest: Wer hilft mit?","detail":"bis Sa. 27.06. 18:00",
             "link":"/mein-bereich/umfragen","tone":"warn"}],"text":"","link":"/mein-bereich/umfragen",
             "link_label":"Zu den Umfragen","reason":"Eine Umfrage wartet auf deine Antwort."}},
          {"key":"pinnwand","title":"Pinnwand","description":"Neue Beiträge auf der Pinnwand",
           "state":"auto","urgent":false,"shown":false,
           "content":{"figures":[{"label":"Neue Beiträge","value":"0","tone":null}],"rows":[],"text":"",
             "link":"/mein-bereich/pinnwand","link_label":"Zur Pinnwand","reason":""}},
          {"key":"meine-anmeldungen","title":"Meine Anmeldungen","state":"off","urgent":false,"shown":false,
           "content":null, "neu_in_spaeterer_fassung": 1}
        ]}
    """.trimIndent()

    @Test
    fun antwortLesen() {
        val data = json.decodeFromString<HomeTiles>(text)
        assertEquals(3, data.tiles.size)
        // Nur Kacheln mit Anlass oder „immer“ erscheinen
        assertEquals(listOf("umfragen"), data.visible.map { it.key })
        val umfragen = data.tiles[0].content!!
        assertEquals("Eine Umfrage wartet auf deine Antwort.", umfragen.reason)
        assertEquals("warn", umfragen.rows.single().tone)
        assertEquals("Zu den Umfragen", umfragen.linkLabel)
        assertEquals("0", data.tiles[1].content!!.figures.single().value)
        assertEquals(TileState.OFF, data.tiles[2].state)
        assertNull(data.tiles[2].content)
    }

    @Test
    fun auswahlBeschriftet() {
        assertEquals(listOf("bei Anlass", "immer", "nie"), TileState.ALL.map(::tileStateLabel))
    }
}
