package de.hubiplan.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UX-Audit 16.11.2026, X-01 und AND-18: ein Wort je Sache, wie in `hubiplan/docs/begriffe.md`.
 * Der Test meldet Wörter aus der Spalte „Nicht verwenden“, sobald sie in Texten der App stehen.
 */
class BegriffeTest {
    private val katalog = Texte.parse(File("src/main/assets/texte/en.json").readText())

    /** Wort oder Muster, erlaubte Texte (Bestand, darf nur sinken), stattdessen. */
    private val verboten = listOf(
        Triple(Regex("Telefon(?!nummer)"), 2, "„Gerät“ (nur das Feld „Telefon“ für die Rufnummer bleibt)"),
        Triple(Regex("Wappen"), 0, "„Logo“"),
        Triple(Regex("abgleichen|abgeglichen"), 0, "„Aktualisieren“ und „Stand: …“"),
        Triple(Regex("vom Gerät abmelden|Verbindung trennen"), 0, "„Auf diesem Gerät abmelden“"),
        Triple(Regex("[Dd]ieses Schreiben"), 0, "„Mitteilung“"),
        Triple(Regex("Mitgliedskarte"), 0, "„Ausweis“"),
        Triple(Regex("To-do"), 0, "„Aufgabe“"),
    )

    @Test
    fun verboteneNebenwoerterFehlen() {
        verboten.forEach { (muster, erlaubt, stattdessen) ->
            val treffer = katalog.keys.filter { muster.containsMatchIn(it) }
            assertTrue("$muster: ${treffer.size} Texte, erlaubt $erlaubt. Stattdessen $stattdessen: ${treffer.lastOrNull()}", treffer.size <= erlaubt)
        }
    }

    @Test
    fun englischFolgtDemGlossar() {
        Texte.setzeFuerTest("en", katalog)
        try {
            assertEquals("Sign out on this device", Texte.text("Auf diesem Gerät abmelden"))
            assertEquals("Refresh", Texte.text("Aktualisieren"))
            assertEquals("Open this message with attachments in the browser", Texte.text("Diese Mitteilung mit Anhängen im Browser öffnen"))
            katalog.forEach { (de, en) ->
                if ("Gerät" in de) assertTrue("„$de“: device statt phone", !Regex("\\bphones?\\b", RegexOption.IGNORE_CASE).containsMatchIn(en))
            }
        } finally {
            Texte.setzeFuerTest("de", katalog)
        }
    }

    /** AND-27: ganztägige Termine stehen in jeder Gerätezeitzone am selben Kalendertag. */
    @Test
    fun ganztaegigOhneZeitzone() {
        val fest = { zone: String ->
            de.hubiplan.app.ui.formatWhen("2027-07-11T00:00:00+02:00", "2027-07-14T00:00:00+02:00", true, java.time.ZoneId.of(zone))
        }
        assertEquals("So., 11.7. – Mi., 14. Juli 2027", fest("Europe/Berlin"))
        assertEquals(fest("Europe/Berlin"), fest("UTC"))
        assertEquals(fest("Europe/Berlin"), fest("America/Los_Angeles"))
        // auch wenn der Server in UTC liefert
        assertEquals(
            fest("Europe/Berlin"),
            de.hubiplan.app.ui.formatWhen("2027-07-10T22:00:00Z", "2027-07-13T22:00:00Z", true, java.time.ZoneId.of("UTC")),
        )
    }

    /** AND-27: Uhrzeiten bleiben Gerätezeit, mit Zusatz, sobald das Gerät woanders steht. */
    @Test
    fun uhrzeitMitZusatzInAndererZeitzone() {
        val abend = { zone: String ->
            de.hubiplan.app.ui.formatWhen("2026-11-13T19:00:00+01:00", "2026-11-13T22:00:00+01:00", false, java.time.ZoneId.of(zone))
        }
        assertTrue("Zeit an deinem Gerät" !in abend("Europe/Berlin"))
        assertTrue("Europe/Vienna hat denselben Abstand", "Zeit an deinem Gerät" !in abend("Europe/Vienna"))
        assertTrue(abend("UTC"), abend("UTC").endsWith("18:00 – 21:00 Uhr (Zeit an deinem Gerät)"))
    }
}
