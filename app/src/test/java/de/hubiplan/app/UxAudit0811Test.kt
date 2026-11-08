package de.hubiplan.app

import androidx.compose.ui.graphics.toArgb
import de.hubiplan.app.data.Theme
import de.hubiplan.app.ui.LoginField
import de.hubiplan.app.ui.Tab
import de.hubiplan.app.ui.barLabelMinScale
import de.hubiplan.app.ui.brandFor
import de.hubiplan.app.ui.statusIconsDark
import de.hubiplan.app.ui.breakableCoordinate
import de.hubiplan.app.ui.colorSchemeFor
import de.hubiplan.app.ui.contrast
import de.hubiplan.app.ui.formatStamp
import de.hubiplan.app.ui.missingLoginFields
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** UX-Audit 08.11.2026, Android A-01, A-10/A-11, A-12, A-14, A-16, A-19. */
class UxAudit0811Test {
    private val katalog = Texte.parse(File("src/main/assets/texte/en.json").readText())
    private val ui = File("src/main/java/de/hubiplan/app/ui")

    @After
    fun deutsch() = Texte.setzeFuerTest("de", katalog)

    /** A-14: kurze Beschriftungen in der Leiste, einzeilig, höchstens bis Standardgröße verkleinert. */
    @Test
    fun kurzeReiterbeschriftungen() {
        Tab.entries.forEach { assertTrue("${it.short} zu lang", it.short.length <= 9) }
        assertEquals("Neues", Tab.MITTEILUNGEN.short)
        assertEquals("Verwalten", Tab.VERWALTUNG.short)
        assertEquals("Mitteilungen", Tab.MITTEILUNGEN.label)
        val src = File(ui, "MainScreen.kt").readText()
        assertTrue("Leiste nutzt die Kurzform", "BarLabel(t.short)" in src)
        assertTrue("einzeilig", "maxLines = 1" in src && "softWrap = false" in src)
        assertEquals(1f, barLabelMinScale(1f), 0.001f)
        assertEquals(0.6f, barLabelMinScale(2f), 0.001f)
        assertEquals(1f / 1.3f, barLabelMinScale(1.3f), 0.001f)
    }

    @Test
    fun kurzeReiterbeschriftungenEnglisch() {
        Texte.setzeFuerTest("en", katalog)
        assertEquals("Messages", Texte.text("Neues"))
        assertEquals("Admin", Texte.text("Verwalten"))
        listOf("Termine", "Aufgaben", "Neues", "Ausweis", "Verwalten").forEach {
            assertTrue(Texte.text(it).length <= 9)
        }
    }

    /** A-19: aktiver Reiter mit mindestens 3 : 1 zur Leiste (Telefon und Tablet), hell und dunkel. */
    @Test
    fun aktiverReiterSichtbar() {
        val farben = listOf("#ffd500", "#8ecae6", "#ffffff", "#000000", "#0e7c66", "#c00000", "#7fff00")
        for (hex in farben) for (dark in listOf(false, true)) {
            val s = colorSchemeFor(Theme(primary = hex, onPrimary = "#ffffff", accent = hex, onAccent = "#000000"), dark)
            val indicator = s.primary.toArgb().toLong() and 0xFFFFFFFFL
            for (bar in listOf(s.surfaceContainer, s.surface)) {
                val c = contrast(indicator, bar.toArgb().toLong() and 0xFFFFFFFFL)
                assertTrue("$hex dark=$dark: $c", c >= 3.0)
            }
            val icon = contrast(s.onPrimary.toArgb().toLong() and 0xFFFFFFFFL, indicator)
            assertTrue("Symbol auf Markierung $hex dark=$dark: $icon", icon >= 3.0)
        }
        val src = File(ui, "MainScreen.kt").readText()
        assertTrue("indicatorColor = scheme.primary" in src)
    }

    /** A-01: kein „‹ Zurück“ mehr im Inhalt, Pfeil in der Kopfleiste. */
    @Test
    fun pfeilZurueckInDerKopfleiste() {
        val treffer = ui.listFiles()!!.filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) -> "tr(\"‹" in l }.map { "${f.name}:${it.index + 1}" }
        }
        assertEquals(emptyList<String>(), treffer)
        assertTrue("BackHead(e?.title" in File(ui, "EventsPane.kt").readText())
        assertTrue("BackHead(current.label)" in File(ui, "AdminPane.kt").readText())
        assertFalse("‹ Zurück" in katalog)
    }

    /** A-10/A-11: Systemleisten freihalten, Lizenzseite mit Kopfleiste, Paketnamen ohne Trennung. */
    @Test
    fun systemleistenUndLizenzseite() {
        assertTrue("safeDrawingPadding" in File("src/main/java/de/hubiplan/app/MainActivity.kt").readText())
        val lic = File(ui, "LicensesScreen.kt").readText()
        assertTrue("safeDrawingPadding" in lic)
        assertTrue("TopAppBar(" in lic)
        assertTrue("Hyphens.None" in lic)
        assertEquals("androidx.​compose:​ui:​1.​7", breakableCoordinate("androidx.compose:ui:1.7"))
    }

    /** A-10: Statusleiste mit eigener Fläche in der Vereinsfarbe, Symbole lesbar darauf. */
    @Test
    fun statusleisteMitFlaeche() {
        assertTrue("StatusBarStrip(window)" in File("src/main/java/de/hubiplan/app/MainActivity.kt").readText())
        assertTrue("StatusBarStrip(dialogWindow)" in File(ui, "LicensesScreen.kt").readText())
        assertFalse(statusIconsDark(brandFor(Theme(primary = "#0e7c66", onPrimary = "#ffffff", accent = "#0e7c66", onAccent = "#000000"))))
        assertTrue(statusIconsDark(brandFor(Theme(primary = "#ffd500", onPrimary = "#ffffff", accent = "#ffd500", onAccent = "#000000"))))
    }

    /** A-12: Knöpfe bleiben antippbar, fehlende Felder werden benannt. */
    @Test
    fun anmeldenUndPasswortVergessenImmerAntippbar() {
        assertEquals(LoginField.entries, missingLoginFields(" ", "", ""))
        assertEquals(listOf(LoginField.PASSWORD), missingLoginFields("verein.example.de", "a@example.org", ""))
        assertEquals(emptyList<LoginField>(), missingLoginFields("verein.example.de", "a@example.org", "x"))
        val src = File(ui, "ConnectScreen.kt").readText()
        assertFalse("recoveryUrl != null," in src)
        assertFalse("email.isNotBlank() &&" in src)
    }

    /** A-16: Datum in der Sprache der App, nicht des Systems. */
    @Test
    fun datumInAppSprache() {
        val berlin = ZoneId.of("Europe/Berlin")
        val t = ZonedDateTime.of(2026, 11, 8, 21, 8, 0, 0, berlin).toInstant().toEpochMilli()
        assertEquals("8. Nov. 2026, 21:08 Uhr", formatStamp(t, berlin))
        Texte.setzeFuerTest("en", katalog)
        assertEquals("8 Nov 2026, 21:08", formatStamp(t, berlin))
        val alt = listOf("SettingsSection.kt", "MemberCardPane.kt").filter { "DateFormat.getDateTimeInstance" in File(ui, it).readText() }
        assertEquals(emptyList<String>(), alt)
    }
}
