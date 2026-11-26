package de.hubiplan.app

import com.sun.net.httpserver.HttpServer
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.Sender
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue

/** Kurzmitteilung senden nur über den Pfad der Verwaltung (Vorstandsschlüssel). */
class SendenVorstandTest {
    private data class Request(val method: String, val path: String, val authorization: String, val device: String)
    private val requests = LinkedBlockingQueue<Request>()
    private lateinit var server: HttpServer
    private lateinit var address: String

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(
                Request(
                    exchange.requestMethod,
                    exchange.requestURI.path,
                    exchange.requestHeaders.getFirst("Authorization").orEmpty(),
                    exchange.requestHeaders.getFirst("X-Hubiplan-Device").orEmpty(),
                ),
            )
            val bytes = """{"counts":{"app":1,"mail":2,"total":3}}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        address = "http://127.0.0.1:${server.address.port}"
    }

    @After fun stop() { server.stop(0) }

    @Test fun sendenLaeuftUeberDieVerwaltungMitBeidenSchluesseln() = runBlocking {
        val api = Api(address, "hpa_erfunden", "hpd_erfunden", null)
        val result = api.sendShort(Sender("org-7", "Musterverein", "org", 7), "Antreten", "Heute 18 Uhr", confirm = false)
        assertEquals(3, result.counts["total"])
        val request = requests.take()
        assertEquals("POST", request.method)
        assertEquals("/api/app/v1/admin/send/org/7/", request.path)
        assertEquals("Bearer hpa_erfunden", request.authorization)
        assertEquals("hpd_erfunden", request.device)
    }

    @Test fun listeDerEinheitenBleibtBeimGeraeteschluessel() = runBlocking {
        Api(address, "hpd_erfunden").senders()
        val request = requests.take()
        assertEquals("/api/app/v1/send/", request.path)
        assertTrue(request.authorization.startsWith("Bearer hpd_"))
    }
}
