package de.hubiplan.app

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import de.hubiplan.app.data.CircularItem
import de.hubiplan.app.ui.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CircularContentTest {
    @Test fun zielEnthaeltGenauZugangUndSchreibenOhneSitzungsdaten() {
        assertEquals("https://club.example.org:8443/mein-bereich/rundschreiben?zugang=9&schreiben=17", circularWebUrl("https://club.example.org:8443/alt?token=geheim#fragment",9,17))
        assertNull(circularWebUrl("http://club.example.org",9,17))
        assertNull(circularWebUrl("https://club.example.org",0,17))
        assertNull(circularWebUrl("https://club.example.org",9,-1))
    }
    @Test fun gleicheAuszeichnungWieImWebMitAbsaetzenUndListen() {
        val blocks = circularBlocks("Hallo\r\nWelt\r\n\r\n- eins\r\n• zwei\r\n\r\n1. zuerst\r\n2) danach")
        assertEquals(listOf("p","ul","ol"), blocks.map {it.kind})
        assertEquals(listOf("Hallo","Welt"),blocks[0].lines)
        assertEquals(listOf("eins","zwei"),blocks[1].lines)
        assertEquals(listOf("zuerst","danach"),blocks[2].lines)
    }
    @Test fun lesbareFormatierungUndNurErlaubteLinkschemata() {
        val opened=mutableListOf<String>()
        val text=circularInline("**wichtig** *leise* [Plan](https://club.example.org/plan) [Kontakt](mailto:board@example.org) [Falsch](javascript:alert)",TextLinkStyles()) {opened+=it}
        assertTrue(text.text.startsWith("wichtig leise Plan Kontakt"))
        assertTrue(text.text.contains("[Falsch](javascript:alert)"))
        assertTrue(text.spanStyles.any {it.item.fontWeight==FontWeight.Bold})
        assertTrue(text.spanStyles.any {it.item.fontStyle==FontStyle.Italic})
        val links=text.getLinkAnnotations(0,text.length)
        assertEquals(2,links.size)
        (links[0].item as LinkAnnotation.Clickable).linkInteractionListener?.onClick(links[0].item)
        assertEquals(listOf("https://club.example.org/plan"),opened)
    }
    @Test fun anhangsmetadatenWerdenGelesenUndAlteAntwortenBleibenKompatibel() {
        val json=Json {ignoreUnknownKeys=true}
        val item=json.decodeFromString<CircularItem>("""{"id":17,"subject":"Einladung","attachments":[{"id":8,"name":"Plan.pdf","size":23,"url":"/api/file/8/"}]}""")
        assertEquals("Plan.pdf",item.attachments.single().name)
        assertTrue(json.decodeFromString<CircularItem>("""{"id":17,"subject":"Einladung"}""").attachments.isEmpty())
    }
}
