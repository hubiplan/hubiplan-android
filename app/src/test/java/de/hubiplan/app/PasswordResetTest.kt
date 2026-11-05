package de.hubiplan.app

import de.hubiplan.app.data.passwordResetUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordResetTest {
    @Test fun verwendetDieselbeHttpsInstanzWieDieAnmeldung() {
        assertEquals("https://verein.example.de/passwort-vergessen", passwordResetUrl(" verein.example.de/ "))
        assertEquals("https://verein.example.de:8443/passwort-vergessen", passwordResetUrl("https://verein.example.de:8443/mein-konto?email=erika@example.org#privat"))
    }
    @Test fun lehntUngeeigneteAdressenAb() {
        for (input in listOf("", "http://verein.example.de", "javascript:alert(1)", "https://", "keine adresse", "https://verein.example.de:70000")) {
            assertNull(input, passwordResetUrl(input))
        }
    }
}
