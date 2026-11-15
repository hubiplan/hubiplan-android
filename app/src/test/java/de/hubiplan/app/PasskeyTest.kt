package de.hubiplan.app

import de.hubiplan.app.data.AdminOverview
import de.hubiplan.app.data.Instance
import de.hubiplan.app.data.Passkeys
import de.hubiplan.app.data.json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anmelden und Verwaltung freischalten mit Passkey (hubiplan docs/app.md); Daten erfunden. */
class PasskeyTest {
    @Test
    fun instanzNenntPlattformen() {
        val mit = json.decodeFromString<Instance>(
            """{"product":"HUBIplan","api_version":1,"passkey":["android","ios"]}""",
        )
        assertTrue("android" in mit.passkey)
        // Ältere Instanzen ohne Feld: kein Passkey
        val ohne = json.decodeFromString<Instance>("""{"product":"HUBIplan","api_version":1}""")
        assertTrue(ohne.passkey.isEmpty())
    }

    @Test
    fun verwaltungMitPasskeyOhneCode() {
        val o = json.decodeFromString<AdminOverview>("""{"two_factor":false,"passkey":true}""")
        assertFalse(o.twoFactor)
        assertTrue(o.passkey)
        assertFalse(json.decodeFromString<AdminOverview>("""{"two_factor":true}""").passkey)
    }

    @Test
    fun hinweisJeVariante() {
        Texte.setzeFuerTest("de", emptyMap())
        val foss = Passkeys.unsupportedText(foss = true)
        assertTrue(foss.contains("Android 14"))
        assertNotEquals(foss, Passkeys.unsupportedText(foss = false))
        assertEquals(false, Passkeys.noPasskeyText().isBlank())
    }
}
