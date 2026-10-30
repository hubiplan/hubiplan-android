package de.hubiplan.app.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/** Ergebnis einer Änderung: neuer Stand und Paket für „Rückgängig“. */
data class Changed<T>(val value: T, val changeSet: String?, val label: String?)

/**
 * Verwaltung einer Einheit (Schritt B2) mit dem Vorstandsschlüssel. Pfade wie im Browser,
 * nur unter `/api/app/v1/admin/u/<Einheit>/`.
 */
class AdminClient(
    private val api: Api,
    val unitId: Int,
    private val onUse: () -> Unit = {},
    /** "u": Einheit · "d": Untereinheit mit Rollen nur dort (Zugführung, B4) */
    val scope: String = "u",
) {
    private val base = "/api/app/v1/admin/$scope/$unitId/"

    /** Jede Anfrage zählt als Nutzung (Gerätesperre erst nach 5 Minuten Pause). */
    private fun use(): Api {
        onUse()
        return api
    }

    /** Bildpfad aus einer Antwort (`/api/orgs/<Einheit>/…`) auf den Verwaltungspfad umstellen. */
    fun imagePath(path: String): String = adminPath(path, unitId)

    suspend fun image(path: String): ByteArray? = use().image(imagePath(path))

    suspend fun members(search: String, page: Int, status: String = "aktiv"): MemberPage {
        val q = URLEncoder.encode(search.trim(), "UTF-8")
        return json.decodeFromString(get("members/?suche=$q&status=$status&page=$page&page_size=50"))
    }

    suspend fun member(id: Int): MemberDetail = json.decodeFromString(get("members/$id/"))

    suspend fun memberTypes(): List<MemberTypeItem> =
        decodeList<MemberTypeItem>(get("member-types/")).filterNot { it.isArchived }

    suspend fun departments(): List<DepartmentItem> =
        decodeList<DepartmentItem>(get("departments/")).filter { it.isActive }

    /** Stammdaten ändern (nur geänderte Felder). */
    suspend fun update(id: Int, values: Map<String, JsonElement>): Changed<MemberDetail> =
        change("PATCH", "members/$id/", JsonObject(values))

    suspend fun preview(id: Int, values: Map<String, JsonElement>): Preview =
        json.decodeFromString(use().admin("POST", "${base}members/$id/preview/", JsonObject(values).toString()).text)

    suspend fun changeType(id: Int, memberType: Int, validFrom: String): Changed<MemberDetail> =
        change("POST", "members/$id/change-type/", obj("member_type" to memberType, "valid_from" to validFrom))

    suspend fun move(id: Int, department: Int, validFrom: String): Changed<MemberDetail> =
        change("POST", "members/$id/move/", obj("department" to department, "valid_from" to validFrom))

    suspend fun exit(id: Int, date: String, reason: String, note: String): Changed<MemberDetail> =
        change("POST", "members/$id/exit/", obj("exit_date" to date, "exit_reason" to reason, "note" to note))

    suspend fun undo(changeSet: String) {
        use().admin("POST", "${base}changes/$changeSet/undo/", "{}")
    }

    suspend fun decidePhoto(id: Int, approve: Boolean): Changed<Unit> {
        val r = use().admin("POST", "${base}members/$id/photo/approve/", obj("decision" to if (approve) "approve" else "reject").toString())
        return Changed(Unit, r.changeSet.takeIf { r.undoable }, r.label)
    }

    suspend fun changeRequests(): List<AdminChangeRequest> = decodeList(get("change-requests/"))

    /** Änderungsmeldung entscheiden. 409: seit der Meldung im Verein geändert ([overwrite]). */
    suspend fun decide(id: Int, accept: Boolean, response: String, overwrite: Boolean = false): Changed<Unit> {
        val r = use().admin(
            "POST",
            "${base}change-requests/$id/decide/",
            obj("accept" to accept, "response" to response, "overwrite" to overwrite).toString(),
        )
        return Changed(Unit, r.changeSet.takeIf { r.undoable }, r.label)
    }

    suspend fun checkCard(code: String): CardCheck =
        json.decodeFromString(use().admin("POST", "${base}cards/check/", obj("code" to code).toString()).text)

    suspend fun birthdays(from: String, to: String): List<BirthdayRow> =
        json.decodeFromString<Rows<BirthdayRow>>(get("birthdays/?von=$from&bis=$to")).results

    suspend fun jubilees(from: String, to: String): List<JubileeRow> =
        json.decodeFromString<Rows<JubileeRow>>(get("jubilees/?von=$from&bis=$to")).results

    // --- B2b ---

    suspend fun awards(id: Int): List<AwardItem> = decodeList(get("members/$id/honorary-titles/"))

    suspend fun titles(): List<TitleItem> = decodeList<TitleItem>(get("honorary-titles/")).filterNot { it.isArchived }

    suspend fun addAward(id: Int, title: Int, validFrom: String, note: String): Changed<Unit> =
        changeUnit("POST", "members/$id/honorary-titles/", obj("title" to title, "valid_from" to validFrom, "note" to note))

    suspend fun endAward(id: Int, award: Int, validUntil: String): Changed<Unit> =
        changeUnit("PATCH", "members/$id/honorary-titles/$award/", obj("valid_until" to validUntil))

    suspend fun fields(id: Int): List<FieldEntry> = decodeList(get("members/$id/fields/"))

    suspend fun saveFields(id: Int, values: Map<String, JsonElement>): Changed<Unit> =
        changeUnit("PUT", "members/$id/fields/", obj("values" to JsonObject(values)))

    /** SEPA-Mandate; [full] nur nach erneuter Gerätesperre (ganze IBAN). */
    suspend fun mandates(id: Int, full: Boolean = false): List<MandateItem> =
        decodeList(get("billing/mandates/?mitglied=$id" + if (full) "&voll=1" else ""))

    suspend fun addMandate(id: Int, holder: String, iban: String, bic: String, signedOn: String): Changed<Unit> =
        changeUnit(
            "POST",
            "billing/mandates/",
            obj("membership" to id, "holder" to holder, "iban" to iban.replace(" ", ""), "bic" to bic, "signed_on" to signedOn),
        )

    suspend fun reentry(id: Int, date: String, memberType: Int): Changed<MemberDetail> =
        change("POST", "members/$id/reentry/", obj("entry_date" to date, "member_type" to memberType))

    suspend fun duplicates(lastName: String, firstName: String, birthDate: String): List<DuplicateItem> {
        fun enc(v: String) = URLEncoder.encode(v.trim(), "UTF-8")
        return decodeList(get("members/duplicates/?name=${enc(lastName)}&vorname=${enc(firstName)}&geburtsdatum=$birthDate"))
    }

    /** Neues Mitglied; [key] schützt vor doppelter Aufnahme bei erneutem Senden. */
    suspend fun admit(values: JsonObject, key: String): MemberDetail =
        json.decodeFromString(use().admin("POST", "${base}members/admit/", values.toString(), mapOf("Idempotency-Key" to key)).text)

    // --- B3: Termine ---

    suspend fun events(from: String, to: String): AdminEventList =
        json.decodeFromString(get("events/?from=$from&to=$to"))

    suspend fun event(id: Int): AdminEvent = json.decodeFromString(get("events/$id/"))

    suspend fun createEvent(values: JsonObject): Changed<AdminEvent> = changeAs("POST", "events/", values)

    /** Ganzen Termin absagen oder die Absage zurücknehmen; [notify]: Eingeladene per Mail. */
    suspend fun cancelEvent(id: Int, cancelled: Boolean, note: String, notify: Boolean): Changed<AdminEvent> =
        changeAs("PATCH", "events/$id/", obj("is_cancelled" to cancelled, "cancel_note" to note, "notify" to notify))

    /** Einen Termin einer Serie absagen ([cancelled]) oder wieder wie in der Serie. */
    suspend fun cancelOccurrence(id: Int, originalStart: String, cancelled: Boolean, note: String): Changed<AdminEvent> =
        if (cancelled) {
            changeAs("PUT", "events/$id/occurrence/", obj("original_start" to originalStart, "is_cancelled" to true, "note" to note))
        } else {
            changeAs("DELETE", "events/$id/occurrence/", obj("original_start" to originalStart))
        }

    suspend fun responses(id: Int): AdminResponses = json.decodeFromString(get("events/$id/responses/"))

    suspend fun setResponse(id: Int, membership: Int, status: String, guests: Int, note: String): Changed<Unit> =
        changeUnit(
            "PUT",
            "events/$id/responses/",
            obj("membership_id" to membership, "status" to status, "guests" to guests, "note" to note),
        )

    suspend fun deleteResponse(id: Int, membership: Int): Changed<Unit> {
        val r = use().admin("DELETE", "${base}events/$id/responses/$membership/")
        return Changed(Unit, r.changeSet.takeIf { r.undoable }, r.label)
    }

    // --- B4: Kasse ---

    suspend fun accounts(): List<FinanceAccount> =
        decodeList<FinanceAccount>(get("finance/accounts/")).filter { it.isActive }

    suspend fun categories(): List<FinanceCategory> =
        decodeList<FinanceCategory>(get("finance/categories/")).filter { it.isActive }

    suspend fun bookings(page: Int, withoutReceipt: Boolean = false): BookingPage =
        json.decodeFromString(get("finance/bookings/?page=$page&page_size=30" + if (withoutReceipt) "&ohne_beleg=1" else ""))

    suspend fun booking(id: Int): BookingItem = json.decodeFromString(get("finance/bookings/$id/"))

    suspend fun receipt(id: Int): ByteArray? = use().image("${base}finance/receipts/$id/")

    /** Buchung mit Beleg in einem Schritt; [draft].key schützt vor doppeltem Buchen. */
    suspend fun book(draft: BookingDraft, jpeg: ByteArray): BookingItem {
        val fields = buildMap {
            put("date", draft.date)
            put("kind", draft.kind)
            put("amount", draft.amount)
            put("account", draft.account.toString())
            draft.category?.let { put("category", it.toString()) }
            put("text", draft.text)
            if (draft.counterparty.isNotBlank()) put("counterparty", draft.counterparty)
        }
        val r = use().adminForm("${base}finance/app-booking/", fields, "beleg.jpg", jpeg, mapOf("Idempotency-Key" to draft.key))
        return json.decodeFromString(r.text)
    }

    /** Beleg zu einer vorhandenen Buchung nachreichen. */
    suspend fun addReceipt(bookingId: Int, jpeg: ByteArray) {
        use().adminForm("${base}finance/bookings/$bookingId/receipts/", emptyMap(), "beleg.jpg", jpeg)
    }

    // --- B4: Untereinheit (Zugführung) ---

    suspend fun deptMembers(search: String, page: Int): MemberPage {
        val q = URLEncoder.encode(search.trim(), "UTF-8")
        return json.decodeFromString(get("members/?suche=$q&page=$page&page_size=50"))
    }

    suspend fun deptMember(id: Int): DeptMemberDetail = json.decodeFromString(get("members/$id/"))

    suspend fun deptUpdate(id: Int, values: Map<String, JsonElement>): Changed<DeptMemberDetail> =
        changeAs("PATCH", "members/$id/", JsonObject(values))

    private suspend inline fun <reified T> changeAs(method: String, path: String, body: JsonObject): Changed<T> {
        val r = use().admin(method, base + path, body.toString())
        return Changed(json.decodeFromString(r.text), r.changeSet.takeIf { r.undoable }, r.label)
    }

    private suspend fun changeUnit(method: String, path: String, body: JsonObject): Changed<Unit> {
        val r = use().admin(method, base + path, body.toString())
        return Changed(Unit, r.changeSet.takeIf { r.undoable }, r.label)
    }

    private suspend fun get(path: String): String = use().admin("GET", base + path).text

    private suspend fun change(method: String, path: String, body: JsonObject): Changed<MemberDetail> {
        val r = use().admin(method, base + path, body.toString())
        return Changed(json.decodeFromString(r.text), r.changeSet.takeIf { r.undoable }, r.label)
    }
}

/** `/api/orgs/<Einheit>/…` → `/api/app/v1/admin/u/<Einheit>/…` (nur für diese Einheit). */
fun adminPath(path: String, unitId: Int): String {
    val prefix = "/api/orgs/$unitId/"
    return if (path.startsWith(prefix)) "/api/app/v1/admin/u/$unitId/" + path.removePrefix(prefix) else path
}

fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(
    pairs.associate { (k, v) ->
        k to when (v) {
            null -> kotlinx.serialization.json.JsonNull
            is Boolean -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is JsonElement -> v
            else -> JsonPrimitive(v.toString())
        }
    },
)

/** Liste aus einer Antwort, ob als Array oder seitenweise (`results`). */
private inline fun <reified T> decodeList(text: String, format: kotlinx.serialization.json.Json = json): List<T> {
    val root = format.parseToJsonElement(text)
    val items = if (root is kotlinx.serialization.json.JsonArray) root else root.jsonObject["results"]?.jsonArray
    return items?.map { format.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), it) } ?: emptyList()
}
