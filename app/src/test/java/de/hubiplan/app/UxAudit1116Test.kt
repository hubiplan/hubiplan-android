package de.hubiplan.app

import de.hubiplan.app.ui.shouldAskNotifications
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UX-Audit 16.11.2026: Benachrichtigungserlaubnis nach dem Verbinden (AND-01). */
class UxAudit1116Test {
    @Test
    fun fragt_ab_android_13_einmal_wenn_die_erlaubnis_fehlt() {
        assertTrue(shouldAskNotifications(sdk = 33, allowed = false, asked = false))
        assertTrue(shouldAskNotifications(sdk = 35, allowed = false, asked = false))
    }

    @Test
    fun fragt_nicht_doppelt_und_nicht_ohne_grund() {
        // schon gefragt: die Schalter unter „Mein Konto“ bleiben der Weg
        assertFalse(shouldAskNotifications(sdk = 34, allowed = false, asked = true))
        // schon erlaubt
        assertFalse(shouldAskNotifications(sdk = 34, allowed = true, asked = false))
        // vor Android 13 gibt es die Erlaubnis nicht
        assertFalse(shouldAskNotifications(sdk = 32, allowed = false, asked = false))
    }
}
