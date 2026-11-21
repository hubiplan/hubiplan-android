package de.hubiplan.app

import de.hubiplan.app.data.MemberTraining
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.TrainingCode
import de.hubiplan.app.data.TrainingDetail
import de.hubiplan.app.data.TrainingLink
import de.hubiplan.app.data.evaluationLines
import de.hubiplan.app.data.json
import de.hubiplan.app.data.shootingDetails
import de.hubiplan.app.data.trainingTime
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.ichBereiche
import de.hubiplan.app.ui.trainingScan
import de.hubiplan.app.ui.trainingSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Training und Schießbuch unter „Ich“ (HUBIplan docs/training.md); alle Daten erfunden. */
class TrainingTest {
    private val text = """
        {"attendance":[{"session":5,"date":"2099-11-18","title":"Übungsabend Luftgewehr","kind":"practice",
           "kind_label":"Übungsabend","department_name":"","source":"code"}],
         "count_year":14,"count_12":16,
         "checkin":[{"id":7,"department":null,"department_name":"","event":null,"date":"2099-11-20",
           "starts_at":"19:00","ends_at":"21:00","hours":2.0,"title":"Training Sportpistole","kind":"training",
           "kind_label":"Training","location":"","leader":"","note":"","shooting":true,"discipline":"Sportpistole",
           "weapon_class":"short","caliber":".22 lr","permit_required":true,"checkin_open":true,"count":3,
           "version":1,"present":false}],
         "shooting_enabled":true,"targets":[],
         "shooting":{"entries":[{"id":31,"membership":4,"date":"2099-11-15","discipline":"Sportpistole",
             "weapon_class":"short","weapon_class_label":"Kurzwaffe","caliber":".22 lr","permit_required":true,
             "own_weapon":true,"competition":false,"place":"Schießstand Beispielhausen","external":true,"note":"",
             "status":"reported","status_label":"gemeldet","source":"member","source_label":"vom Mitglied gemeldet",
             "from_training":false,"confirmed_by":"","confirmed_at":null}],
           "evaluation":{"on":"2099-11-20","start_12":"2098-11-21","start_24":"2097-11-21","member_since":"2090-03-01",
             "acquisition":{"ok":true,"member_12_months":true,"by_months":false,"by_count":true,"count":22,"needed":18,
               "months":[],"longest_gap_days":140,"gap_ok":false,"external":2,"internal":20,"mostly_own_club":true},
             "possession":[{"weapon_class":"short","label":"Kurzwaffe","count":22,"ok":true,"by_quarters":true,
               "by_six":true,"quarters":[],"years":[]}],"pending":1},
           "disciplines":[{"name":"Sportpistole","weapon_class":"short","caliber":".22 lr","permit_required":true}]}}
    """.trimIndent()

    @Test
    fun antwortLesen() {
        val t = json.decodeFromString<MemberTraining>(text)
        assertEquals(14, t.countYear)
        assertEquals(1, t.attendance.size)
        val log = t.shooting!!
        assertTrue(log.entries.single().canWithdraw)
        assertEquals("Kurzwaffe · .22 lr · Schießstand Beispielhausen · anderer Verein", shootingDetails(log.entries.single()))
        assertEquals("Einchecken ist offen", trainingSummary(t))
        assertEquals("Dieses Jahr 14-mal da", trainingSummary(t.copy(checkin = emptyList())))
        val lines = evaluationLines(log.evaluation)
        assertEquals("Erwerb (Abs. 3): letzte 12 Monate: erfüllt", lines.first())
        assertTrue(lines.any { it.startsWith("Längere Pause: 140 Tage") })
        assertTrue(lines.any { it == "Besitz (Abs. 4), Kurzwaffe: letzte 24 Monate: erfüllt" })
        assertEquals("1 gemeldete Termine warten auf die Bestätigung des Vereins.", lines.last())
    }

    @Test
    fun qrCodeLesenUndBauen() {
        val link = TrainingLink.parse("https://verein.example.org/mein-bereich/training/da/7?v=3&c=59841234-0123456789abcdef")
        assertNotNull(link)
        assertEquals(7, link!!.sessionId)
        assertEquals(3, link.clubId)
        assertEquals("https://verein.example.org", link.server)
        // Versammlungscode, fremdes Schema oder kaputter Code: kein Einchecken
        assertNull(TrainingLink.parse("https://verein.example.org/mein-bereich/versammlungen/da/7?v=3&c=59841234-0123456789abcdef"))
        assertNull(TrainingLink.parse("http://verein.example.org/mein-bereich/training/da/7?v=3&c=59841234-0123456789abcdef"))
        assertNull(TrainingLink.parse("https://verein.example.org/mein-bereich/training/da/7?v=3&c=abc"))
        val built = TrainingLink.build("https://verein.example.org/", TrainingCode("59841234-0123456789abcdef", 12, 7, 3))
        assertEquals("https://verein.example.org/mein-bereich/training/da/7?v=3&c=59841234-0123456789abcdef", built)
        assertEquals(link, TrainingLink.parse(built))
    }

    @Test
    fun scanPruefen() {
        val access = PortalAccess(9, "Schützenverein Musterstadt", clubId = 3)
        val ok = "https://verein.example.org/mein-bereich/training/da/7?v=3&c=59841234-0123456789abcdef"
        assertNotNull(trainingScan(ok, "https://verein.example.org", access).first)
        assertEquals(
            "Der QR-Code gehört zu einer anderen HUBIplan-Adresse.",
            trainingScan(ok, "https://andere.example.org", access).second,
        )
        assertNull(trainingScan(ok, "https://verein.example.org", access.copy(clubId = 4)).first)
        assertEquals("Das ist kein QR-Code zum Einchecken ins Training.", trainingScan("Hallo", "https://verein.example.org", access).second)
    }

    @Test
    fun nurMitModul() {
        val on = PortalAccess(5, "Schützenverein Musterstadt", trainingEnabled = true)
        assertTrue(IchBereich.TRAINING in ichBereiche(on))
        assertFalse(IchBereich.TRAINING in ichBereiche(on.copy(trainingEnabled = false)))
        // Ältere HUBIplan-Stände ohne Training: kein Eintrag
        assertFalse(IchBereich.TRAINING in ichBereiche(PortalAccess(5, "Musterstadt")))
    }

    @Test
    fun uebungsleitung() {
        val d = json.decodeFromString<TrainingDetail>(
            """{"id":7,"date":"2099-11-20","title":"Training","checkin_open":true,"checkin_possible":true,
               "attendance":[{"membership":1,"name":"Erika Beispiel","present":true,"source":"code","shooting":true},
                             {"membership":2,"name":"Max Muster","present":false,"source":null,"shooting":false}],
               "version":2}""",
        )
        assertEquals(1, d.presentCount)
        assertEquals("19:00–21:00", trainingTime("19:00", "21:00"))
        assertEquals("19:00", trainingTime("19:00", null))
        assertEquals("", trainingTime(null, "21:00"))
    }
}
