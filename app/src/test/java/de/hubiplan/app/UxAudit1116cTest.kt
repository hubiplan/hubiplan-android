package de.hubiplan.app

import de.hubiplan.app.data.HelperPlan
import de.hubiplan.app.data.HelperShift
import de.hubiplan.app.data.cancellableAfterSignup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** UX-Audit 16.11.2026, Android niedrig: AND-17, 20, 21, 22, 24, 26, 29. */
class UxAudit1116cTest {
    private val ui = File("src/main/java/de/hubiplan/app/ui")
    private fun quelle(name: String) = File(ui, name).readText()

    /** AND-22: Nachfrage nur, wenn die Absage danach nicht mehr selbst geht (Frist wie der Server). */
    @Test
    fun absageFristBeimEintragen() {
        val shift = HelperShift(id = 1, startsAt = "2026-12-05T10:00:00+01:00", endsAt = "2026-12-05T14:00:00+01:00")
        val plan = HelperPlan(id = 9, title = "Sommerfest", cancelDays = 3)
        assertTrue(plan.cancellableAfterSignup(shift, Instant.parse("2026-12-01T08:00:00Z")))
        assertFalse(plan.cancellableAfterSignup(shift, Instant.parse("2026-12-03T08:00:00Z")))
        assertFalse(plan.cancellableAfterSignup(shift.copy(startsAt = "kaputt"), Instant.parse("2026-12-01T08:00:00Z")))
        val helpers = quelle("MemberHelpers.kt")
        assertTrue("Verbindlich eintragen?" in helpers)
    }

    /** AND-21: Webadressen nur über openInBrowser, ohne Absturz ohne Browser. */
    @Test
    fun browserNurUeberOpenInBrowser() {
        listOf("AccountPane.kt", "MemberTodos.kt", "MemberHelpers.kt", "AdminPane.kt", "LicensesScreen.kt").forEach {
            assertFalse("$it öffnet den Browser selbst", "startActivity(Intent(Intent.ACTION_VIEW" in quelle(it))
        }
        assertTrue("runCatching" in quelle("Extern.kt"))
    }

    /** AND-17: Scan ohne Ergebnis prüft die Kamera-Erlaubnis und nennt einen Ausweg. */
    @Test
    fun scanOhneKameraGibtHinweis() {
        assertTrue("scanWithoutResult(" in quelle("ConnectScreen.kt"))
        assertTrue("scanWithoutResult(" in quelle("AdminPane.kt"))
        assertTrue("ACTION_APPLICATION_DETAILS_SETTINGS" in quelle("Extern.kt"))
    }

    /** AND-20: Fenster im Dunkelmodus dunkel. */
    @Test
    fun startfensterDunkel() {
        val night = File("src/main/res/values-night/themes.xml").readText()
        assertTrue("Theme.Material.NoActionBar" in night && "windowBackground" in night)
    }

    /** AND-24: Ansichtswechsel als Segmentknopf, Fehler lässt die Aufgabe stehen. */
    @Test
    fun aufgabenWechselUndFehler() {
        assertTrue("SingleChoiceSegmentedButtonRow" in quelle("MemberHelpers.kt"))
        assertTrue("404, 409, 410 ->" in quelle("MemberTodos.kt"))
    }

    /** AND-25: „Mein Konto“ in drei Unterseiten, Einstellungen nur auf ihrer Unterseite. */
    @Test
    fun meinKontoInDreiUnterseiten() {
        assertEquals(listOf("KONTO", "EINSTELLUNGEN", "UEBER"), de.hubiplan.app.ui.AccountPage.entries.map { it.name })
        val src = quelle("AccountPane.kt")
        assertEquals(1, Regex("SettingsSection\\(vm\\)").findAll(src).count())
        assertTrue("AccountPage.EINSTELLUNGEN -> SettingsSection(vm)" in src)
        assertTrue("BackHead(current.title)" in src)
        assertTrue("rememberSaveable" in src)
    }

    /** AND-26/AND-29: Bezüge für TalkBack, gleiche Größe der Reiterbeschriftungen. */
    @Test
    fun kleinigkeiten() {
        assertTrue("Beleg {n} entfernen" in quelle("ExpensesSection.kt"))
        assertTrue("Meiste Zusagen" in quelle("SurveysSection.kt"))
        assertTrue("MenuCheck(a.id == current?.id)" in quelle("MainScreen.kt"))
        assertTrue("BarLabel(t.short, barScale)" in quelle("MainScreen.kt"))
        assertFalse("0x99888888" in quelle("NotenPult.kt"))
        assertEquals(1, Regex("Bitte Betreff und Text eingeben").findAll(quelle("SendPane.kt")).count())
    }
}
