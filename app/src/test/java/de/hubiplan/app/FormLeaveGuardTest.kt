package de.hubiplan.app

import de.hubiplan.app.ui.FormLeaveGuard
import org.junit.Assert.*
import org.junit.Test

class FormLeaveGuardTest {
    @Test fun unveraendertSofortUndGeaendertErstNachEntscheidung() {
        val guard = FormLeaveGuard()
        var exits = 0
        var discarded = 0
        guard.request { exits++ }
        assertEquals(1, exits)
        guard.entries["buchung"] = FormLeaveGuard.Entry(true, false) { discarded++ }
        guard.request { exits++ }
        assertEquals(1, exits)
        guard.stay()
        assertTrue(guard.hasDraft)
        assertEquals(0, discarded)
        guard.request { exits++ }
        guard.discard()
        guard.discard()
        assertEquals(2, exits)
        assertEquals(1, discarded)
        assertFalse(guard.hasDraft)
    }
    @Test fun laufendeSchreibvorgaengeBlockierenWechselUndDoppeltesVerwerfen() {
        val guard = FormLeaveGuard()
        var exits = 0
        guard.entries["quittung"] = FormLeaveGuard.Entry(true, true) {}
        guard.request { exits++ }
        assertNull(guard.pending)
        guard.entries["quittung"] = FormLeaveGuard.Entry(true, false) {}
        guard.request { exits++ }
        guard.request { exits += 100 }
        guard.entries["quittung"] = FormLeaveGuard.Entry(true, true) {}
        guard.discard()
        assertEquals(0, exits)
        guard.entries["quittung"] = FormLeaveGuard.Entry(true, false) {}
        guard.discard()
        assertEquals(1, exits)
    }
}
