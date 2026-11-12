package de.hubiplan.app

import de.hubiplan.app.data.serverLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Gesamtaudit 11.11.2026, H-08: Verweise aus Aufgaben bleiben auf der eigenen Instanz. */
class Gesamtaudit1111Test {
    private val server = "https://verein.example.de"

    @Test
    fun pfadMitSchraegstrichBleibtAufDerInstanz() {
        assertEquals("https://verein.example.de/mitglieder/wechsel/3", serverLink(server, "/mitglieder/wechsel/3"))
        assertEquals("https://verein.example.de/a?b=1#c", serverLink(server, "/a?b=1#c"))
        assertEquals("https://verein.example.de:8443/x", serverLink("https://verein.example.de:8443", "/x"))
    }

    @Test
    fun fremdeAdressenAblehnen() {
        for (path in listOf(
            "",
            ".boese.example/x",
            "@boese.example/x",
            ":443@boese.example/",
            "//boese.example/x",
            "/\\boese.example/x",
            "https://boese.example/x",
            "javascript:alert(1)",
            "/ x",
            "/a\nb",
        )) {
            assertNull(path, serverLink(server, path))
        }
    }

    @Test
    fun ungueltigeInstanzAblehnen() {
        assertNull(serverLink("", "/x"))
        assertNull(serverLink("http://verein.example.de", "/x"))
    }
}
