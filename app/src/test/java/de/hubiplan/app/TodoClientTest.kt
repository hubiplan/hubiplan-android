package de.hubiplan.app

import com.sun.net.httpserver.HttpServer
import de.hubiplan.app.data.AdminClient
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.json
import de.hubiplan.app.data.obj
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue

/** Lokale HTTP-Gegenstelle prüft echte Pfade, Versionsdaten und scoped Rücknahme. */
class TodoClientTest {
    private data class Reply(val body: String, val status: Int = 200, val headers: Map<String, String> = emptyMap())
    private data class Request(val method: String, val path: String, val body: String)
    private val replies = LinkedBlockingQueue<Reply>()
    private val requests = LinkedBlockingQueue<Request>()
    private lateinit var server: HttpServer
    private lateinit var api: Api

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(Request(exchange.requestMethod, exchange.requestURI.path, exchange.requestBody.bufferedReader().readText()))
            val reply = replies.poll() ?: Reply("{}", 500)
            reply.headers.forEach { (key, value) -> exchange.responseHeaders.add(key, value) }
            val bytes = reply.body.toByteArray()
            exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        api = Api("http://127.0.0.1:${server.address.port}")
    }

    @After fun stop() { server.stop(0) }

    @Test fun abteilungLiestUndLegtPrivatAn() = runBlocking {
        val client = AdminClient(api, 9, scope = "d")
        replies.add(Reply("""{"items":[{"id":12,"title":"Probe"}]}"""))
        assertEquals(12, client.todos().items.single().id)
        assertEquals("/api/app/v1/admin/d/9/todos/", requests.take().path)
        replies.add(Reply("""{"id":13,"title":"Neu"}""", 201))
        assertEquals(13, client.createTodo(obj("title" to "Neu")).value.id)
        val request = requests.take()
        assertEquals("POST", request.method)
        assertEquals("/api/app/v1/admin/d/9/todos/", request.path)
        assertEquals("Neu", json.parseToJsonElement(request.body).jsonObject["title"]?.jsonPrimitive?.content)
    }

    @Test fun erledigenSendetVersionUndRuecknahmeBleibtAnAufgabeGebunden() = runBlocking {
        val client = AdminClient(api, 7)
        val change = "c713095d-ea9b-455a-9d63-8c0b4b3957c2"
        replies.add(Reply("""{"id":12,"status":"done","version":4}""", headers = mapOf("X-Change-Set" to change, "X-Change-Undoable" to "1", "X-Change-Task" to "12")))
        assertEquals(change, client.completeTodo(12, 3, true).changeSet)
        val request = requests.take()
        assertEquals("PATCH", request.method)
        val body = json.parseToJsonElement(request.body).jsonObject
        assertEquals("3", body["version"]?.jsonPrimitive?.content)
        assertEquals("done", body["status"]?.jsonPrimitive?.content)
        replies.add(Reply("{}"))
        client.undo(change)
        assertEquals("/api/app/v1/admin/u/7/todos/12/changes/$change/undo/", requests.take().path)
    }

    @Test fun entzugUndKonfliktWerdenNichtAlsErfolgBehandelt() = runBlocking {
        val client = AdminClient(api, 7)
        for (status in listOf(403, 404, 409)) {
            replies.add(Reply("""{"detail":"Nicht verfügbar"}""", status))
            try {
                client.completeTodo(12, 3, false)
                fail("Fehler erwartet: $status")
            } catch (e: ApiException) { assertEquals(status, e.status) }
            requests.take()
        }
    }
}
