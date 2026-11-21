package de.hubiplan.app

import com.sun.net.httpserver.HttpServer
import de.hubiplan.app.data.AdminClient
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.LeadLive
import de.hubiplan.app.data.MeetingLink
import de.hubiplan.app.data.acceptApplication
import de.hubiplan.app.data.application
import de.hubiplan.app.data.applications
import de.hubiplan.app.data.approveApplication
import de.hubiplan.app.data.attendance
import de.hubiplan.app.data.bookingRequests
import de.hubiplan.app.data.checkinLink
import de.hubiplan.app.data.createPoll
import de.hubiplan.app.data.decideBooking
import de.hubiplan.app.data.json
import de.hubiplan.app.data.meetings
import de.hubiplan.app.data.pollAction
import de.hubiplan.app.data.removeAttendance
import de.hubiplan.app.data.setAttendance
import de.hubiplan.app.data.setCheckin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue

/** Vorstandsaufgaben: Pfade, Inhalte und Antworten wie die HUBIplan-Schnittstelle. Erfundene Daten. */
class AdminVorstandTest {
    private data class Reply(val body: String, val status: Int = 200)
    private data class Request(val method: String, val path: String, val query: String?, val body: String)
    private val replies = LinkedBlockingQueue<Reply>()
    private val requests = LinkedBlockingQueue<Request>()
    private lateinit var server: HttpServer
    private lateinit var client: AdminClient

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(Request(exchange.requestMethod, exchange.requestURI.path, exchange.requestURI.query, exchange.requestBody.bufferedReader().readText()))
            val reply = replies.poll() ?: Reply("{}", 500)
            val bytes = reply.body.toByteArray()
            exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        client = AdminClient(Api("http://127.0.0.1:${server.address.port}"), 7)
    }

    @After fun stop() { server.stop(0) }

    private fun body(r: Request): JsonObject = json.parseToJsonElement(r.body).jsonObject

    @Test fun beitrittsantraegeLesenUndEntscheiden() = runBlocking {
        replies.add(Reply("""{"results":[{"id":3,"status":"open","name":"Muster, Erika","approvals_met":false,"missing":["Vorstand"]}],"may_approve":true,"counts":{"open":1}}"""))
        val list = client.applications()
        assertEquals("Muster, Erika", list.results.single().name)
        assertTrue(list.results.single().isOpen)
        var r = requests.take()
        assertEquals("/api/app/v1/admin/u/7/admission/", r.path)
        assertEquals("status=offen", r.query)

        replies.add(Reply("""{"id":3,"status":"open","approvals_met":true,"may_decide":true,"may_approve":true,"own_decision":null,"summary":["Name: Erika Muster"],"entry_date":"2026-12-01"}"""))
        val d = client.application(3)
        assertTrue(d.canAccept && d.canApprove)
        requests.take()

        replies.add(Reply("""{"id":3,"status":"open","own_decision":"approve","may_approve":true}"""))
        assertFalse(client.approveApplication(3, true, " passt ").canApprove)
        r = requests.take()
        assertEquals("/api/app/v1/admin/u/7/admission/3/approve/", r.path)
        assertEquals("approve", body(r)["decision"]?.jsonPrimitive?.content)
        assertEquals("passt", body(r)["note"]?.jsonPrimitive?.content)

        replies.add(Reply("""{"id":3,"status":"accepted"}"""))
        assertEquals("accepted", client.acceptApplication(3, "2026-12-01", "", true).status)
        r = requests.take()
        assertEquals("/api/app/v1/admin/u/7/admission/3/accept/", r.path)
        assertEquals("2026-12-01", body(r)["entry_date"]?.jsonPrimitive?.content)
        assertEquals("true", body(r)["welcome"]?.jsonPrimitive?.content)
    }

    @Test fun ablehnungEinesAndernVerhindertAufnahme() {
        val d = json.decodeFromString<de.hubiplan.app.data.ApplicationDetail>(
            """{"id":1,"status":"open","disputed":true,"approvals_met":true,"may_decide":true}""",
        )
        assertFalse(d.canAccept)
    }

    @Test fun belegungFreigebenMitVersion() = runBlocking {
        replies.add(Reply("""{"occurrences":[],"requests":[{"id":11,"item_title":"Saal","title":"Geburtstag","version":2,"member_name":"Anna Beispiel","starts_at":"2026-12-05T18:00:00+01:00","ends_at":"2026-12-05T23:00:00+01:00"}]}"""))
        val list = client.bookingRequests("2026-11-21")
        assertEquals("Anna Beispiel", list.single().requester)
        var r = requests.take()
        assertEquals("/api/app/v1/admin/u/7/bookings/", r.path)
        assertEquals("start=2026-11-21", r.query)
        replies.add(Reply("""{"id":11,"status":"confirmed","version":3}"""))
        assertEquals("confirmed", client.decideBooking(11, 2, "confirm").status)
        r = requests.take()
        assertEquals("/api/app/v1/admin/u/7/bookings/11/decision/", r.path)
        assertEquals("2", body(r)["version"]?.jsonPrimitive?.content)
        replies.add(Reply("""{"detail":"Die Buchung wurde inzwischen geändert."}""", 409))
        try {
            client.decideBooking(11, 2, "reject")
            fail("409 erwartet")
        } catch (e: ApiException) {
            assertEquals(409, e.status)
        }
    }

    @Test fun versammlungLeiten() = runBlocking {
        val dept = AdminClient(Api("http://127.0.0.1:${server.address.port}"), 4, scope = "d")
        replies.add(Reply("""[{"id":5,"title":"Jahreshauptversammlung","status":"invited","agenda":[{"id":9,"type":"resolution","title":"Entlastung"}]}]"""))
        val m = dept.meetings().single()
        assertTrue(m.isLive && m.agenda.single().votable)
        assertEquals("/api/app/v1/admin/d/4/meetings/", requests.take().path)

        replies.add(Reply("""{"voters":10,"present":1,"editable":true,"rows":[{"id":1,"membership":20,"name":"Anna Beispiel","voter":true,"status":"present"}]}"""))
        assertTrue(client.setAttendance(5, 20, null, "present").rows.single().present)
        var r = requests.take()
        assertEquals("/api/app/v1/admin/u/7/meetings/5/attendance/", r.path)
        assertEquals("20", body(r)["membership"]?.jsonPrimitive?.content)
        replies.add(Reply("""{"rows":[]}"""))
        client.removeAttendance(5, 1)
        r = requests.take()
        assertEquals("DELETE", r.method)
        assertEquals("/api/app/v1/admin/u/7/meetings/5/attendance/1/", r.path)

        replies.add(Reply("""{"meeting":5,"organization":7,"checkin_open":true,"live":true,"code":"123-0123456789abcdef","refresh_in":12,"polls":[]}"""))
        val live = client.setCheckin(5, true)
        assertEquals("true", body(requests.take())["open"]?.jsonPrimitive?.content)
        val link = checkinLink("https://verein.example.org/", live)
        assertEquals("https://verein.example.org/mein-bereich/versammlungen/da/5?v=7&c=123-0123456789abcdef", link)
        // Die Mitglieder-App versteht genau diesen Link
        val parsed = MeetingLink.parse(link!!)!!
        assertEquals(5, parsed.meetingId)
        assertEquals(7, parsed.clubId)
        assertNull(checkinLink("https://verein.example.org", LeadLive(meeting = 5, organization = 7, checkinOpen = false, code = "x")))

        replies.add(Reply("""{"meeting":5,"polls":[{"id":2,"status":"draft"}]}""", 201))
        client.createPoll(5, 9, "Entlastung?", secret = true, office = "", candidates = listOf(" ", "Anna"))
        r = requests.take()
        assertEquals("/api/app/v1/admin/u/7/meetings/5/polls/", r.path)
        assertEquals("9", body(r)["agenda_item"]?.jsonPrimitive?.content)
        assertEquals(1, body(r)["candidates"]?.jsonArray?.size)
        replies.add(Reply("""{"meeting":5,"polls":[{"id":2,"status":"open"}]}"""))
        assertEquals("open", client.pollAction(5, 2, "open").polls.single().status)
        assertEquals("/api/app/v1/admin/u/7/meetings/5/polls/2/", requests.take().path)

        replies.add(Reply("""{"rows":[]}"""))
        client.attendance(5)
        assertEquals("/api/app/v1/admin/u/7/meetings/5/attendance/", requests.take().path)
    }
}
