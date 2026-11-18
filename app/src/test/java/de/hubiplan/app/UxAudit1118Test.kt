package de.hubiplan.app

import de.hubiplan.app.ui.BLACK
import de.hubiplan.app.ui.NEUTRAL_DUNKEL
import de.hubiplan.app.ui.NEUTRAL_HELL
import de.hubiplan.app.ui.WHITE
import de.hubiplan.app.ui.contrast
import de.hubiplan.app.ui.mix
import de.hubiplan.app.ui.parseHexColor
import de.hubiplan.app.ui.withContrast
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** UX-Audit 16.11.2026, Android-Reste vom 18.11.: AND-09, AND-18, AND-19. */
class UxAudit1118Test {
    private val ui = File("src/main/java/de/hubiplan/app/ui")

    /** Alle Aufrufe von OutlinedTextField samt Argumenten, mit Datei und Zeile. */
    private fun felder(): List<Pair<String, String>> = ui.listFiles()!!.filter { it.extension == "kt" }.flatMap { f ->
        val s = f.readText()
        Regex("(?<![\\w.])OutlinedTextField\\(").findAll(s).map { m ->
            var i = m.range.last + 1
            var tiefe = 1
            while (tiefe > 0) {
                when (s[i]) {
                    '(' -> tiefe++
                    ')' -> tiefe--
                }
                i++
            }
            "${f.name}:${s.substring(0, m.range.first).count { it == '\n' } + 1}" to s.substring(m.range.first, i)
        }.toList()
    }

    /** AND-09: Jedes einzeilige Feld sagt, was die Eingabetaste tut; Freitext beginnt groß. */
    @Test
    fun eingabetasteUndGrossschreibung() {
        val bekannt = listOf("imeAction", "WEITER", "SUCHE", "TITELZEILE", "IBAN_TASTATUR", "keyboardFor(", "keyboardOptions = keyboardOptions")
        for ((ort, feld) in felder()) {
            if ("Eingabe.kt" in ort || "ServerFields.kt" in ort) continue
            if ("singleLine = true" in feld) {
                assertTrue("$ort: einzeiliges Feld ohne Eingabetaste", bekannt.any { it in feld })
            } else {
                assertTrue("$ort: Freitext ohne Großschreibung", "FREITEXT" in feld || "keyboardOptions" in feld)
            }
        }
        val server = File(ui, "ServerFields.kt").readText()
        assertTrue("singleLine && keyboardOptions.imeAction == ImeAction.Default" in server)
    }

    /** AND-18: Mitglieder wenden sich an ihren Verein, nicht an „den Vorstand“; Auswahl im Kopf ohne „Einheit“. */
    @Test
    fun vereinStattVorstand() {
        val texte = ui.parentFile.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse("wende dich an den Vorstand" in texte)
        assertFalse("Nachricht an den Vorstand (" in texte)
        assertFalse("Rundschreiben des Vorstands" in texte)
        assertFalse("tr(\"Einheit wählen\")" in texte)
        assertTrue("tr(\"Verein oder Gruppe wählen\")" in texte)
    }

    /** AND-19: Neutrale Flächen wie in der Weboberfläche, lesbar, Vereinsfarben auf allen Flächen lesbar. */
    @Test
    fun neutraleFlaechenLesbar() {
        for (n in listOf(NEUTRAL_HELL, NEUTRAL_DUNKEL)) {
            for (f in n.flaechen) {
                assertTrue("Schrift auf ${f.toString(16)}", contrast(n.onSurface, f) >= 4.5)
                assertTrue("Nebenschrift auf ${f.toString(16)}", contrast(n.onSurfaceVariant, f) >= 4.5)
                assertTrue("Feldrahmen auf ${f.toString(16)}", contrast(n.outline, f) >= 3.0)
            }
            assertTrue("Snackbar", contrast(n.inverseOnSurface, n.inverseSurface) >= 4.5)
        }
        // Kein Violettstich: Rot- und Blauanteil liegen nicht über dem Grünanteil
        for (f in NEUTRAL_HELL.flaechen + NEUTRAL_DUNKEL.flaechen) {
            val r = (f shr 16) and 0xFF
            val g = (f shr 8) and 0xFF
            val b = f and 0xFF
            assertTrue("Fläche ${f.toString(16)} grünlich-grau", g >= r && g >= b)
        }
        for (hex in listOf("#ffd700", "#e0a526", "#7fb3d5", "#4caf50", "#00a0e0", "#1f4e79", "#560000")) {
            val c = parseHexColor(hex)!!
            val hell = withContrast(c, NEUTRAL_HELL.flaechen, BLACK)
            val dunkel = withContrast(mix(c, WHITE, 0.45f), NEUTRAL_DUNKEL.flaechen, WHITE)
            NEUTRAL_HELL.flaechen.forEach { assertTrue("$hex hell", contrast(hell, it) >= 4.5) }
            NEUTRAL_DUNKEL.flaechen.forEach { assertTrue("$hex dunkel", contrast(dunkel, it) >= 4.5) }
        }
    }
}
