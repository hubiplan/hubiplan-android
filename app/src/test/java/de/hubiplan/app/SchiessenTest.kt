package de.hubiplan.app

import de.hubiplan.app.data.AdminCompetition
import de.hubiplan.app.data.AdminUnit
import de.hubiplan.app.data.RankingDetail
import de.hubiplan.app.data.RankingPrize
import de.hubiplan.app.data.RankingRow
import de.hubiplan.app.data.StandAccess
import de.hubiplan.app.data.StandLink
import de.hubiplan.app.data.StandStore
import de.hubiplan.app.data.json
import de.hubiplan.app.data.openCompetitions
import de.hubiplan.app.data.sameInstance
import de.hubiplan.app.data.standUrl
import de.hubiplan.app.ui.Section
import de.hubiplan.app.ui.prizeLine
import de.hubiplan.app.ui.rankingRowLine
import de.hubiplan.app.ui.sectionsFor
import de.hubiplan.app.ui.showsCompetitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/** Schießen in der App (hubiplan konzepte/schiessen-android.md); alle Daten erfunden. */
class SchiessenTest {
    private val token = "AbCdEfGhIjKlMnOpQrStUvWxYz012345"

    @Test
    fun standLinkLesen() {
        val link = StandLink.parse("https://verein.example.org/stand/zugang/$token")
        assertEquals("https://verein.example.org", link?.server)
        assertEquals(token, link?.token)
        assertEquals("https://verein.example.org/stand/zugang/$token", link?.url)
        assertEquals(link, StandLink.parse(" https://verein.example.org/stand/zugang/$token/ "))
    }

    @Test
    fun fremdeOderFalscheCodesNicht() {
        assertNull(StandLink.parse("http://verein.example.org/stand/zugang/$token"))
        assertNull(StandLink.parse("https://verein.example.org/app/verbinden#code=abc"))
        assertNull(StandLink.parse("https://verein.example.org/stand/zugang/kurz"))
        assertNull(StandLink.parse("https://verein.example.org/stand/zugang/$token?x=1"))
        assertNull(StandLink.parse("https://verein.example.org/stand/zugang/../mein-konto"))
        assertNull(StandLink.parse("HUBIPLAN-AUSWEIS:123"))
    }

    @Test
    fun nurEigeneInstanz() {
        val server = "https://verein.example.org"
        assertTrue(sameInstance(server, "https://verein.example.org/stand/zugang/$token"))
        assertTrue(sameInstance(server, "https://VEREIN.example.org:443/api/public/stand/x/"))
        assertFalse(sameInstance(server, "https://example.net/"))
        assertFalse(sameInstance(server, "http://verein.example.org/"))
        assertFalse(sameInstance(server, "https://verein.example.org:8443/"))
        assertFalse(sameInstance(server, "javascript:alert(1)"))
        assertEquals("$server/stand/zugang/$token", standUrl("$server/", token))
    }

    @Test
    fun bereichNurMitModulUndRecht() {
        val schiessmeister = AdminUnit(1, "Verein", permissions = listOf("competitions.results"), stufen = true, modules = listOf("competitions"))
        assertTrue(showsCompetitions(schiessmeister))
        assertTrue(Section.COMPETITIONS in sectionsFor(schiessmeister))
        assertTrue(showsCompetitions(schiessmeister.copy(permissions = listOf("results.edit"))))
        assertFalse(showsCompetitions(schiessmeister.copy(permissions = listOf("competitions.view"))))
        // Älterer Server ohne Modulliste: kein Bereich (er kennt die Pfade nicht)
        assertFalse(showsCompetitions(schiessmeister.copy(modules = emptyList())))
        assertFalse(showsCompetitions(schiessmeister.copy(scope = "d")))
    }

    @Test
    fun offeneWettbewerbeHeuteZuerst() {
        val list = listOf(
            AdminCompetition(1, "Alt", "2027-05-01", "laeuft"),
            AdminCompetition(2, "Später", "2027-07-01", "vorbereitung"),
            AdminCompetition(3, "Heute", "2027-06-14", "vorbereitung"),
            AdminCompetition(4, "Fertig", "2027-06-14", "endgueltig"),
            AdminCompetition(5, "Fremd", "2027-06-14", "vorbereitung", own = false),
        )
        assertEquals(listOf(3, 2, 1), openCompetitions(list, "2027-06-14").map { it.id })
    }

    @Test
    fun antwortenLesen() {
        val created = json.decodeFromString<StandAccess>(
            """{"id":7,"label":"App (Pixel 8)","expires_at":"2027-06-14T23:59:59+02:00","active":true,"created_at":"2027-06-14T10:00:00+02:00","token":"$token"}""",
        )
        assertEquals(token, created.token)
        val now = OffsetDateTime.parse("2027-06-14T20:00:00+02:00")
        assertTrue(StandStore.valid(created, now))
        assertFalse(StandStore.valid(created, now.plusDays(1)))
        assertFalse(StandStore.valid(created.copy(expiresAt = "kaputt"), now))
        val comp = json.decodeFromString<AdminCompetition>(
            """{"id":3,"title":"Preisschießen 2027","date":"2027-06-14","status":"laeuft","form":"rangliste","own":true,"starter_count":12,"can_manage":false,"can_stand_access":true,"scorings":[]}""",
        )
        assertEquals(true, comp.canStandAccess)
        val detail = json.decodeFromString<RankingDetail>(
            """{"id":"abc","title":"Preisschießen 2027","date":"2027-06-14","live":true,"final":false,"depth":"top3",
               "scorings":[{"name":"Einzel","kind":"einzel","field":12,"complete":false,
                 "rows":[{"place":1,"value":"30","prize":true,"name":"Max M.","unit":"Zug 1"}]}],
               "prizes":[],"titles":[],"version":"x1","poll_seconds":15}""",
        )
        assertEquals(15, detail.pollSeconds)
        assertEquals("1. Max M. (Zug 1) · 30", rankingRowLine(detail.scorings[0].rows[0]))
    }

    @Test
    fun zeilen() {
        assertEquals("ohne Namen · 20", rankingRowLine(RankingRow(name = "", value = "20")))
        assertEquals("2. Gerd G. (Nachbarverein) · 27", rankingRowLine(RankingRow(2, "Gerd G.", "27", club = "Nachbarverein")))
        assertEquals("Kopf: noch dran", prizeLine(RankingPrize("Kopf")))
        assertEquals("Kopf: Max M. (Zug 1), 37. Schuss", prizeLine(RankingPrize("Kopf", true, "Max M.", "Zug 1", 37)))
    }
}
