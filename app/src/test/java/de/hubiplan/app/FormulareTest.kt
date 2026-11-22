package de.hubiplan.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gestaltung „Frisch mobil“, Etappe U6: Formulare schützen angefangene Eingaben, überall. */
class FormulareTest {
    private val ui = File("src/main/java/de/hubiplan/app/ui")

    @Test
    fun jedesFormularDerMitgliederFragtVorDemVerwerfen() {
        // Dateien mit Formularen im Mitgliederbereich: Zurück und Reiterwechsel fragen nach
        val formulare = listOf(
            "AnmeldungSection.kt", "BestellungenSection.kt", "ExpensesSection.kt", "FeeProofsSection.kt",
            "IchAnfragen.kt", "IchCompany.kt", "PinnwandSection.kt", "ProfilePane.kt", "SendPane.kt",
            "SurveysSection.kt", "TrainingSection.kt",
        )
        val ohne = formulare.filter { "rememberFormDraft(" !in File(ui, it).readText() }
        assertEquals("ohne Entwurfsschutz: $ohne", emptyList<String>(), ohne)
        // Belegung, Abwesenheit und Kündigung stehen in einer Datei: jedes hat seinen eigenen Schutz
        val anfragen = File(ui, "IchAnfragen.kt").readText()
        assertTrue(Regex("rememberFormDraft\\(").findAll(anfragen).count() >= 3)
    }

    @Test
    fun datumUndUhrzeitUeberDieAuswahlDesSystems() {
        // Jedes Datumsfeld hat den Kalenderknopf; das Feld selbst nimmt nur Ziffern
        val a11y = File(ui, "A11y.kt").readText()
        assertTrue("DatePickerDialog(" in a11y)
        assertTrue("TimePickerDialog(" in a11y)
        val felder = ui.listFiles { f -> f.extension == "kt" }.orEmpty().sumOf { f ->
            Regex("DatePickButton\\(").findAll(f.readText()).count()
        }
        assertTrue("Datumsfelder mit Kalenderknopf: $felder", felder >= 12)
    }
}
