package de.hubiplan.app

import de.hubiplan.app.data.HelperDuty
import de.hubiplan.app.data.HelperOverview
import de.hubiplan.app.data.canCancel
import de.hubiplan.app.data.canSignup
import de.hubiplan.app.data.json
import de.hubiplan.app.ui.dutyLine
import de.hubiplan.app.ui.shiftFreeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Helferdienste im Mitgliederbereich (HUBIplan Schritt H5); alle Daten erfunden. */
class HelferdiensteTest {
    private val text = """
        {"plans":[{"id":3,"title":"Schützenfest 2027","unit":"","event_title":"Schützenfest",
          "signup":"self","cancel_days":2,"max_shifts":null,"note":"",
          "duties":[{"id":5,"name":"Ausschank","description":"","location":"Festzelt","min_age":18,
            "shifts":[
              {"id":11,"starts_at":"2027-08-28T18:00:00+02:00","ends_at":"2027-08-28T21:00:00+02:00",
               "needed":4,"taken":2,"note":"","names":[],"mine":null,"can_cancel":false,"past":false},
              {"id":12,"starts_at":"2027-08-28T21:00:00+02:00","ends_at":"2027-08-29T00:00:00+02:00",
               "needed":2,"taken":2,"note":"","names":[],"mine":40,"can_cancel":true,"past":false},
              {"id":13,"starts_at":"2027-08-27T18:00:00+02:00","ends_at":"2027-08-27T21:00:00+02:00",
               "needed":3,"taken":3,"note":"","names":[],"mine":null,"can_cancel":false,"past":false}]}]},
          {"id":4,"title":"Kirmes","signup":"admin","duties":[{"id":6,"name":"Aufbau",
            "shifts":[{"id":21,"starts_at":"2027-09-01T10:00:00+02:00","ends_at":"2027-09-01T12:00:00+02:00",
              "needed":5,"taken":0,"mine":null,"past":false}]}]}],
         "mine":[{"id":40,"plan":"Schützenfest 2027","duty":"Ausschank","location":"Festzelt",
           "starts_at":"2027-08-28T21:00:00+02:00","ends_at":"2027-08-29T00:00:00+02:00"},
          {"id":41,"plan":"Pfingsten","duty":"Kasse","location":"",
           "starts_at":"2027-05-16T10:00:00+02:00","ends_at":"2027-05-16T12:00:00+02:00"}]}
    """.trimIndent()

    @Test
    fun antwortLesen() {
        val data = json.decodeFromString<HelperOverview>(text)
        assertEquals(2, data.plans.size)
        assertEquals(18, data.plans[0].duties[0].minAge)
        assertEquals(2, data.plans[0].duties[0].shifts[0].free)
        assertEquals(40, data.plans[0].duties[0].shifts[1].mine)
        assertEquals("Kasse", data.mine[1].duty)
    }

    @Test
    fun eintragenNurBeiFreienPlaetzen() {
        val data = json.decodeFromString<HelperOverview>(text)
        val (open, mine, full) = data.plans[0].duties[0].shifts
        assertTrue(data.plans[0].canSignup(open))
        assertFalse(data.plans[0].canSignup(mine))
        assertFalse(data.plans[0].canSignup(full))
        // Nur die Verwaltung teilt ein
        assertFalse(data.plans[1].canSignup(data.plans[1].duties[0].shifts[0]))
    }

    @Test
    fun absageNurInDerFrist() {
        val data = json.decodeFromString<HelperOverview>(text)
        assertTrue(data.canCancel(40))
        assertFalse(data.canCancel(41))
    }

    @Test
    fun beschriftungen() {
        val data = json.decodeFromString<HelperOverview>(text)
        Texte.setzeFuerTest("de")
        assertEquals("2 von 4 frei", shiftFreeLabel(data.plans[0].duties[0].shifts[0]))
        assertEquals("besetzt", shiftFreeLabel(data.plans[0].duties[0].shifts[1]))
        assertEquals("Ausschank · Festzelt · ab 18 Jahren", dutyLine(data.plans[0].duties[0]))
        assertEquals("Aufbau", dutyLine(HelperDuty(6, "Aufbau")))
    }
}
