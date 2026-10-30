package de.hubiplan.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Fehler mit Meldung für das Mitglied. [status] 401 mit Geräteschlüssel: neu verbinden. */
class ApiException(val status: Int, message: String) : Exception(message)

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

private val JSON_TYPE = "application/json".toMediaType()

private val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(false)
    .build()

/** Ergebnis der Passwort-Anmeldung: fertig oder zweiter Faktor nötig. */
sealed interface LoginStep {
    data class Done(val sessionToken: String) : LoginStep
    data class NeedsCode(val sessionToken: String) : LoginStep
}

/**
 * Zugriff auf eine HUBIplan-Instanz. [token] ist der Geräteschlüssel (hpd_…),
 * fehlt er, sind nur die öffentlichen Aufrufe möglich.
 */
class Api(val server: String, private val token: String? = null) {

    private suspend fun call(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(server + path)
            .header("Accept", "application/json")
        token?.let { builder.header("Authorization", "Bearer $it") }
        headers.forEach { (k, v) -> builder.header(k, v) }
        val requestBody = body?.toRequestBody(JSON_TYPE)
        builder.method(method, requestBody ?: if (method == "GET" || method == "DELETE") null else "".toRequestBody(JSON_TYPE))
        try {
            http.newCall(builder.build()).execute().use { resp ->
                resp.code to (resp.body?.string() ?: "")
            }
        } catch (e: IOException) {
            throw ApiException(0, "Keine Verbindung zum Server. Bitte die Internetverbindung prüfen.")
        }
    }

    private suspend fun expect(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val (status, text) = call(method, path, body, headers)
        if (status in 200..299) return text
        throw ApiException(status, errorMessage(status, text))
    }

    suspend fun instance(): Instance = json.decodeFromString(expect("GET", "/api/app/v1/instance/"))

    suspend fun pair(code: String = "", loginLink: String = "", deviceName: String, appVersion: String): Paired =
        json.decodeFromString(
            expect(
                "POST",
                "/api/app/v1/pair/",
                json.encodeToString(
                    JsonObject.serializer(),
                    jsonObjectOf(
                        "code" to code,
                        "login_link" to loginLink,
                        "name" to deviceName,
                        "platform" to "android",
                        "app_version" to appVersion,
                    ),
                ),
            ),
        )

    /** Anmeldung mit Passwort über allauth (Client „app“). */
    suspend fun login(email: String, password: String): LoginStep {
        val (status, text) = call(
            "POST",
            "/_allauth/app/v1/auth/login",
            json.encodeToString(JsonObject.serializer(), jsonObjectOf("email" to email, "password" to password)),
        )
        return authStep(status, text, null)
    }

    suspend fun secondFactor(sessionToken: String, code: String): LoginStep {
        val (status, text) = call(
            "POST",
            "/_allauth/app/v1/auth/2fa/authenticate",
            json.encodeToString(JsonObject.serializer(), jsonObjectOf("code" to code)),
            mapOf("X-Session-Token" to sessionToken),
        )
        return authStep(status, text, sessionToken)
    }

