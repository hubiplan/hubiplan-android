package de.hubiplan.app

import androidx.compose.ui.graphics.toArgb
import de.hubiplan.app.data.PairingLink
import de.hubiplan.app.data.Theme
import de.hubiplan.app.ui.brandFor
import de.hubiplan.app.ui.contrast
import de.hubiplan.app.ui.luminance
import de.hubiplan.app.ui.statusIconsDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** UX-Audit 08.11.2026, dritte App-Runde: A-05, A-06, A-07, X-04. */
class UxAudit0811cTest {
    private val ui = File("src/main/java/de/hubiplan/app/ui")
    private fun argb(c: androidx.compose.ui.graphics.Color) = c.toArgb().toLong() and 0xFFFFFFFFL

    /** X-04: Link „In der HUBIplan-App öffnen“ mit Adresse und Einmalcode. */
    @Test
    fun appLinkZumVerbinden() {
        assertEquals(
            PairingLink("https://verein.example.de", "abc-123_X"),
            PairingLink.parse("hubiplan://verbinden?server=https%3A%2F%2Fverein.example.de&code=abc-123_X"),
        )
        assertEquals(
            PairingLink("https://test.example:8443", "x"),
            PairingLink.parse("hubiplan://verbinden?code=x&server=https%3A%2F%2Ftest.example%3A8443%2F"),
        )
        // Zweitform: QR-Adresse als Ganzes, dieselbe Prüfung wie beim Scannen
        assertEquals(
            PairingLink("https://verein.example.de", "abc"),
            PairingLink.parse("hubiplan://verbinden?link=https%3A%2F%2Fverein.example.de%2Fapp%2Fverbinden%23code%3Dabc"),
        )
    }

    @Test
    fun appLinkOhneHttpsOderUnvollstaendigAblehnen() {
        assertNull(PairingLink.parse("hubiplan://verbinden?server=http%3A%2F%2Fverein.example.de&code=abc"))
        assertNull(PairingLink.parse("hubiplan://verbinden?server=https%3A%2F%2Fverein.example.de"))
        assertNull(PairingLink.parse("hubiplan://verbinden?server=https%3A%2F%2Fverein.example.de&code="))
        assertNull(PairingLink.parse("hubiplan://verbinden?code=abc"))
        assertNull(PairingLink.parse("hubiplan://anderes?server=https%3A%2F%2Fverein.example.de&code=abc"))
        assertNull(PairingLink.parse("hubiplan://verbinden?server=https%3A%2F%2Fnutzer%40verein.example.de&code=abc"))
        assertNull(PairingLink.parse("hubiplan://verbinden?server=https%3A%2F%2Fverein.example.de%2Fpfad&code=abc"))
        assertNull(PairingLink.parse("hubiplan://verbinden?link=http%3A%2F%2Fverein.example.de%2Fapp%2Fverbinden%23code%3Dabc"))
        assertNull(PairingLink.parse("hubiplan://verbinden?server=%zz&code=abc"))
    }

    @Test
    fun appLinkImManifest() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("android:scheme=\"hubiplan\"" in manifest)
        assertTrue("android.intent.category.BROWSABLE" in manifest)
        val connect = File(ui, "ConnectScreen.kt").readText()
        assertTrue("Wie möchtest du dich verbinden?" in connect)
        assertFalse("Oder mit E-Mail und Passwort anmelden" in connect)
    }

    /** A-06: Im Dunkelmodus kein heller Balken; dunkle Vereinsfarben bleiben unverändert. */
    @Test
    fun kopfleisteImDunkelmodus() {
        val gelb = Theme(primary = "#ffd500", onPrimary = "#000000", accent = "#ffd500", onAccent = "#000000")
        assertEquals(0xFFFFD500L, argb(brandFor(gelb).brand))
        val dunkel = brandFor(gelb, dark = true)
        assertTrue(luminance(argb(dunkel.brand)) < 0.2)
        assertTrue(contrast(argb(dunkel.brand), argb(dunkel.onBrand)) >= 4.5)
        assertFalse(statusIconsDark(dunkel))
        val blau = Theme(primary = "#14365d", onPrimary = "#ffffff", accent = "#14365d", onAccent = "#ffffff")
        assertEquals(brandFor(blau).brand, brandFor(blau, dark = true).brand)
    }

    /** A-07: keine Zeichen ✓ ✗ ▾ in der Oberfläche, Rückmeldung im Klartext. */
    @Test
    fun symboleStattZeichen() {
        val treffer = ui.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex()
                .filter { (_, line) -> !line.trimStart().startsWith("*") && !line.trimStart().startsWith("//") }
                .filter { (_, line) -> line.contains('✓') || line.contains('✗') || line.contains('▾') }
                .map { (i, _) -> "${f.name}:${i + 1}" }
        }.toList()
        assertEquals(emptyList<String>(), treffer)
        val events = File(ui, "EventsPane.kt").readText()
        assertTrue("tr(\"Zugesagt\")" in events && "tr(\"Nicht dabei\")" in events && "tr(\"Noch offen\")" in events)
    }

    /** A-05: Passwortmanager erkennen E-Mail und Passwort. */
    @Test
    fun autofillHinweise() {
        val connect = File(ui, "ConnectScreen.kt").readText()
        assertTrue(".autofill(ContentType.Username)" in connect)
        assertTrue(".autofill(ContentType.Password)" in connect)
        assertTrue("autofillManager?.commit()" in connect)
        assertTrue(".autofill(ContentType.Password)" in File(ui, "AdminPane.kt").readText())
    }
}
