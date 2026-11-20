package de.hubiplan.app

import de.hubiplan.app.data.IcsLeser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Termin aus einer geteilten Kalenderdatei (.ics) vorausfüllen; alle Daten erfunden. */
class TerminAusKalenderdateiTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test fun outlookEinladungMitWindowsZoneUndTeamsLink() {
        val ics = """
            BEGIN:VCALENDAR
            METHOD:REQUEST
            BEGIN:VTIMEZONE
            TZID:W. Europe Standard Time
            BEGIN:STANDARD
            DTSTART:16010101T030000
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            SUMMARY;LANGUAGE=de-DE:Vorstandssitzung
            DTSTART;TZID=W. Europe Standard Time:20990305T193000
            DTEND;TZID=W. Europe Standard Time:20990305T213000
            LOCATION:Vereinsheim\, Saal
            DESCRIPTION:Tagesordnung folgt.\nTeilnehmen: https://teams.microsoft.com/l/
             meetup-join/abc123
            BEGIN:VALARM
            TRIGGER:-PT15M
            SUMMARY:Erinnerung
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val t = IcsLeser.lesen(ics, berlin)!!
        assertEquals("Vorstandssitzung", t.title)
        assertEquals(LocalDate.of(2099, 3, 5), t.day)
        assertEquals(LocalTime.of(19, 30), t.start)
        assertEquals(LocalTime.of(21, 30), t.end)
        assertFalse(t.allDay)
        assertEquals("Vereinsheim, Saal", t.location)
        assertEquals("Tagesordnung folgt.\nTeilnehmen: https://teams.microsoft.com/l/meetup-join/abc123", t.description)
        assertEquals("https://teams.microsoft.com/l/meetup-join/abc123", t.videoUrl)
        assertFalse(t.repeats)
    }

    @Test fun utcZeitInDeutscheZeitUndConferenceVorrang() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Sitzung
            DTSTART:20990701T170000Z
            DURATION:PT90M
            CONFERENCE;VALUE=URI;FEATURE=VIDEO:https://cloud.example.org/call/k3x9
            DESCRIPTION:Alternativ https://meet.jit.si/Verein
            RRULE:FREQ=MONTHLY;COUNT=3
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val t = IcsLeser.lesen(ics, berlin)!!
        assertEquals(LocalTime.of(19, 0), t.start)
        assertEquals(LocalTime.of(20, 30), t.end)
        assertEquals("https://cloud.example.org/call/k3x9", t.videoUrl)
        assertTrue(t.repeats)
    }

    @Test fun ganztaegig() {
        val ics = "BEGIN:VEVENT\r\nSUMMARY:Arbeitseinsatz\r\nDTSTART;VALUE=DATE:20990307\r\nDTEND;VALUE=DATE:20990308\r\nEND:VEVENT\r\n"
        val t = IcsLeser.lesen(ics, berlin)!!
        assertTrue(t.allDay)
        assertEquals(LocalDate.of(2099, 3, 7), t.day)
        assertNull(t.start)
        assertNull(t.end)
        assertEquals("", t.videoUrl)
    }

    @Test fun mehrtaegigOhneEndzeit() {
        val ics = "BEGIN:VEVENT\nSUMMARY:Ausflug\nDTSTART;TZID=Europe/Berlin:20990310T180000\nDTEND;TZID=Europe/Berlin:20990311T120000\nEND:VEVENT"
        val t = IcsLeser.lesen(ics, berlin)!!
        assertEquals(LocalTime.of(18, 0), t.start)
        assertNull(t.end)
    }

    @Test fun absageUndUnbrauchbaresGebenNull() {
        assertNull(IcsLeser.lesen("BEGIN:VCALENDAR\nMETHOD:CANCEL\nBEGIN:VEVENT\nSUMMARY:X\nDTSTART:20990101T100000Z\nEND:VEVENT\nEND:VCALENDAR", berlin))
        assertNull(IcsLeser.lesen("BEGIN:VEVENT\nSUMMARY:X\nSTATUS:CANCELLED\nDTSTART:20990101T100000Z\nEND:VEVENT", berlin))
        assertNull(IcsLeser.lesen("BEGIN:VEVENT\nDTSTART:20990101T100000Z\nEND:VEVENT", berlin))
        assertNull(IcsLeser.lesen("BEGIN:VEVENT\nSUMMARY:Ohne Beginn\nEND:VEVENT", berlin))
        assertNull(IcsLeser.lesen("keine Kalenderdatei", berlin))
    }

    @Test fun videolinkImOrt() {
        assertEquals("https://zoom.us/j/123456", IcsLeser.videoLink("Online (https://zoom.us/j/123456)."))
        assertNull(IcsLeser.videoLink("https://www.example.org/anfahrt"))
    }
}