    private fun authStep(status: Int, text: String, previous: String?): LoginStep {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw ApiException(status, errorMessage(status, text))
        }
        val session = root["meta"]?.jsonObject?.get("session_token")?.jsonPrimitive?.contentOrNull ?: previous
        if (status == 200 && session != null) return LoginStep.Done(session)
        val pendingMfa = root["data"]?.jsonObject?.get("flows")?.jsonArray?.any {
            val flow = it.jsonObject
            flow["id"]?.jsonPrimitive?.contentOrNull == "mfa_authenticate" &&
                flow["is_pending"]?.jsonPrimitive?.booleanOrNull == true
        } ?: false
        if (status == 401 && pendingMfa && session != null) return LoginStep.NeedsCode(session)
        throw ApiException(status, runCatching { allauthMessage(root) }.getOrNull() ?: "Anmeldung fehlgeschlagen. Bitte E-Mail und Passwort prüfen.")
    }

    suspend fun createDevice(sessionToken: String, deviceName: String, appVersion: String): Paired =
        json.decodeFromString(
            expect(
                "POST",
                "/api/app/v1/devices/",
                json.encodeToString(
                    JsonObject.serializer(),
                    jsonObjectOf("name" to deviceName, "platform" to "android", "app_version" to appVersion),
                ),
                mapOf("X-Session-Token" to sessionToken),
            ),
        )

    suspend fun me(): Me = json.decodeFromString(expect("GET", "/api/app/v1/me/"))

    suspend fun logout(deviceId: Int) {
        expect("DELETE", "/api/app/v1/devices/$deviceId/")
    }

    suspend fun events(accessId: Int, from: String, to: String): List<Occurrence> =
        json.decodeFromString<EventList>(expect("GET", "/api/portal/$accessId/events/?from=$from&to=$to")).items

    suspend fun event(accessId: Int, eventId: Int): EventDetail =
        json.decodeFromString(expect("GET", "/api/portal/$accessId/events/$eventId/"))

    suspend fun respond(accessId: Int, eventId: Int, status: String, guests: Int = 0, note: String = ""): EventDetail =
        json.decodeFromString(
            expect(
                "PUT",
                "/api/portal/$accessId/events/$eventId/response/",
                """{"status":${quote(status)},"guests":$guests,"note":${quote(note)}}""",
            ),
        )

    suspend fun withdraw(accessId: Int, eventId: Int): EventDetail =
        json.decodeFromString(expect("DELETE", "/api/portal/$accessId/events/$eventId/response/"))

    /** Push-Endpunkt dieses Geräts melden (UnifiedPush). */
    suspend fun registerPush(endpoint: String, p256dh: String, auth: String, kind: String = "unifiedpush") {
        expect(
            "PUT",
            "/api/app/v1/devices/current/push/",
            json.encodeToString(
                JsonObject.serializer(),
                jsonObjectOf("kind" to kind, "endpoint" to endpoint, "p256dh" to p256dh, "auth" to auth),
            ),
        )
    }

    // --- Verwaltung (Schritt A8) ---

    suspend fun adminOverview(): AdminOverview = json.decodeFromString(expect("GET", "/api/app/v1/admin/"))

    /** Mit Passwort und Zwei-Faktor-Code freischalten; liefert den Vorstandsschlüssel. */
    suspend fun unlockAdmin(password: String, code: String): AdminUnlock = json.decodeFromString(
        expect("POST", "/api/app/v1/admin/unlock/", """{"password":${quote(password)},"code":${quote(code)}}"""),
    )

    suspend fun lockAdmin() {
        expect("DELETE", "/api/app/v1/admin/unlock/")
    }

    /** Mit dem Vorstandsschlüssel als [token]. */
    suspend fun adminTasks(unitId: Int): AdminTasks =
        json.decodeFromString(expect("GET", "/api/app/v1/admin/u/$unitId/tasks/"))

    suspend fun unregisterPush() {
        expect("DELETE", "/api/app/v1/devices/current/push/")
    }

    suspend fun senders(): Senders = json.decodeFromString(expect("GET", "/api/app/v1/send/"))

    /** Kurzmitteilung: [confirm] false zählt nur die Empfänger, true sendet. */
    suspend fun sendShort(sender: Sender, subject: String, body: String, confirm: Boolean): SendResult =
        json.decodeFromString(
            expect(
                "POST",
                "/api/app/v1/send/${sender.type}/${sender.id}/",
                """{"subject":${quote(subject)},"body":${quote(body)},"confirm":$confirm}""",
            ),
        )

    suspend fun profile(accessId: Int): Profile = json.decodeFromString(expect("GET", "/api/portal/$accessId/"))

    /** Änderung melden; Felder wie im Browser (email, phone, street, postal_code, city, …). */
    suspend fun reportChange(accessId: Int, values: Map<String, String>, message: String): Boolean {
        val body = JsonObject((values + ("message" to message)).mapValues { JsonPrimitive(it.value) })
        val text = expect("POST", "/api/portal/$accessId/change-requests/", json.encodeToString(JsonObject.serializer(), body))
        // true: wartet auf den Vorstand · false: sofort übernommen
        return json.parseToJsonElement(text).jsonObject["request"].let { it != null && it !is kotlinx.serialization.json.JsonNull }
    }

    suspend fun photo(accessId: Int): PhotoState = json.decodeFromString(expect("GET", "/api/portal/$accessId/photo/"))

    /** Foto hochladen (JPEG, auf dem Gerät verkleinert und ohne EXIF) mit Einwilligung. */
    suspend fun uploadPhoto(accessId: Int, jpeg: ByteArray): PhotoState = withContext(Dispatchers.IO) {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("consent", "true")
            .addFormDataPart("file", "foto.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val request = Request.Builder().url("$server/api/portal/$accessId/photo/")
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .post(form)
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (resp.code !in 200..299) throw ApiException(resp.code, errorMessage(resp.code, text))
                json.decodeFromString(PhotoState.serializer(), text)
            }
        } catch (e: IOException) {
            throw ApiException(0, "Keine Verbindung zum Server. Bitte die Internetverbindung prüfen.")
        }
    }

    suspend fun deletePhoto(accessId: Int): PhotoState = json.decodeFromString(expect("DELETE", "/api/portal/$accessId/photo/"))

    /** Bild mit Geräteschlüssel laden (Pfad aus der Schnittstelle). */
    suspend fun image(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(server + path)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        try {
            http.newCall(request).execute().use { resp -> if (resp.isSuccessful) resp.body?.bytes() else null }
        } catch (e: IOException) {
            null
        }
    }

    suspend fun circulars(accessId: Int): List<CircularItem> =
        json.decodeFromString(expect("GET", "/api/portal/$accessId/circulars/"))

    suspend fun circular(accessId: Int, id: Int): CircularItem =
        json.decodeFromString(expect("GET", "/api/portal/$accessId/circulars/$id/"))
}

private fun quote(value: String): String =
    json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))

private fun jsonObjectOf(vararg pairs: Pair<String, String>): JsonObject =
    JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

private fun allauthMessage(root: JsonObject): String? =
    root["errors"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull

/** Meldung aus einer Fehlerantwort von HUBIplan (detail oder Feldfehler). */
fun errorMessage(status: Int, text: String): String {
    val parsed = try {
        json.parseToJsonElement(text)
    } catch (e: Exception) {
        null
    }
    if (parsed is JsonObject) {
        (parsed["detail"] as? JsonPrimitive)?.contentOrNull?.let { return it }
        runCatching { allauthMessage(parsed) }.getOrNull()?.let { return it }
        val fields = parsed.values.mapNotNull { v ->
            when (v) {
                is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> v.contentOrNull
                else -> null
            }
        }
        if (fields.isNotEmpty()) return fields.joinToString(" ")
    }
    return when (status) {
        401 -> "Dieses Gerät ist nicht mehr angemeldet. Bitte die App neu verbinden."
        403 -> "Dafür fehlt die Berechtigung."
        404 -> "Nicht gefunden."
        429 -> "Zu viele Versuche. Bitte später erneut."
        in 500..599 -> "Der Server hat gerade ein Problem. Bitte später erneut versuchen."
        else -> "Unerwartete Antwort vom Server ($status)."
    }
}
