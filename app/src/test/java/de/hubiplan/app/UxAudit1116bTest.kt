package de.hubiplan.app

import de.hubiplan.app.ui.Tab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** UX-Audit 16.11.2026, Android mittel: AND-07, 08, 11, 13, 14, 15, 16. */
class UxAudit1116bTest {
    private val katalog = Texte.parse(File("src/main/assets/texte/en.json").readText())
    private val ui = File("src/main/java/de/hubiplan/app/ui")
    private fun quelle(name: String) = File(ui, name).readText()

    @After
    fun deutsch() = Texte.setzeFuerTest("de", katalog)

    /** AND-11: Reiter folgen einem Sprachwechsel ohne Neustart. */
    @Test
    fun reiterWechselnDieSprache() {
        Texte.setzeFuerTest("de", katalog)
        assertEquals("Termine", Tab.TERMINE.label)
        Texte.setzeFuerTest("en", katalog)
        assertEquals(Texte.text("Termine"), Tab.TERMINE.label)
        assertTrue(Tab.TERMINE.label != "Termine")
        assertEquals(Texte.text("Neues"), Tab.MITTEILUNGEN.short)
    }

    /** AND-14: Auswahlknöpfe als ganze Zeile mit 48 dp. */
    @Test
    fun auswahlzeilenStattEinzelknopf() {
        assertTrue("fun LabeledRadio(" in quelle("A11y.kt"))
        listOf("VersammlungLive.kt", "SettingsSection.kt", "SurveysSection.kt").forEach {
            assertTrue("$it nutzt LabeledRadio", "LabeledRadio(" in quelle(it))
        }
    }

    /** AND-08: Rückmeldungen nach Aktionen kommen als Meldung am unteren Rand. */
    @Test
    fun meldungenUnten() {
        assertTrue("SnackbarHost(" in quelle("MainScreen.kt"))
        listOf("ProfilePane.kt", "FeeProofsSection.kt", "ExpensesSection.kt").forEach {
            assertTrue("$it nutzt ActionNotice", "ActionNotice(" in quelle(it))
        }
    }

    /** AND-16: abgemeldetes Konto wird gemeldet; AND-15: ungelesene Mitteilungen sind markiert. */
    @Test
    fun abmeldungUndUngelesen() {
        val main = quelle("MainScreen.kt")
        assertTrue("SignedOutDialog" in main)
        assertTrue("BadgedBox" in main)
        assertTrue("\"● neu\"" in quelle("CircularsPane.kt"))
    }

    /** AND-07: offline bleibt der gespeicherte Stand sichtbar und lädt bei Verbindung neu. */
    @Test
    fun offlineStand() {
        val main = quelle("MainScreen.kt")
        assertTrue("OfflinePane(" in main && "ReloadWhenOnline(" in main)
        val konto = File("src/main/java/de/hubiplan/app/data/LocalAccountData.kt").readText()
        assertTrue("Offline-Kopie wird mit dem Konto gelöscht", "OfflineStore.FOLDER" in konto && "ReadStore.FOLDER" in konto)
    }
}
