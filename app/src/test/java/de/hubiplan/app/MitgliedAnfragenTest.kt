package de.hubiplan.app

import com.sun.net.httpserver.HttpServer
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.Belegung
import de.hubiplan.app.data.BookingWeek
import de.hubiplan.app.data.MyEntry
import de.hubiplan.app.data.OwnAbsences
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.Profile
import de.hubiplan.app.data.decodeApi
import de.hubiplan.app.data.json
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.bookingStatusLabel
import de.hubiplan.app.ui.bookingWhen
import de.hubiplan.app.ui.entryLine
import de.hubiplan.app.ui.entryMeta
import de.hubiplan.app.ui.ichBereiche
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.LinkedBlockingQueue

/** Belegung, Kündigung, Meldungen und Abwesenheiten wie im Mitgliederbereich; alle Daten erfunden. */
class MitgliedAnfragenTest {
    private data class Seen(val method: String, val path: String, val query: String?, val body: String, val key: String?)
    private val replies = LinkedBlockingQueue<Pair<Int, String>>()
    private val seen = LinkedBlockingQueue<Seen>()
    private lateinit var server: HttpServer
    private lateinit var api: Api
    private val berlin = ZoneId.of("Europe/Berlin")

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            seen.add(Seen(ex.requestMethod, ex.requestURI.path, ex.requestURI.query, ex.requestBody.bufferedReader().readText(), ex.requestHeaders.getFirst("Idempotency-Key")))
            val (status, body) = replies.poll() ?: (500 to "{}")
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        api = Api("http://127.0.0.1:${server.address.port}")
    }

    @After fun stop() { server.stop(0) }

    private fun field(body: String, name: String) = json.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.content

    @Test
    fun bereicheNurMitSchalter() {
        val none = PortalAccess(5, "Musterstadt", modules = listOf("billing"))
        assertFalse(IchBereich.BELEGUNG in ichBereiche(none))
        assertFalse(IchBereich.ERGEBNISSE in ichBereiche(none))
        assertFalse(IchBereich.ABWESENHEITEN in ichBereiche(none))
        val all = PortalAccess(5, "Musterstadt", modules = listOf("entries", "music"), bookingsEnabled = true, musician = true)
        val list = ichBereiche(all)
        assertTrue(IchBereich.BELEGUNG in list)
        // Meldungen allein reichen für „Wettbewerbe“
        assertTrue(IchBereich.ERGEBNISSE in list)
        assertTrue(IchBereich.ABWESENHEITEN in list)
        assertEquals("Wettbewerbe", IchBereich.ERGEBNISSE.label)
    }

    @Test
    fun anfrageZeiten() {
        val day = LocalDate.of(2026, 11, 20)
        // Ganztägig: erster und letzter Tag 0:00 deutscher Zeit
        assertEquals(
            "2026-11-20T00:00:00+01:00" to "2026-11-22T00:00:00+01:00",
            Belegung.requestTimes(day, day.plusDays(2), null, null, true),
        )
        assertEquals(
            "2026-11-20T18:00:00+01:00" to "2026-11-20T23:00:00+01:00",
            Belegung.requestTimes(day, null, LocalTime.of(18, 0), LocalTime.of(23, 0), false, berlin),
        )
        // Ende vor Beginn ohne Enddatum: am nächsten Tag
        assertEquals(
            "2026-11-20T20:00:00+01:00" to "2026-11-21T02:00:00+01:00",
            Belegung.requestTimes(day, null, LocalTime.of(20, 0), LocalTime.of(2, 0), false, berlin),
        )
        // Mit Enddatum vor dem Beginn: ungültig
        assertNull(Belegung.requestTimes(day, day.minusDays(1), LocalTime.of(20, 0), LocalTime.of(2, 0), false, berlin))
        assertNull(Belegung.requestTimes(day, day.minusDays(1), null, null, true))
        assertNull(Belegung.requestTimes(day, null, null, LocalTime.of(2, 0), false, berlin))
        assertEquals(LocalDate.of(2026, 11, 16), Belegung.monday(day))
        assertEquals(LocalDate.of(2026, 11, 16), Belegung.monday(LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun wocheLesenUndGanztaegigBisLetztenTag() {
        val week = decodeApi<BookingWeek>(
            """{"items":[{"id":3,"kind":"facility","title":"Schützenhaus","facility":null,"horizon_days":365}],
               "occurrences":[{"item":3,"title":"Belegt","starts_at":"2026-11-21T00:00:00+01:00","ends_at":"2026-11-22T00:00:00+01:00","all_day":true,"own":false}],
               "own":[{"id":8,"item":3,"item_title":"Schützenhaus","title":"Geburtstag","note":"","starts_at":"2026-11-27T18:00:00+01:00",
                       "ends_at":"2026-11-27T23:00:00+01:00","all_day":false,"status":"requested","version":1}]}""",
        )
        assertEquals(365, week.items.single().horizonDays)
        val busy = week.occurrences.single()
        assertEquals("Sa., 21. November 2026", bookingWhen(busy.startsAt, busy.endsAt, busy.allDay, berlin))
        val own = week.own.single()
        assertTrue(own.cancellable)
        assertFalse(own.copy(status = "rejected").cancellable)
        assertEquals("angefragt", bookingStatusLabel(own.status))
        assertEquals("Fr., 27. November 2026, 18:00 – 23:00 Uhr", bookingWhen(own.startsAt, own.endsAt, own.allDay, berlin))
    }

    @Test
    fun belegungPfadeUndVersion() = runBlocking {
        replies.add(200 to """{"items":[],"occurrences":[],"own":[]}""")
        api.bookings(5, "2026-11-16")
        var s = seen.take()
        assertEquals("/api/portal/5/bookings/", s.path)
        assertEquals("start=2026-11-16", s.query)
        replies.add(201 to """{"id":9,"item":3,"title":"Probe","starts_at":"2026-11-20T18:00:00+01:00","ends_at":"2026-11-20T20:00:00+01:00","status":"requested","version":1}""")
        val made = api.requestBooking(5, 3, "Probe", "", "2026-11-20T18:00:00+01:00", "2026-11-20T20:00:00+01:00", false)
        s = seen.take()
        assertEquals("POST", s.method)
        assertEquals("3", field(s.body, "item"))
        assertEquals("false", field(s.body, "all_day"))
        replies.add(200 to """{"id":9,"starts_at":"2026-11-20T18:00:00+01:00","ends_at":"2026-11-20T20:00:00+01:00","status":"cancelled","version":2}""")
        assertEquals("cancelled", api.cancelBooking(5, made).status)
        s = seen.take()
        assertEquals("/api/portal/5/bookings/9/cancel/", s.path)
        assertEquals("1", field(s.body, "version"))
    }

    @Test
    fun kuendigungImProfil() = runBlocking {
        val profile = decodeApi<Profile>(
            """{"first_name":"Anke","last_name":"Annahmer","cancellation":{"enabled":true,"possible":false,
               "earliest_date":"2027-12-31","rule":"mit einer Frist von 3 Monaten zum Ende des Geschäftsjahres","planned_exit":null,
               "requests":[{"id":4,"exit_date":"2027-12-31","status":"pending","status_label":"offen","created_at":"2026-11-15T10:00:00+01:00"},
                           {"id":2,"exit_date":"2026-12-31","status":"withdrawn","status_label":"zurückgenommen"}]}}""",
        )
        val info = profile.cancellation!!
        assertTrue(info.shown)
        assertEquals(4, info.pending?.id)
        // Ältere Server ohne Angaben: kein Abschnitt
        assertNull(decodeApi<Profile>("""{"first_name":"Anke"}""").cancellation)
        replies.add(201 to """{"id":5,"exit_date":"2027-12-31","status":"pending"}""")
        api.cancelMembership(5, "2027-12-31", "Umzug")
        var s = seen.take()
        assertEquals("/api/portal/5/cancellation/", s.path)
        assertEquals("true", field(s.body, "confirm"))
        assertEquals("2027-12-31", field(s.body, "exit_date"))
        replies.add(200 to """{"id":5,"status":"withdrawn"}""")
        assertEquals("withdrawn", api.withdrawCancellation(5, 5).status)
        s = seen.take()
        assertEquals("DELETE", s.method)
        assertEquals("/api/portal/5/cancellation/5/", s.path)
    }

    @Test
    fun meldungenInWorten() {
        val list = decodeApi<List<MyEntry>>(
            """[{"id":1,"title":"Kreisschießen","organizer":"Kreisverband Musterland","date":"2026-12-05","location":"Musterdorf",
                 "event":null,"group":"Mannschaft 1","position":"Schütze 2","substitute":true,"confirmed":true},
                {"id":2,"title":"Pokal","organizer":"","date":"2026-01-10","location":"","group":"Liste","position":"","substitute":false,"confirmed":false}]""",
        )
        val today = LocalDate.of(2026, 11, 15)
        assertFalse(list[0].past(today))
        assertTrue(list[1].past(today))
        assertEquals("Gemeldet in Mannschaft 1 als Schütze 2 (Ersatz), vom Veranstalter bestätigt", entryLine(list[0]))
        assertEquals("05.12.2026 · Musterdorf · Veranstalter: Kreisverband Musterland", entryMeta(list[0]))
        assertEquals("Gemeldet in Liste", entryLine(list[1]))
    }

    @Test
    fun abwesenheitMitSchluessel() = runBlocking {
        replies.add(200 to """{"enabled":true,"items":[{"id":7,"musician":3,"starts_at":"2026-12-01T00:00:00+01:00","ends_at":"2026-12-08T23:59:00+01:00"}]}""")
        val own = api.myAbsences(5)
        assertEquals(7, own.items.single().id)
        assertEquals("/api/portal/5/music/absences/", seen.take().path)
        assertFalse(decodeApi<OwnAbsences>("""{"enabled":false,"items":[]}""").enabled)
        replies.add(200 to """{"id":7,"starts_at":"2026-12-02T00:00:00+01:00","ends_at":"2026-12-08T23:59:00+01:00"}""")
        api.saveAbsence(5, 7, "2026-12-02T00:00:00+01:00", "2026-12-08T23:59:00+01:00", "6f1c2a8e-0b7d-4c5e-9a3f-2d4e6b8c0a12")
        var s = seen.take()
        assertEquals("save", field(s.body, "action"))
        assertEquals("7", field(s.body, "id"))
        assertEquals("6f1c2a8e-0b7d-4c5e-9a3f-2d4e6b8c0a12", s.key)
        replies.add(200 to """{"id":8,"starts_at":"2026-12-02T00:00:00+01:00","ends_at":"2026-12-03T00:00:00+01:00"}""")
        replies.add(200 to """{"ok":true}""")
        api.saveAbsence(5, null, "2026-12-02T00:00:00+01:00", "2026-12-03T00:00:00+01:00", "1a1c2a8e-0b7d-4c5e-9a3f-2d4e6b8c0a12")
        s = seen.take()
        assertNull(field(s.body, "id"))
        api.deleteAbsence(5, 7, "2b1c2a8e-0b7d-4c5e-9a3f-2d4e6b8c0a12")
        s = seen.take()
        assertEquals("delete", field(s.body, "action"))
        assertEquals("2b1c2a8e-0b7d-4c5e-9a3f-2d4e6b8c0a12", s.key)
    }
}
