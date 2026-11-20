package de.hubiplan.app

import de.hubiplan.app.data.EventDetail
import de.hubiplan.app.data.Occurrence
import de.hubiplan.app.data.VideoRoom
import de.hubiplan.app.data.json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Videoraum (Nextcloud Talk) am Termin; alle Daten erfunden. */
class VideoraumTest {
    @Test fun terminTraegtLinkUndPasswort() {
        val e = json.decodeFromString(
            EventDetail.serializer(),
            """
            {"id":7,"title":"Vorstandssitzung","starts_at":"2099-03-05T19:30:00+01:00","ends_at":"2099-03-05T21:30:00+01:00",
             "video":{"url":"https://cloud.example.org/call/abc123","password":"Xy-7kq+P2mZt9wRa","created_at":"2099-03-01T10:00:00+01:00"}}
            """.trimIndent(),
        )
        assertEquals("https://cloud.example.org/call/abc123", e.video?.url)
        assertEquals("Xy-7kq+P2mZt9wRa", e.video?.password)
        assertTrue(e.video!!.openable)
        val occ = json.decodeFromString(
            Occurrence.serializer(),
            """{"key":"7","event_id":7,"title":"Vorstandssitzung","starts_at":"2099-03-05T19:30:00+01:00","ends_at":"2099-03-05T21:30:00+01:00","video":{"url":"https://cloud.example.org/call/abc123","password":""}}""",
        )
        assertEquals("", occ.video?.password)
    }

    @Test fun ohneVideoraumOderAlterServer() {
        val e = json.decodeFromString(
            EventDetail.serializer(),
            """{"id":8,"title":"Sommerfest","starts_at":"2099-06-13T14:00:00+02:00","ends_at":"2099-06-13T18:00:00+02:00","video":null}""",
        )
        assertNull(e.video)
        val alt = json.decodeFromString(
            Occurrence.serializer(),
            """{"key":"8","event_id":8,"title":"Sommerfest","starts_at":"2099-06-13T14:00:00+02:00","ends_at":"2099-06-13T18:00:00+02:00"}""",
        )
        assertNull(alt.video)
    }

    @Test fun nurHttpsWirdGeoeffnet() {
        assertFalse(VideoRoom(url = "http://cloud.example.org/call/x").openable)
        assertFalse(VideoRoom(url = "javascript:alert(1)").openable)
        assertFalse(VideoRoom().openable)
    }
}
