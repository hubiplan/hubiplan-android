package de.hubiplan.app

import de.hubiplan.app.data.EventDetail
import de.hubiplan.app.data.FeeQuote
import de.hubiplan.app.data.MemberRegistration
import de.hubiplan.app.data.RegistrationChoice
import de.hubiplan.app.data.RegistrationGap
import de.hubiplan.app.data.cancelPayload
import de.hubiplan.app.data.cancelQuery
import de.hubiplan.app.data.json
import de.hubiplan.app.data.registrationMissing
import de.hubiplan.app.data.registrationPayload
import de.hubiplan.app.data.registrationSum
import de.hubiplan.app.data.substitutePayload
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verbindliche Anmeldung mit Teilnahmegebühr (HUBIplan A1–A3); alle Daten erfunden. */
class AnmeldungenTest {
    private val antwort = """
        {"event_id":5,"can_cancel":true,
         "offer":{"id":1,"state":"open","state_text":"Anmeldung offen","closes_at":"2099-06-01T23:59:00+02:00",
          "is_open":true,"capacity":50,"free":47,"taken":3,"max_persons":4,"guests_allowed":true,"due_days":14,
          "category":null,"terms":"Stornierung nur bis zum Anmeldeschluss.",
          "prices":[
           {"id":11,"name":"Erwachsene","name_en":"Adults","applies":"all","age_from":null,"age_to":null,"capacity":null,
            "free":null,"vat_rate":null,"is_active":true,
            "current":{"id":21,"name":"Early Bird","amount":"29.00","until_date":"2099-03-01","remaining":null},
            "next":{"name":"Normal","amount":"35.00","until_date":null}},
           {"id":12,"name":"Kinder","name_en":"","applies":"members","age_from":6,"age_to":13,"capacity":10,
            "free":0,"vat_rate":null,"is_active":true,
            "current":{"id":22,"name":"","amount":"15.00","until_date":null,"remaining":null},"next":null},
           {"id":13,"name":"Gäste","name_en":"Guests","applies":"guests","age_from":null,"age_to":null,"capacity":null,
            "free":null,"vat_rate":null,"is_active":true,
            "current":{"id":23,"name":"","amount":"40.00","until_date":null,"remaining":null},"next":null}]},
         "persons":[{"membership_id":7,"name":"Anna Muster","self":true,"registered":false,"has_birth_date":true},
                    {"membership_id":8,"name":"Kim Muster","self":false,"registered":true,"has_birth_date":false}],
         "mine":[{"id":3,"number":"A2099-0001","status":"registered","name":"Anna Muster","total":"15.00",
                  "paid_state":"open","created_at":"2099-01-02T10:00:00+01:00","cancelled_at":null,
                  "participants":[{"id":4,"name":"Kim Muster","membership_id":8,"price_id":12,"price_name":"Kinder",
                                   "amount":"15.00","status":"active"}],
                  "invoice":{"id":9,"number":"2099-0042","status":"open","total":"15.00","paid":"0.00","due_date":"2099-01-16"}}]}
    """.trimIndent()

    @Test fun angebotUndEigeneAnmeldungLesen() {
        val d = json.decodeFromString(MemberRegistration.serializer(), antwort)
        assertEquals(47, d.offer.free)
        assertEquals("Adults", d.offer.prices[0].label(english = true))
        assertEquals("Kinder", d.offer.prices[1].label(english = true))
        // Ausgebuchter Kindertarif und Gästetarif sind für Mitglieder nicht wählbar
        assertEquals(listOf(11), d.offer.pricesFor(member = true).map { it.id })
        assertEquals(listOf(11, 13), d.offer.pricesFor(member = false).map { it.id })
        assertTrue(d.offer.prices[1].hasAge)
        val mine = d.mine.single()
        assertTrue(mine.active)
        assertEquals("2099-0042", mine.invoice?.number)
        assertEquals("Kinder", mine.participants.single().priceName)
    }

    @Test fun summeNachAktuellerPreisstufe() {
        val d = json.decodeFromString(MemberRegistration.serializer(), antwort)
        val persons = listOf(RegistrationChoice(7, "", 11), RegistrationChoice(null, "Gerd Gast", 13))
        assertEquals("69.00", registrationSum(d.offer, persons).toPlainString())
    }

    @Test fun anfrageMitGastUndKennzeichenApp() {
        val payload = registrationPayload(
            listOf(RegistrationChoice(7, "ignoriert", 11), RegistrationChoice(null, " Gerd Gast ", 13, "2012-05-04")),
            note = " vegetarisch ",
            termsAccepted = true,
        )
        val persons = payload["persons"] as JsonArray
        val member = persons[0] as JsonObject
        val guest = persons[1] as JsonObject
        assertEquals(7, member["membership_id"]!!.jsonPrimitive.int)
        assertNull(member["name"])
        assertEquals(JsonNull, guest["membership_id"])
        assertEquals("Gerd Gast", guest["name"]!!.jsonPrimitive.content)
        assertEquals("2012-05-04", guest["birth_date"]!!.jsonPrimitive.content)
        assertEquals("vegetarisch", payload["note"]!!.jsonPrimitive.content)
        assertTrue(payload["terms_accepted"]!!.jsonPrimitive.boolean)
        assertTrue(payload["app"]!!.jsonPrimitive.boolean)
    }

