package de.hubiplan.app

import de.hubiplan.app.data.BrowserLogin
import de.hubiplan.app.data.PendingBrowserLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verbindungscode an die App gebunden (Gesamtaudit 15.11., APP-01): Geheimnis, Prüfwert und
 * der Link hubiplan://anmelden. Gleiche Fälle wie in hubiplan-ios (BrowserLoginTests).
 */
class BrowserLoginTest {
    @Test
    fun pruefwertWieRfc7636() {
        // Beispiel aus RFC 7636, Anhang B; derselbe Wert steht im Servertest
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            BrowserLogin.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun geheimnisZufaelligUndImErlaubtenZeichensatz() {
        val a = BrowserLogin.newVerifier()
        val b = BrowserLogin.newVerifier()
        assertNotEquals(a, b)
        assertEquals(43, a.length)
        assertTrue(a.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' })
        assertEquals(43, BrowserLogin.challenge(a).length)
    }

    @Test
    fun anmeldeseiteMitPruefwert() {
        assertEquals(
            "https://verein.example.de/app/anmelden?challenge=abc",
            BrowserLogin.url("verein.example.de", "abc"),
        )
        assertNull(BrowserLogin.url("http://verein.example.de", "abc"))
        assertNull(BrowserLogin.url("", "abc"))
    }

    @Test
    fun startlinkOhneCode() {
        assertEquals(
            "https://verein.example.de",
            BrowserLogin.parseStart("hubiplan://anmelden?server=https%3A%2F%2Fverein.example.de"),
        )
        assertEquals(
            "https://verein.example.de:8443",
            BrowserLogin.parseStart("hubiplan://anmelden?server=https%3A%2F%2Fverein.example.de%3A8443"),
        )
    }

    @Test
    fun fremdeOderUnvollstaendigeStartlinksAblehnen() {
        assertNull(BrowserLogin.parseStart("hubiplan://anmelden?server=http%3A%2F%2Fverein.example.de"))
        assertNull(BrowserLogin.parseStart("hubiplan://anmelden?server=https%3A%2F%2Fverein.example.de%2Fpfad"))
        assertNull(BrowserLogin.parseStart("hubiplan://anmelden?server=https%3A%2F%2Fnutzer%40verein.example.de"))
        assertNull(BrowserLogin.parseStart("hubiplan://anmelden"))
        assertNull(BrowserLogin.parseStart("hubiplan://verbinden?server=https%3A%2F%2Fverein.example.de&code=x"))
        assertNull(BrowserLogin.parseStart("https://verein.example.de/app/anmelden"))
    }

    @Test
    fun geheimnisNurFuerDieselbeAdresseUndRechtzeitig() {
        val pending = PendingBrowserLogin("https://verein.example.de", "geheim", createdAt = 1_000)
        assertEquals("geheim", BrowserLogin.verifierFor(pending, "https://verein.example.de", 1_000 + 60_000))
        // Rückruf einer anderen Adresse: kein Geheimnis (Code ohne Prüfwert wie bisher)
        assertNull(BrowserLogin.verifierFor(pending, "https://andere.example.de", 2_000))
        // Zu spät: der Code wäre ohnehin abgelaufen
        assertNull(BrowserLogin.verifierFor(pending, "https://verein.example.de", 1_000 + BrowserLogin.MAX_AGE_MS + 1))
        assertNull(BrowserLogin.verifierFor(null, "https://verein.example.de", 2_000))
    }
}
