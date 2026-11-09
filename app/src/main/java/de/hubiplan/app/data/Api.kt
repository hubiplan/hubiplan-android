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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import de.hubiplan.app.tr

/**
 * Fehler mit Meldung für das Mitglied. [status] 401 mit Geräteschlüssel: neu verbinden.
 * [origin]: Konto der abgelehnten Anfrage (siehe [apiOrigin]), damit eine verspätete Ablehnung
 * nicht ein inzwischen gewähltes anderes Konto entfernt (Audit 02.11., WA-V01).
 */
class ApiException(
    val status: Int,
    message: String,
    val body: String = "",
    val origin: String? = null,
) : Exception(message) {
    val fieldErrors: Map<String, String> = if (status == 400 || status == 422) parseFieldErrors(body) else emptyMap()
    val generalError: String? = parseGeneralError(body)
}

/** Kennung von Server und Schlüssel einer Anfrage, ohne den Schlüssel selbst. */
fun apiOrigin(server: String, token: String?): String = "$server#${token?.hashCode()}"

/** Antwort der Verwaltung mit Änderungspaket (für „Rückgängig“). */
data class AdminResponse(val text: String, val changeSet: String?, val undoable: Boolean, val label: String?, val task: Int? = null)

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Größte JSON-Antwort, die die App liest (Audit 02.11., WA-S01). */
const val MAX_JSON_BYTES = 8L * 1024 * 1024

/** Größtes Bild oder PDF, das die App lädt (Belege, Quittungen, Fotos, Wappen). */
const val MAX_FILE_BYTES = 25L * 1024 * 1024

private val PROTOCOL_ERROR: String
    get() = tr("Die Antwort des Servers war unverständlich. Bitte später erneut versuchen.")

/** Antwort war 2xx, passt aber nicht zum erwarteten Aufbau (Audit 02.11., WA-F01). */
fun protocolError(): ApiException = ApiException(502, PROTOCOL_ERROR)

/**
 * JSON einer Serverantwort lesen. Kaputtes JSON, falsche Feldtypen oder fehlende Pflichtfelder
 * werden zu [ApiException], die die Oberflächen schon als Fehler anzeigen, statt die App zu
 * beenden (Audit 02.11., WA-F01). Der Antworttext kommt nicht in die Meldung.
 */
inline fun <reified T> decodeApi(text: String): T = try {
    json.decodeFromString<T>(text)
} catch (e: IllegalArgumentException) {
    // SerializationException ist eine IllegalArgumentException
    throw protocolError()
}

fun <T> decodeApi(serializer: kotlinx.serialization.KSerializer<T>, text: String): T = try {
    json.decodeFromString(serializer, text)
} catch (e: IllegalArgumentException) {
    throw protocolError()
}

/** Antwortkörper lesen, höchstens [limit] Bytes (auch bei chunked oder gzip, Audit 02.11., WA-S01). */
internal fun readLimited(body: okhttp3.ResponseBody?, limit: Long): ByteArray {
    body ?: return ByteArray(0)
    if (body.contentLength() > limit) throw tooLarge()
    val source = body.source()
    val buffer = okio.Buffer()
    while (buffer.size <= limit) {
        if (source.read(buffer, 64L * 1024) == -1L) return buffer.readByteArray()
    }
    throw tooLarge()
}

private fun tooLarge() = ApiException(502, tr("Die Antwort des Servers ist zu groß."))

private val JSON_TYPE = "application/json".toMediaType()

/** Geräteschlüssel neben dem Vorstandsschlüssel (Härtung H3). */
const val DEVICE_HEADER = "X-Hubiplan-Device"

