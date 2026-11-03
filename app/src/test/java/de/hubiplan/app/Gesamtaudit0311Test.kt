package de.hubiplan.app

import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.LoginStep
import de.hubiplan.app.data.PairingLink
import de.hubiplan.app.data.normalizeServer
import de.hubiplan.app.data.parseAuthStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Gesamtaudit 03.11.2026: F04 (ungültiger Port) und F03 (kaputte Anmeldeantwort). */
class Gesamtaudit0311Test {
    @Test
    fun portAusserhalbDesBereichsAblehnen() {
        for (port in listOf("0", "65536", "99999")) {
            assertNull(port, normalizeServer("verein.example.de:$port"))
            assertNull(port, PairingLink.parse("https://verein.example.de:$port/app/verbinden#code=x"))
        }
    }

    @Test
    fun gueltigePortsBleiben() {
        assertEquals("https://verein.example.de", normalizeServer("verein.example.de"))
        for (port in listOf(1, 443, 8443, 65535)) {
            assertEquals("https://verein.example.de:$port", normalizeServer("verein.example.de:$port"))
            assertEquals(
                "https://verein.example.de:$port",
                PairingLink.parse("https://verein.example.de:$port/app/verbinden#code=x")?.server,
            )
        }
    }

    @Test
    fun kaputteAnmeldeantwortWirdFehlermeldung() {
        val kaputt = listOf(
            """{"meta": []}""",
            """{"meta": {"session_token": {"a": 1}}}""",
            """{"data": {"flows": {"id": "x"}}}""",
            """{"data": {"flows": ["mfa_authenticate"]}}""",
            """{"data": []}""",
        )
        for (text in kaputt) {
            assertThrows(text, ApiException::class.java) { parseAuthStep(401, text, null) }
        }
        assertThrows(ApiException::class.java) { parseAuthStep(500, "kein JSON", null) }
    }

    @Test
    fun gueltigeAnmeldeantwortenUnveraendert() {
        assertEquals(LoginStep.Done("s1"), parseAuthStep(200, """{"meta": {"session_token": "s1"}}""", null))
        val mfa = """{"meta": {"session_token": "s2"}, "data": {"flows": [{"id": "mfa_authenticate", "is_pending": true}]}}"""
        assertEquals(LoginStep.NeedsCode("s2"), parseAuthStep(401, mfa, null))
        assertEquals(LoginStep.Done("alt"), parseAuthStep(200, "{}", "alt"))
    }
}
