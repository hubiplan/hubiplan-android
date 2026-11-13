package de.hubiplan.app

import de.hubiplan.app.data.MeetingLink
import de.hubiplan.app.data.MeetingLive
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.decodeApi
import de.hubiplan.app.ui.meetingTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Versammlungen V7: QR-Code der Leitung und Stand im Mitgliederbereich. Erfundene Daten. */
class VersammlungLiveTest {
    private val code = "59123456-0123456789abcdef"
    private val url = "https://verein.example.de/mein-bereich/versammlungen/da/12?v=7&c=$code"

    @Test
    fun qrCodeDerLeitung() {
        assertEquals(MeetingLink("https://verein.example.de", 12, 7, code), MeetingLink.parse(url))
    }

    @Test
    fun fremdeCodesAblehnen() {
        assertNull(MeetingLink.parse(url.replace("https:", "http:")))
        assertNull(MeetingLink.parse("https://verein.example.de/mein-bereich/versammlungen/da/12?v=7"))
        assertNull(MeetingLink.parse("https://verein.example.de/mein-bereich/versammlungen/da/x?v=7&c=$code"))
        assertNull(MeetingLink.parse("https://verein.example.de/stand/zugang/abc?v=7&c=$code"))
        assertNull(MeetingLink.parse("https://verein.example.de/mein-bereich/versammlungen/da/12?v=7&c=falsch"))
        assertNull(MeetingLink.parse("WIFI:S:netz;T:WPA;P:geheim;;"))
    }

    @Test
    fun nurEigeneInstanzUndEigenerVerein() {
        val access = PortalAccess(5, "Schützenverein Musterstadt", clubId = 7)
        val (ok, _) = meetingTarget(url, "https://verein.example.de", access)
        assertEquals(12, ok?.meetingId)
        assertEquals(code, ok?.code)
        assertNull(meetingTarget(url, "https://andere.example.de", access).first)
        assertNotNull(meetingTarget(url, "https://andere.example.de", access).second)
        assertNull(meetingTarget(url, "https://verein.example.de", access.copy(clubId = 8)).first)
        // Ältere Serverstände ohne club_id: nur die Instanz zählt
        assertEquals(12, meetingTarget(url, "https://verein.example.de", access.copy(clubId = 0)).first?.meetingId)
    }

    @Test
    fun standMitVollmachtLesen() {
        val state = decodeApi<MeetingLive>(
            """{"id":12,"title":"Jahreshauptversammlung","checkin_open":true,"remote":true,"voter":true,
            "is_organization":false,"status":"","mode":"","votes":0,"polls":[{"id":4,"agenda_position":7,
            "question":"Entlastung","secret":true,"status":"open","options":[{"id":1,"role":"yes","label":"Ja"}],
            "result":null,"votes":[{"attendance":3,"name":"Erika Beispiel","how":"own","done":false},
            {"attendance":4,"name":"Max Muster","how":"proxy","done":true}],"neu":"egal"}]}""",
        )
        assertTrue(state.canJoinRemote)
        assertFalse(state.present)
        assertEquals(listOf(3), state.polls.single().openVotes.map { it.attendance })
        assertTrue(state.polls.single().running)
    }
}
