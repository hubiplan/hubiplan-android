package de.hubiplan.app

import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.MyPlacing
import de.hubiplan.app.data.Network
import de.hubiplan.app.data.errorMessage
import de.hubiplan.app.ui.Section
import de.hubiplan.app.ui.Symbole
import de.hubiplan.app.ui.Tab
import de.hubiplan.app.ui.formatSince
import de.hubiplan.app.ui.offlineText
import de.hubiplan.app.ui.placingLine
import de.hubiplan.app.ui.problem
import de.hubiplan.app.ui.sectionIcon
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** UX-Audit 08.11.2026, Android zweite Runde: A-02, A-03, A-04, A-15, A-17, A-18. */
class UxAudit0811bTest {
    private val katalog = Texte.parse(File("src/main/assets/texte/en.json").readText())
    private val ui = File("src/main/java/de/hubiplan/app/ui")
    private fun src(name: String) = File(ui, name).readText()

    @After
    fun deutsch() = Texte.setzeFuerTest("de", katalog)

    /** A-02: Termine, Mitteilungen, Aufgaben und die Listen der Verwaltung lassen sich herunterziehen. */
    @Test
    fun ziehenZumAktualisieren() {
        assertTrue("PullToRefreshBox" in src("Rueckmeldung.kt"))
        listOf("EventsPane.kt", "CircularsPane.kt", "MemberTodos.kt").forEach { assertTrue(it, "LoadableList(" in src(it)) }
        listOf("MemberHelpers.kt", "AdminPane.kt").forEach { assertTrue(it, "Refreshable(" in src(it)) }
        // TalkBack: Aktion „Aktualisieren“ an der Standzeile als Ersatz für die Geste
        assertTrue("CustomAccessibilityAction(action)" in src("Rueckmeldung.kt"))
    }

    /** A-03: Fehler in Nutzersprache, Statuscode nur unter „Details“, offline getrennt. */
    @Test
    fun fehlerInNutzersprache() {
        val offline = ApiException(0, "x", network = Network.OFFLINE).problem()
        assertTrue(offline.offline)
        val timeout = ApiException(0, "x", network = Network.TIMEOUT).problem()
        assertFalse(timeout.offline)
        assertEquals("HUBIplan antwortet gerade nicht. Bitte später erneut versuchen.", timeout.text)
        val server = ApiException(503, errorMessage(503, "<html>Bad Gateway</html>")).problem()
        assertFalse("kein Code im Text", "503" in server.text)
        assertFalse("kein Servertext", "html" in server.text)
        assertTrue(server.detail!!.contains("503"))
        assertNull(ApiException(0, "Bitte die Adresse angeben.").problem().detail)
        // Kein e.message mehr direkt in der Oberfläche (nur beim Abmelden eines Geräts weitergereicht)
        ui.listFiles()!!.filter { it.extension == "kt" }.forEach { f ->
            val raw = Regex("""(error|message|photoError)\s*=\s*e\.message""").find(f.readText())
            assertNull("${f.name}: ${raw?.value}", raw)
        }
        assertFalse("kein Fehlerrot für offline", "Keine Verbindung zu HUBIplan. Bitte später" in src("MainScreen.kt"))
        assertTrue("OfflineBanner(since = null" in src("MainScreen.kt"))
    }

    @Test
    fun offlineMitStand() {
        val berlin = ZoneId.of("Europe/Berlin")
        val stand = ZonedDateTime.of(2026, 11, 9, 14, 32, 0, 0, berlin).toInstant().toEpochMilli()
        val spaeter = ZonedDateTime.of(2026, 11, 9, 18, 0, 0, 0, berlin).toInstant().toEpochMilli()
        assertEquals("14:32 Uhr", formatSince(stand, spaeter, berlin))
        assertTrue(formatSince(stand, spaeter + 86_400_000L, berlin).startsWith("9. Nov"))
        assertEquals("Offline – keine Verbindung zu HUBIplan.", offlineText(null))
        assertTrue(offlineText(stand).startsWith("Offline – du siehst den Stand von "))
        Texte.setzeFuerTest("en", katalog)
        assertEquals("14:32", formatSince(stand, spaeter, berlin))
        assertEquals("Offline – no connection to HUBIplan.", offlineText(null))
    }

    /** A-04: Begriffe aus `docs/begriffe.md`, keine Glocke und kein Einkaufswagen. */
    @Test
    fun begriffeUndSymbole() {
        val verboten = listOf("Änderungsmeldung", "Mitgliedskarte", "To-do", "Benutzerkonto", "Anlässe")
        val treffer = katalog.keys.filter { k -> verboten.any { it in k } }
        assertEquals(emptyList<String>(), treffer)
        assertEquals("Datenänderungen, Fotos und Termine, die auf dich warten", Section.TASKS.hint)
        assertEquals("Aufgaben verteilen", Section.TODOS.label)
        assertNotEquals(Tab.AUFGABEN.label, Section.TODOS.label)
        assertEquals(Symbole.ZuErledigen, sectionIcon(Section.TASKS))
        assertEquals(Symbole.Kassenbuch, sectionIcon(Section.FINANCE))
        assertEquals(Symbole.Rechnungen, sectionIcon(Section.BILLING))
        listOf("AdminPane.kt", "MainScreen.kt").forEach { f ->
            listOf("ShoppingCart", "Icons.Filled.Notifications", "Icons.Filled.Build").forEach { assertFalse("$f: $it", it in src(f)) }
        }
        assertTrue("Tab.VERWALTUNG -> Symbole.Verwaltung" in src("MainScreen.kt"))
    }

    /** A-15: Angebot nur in der Terminliste, kompakt, nur einmal. */
    @Test
    fun startbildschirmHinweis() {
        val main = src("MainScreen.kt")
        assertTrue("tab == Tab.TERMINE && detail == null) ShortcutOffer(vm)" in main)
        assertTrue("vm.shortcutOfferSeen()" in main)
        assertFalse("keine Erklärzeile mehr", "Eine Verknüpfung mit Wappen" in main)
        assertTrue("SHORTCUT_SEEN" in src("AppViewModel.kt"))
    }

    /** A-17: Vom Hinweis direkt in die Einrichtung der Bildschirmsperre, danach neu prüfen. */
    @Test
    fun bildschirmsperreEinrichten() {
        val admin = src("AdminPane.kt")
        assertTrue("tr(\"Bildschirmsperre einrichten\")" in admin)
        assertTrue("DeviceUnlock.openSetup(context)" in admin)
        assertTrue("remember(resumed) { DeviceUnlock.available(context) }" in admin)
        val unlock = src("DeviceUnlock.kt")
        assertTrue("ACTION_BIOMETRIC_ENROLL" in unlock && "ACTION_SECURITY_SETTINGS" in unlock)
    }

    /** A-18: Jede Zahl beschriftet, Kontakte antippbar. */
    @Test
    fun ausweis() {
        assertEquals("Einzel: 2. Platz von 14 · 27 Ringe", placingLine(MyPlacing("Einzel", 2, "27", 14, unitLabel = "Ringe")))
        assertEquals("Einzelwertung: Wert 27", placingLine(MyPlacing("Einzelwertung", null, "27", null)))
        assertEquals("Einzel: 2. Platz von 14 · 28 Ringe", placingLine(MyPlacing("Einzel", 2, "28 Ringe", 14, unitLabel = "Ringe")))
        Texte.setzeFuerTest("en", katalog)
        assertEquals("Single: Place 2 · Value 27", placingLine(MyPlacing("Single", 2, "27", null)))
        assertTrue("ContactRows(c)" in src("ProfilePane.kt"))
    }
}