private const val TOKEN_PREFIX = "hpd_"
// Unterschrieben wird jede Anfrage mit dem Vorstandsschlüssel (hpa_…), egal unter welchem Pfad:
// eine einzige Regel statt einer Pfadliste, die von der des Servers abweichen kann (AND-F02)
private const val ADMIN_TOKEN_PREFIX = "hpa_"
// Server, die Unterschriftsformat 2 angekündigt haben (Kopfzeile in jeder App-Antwort)
private val SIGNATURE_V2 = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
private val KEY_PATHS = setOf("/api/app/v1/devices/current/renew/", "/api/app/v1/devices/current/key/")
private val OFFLINE: String get() = tr("Keine Verbindung zum Server. Bitte die Internetverbindung prüfen.")

/** `code` aus einer Fehlerantwort (z. B. `device_renew`, `admin_signature`). */
fun errorCode(text: String): String? = try {
    (json.parseToJsonElement(text) as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull
} catch (e: Exception) {
    null
}

private val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    // Gesamtzeit je Anfrage, auch wenn der Server tröpfchenweise sendet (WA-S01)
    .callTimeout(2, TimeUnit.MINUTES)
    .followRedirects(false)
    .build()

/** Ergebnis der Passwort-Anmeldung: fertig oder zweiter Faktor nötig. */
sealed interface LoginStep {
    data class Done(val sessionToken: String) : LoginStep
    data class NeedsCode(val sessionToken: String) : LoginStep
}

/**
 * Zugriff auf eine HUBIplan-Instanz. [token] ist der Geräteschlüssel (hpd_…),
 * fehlt er, sind nur die öffentlichen Aufrufe möglich. Für die Verwaltung ist [token] der
 * Vorstandsschlüssel (hpa_…) und [deviceToken] der Geräteschlüssel desselben Geräts: der Server
 * nimmt den Vorstandsschlüssel nur zusammen mit ihm an (Kopf `X-Hubiplan-Device`).
 */
class Api(
    val server: String,
    private val token: String? = null,
    private val deviceToken: String? = null,
    /** Härtung H7: Vorstands-Schlüsselpaar; gesetzt, wenn Vorstandsanfragen unterschrieben werden. */
    private val adminKeyAlias: String? = null,
    /** Austauschbarer Transport für reproduzierbare Geräteprüfungen, Standard unverändert. */
    private val transport: OkHttpClient = http,
) {

    private class Raw(val code: Int, val body: ByteArray, val headers: okhttp3.Headers) {
        val text: String get() = String(body)
    }

    /** Geräteschlüssel, mit dem dieses Objekt angemeldet ist (direkt oder neben dem Vorstandsschlüssel). */
    private fun deviceKeyToken(): String? =
        (if (token?.startsWith(TOKEN_PREFIX) == true) token else deviceToken)?.let(DeviceTokens::current)

    private fun request(
        method: String,
        path: String,
        body: ByteArray?,
        contentType: okhttp3.MediaType?,
        headers: Map<String, String>,
        accept: Boolean,
    ): Request {
        // Gespeicherte Adresse mit ungültigem Port (ältere Version): Fehler melden statt Absturz (Audit F04)
        val url = (server + path).toHttpUrlOrNull()
            ?: throw ApiException(0, tr("Bitte die Adresse von HUBIplan angeben, z. B. verein.example.de."))
        val builder = Request.Builder().url(url)
        if (accept) builder.header("Accept", "application/json")
        token?.let { builder.header("Authorization", "Bearer ${if (it.startsWith(TOKEN_PREFIX)) DeviceTokens.current(it) else it}") }
        deviceToken?.let { builder.header(DEVICE_HEADER, DeviceTokens.current(it)) }
        if (adminKeyAlias != null && token?.startsWith(ADMIN_TOKEN_PREFIX) == true) {
            val signedPath = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
            val time = (System.currentTimeMillis() / 1000).toString()
            val nonce = if (server in SIGNATURE_V2) SignedFormat.newNonce() else ""
            val idempotency = headers[SignedFormat.IDEMPOTENCY_HEADER].orEmpty()
            val message = SignedFormat.adminMessage(method, signedPath, time, body ?: ByteArray(0), nonce, idempotency)
            val signature = try {
                DeviceKeys.sign(adminKeyAlias, message)
            } catch (e: DeviceKeys.Unavailable) {
                // Wie „Verwaltung gesperrt“: die App schaltet neu frei
                throw ApiException(401, tr("Bitte die Verwaltung neu freischalten."))
            }
            builder.header(SignedFormat.TIME_HEADER, time).header(SignedFormat.SIGNATURE_HEADER, signature)
            if (nonce.isNotEmpty()) builder.header(SignedFormat.NONCE_HEADER, nonce)
        }
        headers.forEach { (k, v) -> builder.header(k, v) }
        val requestBody = body?.toRequestBody(contentType)
        builder.method(method, requestBody ?: if (method == "GET" || method == "DELETE") null else ByteArray(0).toRequestBody(JSON_TYPE))
        return builder.build()
    }

    /**
     * Eine Anfrage; erneuert bei `401 device_renew` den Geräteschlüssel und wiederholt einmal.
     * Ohne Erneuerung nie 401 nach oben geben (das hieße „Gerät abgemeldet“).
     */
    private suspend fun send(
        method: String,
        path: String,
        body: okhttp3.RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
        accept: Boolean = true,
        retry: Boolean = true,
        limit: Long = MAX_JSON_BYTES,
    ): Raw = withContext(Dispatchers.IO) {
        val bytes = body?.let { b -> okio.Buffer().also { b.writeTo(it) }.readByteArray() }
        val used = deviceKeyToken()
        val raw = try {
            transport.newCall(request(method, path, bytes, body?.contentType(), headers, accept)).execute().use { resp ->
                Raw(resp.code, readLimited(resp.body, limit), resp.headers)
            }
        } catch (e: IOException) {
            throw ApiException(0, OFFLINE)
        }
        if (raw.headers[SignedFormat.FORMAT_HEADER] == "2") SIGNATURE_V2.add(server)
        // Erneuern und Nachmelden selbst nie erneut erneuern (das wäre eine Schleife)
        if (raw.code == 401 && errorCode(raw.text) == "device_renew" && used != null && path !in KEY_PATHS) {
            if (!retry || DeviceTokens.renew(server, used) == null) {
                throw ApiException(0, tr("Der Geräteschlüssel ließ sich gerade nicht erneuern. Bitte später erneut versuchen."))
            }
            return@withContext send(method, path, body?.let { bytes!!.toRequestBody(it.contentType()) }, headers, accept, retry = false, limit = limit)
        }
        raw
    }

    private suspend fun call(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val raw = send(method, path, body?.toRequestBody(JSON_TYPE), headers)
        return raw.code to raw.text
    }

    private suspend fun expect(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val (status, text) = call(method, path, body, headers)
        if (status in 200..299) return text
        throw ApiException(status, errorMessage(status, text), body = text, origin = apiOrigin(server, token))
    }

    suspend fun instance(): Instance = decodeApi(expect("GET", "/api/app/v1/instance/"))

    suspend fun pair(code: String = "", loginLink: String = "", deviceName: String, appVersion: String, deviceKey: String = ""): Paired =
        decodeApi(
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
                        "device_key" to deviceKey,
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

    private fun authStep(status: Int, text: String, previous: String?): LoginStep = parseAuthStep(status, text, previous)

    suspend fun createDevice(sessionToken: String, deviceName: String, appVersion: String, deviceKey: String = ""): Paired =
        decodeApi(
            expect(
                "POST",
                "/api/app/v1/devices/",
                json.encodeToString(
                    JsonObject.serializer(),
                    jsonObjectOf("name" to deviceName, "platform" to "android", "app_version" to appVersion, "device_key" to deviceKey),
                ),
                mapOf("X-Session-Token" to sessionToken),
            ),
        )

    suspend fun me(): Me = decodeApi(expect("GET", "/api/app/v1/me/"))

    /** Sprache des Kontos (gilt auch im Browser) und wählbare Sprachen. */
    suspend fun language(): LanguageState = decodeApi(expect("GET", "/api/app/v1/me/language/"))

    suspend fun setLanguage(code: String): LanguageState = decodeApi(
        expect("PATCH", "/api/app/v1/me/language/", json.encodeToString(JsonObject.serializer(), jsonObjectOf("language" to code))),
    )

    // --- Geräteschlüssel erneuern (Härtung H7) ---

    suspend fun renewChallenge(): String =
        decodeApi<RenewChallenge>(expect("POST", "/api/app/v1/devices/current/renew/")).challenge

    suspend fun renew(challenge: String, signature: String): Renewed = decodeApi(
        expect(
            "POST",
            "/api/app/v1/devices/current/renew/",
            """{"challenge":${quote(challenge)},"signature":${quote(signature)}}""",
        ),
    )

    /** Schlüsselpaar einer älteren Verbindung nachmelden. */
    suspend fun registerDeviceKey(publicKey: String): DeviceInfo = decodeApi(
        expect("PUT", "/api/app/v1/devices/current/key/", """{"device_key":${quote(publicKey)}}"""),
    )

    suspend fun logout(deviceId: Int) {
        expect("DELETE", "/api/app/v1/devices/$deviceId/")
    }

    suspend fun memberTodos(accessId: Int): MemberTodoList =
        decodeApi(expect("GET", "/api/portal/$accessId/todos/"))

    suspend fun memberTodo(accessId: Int, id: Int): MemberTodo =
        decodeApi(expect("GET", "/api/portal/$accessId/todos/$id/"))

    suspend fun completeMemberTodo(accessId: Int, id: Int, version: Int, done: Boolean): MemberTodo =
        decodeApi(expect("PATCH", "/api/portal/$accessId/todos/$id/",
            json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj("version" to version, "status" to if (done) "done" else "open"))))

    suspend fun claimMemberTodo(accessId: Int, id: Int, version: Int, claim: Boolean): MemberTodo =
        decodeApi(expect("PATCH", "/api/portal/$accessId/todos/$id/",
            json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj("version" to version, "action" to if (claim) "claim" else "release"))))

    suspend fun events(accessId: Int, from: String, to: String): List<Occurrence> =
        decodeApi<EventList>(expect("GET", "/api/portal/$accessId/events/?from=$from&to=$to")).items

    suspend fun event(accessId: Int, eventId: Int): EventDetail =
        decodeApi(expect("GET", "/api/portal/$accessId/events/$eventId/"))

    suspend fun respond(accessId: Int, eventId: Int, status: String, guests: Int = 0, note: String = ""): EventDetail =
        decodeApi(
            expect(
                "PUT",
                "/api/portal/$accessId/events/$eventId/response/",
                """{"status":${quote(status)},"guests":$guests,"note":${quote(note)}}""",
            ),
        )

    suspend fun withdraw(accessId: Int, eventId: Int): EventDetail =
        decodeApi(expect("DELETE", "/api/portal/$accessId/events/$eventId/response/"))

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

    suspend fun adminOverview(): AdminOverview = decodeApi(expect("GET", "/api/app/v1/admin/"))

    /** Mit Passwort und Zwei-Faktor-Code freischalten; liefert den Vorstandsschlüssel. */
    suspend fun unlockAdmin(password: String, code: String, adminKey: String = ""): AdminUnlock = decodeApi(
        expect(
            "POST",
            "/api/app/v1/admin/unlock/",
            """{"password":${quote(password)},"code":${quote(code)},"admin_key":${quote(adminKey)}}""",
        ),
    )

    suspend fun lockAdmin() {
        expect("DELETE", "/api/app/v1/admin/unlock/")
    }

    /** Mit dem Vorstandsschlüssel als [token]. */
    suspend fun adminTasks(unitId: Int): AdminTasks =
        decodeApi(expect("GET", "/api/app/v1/admin/u/$unitId/tasks/"))

    /**
     * Anfrage der Verwaltung (Schritt B2) unter `/api/app/v1/admin/u/<Einheit>/`. Liefert den
     * Text und das Änderungspaket für „Rückgängig“ (Kopfzeile `X-Change-Set`).
     */
    suspend fun admin(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): AdminResponse = adminSend(
        method,
        path,
        body?.toRequestBody(JSON_TYPE) ?: if (method == "GET" || method == "DELETE") null else "".toRequestBody(JSON_TYPE),
        headers,
    )

    /** Formular mit Datei (Schritt B4: Beleg als JPEG), sonst wie [admin]. */
    suspend fun adminForm(
        path: String,
        fields: Map<String, String>,
        fileName: String,
        jpeg: ByteArray,
        headers: Map<String, String> = emptyMap(),
    ): AdminResponse {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (k, v) -> addFormDataPart(k, v) }
            addFormDataPart("file", fileName, jpeg.toRequestBody("image/jpeg".toMediaType()))
        }.build()
        return adminSend("POST", path, form, headers)
    }

    private suspend fun adminSend(
        method: String,
        path: String,
        body: okhttp3.RequestBody?,
        headers: Map<String, String>,
    ): AdminResponse {
        val resp = send(method, path, body, headers)
        val text = resp.text
        if (resp.code !in 200..299) throw ApiException(resp.code, errorMessage(resp.code, text), text, apiOrigin(server, token))
        return AdminResponse(
            text,
            resp.headers["X-Change-Set"],
            resp.headers["X-Change-Undoable"] == "1",
            resp.headers["X-Change-Label"]?.let { java.net.URLDecoder.decode(it, "UTF-8") },
            resp.headers["X-Change-Task"]?.toIntOrNull()?.takeIf { it > 0 },
        )
    }

    suspend fun unregisterPush() {
        expect("DELETE", "/api/app/v1/devices/current/push/")
    }

    suspend fun senders(): Senders = decodeApi(expect("GET", "/api/app/v1/send/"))

    /** Kurzmitteilung: [confirm] false zählt nur die Empfänger, true sendet. */
    suspend fun sendShort(sender: Sender, subject: String, body: String, confirm: Boolean): SendResult =
        decodeApi(
            expect(
                "POST",
                "/api/app/v1/send/${sender.type}/${sender.id}/",
                """{"subject":${quote(subject)},"body":${quote(body)},"confirm":$confirm}""",
            ),
        )

    suspend fun profile(accessId: Int): Profile = decodeApi(expect("GET", "/api/portal/$accessId/"))

    /** Änderung melden; Felder wie im Browser (email, phone, street, postal_code, city, …). */
    suspend fun reportChange(accessId: Int, values: Map<String, String>, message: String): Boolean {
        val body = JsonObject((values + ("message" to message)).mapValues { JsonPrimitive(it.value) })
        val text = expect("POST", "/api/portal/$accessId/change-requests/", json.encodeToString(JsonObject.serializer(), body))
        // true: wartet auf den Vorstand · false: sofort übernommen
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw protocolError()
        return root["request"].let { it != null && it !is kotlinx.serialization.json.JsonNull }
    }

    suspend fun card(accessId: Int): CardState = decodeApi(expect("GET", "/api/portal/$accessId/card/"))

    /** Speicherlink für Google Wallet (überträgt die Passangaben an Google). */
    suspend fun walletGoogle(accessId: Int): String {
        val text = expect("POST", "/api/portal/$accessId/card/wallet/google/", "{}")
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        }
        val url = (root?.get("url") as? JsonPrimitive)?.content
        if (url == null || !url.startsWith("https://pay.google.com/")) throw protocolError()
        return url
    }

    /** Apple-Wallet-Pass (`.pkpass`), auch für freie Wallet-Apps auf Android. */
    suspend fun walletApple(accessId: Int): ByteArray {
        val resp = send("GET", "/api/portal/$accessId/card/wallet/apple/", accept = false, limit = MAX_FILE_BYTES)
        if (resp.code !in 200..299) throw ApiException(resp.code, errorMessage(resp.code, resp.text), body = resp.text, origin = apiOrigin(server, token))
        return resp.body
    }

    suspend fun photo(accessId: Int): PhotoState = decodeApi(expect("GET", "/api/portal/$accessId/photo/"))

    /** Foto hochladen (JPEG, auf dem Gerät verkleinert und ohne EXIF) mit Einwilligung. */
    suspend fun uploadPhoto(accessId: Int, jpeg: ByteArray): PhotoState {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("consent", "true")
            .addFormDataPart("file", "foto.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val resp = send("POST", "/api/portal/$accessId/photo/", form)
        if (resp.code !in 200..299) throw ApiException(resp.code, errorMessage(resp.code, resp.text), body = resp.text, origin = apiOrigin(server, token))
        return decodeApi(PhotoState.serializer(), resp.text)
    }

    suspend fun deletePhoto(accessId: Int): PhotoState = decodeApi(expect("DELETE", "/api/portal/$accessId/photo/"))

    /** Bild mit Geräteschlüssel laden (Pfad aus der Schnittstelle). */
    suspend fun image(path: String): ByteArray? = try {
        send("GET", path, accept = false, limit = MAX_FILE_BYTES).takeIf { it.code in 200..299 }?.body
    } catch (e: ApiException) {
        null
    }

    /** „Meine Ergebnisse“ (Wettbewerbe Schritt 5). */
    suspend fun myResults(accessId: Int): MyResults =
        decodeApi(expect("GET", "/api/portal/$accessId/results/"))

    /** Ranglisten wie auf der Webseite des Vereins (Schießen in der App, SB4). */
    suspend fun rankings(accessId: Int): Rankings =
        decodeApi(expect("GET", "/api/portal/$accessId/rankings/"))

    suspend fun ranking(accessId: Int, id: String): RankingDetail =
        decodeApi(expect("GET", "/api/portal/$accessId/rankings/${java.net.URLEncoder.encode(id, "UTF-8")}/"))

    suspend fun rankingVersion(accessId: Int, id: String): RankingVersion =
        decodeApi(expect("GET", "/api/portal/$accessId/rankings/${java.net.URLEncoder.encode(id, "UTF-8")}/?version=1"))

    // --- Helferdienste im Mitgliederbereich (HUBIplan Schritt H2) ---

    suspend fun memberHelpers(accessId: Int): HelperOverview =
        decodeApi(expect("GET", "/api/portal/$accessId/helpers/"))

    suspend fun signupShift(accessId: Int, shiftId: Int) {
        expect("POST", "/api/portal/$accessId/helpers/shifts/$shiftId/signup/", "{}")
    }

    suspend fun cancelShift(accessId: Int, assignmentId: Int) {
        expect("POST", "/api/portal/$accessId/helpers/assignments/$assignmentId/cancel/", "{}")
    }

    suspend fun circulars(accessId: Int): List<CircularItem> =
        decodeApi(expect("GET", "/api/portal/$accessId/circulars/"))

    suspend fun circular(accessId: Int, id: Int): CircularItem =
        decodeApi(expect("GET", "/api/portal/$accessId/circulars/$id/"))

    // --- Noten in der App (N3–N8) ---

    /** Notenmappen des eigenen Musikerprofils: feste Mappen und Termine. */
    suspend fun scoreFolders(accessId: Int): ScoreFolders =
        decodeApi(expect("GET", "/api/portal/$accessId/music/folders/"))

    /**
     * Notendatei laden. [path] kommt aus der Mappe (nur eigener Mitgliederbereich), [format]
     * "", "pdf" oder "midi", [transpose] z. B. "M2" oder "-3". Mit [etag] antwortet der Server
     * bei unveränderter Fassung mit 304.
     */
    suspend fun scoreFile(accessId: Int, path: String, format: String = "", transpose: String = "", etag: String? = null): ScoreDownload {
        val prefix = "/api/portal/$accessId/music/files/"
        val base = path.substringBefore('?')
        if (!base.startsWith(prefix) || base.drop(prefix.length).trimEnd('/').toIntOrNull() == null) throw protocolError()
        val query = listOfNotNull(
            format.takeIf { it.isNotEmpty() }?.let { "format=$it" },
            transpose.takeIf { it.isNotEmpty() }?.let { "transpose=" + java.net.URLEncoder.encode(it, "UTF-8") },
        ).joinToString("&")
        val headers = etag?.let { mapOf("If-None-Match" to it) } ?: emptyMap()
        val resp = send("GET", base + (if (query.isEmpty()) "" else "?$query"), headers = headers, accept = false, limit = MAX_FILE_BYTES)
        if (resp.code == 304) return ScoreDownload(true, ByteArray(0), resp.headers["ETag"] ?: etag, null)
        if (resp.code !in 200..299) throw ApiException(resp.code, errorMessage(resp.code, resp.text), body = resp.text, origin = apiOrigin(server, token))
        return ScoreDownload(false, resp.body, resp.headers["ETag"], resp.headers["Content-Type"])
    }

    /** Live-Vorgabe der Leitung für eine Mappe oder einen Termin ([target] z. B. "folder-3"). */
    suspend fun live(accessId: Int, target: String): LiveState =
        decodeApi(expect("GET", "/api/portal/$accessId/music/live/${liveTarget(target)}/"))

    suspend fun sendLive(accessId: Int, target: String, arrangement: Int, number: String): LiveCue? =
        decodeApi<LiveSent>(
            expect(
                "POST",
                "/api/portal/$accessId/music/live/${liveTarget(target)}/",
                """{"arrangement":$arrangement,"number":${quote(number)}}""",
            ),
        ).cue
}