    @Test fun luekenVorDemAbschicken() {
        val offer = json.decodeFromString(MemberRegistration.serializer(), antwort).offer
        val none = { _: RegistrationChoice -> false }
        assertEquals(RegistrationGap.NOBODY, registrationMissing(offer, emptyList(), none, true))
        val five = List(5) { RegistrationChoice(it + 1, "", 11) }
        assertEquals(RegistrationGap.TOO_MANY, registrationMissing(offer, five, none, true))
        assertEquals(RegistrationGap.NAME, registrationMissing(offer, listOf(RegistrationChoice(null, " ", 13)), none, true))
        val kid = RegistrationChoice(9, "", 12)
        assertEquals(RegistrationGap.BIRTH_DATE, registrationMissing(offer, listOf(kid), { true }, true))
        assertEquals(RegistrationGap.TERMS, registrationMissing(offer, listOf(RegistrationChoice(7, "", 11)), none, false))
        assertNull(registrationMissing(offer, listOf(RegistrationChoice(7, "", 11)), none, true))
    }

    @Test fun terminMitAnmeldungUndAeltererServer() {
        val e = json.decodeFromString(
            EventDetail.serializer(),
            """
            {"id":5,"title":"Sommerfest","starts_at":"2099-06-13T14:00:00+02:00","ends_at":"2099-06-13T18:00:00+02:00",
             "registration":{"enabled":true,"state":"open","state_text":"Anmeldung offen","closes_at":"2099-06-01T23:59:00+02:00",
               "free":47,"can_setup":false,"can_view_list":false,"can_register":true}}
            """.trimIndent(),
        )
        assertTrue(e.registration!!.canRegister)
        assertEquals(47, e.registration!!.free)
        val alt = json.decodeFromString(
            EventDetail.serializer(),
            """{"id":5,"title":"Sommerfest","starts_at":"2099-06-13T14:00:00+02:00","ends_at":"2099-06-13T18:00:00+02:00"}""",
        )
        assertNull(alt.registration)
        val ohne = json.decodeFromString(
            EventDetail.serializer(),
            """{"id":5,"title":"Sommerfest","starts_at":"2099-06-13T14:00:00+02:00","ends_at":"2099-06-13T18:00:00+02:00","registration":null}""",
        )
        assertFalse(ohne.registration?.enabled ?: false)
    }

    private val warteliste = """
        {"event_id":5,"can_cancel":true,
         "offer":{"id":1,"state":"full","state_text":"Alle Plätze sind vergeben.","closes_at":"2099-06-01T23:59:00+02:00",
          "capacity":2,"free":0,"taken":2,"max_persons":4,"guests_allowed":true,"terms":"","prices":[],
          "waitlist":true,"waiting":1,"substitute_allowed":true,"cancel_rules":[{"days":30,"percent":50}],
          "cancel_fee_fixed":"5.00","expire_unpaid_days":null},
         "persons":[],
         "mine":[{"id":3,"number":"A2099-0002","status":"waiting","total":"35.00","paid_state":"waiting",
                  "participants":[{"id":4,"name":"Anna Muster","membership_id":7,"price_id":11,"price_name":"Erwachsene",
                                   "amount":"35.00","status":"active"}],
                  "invoice":null,"waitlist_position":1,"moved_up_at":null,"free_cancel_until":null,
                  "cancel_fee":"0.00","fee_invoice":null,"can_cancel":true,"can_substitute":true}]}
    """.trimIndent()

    @Test fun wartelisteUndStornostaffelLesen() {
        val d = json.decodeFromString(MemberRegistration.serializer(), warteliste)
        assertTrue(d.offer.canJoin)
        assertTrue(d.offer.toWaitlist)
        assertEquals(50, d.offer.cancelRules.single().percent)
        val mine = d.mine.single()
        assertTrue(mine.active && mine.waiting)
        assertEquals(1, mine.waitlistPosition)
        assertEquals(true, mine.canCancel)
        assertEquals(11, mine.activeParticipants.single().priceId)
        // Älterer Stand ohne Warteliste: voll heißt nicht anmeldbar, can_cancel fehlt
        val alt = json.decodeFromString(MemberRegistration.serializer(), antwort)
        assertFalse(alt.offer.copy(state = "full").canJoin)
        assertNull(alt.mine.single().canCancel)
    }

    @Test fun gebuehrenvorschauUndAnfragen() {
        val q = json.decodeFromString(FeeQuote.serializer(), """{"base":"35.00","percent":50,"fee":"22.50","free_reason":""}""")
        assertTrue(q.hasFee)
        val frei = json.decodeFromString(FeeQuote.serializer(), """{"base":"35.00","percent":0,"fee":"0.00","free_reason":"moved_up"}""")
        assertFalse(frei.hasFee)
        assertEquals("", cancelQuery(emptyList()))
        assertEquals("?participants=4,5", cancelQuery(listOf(4, 5)))
        assertTrue(cancelPayload(emptyList()).isEmpty())
        assertEquals(4, (cancelPayload(listOf(4))["participants"] as JsonArray)[0].jsonPrimitive.int)
        val gast = substitutePayload(null, " Erna Ersatz ", "1980-03-04")
        assertEquals(JsonNull, gast["membership_id"])
        assertEquals("Erna Ersatz", gast["name"]!!.jsonPrimitive.content)
        assertEquals("1980-03-04", gast["birth_date"]!!.jsonPrimitive.content)
        val familie = substitutePayload(8, "ignoriert", null)
        assertEquals(8, familie["membership_id"]!!.jsonPrimitive.int)
        assertNull(familie["name"])
        assertNull(familie["birth_date"])
    }
}
