package de.hubiplan.app

import de.hubiplan.app.data.EventDetail
import de.hubiplan.app.data.json
import de.hubiplan.app.push.PushMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rückmeldung zum Termin, Anfahrt und Anhänge (HUBIplan T3); alle Daten erfunden. */
class TerminRueckmeldungTest {
    @Test fun terminMitVielleichtBemerkungAnfahrtUndAnhang() {
        val e = json.decodeFromString(
            EventDetail.serializer(),
            """
            {"id":5,"title":"Sommerfest","starts_at":"2099-06-13T14:00:00+02:00","ends_at":"2099-06-13T18:00:00+02:00",
             "location":"Festplatz","address":"Wiesenweg 1","directions":"Parken am Sportplatz.",
             "latitude":51.17123,"longitude":6.75456,
             "map_links":{"osm":"https://www.openstreetmap.org/directions?route=%3B51.17123%2C6.75456"},
             "attachments":[{"id":3,"name":"Plan.pdf","size":2048,"content_type":"application/pdf","url":"/x/"}],
             "rsvp":{"enabled":true,"maybe_allowed":true,"note_allowed":true,"note_question":"Essenswunsch?",
               "can_respond":true,"mine":{"status":"maybe","guests":0,"note":"Vegetarisch","source":"link"}}}
            """.trimIndent(),
        )
        assertTrue(e.rsvp.maybeAllowed)
        assertEquals("Essenswunsch?", e.rsvp.noteQuestion)
        assertEquals("maybe", e.rsvp.mine?.status)
        assertEquals("link", e.rsvp.mine?.source)
        assertEquals(51.17123, e.latitude!!, 0.000001)
        assertNull(e.mapLinks.google)
        assertEquals("Plan.pdf", e.attachments.single().name)
    }

    @Test fun verknuepftesDokumentAusDerAblage() {
        val e = json.decodeFromString(
            EventDetail.serializer(),
            """
            {"id":5,"title":"Sommerfest","starts_at":"2099-06-13T14:00:00+02:00","ends_at":"2099-06-13T18:00:00+02:00",
             "attachments":[{"id":4,"kind":"document","name":"Ablaufplan","file_name":"Ablauf-2099.pdf","size":1024,
               "content_type":"application/pdf","document":7,"available":true,"url":"/x/"}]}
            """.trimIndent(),
        )
        val file = e.attachments.single()
        assertEquals("Ablaufplan", file.name)
        assertEquals("Ablauf-2099.pdf", file.fileName)
    }

    @Test fun aeltererServerOhneNeueFelder() {
        val e = json.decodeFromString(
            EventDetail.serializer(),
            """{"id":5,"title":"Probe","starts_at":"2099-06-13T14:00:00+02:00","ends_at":"2099-06-13T18:00:00+02:00"}""",
        )
        assertFalse(e.rsvp.maybeAllowed)
        assertTrue(e.attachments.isEmpty())
        assertEquals("", e.directions)
    }

    @Test fun pushZuTerminUndRundschreiben() {
        val invite = PushMessage.parse("""{"v":1,"t":"event","access":4,"id":5,"kind":"invite","title":"Einladung: Sommerfest","rsvp":true,"maybe":true}""")!!
        assertEquals("event", invite.type)
        assertTrue(invite.rsvp && invite.maybe)
        val circular = PushMessage.parse("""{"v":1,"t":"circular","access":4,"id":9,"event":5,"rsvp":true}""")!!
        assertEquals(5, circular.event)
        assertFalse(circular.maybe)
        val plain = PushMessage.parse("""{"v":1,"t":"circular","access":4,"id":9}""")!!
        assertNull(plain.event)
        assertFalse(plain.rsvp)
    }
}
