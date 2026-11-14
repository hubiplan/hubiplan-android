package de.hubiplan.app

import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.SurveyAnswerRow
import de.hubiplan.app.data.SurveyList
import de.hubiplan.app.data.answerPayload
import de.hubiplan.app.data.filled
import de.hubiplan.app.data.initialRow
import de.hubiplan.app.data.json
import de.hubiplan.app.data.ready
import de.hubiplan.app.data.waitingCount
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.ichBereiche
import de.hubiplan.app.ui.surveySummary
import de.hubiplan.app.ui.surveyValueLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Umfragen unter „Ich“ (HUBIplan Umfragen, Stufe U2); alle Daten erfunden. */
class UmfragenTest {
    private val text = """
        {"surveys":[
          {"id":5,"title":"Ausflug 2099","description":"","unit":"","event":null,"status":"open",
           "closes_at":"2099-05-01T18:00:00Z","names":"open","results_mode":"after_answer","anonymous":false,
           "answered":false,"can_answer":true,"can_change":false,"mine":null,"results":null,"follow_event":null,
           "questions":[
             {"id":1,"kind":"single","text":"Wie fährst du mit?","required":true,"max_choices":null,"allow_maybe":false,
              "options":[{"id":11,"label":"Bus","starts_at":null,"ends_at":null,"all_day":false,"capacity":50},
                         {"id":12,"label":"eigenes Auto","starts_at":null,"ends_at":null,"all_day":false,"capacity":null}]},
             {"id":2,"kind":"dates","text":"Wann passt es?","required":true,"max_choices":null,"allow_maybe":true,
              "options":[{"id":21,"label":"","starts_at":"2099-06-13T16:00:00Z","ends_at":null,"all_day":false,"capacity":null},
                         {"id":22,"label":"","starts_at":"2099-06-20T16:00:00Z","ends_at":null,"all_day":false,"capacity":null}]},
             {"id":3,"kind":"quantity","text":"Essen","required":false,"max_choices":null,"allow_maybe":false,"max_total":2,
              "options":[{"id":31,"label":"Schnitzel"},{"id":32,"label":"vegetarisch"}]},
             {"id":4,"kind":"text","text":"Anmerkungen","required":false,"options":[]}]},
          {"id":6,"title":"Grillabend","status":"closed","anonymous":true,"answered":true,"can_answer":false,
           "questions":[],"results":{"participants":2,"hidden":true,"questions":[]},
           "follow_event":{"id":40,"title":"Grillabend","starts_at":"2099-06-13T16:00:00Z","ends_at":"2099-06-13T20:00:00Z",
             "all_day":false,"rsvp_enabled":true,"said_yes":true,"response":null}}]}
    """.trimIndent()

    @Test
    fun antwortLesen() {
        val list = json.decodeFromString<SurveyList>(text)
        assertEquals(2, list.surveys.size)
        val s = list.surveys[0]
        assertTrue(s.waiting)
        assertEquals(2, s.questions[2].quantityLimit)
        assertEquals(1, list.waitingCount())
        val closed = list.surveys[1]
        assertFalse(closed.isOpen)
        assertTrue(closed.canFollow)
        assertTrue(closed.results!!.hidden)
        assertFalse(closed.copy(followEvent = closed.followEvent!!.copy(response = "yes")).canFollow)
    }

    @Test
    fun formularUndNutzlast() {
        val s = json.decodeFromString<SurveyList>(text).surveys[0]
        val draft = s.questions.associate { it.id to it.initialRow(s.mine) }.toMutableMap()
        // Terminfindung startet mit „Nein“ für jeden Vorschlag
        assertEquals(mapOf("21" to "no", "22" to "no"), draft[2]!!.dates)
        assertFalse(s.ready(draft))
        draft[1] = draft[1]!!.copy(options = listOf(11))
        assertTrue(s.ready(draft))
        draft[2] = draft[2]!!.copy(dates = mapOf("21" to "yes", "22" to "maybe"))
        draft[3] = draft[3]!!.copy(quantities = mapOf("31" to 2, "32" to 1))
        assertFalse("mehr als erlaubt", s.ready(draft))
        draft[3] = draft[3]!!.copy(quantities = mapOf("31" to 2, "32" to 0))
        assertTrue(s.ready(draft))
        val payload = s.answerPayload(draft).toString()
        assertEquals(
            """{"answers":[{"question":1,"options":[11]},{"question":2,"dates":{"21":"yes","22":"maybe"}},""" +
                """{"question":3,"quantities":{"31":2}}]}""",
            payload,
        )
        assertFalse(s.questions[3].filled(SurveyAnswerRow(4, text = "  ")))
    }

    @Test
    fun ichZeigtUmfragen() {
        val access = PortalAccess(5, "Schützenverein Musterstadt", modules = listOf("surveys", "meetings"))
        assertEquals(listOf(IchBereich.DATEN, IchBereich.VERSAMMLUNGEN, IchBereich.UMFRAGEN), ichBereiche(access))
        assertFalse(IchBereich.UMFRAGEN in ichBereiche(access.copy(modules = emptyList())))
        // Ältere Stände ohne Modulliste: Schalter
        assertTrue(IchBereich.UMFRAGEN in ichBereiche(PortalAccess(5, "Musterstadt", surveysEnabled = true)))
        val list = json.decodeFromString<SurveyList>(text)
        assertEquals("1 wartet auf dich", surveySummary(list))
        assertEquals("Keine offenen Umfragen", surveySummary(SurveyList()))
        assertEquals("Enthaltung", surveyValueLabel("abstain"))
        assertNull(list.surveys[0].mine)
    }
}
