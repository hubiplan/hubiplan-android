package de.hubiplan.app

import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.errorMessage
import de.hubiplan.app.data.parseFieldErrors
import org.junit.Assert.*
import org.junit.Test

class ServerFieldsTest {
    @Test fun zweiFelderMitGleichemTextBleibenUnterscheidbar() {
        val body = """{"email":["Ungültig."],"amount":["Ungültig."]}"""
        val error = ApiException(400, errorMessage(400, body), body)
        assertEquals(mapOf("email" to "Ungültig.", "amount" to "Ungültig."), error.fieldErrors)
        assertEquals("E-Mail: Ungültig. Betrag in €: Ungültig.", error.message)
    }
    @Test fun verschachtelteFelderUndMehrereMeldungenBleibenErhalten() {
        val fields = parseFieldErrors("""{"contact":{"email":["Format.","Pflicht."]},"lines":[{"amount":["Zu klein."]}],"non_field_errors":["Allgemein."]}""")
        assertEquals("Format. Pflicht.", fields["contact.email"])
        assertEquals("Zu klein.", fields["lines.0.amount"])
        assertFalse(fields.containsKey("non_field_errors"))
    }
    @Test fun allgemeineUndFeldfehlerBleibenGemeinsamSichtbar() {
        val body = """{"detail":"Bitte Daten prüfen.","email":["Ungültig."]}"""
        val error = ApiException(422, errorMessage(422, body), body)
        assertEquals("Bitte Daten prüfen.", error.generalError)
        assertEquals("Bitte Daten prüfen. E-Mail: Ungültig.", error.message)
    }
    @Test fun allgemeineUndKaputteAntwortenWerdenKeineFeldfehler() {
        assertTrue(parseFieldErrors("<html>Fehler</html>").isEmpty())
        assertTrue(parseFieldErrors("""{"detail":"Kein Zugriff.","status":403}""").isEmpty())
        assertEquals("Allgemein.", errorMessage(400, """{"non_field_errors":["Allgemein."]}"""))
    }
}