/** Nur die bekannten Ziele in den Pfad lassen. */
internal fun liveTarget(target: String): String =
    target.takeIf { Regex("^(folder|performance|rehearsal)-\\d{1,12}$").matches(it) } ?: throw protocolError()

private fun quote(value: String): String =
    json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))

private fun jsonObjectOf(vararg pairs: Pair<String, String>): JsonObject =
    JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

/** Antwort eines allauth-Anmeldeschritts auswerten; jede kaputte Antwort wird zur [ApiException] (Audit F03). */
internal fun parseAuthStep(status: Int, text: String, previous: String?): LoginStep {
    val root = try {
        json.parseToJsonElement(text).jsonObject
    } catch (e: Exception) {
        throw ApiException(status, errorMessage(status, text))
    }
    // Falsch geformte Antworten (z. B. "meta" als Liste) melden statt abzustürzen
    val (session, pendingMfa) = try {
        val session = root["meta"]?.jsonObject?.get("session_token")?.jsonPrimitive?.contentOrNull ?: previous
        session to (root["data"]?.jsonObject?.get("flows")?.jsonArray?.any {
            val flow = it.jsonObject
            flow["id"]?.jsonPrimitive?.contentOrNull == "mfa_authenticate" &&
                flow["is_pending"]?.jsonPrimitive?.booleanOrNull == true
        } ?: false)
    } catch (e: IllegalArgumentException) {
        throw ApiException(status, tr("Die Antwort des Servers war unverständlich. Bitte später erneut versuchen."))
    }
    if (status == 200 && session != null) return LoginStep.Done(session)
    if (status == 401 && pendingMfa && session != null) return LoginStep.NeedsCode(session)
    throw ApiException(status, runCatching { allauthMessage(root) }.getOrNull() ?: tr("Anmeldung fehlgeschlagen. Bitte E-Mail und Passwort prüfen."))
}

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
        val general = parseGeneralError(text)
        val fields = parseFieldErrors(text)
        val summary = fields.takeIf { it.isNotEmpty() }?.entries?.joinToString(" ") { (key, value) -> "${validationFieldLabel(key)}: $value" }
        if (general != null || summary != null) return listOfNotNull(general, summary).joinToString(" ")
    }

    return when (status) {
        401 -> tr("Dieses Gerät ist nicht mehr angemeldet. Bitte die App neu verbinden.")
        403 -> tr("Dafür fehlt die Berechtigung.")
        404 -> tr("Nicht gefunden.")
        429 -> tr("Zu viele Versuche. Bitte später erneut.")
        in 500..599 -> tr("Der Server hat gerade ein Problem. Bitte später erneut versuchen.")
        else -> tr("Unerwartete Antwort vom Server ({status}).", "status" to status)
    }
}

