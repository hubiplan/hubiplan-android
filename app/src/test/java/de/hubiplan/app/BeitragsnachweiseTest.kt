package de.hubiplan.app

import de.hubiplan.app.data.FeeProofOverview
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.json
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.feeProofSummary
import de.hubiplan.app.ui.feeStateLabel
import de.hubiplan.app.ui.feeStateSentence
import de.hubiplan.app.ui.ichBereiche
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beitragsnachweise unter „Ich“ (HUBIplan docs/beitragsnachweise.md); alle Daten erfunden. */
class BeitragsnachweiseTest {
    private val text = """
        {"states":[
          {"discount":3,"discount_name":"Studierende","kinds":["Immatrikulationsbescheinigung"],
           "year":2099,"year_label":"2099","due":"2099-02-28","final":"2099-03-14","state":"open",
           "applies":true,"proof":null,"unknown_field":1},
          {"discount":3,"discount_name":"Studierende","kinds":[],"year":2098,"year_label":"2098",
           "due":"2098-02-28","final":"2098-02-28","state":"accepted","applies":true,
           "proof":{"id":7,"status":"accepted","valid_until":"2099-03-31","received_on":"2098-01-20"}}
        ],
        "proofs":[{"id":7,"discount_name":"Studierende","year":2098,"year_label":"2098","status":"accepted",
                   "received_on":"2098-01-20","valid_until":"2099-03-31","file_name":"","note":""}]}
    """.trimIndent()

    @Test
    fun liestStandUndNachweise() {
        val o = json.decodeFromString<FeeProofOverview>(text)
        val open = o.states[0]
        assertTrue(open.canSubmit && open.needsMe)
        assertEquals(listOf("Immatrikulationsbescheinigung"), open.kinds)
        assertEquals("Bitte bis 28.02.2099 einreichen (spätestens 14.03.2099).", feeStateSentence(open))
        val done = o.states[1]
        assertFalse(done.canSubmit)
        assertEquals("Anerkannt, gültig bis 31.03.2099.", feeStateSentence(done))
        assertEquals("Nachweis anerkannt", feeStateLabel(done.state))
        assertEquals(1, o.proofs.size)
    }

    @Test
    fun ichZeigtNachweiseNurMitErmaessigung() {
        val on = PortalAccess(5, "Schützenverein Musterstadt", feeProofsEnabled = true, feeProofsDue = 1)
        assertTrue(IchBereich.NACHWEISE in ichBereiche(on))
        assertFalse(IchBereich.NACHWEISE in ichBereiche(on.copy(feeProofsEnabled = false)))
        assertEquals("1 Nachweis fehlt", feeProofSummary(1))
        assertEquals("Nachweis für den ermäßigten Beitrag", feeProofSummary(0))
    }
}
