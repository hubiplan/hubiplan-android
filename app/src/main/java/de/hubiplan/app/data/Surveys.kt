package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Umfragen und Meinungsbilder im Mitgliederbereich (GET /api/portal/<Zugang>/surveys/,
 * Beschreibung in hubiplan/docs/umfragen.md). Nichts davon wird auf dem Gerät gespeichert.
 */
@Serializable
data class SurveyList(val surveys: List<MemberSurvey> = emptyList())

@Serializable
data class MemberSurvey(
    val id: Int,
    val title: String = "",
    val description: String = "",
    val unit: String = "",
    val event: SurveyEvent? = null,
    /** "open" oder "closed" */
    val status: String = "open",
    @SerialName("closes_at") val closesAt: String? = null,
    val anonymous: Boolean = false,
    @SerialName("results_mode") val resultsMode: String = "after_answer",
    val answered: Boolean = false,
    @SerialName("can_answer") val canAnswer: Boolean = false,
    @SerialName("can_change") val canChange: Boolean = false,
    val mine: List<SurveyAnswerRow>? = null,
    val questions: List<SurveyQuestion> = emptyList(),
    val results: SurveyResult? = null,
    @SerialName("follow_event") val followEvent: FollowEvent? = null,
) {
    val isOpen: Boolean get() = status == "open"

    /** Wartet noch auf die eigene Antwort (Plakette unter „Ich“). */
    val waiting: Boolean get() = isOpen && canAnswer && !answered

    /** „Zusage übernehmen“: Termin aus der Terminfindung, selbst „Ja“ gesagt, noch nicht zugesagt. */
    val canFollow: Boolean
        get() = followEvent?.let { it.saidYes && it.rsvpEnabled && it.response != "yes" } ?: false
}

@Serializable
data class SurveyEvent(val id: Int, val title: String = "", @SerialName("starts_at") val startsAt: String? = null)

@Serializable
data class FollowEvent(
    val id: Int,
    val title: String = "",
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("all_day") val allDay: Boolean = false,
    @SerialName("rsvp_enabled") val rsvpEnabled: Boolean = false,
    @SerialName("said_yes") val saidYes: Boolean = false,
    val response: String? = null,
)

@Serializable
data class SurveyQuestion(
    val id: Int,
    /** single, multiple, yesno, dates, quantity, text */
    val kind: String,
    val text: String = "",
    val required: Boolean = false,
    @SerialName("max_choices") val maxChoices: Int? = null,
    @SerialName("allow_maybe") val allowMaybe: Boolean = false,
    val options: List<SurveyOption> = emptyList(),
    /** Mengenauswahl: höchstens so viele Stück zusammen (am Termin 1 + Begleitpersonen) */
    @SerialName("max_total") val maxTotal: Int? = null,
) {
    val quantityLimit: Int get() = maxTotal ?: maxChoices ?: 1
}

@Serializable
data class SurveyOption(
    val id: Int,
    val label: String = "",
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("all_day") val allDay: Boolean = false,
    val capacity: Int? = null,
)

/** Eigene Antwort je Frage, wie sie der Server liefert und annimmt. */
@Serializable
data class SurveyAnswerRow(
    val question: Int,
    val options: List<Int>? = null,
    val value: String? = null,
    val dates: Map<String, String>? = null,
    val quantities: Map<String, Int>? = null,
    val text: String? = null,
)

@Serializable
data class SurveyResult(
    val participants: Int = 0,
    val hidden: Boolean = false,
    val archived: Boolean = false,
    val questions: List<ResultQuestion> = emptyList(),
)

@Serializable
data class ResultQuestion(
    val id: Int,
    val kind: String,
    val text: String = "",
    val options: List<ResultOption>? = null,
    val values: Map<String, ResultValue>? = null,
    val texts: List<ResultText>? = null,
    val best: List<Int>? = null,
)

@Serializable
data class ResultOption(
    val id: Int,
    val label: String = "",
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("all_day") val allDay: Boolean = false,
    val capacity: Int? = null,
    val count: Int = 0,
    val maybe: Int? = null,
    val names: List<String> = emptyList(),
    @SerialName("maybe_names") val maybeNames: List<String>? = null,
)

@Serializable
data class ResultValue(val count: Int = 0, val names: List<String> = emptyList())

@Serializable
data class ResultText(val text: String = "", val name: String = "")

/** Startwerte des Formulars: eigene Antworten, bei Terminfindung jeder Vorschlag „Nein“. */
fun SurveyQuestion.initialRow(mine: List<SurveyAnswerRow>?): SurveyAnswerRow {
    val own = mine?.firstOrNull { it.question == id } ?: SurveyAnswerRow(question = id)
    return if (kind == "dates" && own.dates == null) own.copy(dates = options.associate { it.id.toString() to "no" }) else own
}

/** Ist die Frage beantwortet? Leere freiwillige Fragen gehen nicht mit. */
fun SurveyQuestion.filled(row: SurveyAnswerRow): Boolean = when (kind) {
    "single", "multiple" -> !row.options.isNullOrEmpty()
    "yesno" -> !row.value.isNullOrBlank()
    "dates" -> row.dates != null
    "quantity" -> (row.quantities ?: emptyMap()).values.any { it > 0 }
    else -> !row.text.isNullOrBlank()
}

/** Darf abgeschickt werden? Pflichtfragen beantwortet, Mengen im Rahmen, Mehrfachauswahl nicht zu viel. */
fun MemberSurvey.ready(draft: Map<Int, SurveyAnswerRow>): Boolean = questions.all { q ->
    val row = draft[q.id] ?: SurveyAnswerRow(q.id)
    (!q.required || q.filled(row)) &&
        (q.kind != "quantity" || (row.quantities ?: emptyMap()).values.sum() <= q.quantityLimit) &&
        (q.kind != "multiple" || q.maxChoices == null || (row.options?.size ?: 0) <= q.maxChoices)
}

/** Nutzlast für POST …/answer/: nur beantwortete Fragen, je Frageart nur das passende Feld. */
fun MemberSurvey.answerPayload(draft: Map<Int, SurveyAnswerRow>): JsonObject {
    val rows = questions.mapNotNull { q ->
        val row = draft[q.id] ?: return@mapNotNull null
        if (!q.filled(row)) return@mapNotNull null
        val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("question" to JsonPrimitive(q.id))
        when (q.kind) {
            "single", "multiple" -> fields["options"] = JsonArray(row.options.orEmpty().map { JsonPrimitive(it) })
            "yesno" -> fields["value"] = JsonPrimitive(row.value)
            "dates" -> fields["dates"] = JsonObject(row.dates.orEmpty().mapValues { JsonPrimitive(it.value) })
            "quantity" -> fields["quantities"] = JsonObject(
                row.quantities.orEmpty().filterValues { it > 0 }.mapValues { JsonPrimitive(it.value) },
            )
            else -> fields["text"] = JsonPrimitive(row.text.orEmpty().trim())
        }
        JsonObject(fields)
    }
    return JsonObject(mapOf("answers" to JsonArray(rows)))
}

/** Zahl der Umfragen, die noch auf eine Antwort warten (Kurzstand unter „Ich“). */
fun SurveyList.waitingCount(): Int = surveys.count { it.waiting }