/** Feldpfade bleiben erhalten; allgemeine und allauth-Fehler sind keine Feldmarkierung. */
fun parseFieldErrors(text: String): Map<String, String> {
    val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyMap()
    val out = linkedMapOf<String, String>()
    fun visit(path: String, value: kotlinx.serialization.json.JsonElement) {
        when (value) {
            is JsonObject -> value.forEach { (key, item) -> visit("$path.$key", item) }
            is JsonArray -> {
                val messages = value.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }
                if (messages.isNotEmpty()) out[path] = messages.joinToString(" ")
                value.forEachIndexed { index, item -> if (item is JsonObject || item is JsonArray) visit("$path.$index", item) }
            }
            is JsonPrimitive -> if (value.isString) value.contentOrNull?.let { out[path] = it }
        }
    }
    root.filterKeys { it !in setOf("detail", "non_field_errors", "errors", "status", "meta", "data", "code", "request_id") }.forEach { (key, value) -> visit(key, value) }
    return out
}

/** Lesbare Rückfallebene auch für noch unbekannte Serverfelder. */
fun validationFieldLabel(key: String): String = when (key.substringAfterLast('.')) {
    "first_name" -> tr("Vorname")
    "last_name" -> tr("Nachname")
    "birth_date" -> tr("Geburtsdatum")
    "entry_date" -> tr("Eintritt am")
    "email" -> tr("E-Mail")
    "phone" -> tr("Telefon")
    "street" -> tr("Straße")
    "postal_code" -> tr("PLZ")
    "city" -> tr("Ort")
    "amount" -> tr("Betrag in €")
    "account" -> tr("Konto")
    "category" -> tr("Kategorie")
    "date" -> tr("Datum (TT.MM.JJJJ)")
    "name", "counterparty_name" -> tr("Name")
    "counterparty" -> tr("Zahler oder Empfänger")
    "purpose" -> tr("Zweck")
    "text" -> tr("Text")
    "title" -> tr("Titel")
    "subject" -> tr("Betreff")
    "body" -> tr("Text")
    "member_type" -> tr("Mitgliedsart")
    "department_ids" -> tr("Untereinheit")
    "starts_at" -> tr("Beginn (HH:MM)")
    "ends_at" -> tr("Ende (optional)")
    "description" -> tr("Beschreibung")
    "due_date" -> tr("Frist (TT.MM.JJJJ, optional)")
    "due_time" -> tr("Uhrzeit (optional)")
    "rsvp_deadline" -> tr("Anmeldeschluss (optional, TT.MM.JJJJ)")
    else -> tr("Angabe")
}

fun parseGeneralError(text: String): String? {
    val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
    (root["detail"] as? JsonPrimitive)?.contentOrNull?.let { return it }
    runCatching { allauthMessage(root) }.getOrNull()?.let { return it }
    return (root["non_field_errors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotEmpty() }?.joinToString(" ")
}
