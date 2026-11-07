package de.hubiplan.app

import de.hubiplan.app.data.CardDisplay
import de.hubiplan.app.data.ContactPersonItem
import de.hubiplan.app.data.Me
import de.hubiplan.app.data.MemberDetail
import de.hubiplan.app.data.MemberPage
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.Profile
import de.hubiplan.app.data.json
import de.hubiplan.app.ui.cardFirstLine
import de.hubiplan.app.ui.contactRoles
import de.hubiplan.app.ui.masterDataKeys
import de.hubiplan.app.ui.masterDataLabel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Organisationen als Mitglieder in der App (Konzept J7); alle Daten erfunden. */
class OrganisationenTest {
    private val katalog = Texte.parse(File("src/main/assets/texte/en.json").readText())

    @After
    fun deutsch() = Texte.setzeFuerTest("de", katalog)

    @Test
    fun listeZeigtOrganisationMitZusatz() {
        val page = json.decodeFromString<MemberPage>(
            """
            {"count":2,"next":null,"results":[
              {"id":1,"kind":"organization","last_name":"Musterbau GmbH","first_name":"",
               "name_suffix":"Niederlassung Musterstadt","member_type":"Fördermitglied"},
              {"id":2,"kind":"person","last_name":"Muster","first_name":"Erika"}]}
            """.trimIndent(),
        )
        assertTrue(page.results[0].isOrganization)
        assertEquals("Musterbau GmbH – Niederlassung Musterstadt", page.results[0].listName)
        assertEquals("Muster, Erika", page.results[1].listName)
    }

    @Test
    fun aeltereAntwortOhneArt() {
        val page = json.decodeFromString<MemberPage>("""{"count":1,"results":[{"id":3,"last_name":"Muster","first_name":"Max"}]}""")
        assertFalse(page.results[0].isOrganization)
        assertEquals("Muster, Max", page.results[0].listName)
    }

    @Test
    fun akteMitAnsprechpartnern() {
        val d = json.decodeFromString<MemberDetail>(
            """
            {"id":5,"kind":"organization","last_name":"Musterbau GmbH","first_name":"",
             "organization":{"name_suffix":"","legal_form":"gmbh","legal_form_label":"GmbH",
               "register":"HRB 12345","vat_id":"","website":"https://musterbau.example"},
             "contact_persons":[{"id":9,"display_name":"Max Muster","function":"Geschäftsführer",
               "contact_email":"max@musterbau.example","contact_phone":"","is_primary":true,
               "receives_mail":true,"receives_invoices":false,"represents":true,
               "access_email":"max@musterbau.example","linked_member":null}],
             "contact_for":[],
             "billing_address":{"name":"Musterbau GmbH","attention":"Buchhaltung","street":"Musterweg 1",
               "postal_code":"12345","city":"Musterstadt","country":"","email":"","order_reference":""}}
            """.trimIndent(),
        )
        assertEquals("Musterbau GmbH", d.name)
        assertEquals("GmbH", d.organization?.legalFormLabel)
        assertEquals("Buchhaltung", d.billingAddress?.attention)
        assertEquals(
            "Geschäftsführer · Hauptansprechpartner · vertritt in Versammlungen · erhält Rundschreiben",
            contactRoles(d.contactPersons[0]),
        )
        assertEquals("", contactRoles(ContactPersonItem(1)))
    }

    @Test
    fun personOhneOrganisationsangaben() {
        val d = json.decodeFromString<MemberDetail>("""{"id":6,"last_name":"Muster","first_name":"Erika","organization":null}""")
        assertFalse(d.isOrganization)
        assertNull(d.organization)
        assertEquals("Erika Muster", d.name)
    }

    @Test
    fun stammdatenOhneVornameUndGeburtsdatum() {
        val org = masterDataKeys(organization = true)
        assertFalse("first_name" in org)
        assertFalse("birth_date" in org)
        assertTrue("last_name" in org && "email" in org)
        assertTrue("first_name" in masterDataKeys(organization = false))
        assertEquals("Name der Organisation", masterDataLabel("last_name", true))
        assertEquals("Nachname", masterDataLabel("last_name", false))
    }

    @Test
    fun kontextwahlZeigtOrganisation() {
        val me = json.decodeFromString<Me>(
            """
            {"user":{"email":"max@musterbau.example","display":"Max Muster"},
             "device":{"id":1,"name":"Testgerät"},
             "portal":[
              {"id":1,"club_name":"Schützenverein Musterstadt","first_name":"Max","last_name":"Muster",
               "member_kind":"organization","member_name":"Musterbau GmbH"},
              {"id":2,"club_name":"Schützenverein Musterstadt","first_name":"Max","last_name":"Muster"}]}
            """.trimIndent(),
        )
        val (contact, own) = me.portal
        assertTrue(contact.isContact)
        assertEquals("Schützenverein Musterstadt: Musterbau GmbH (Ansprechpartner)", contact.label)
        assertFalse(own.isContact)
        assertEquals("Schützenverein Musterstadt", own.label)
        Texte.setzeFuerTest("en", katalog)
        assertEquals("Schützenverein Musterstadt: Musterbau GmbH (contact person)", contact.label)
    }

    @Test
    fun ausweisDerOrganisation() {
        val org = CardDisplay(kind = "organization", lastName = "Musterbau GmbH")
        assertEquals("Organisationsmitglied", cardFirstLine(org))
        assertEquals("Musterbau GmbH", org.name)
        val person = CardDisplay(firstName = "Erika", lastName = "Muster")
        assertEquals("Erika", cardFirstLine(person))
        assertEquals("Erika Muster", person.name)
    }

    @Test
    fun profilDerOrganisation() {
        val p = json.decodeFromString<Profile>(
            """{"kind":"organization","display_name":"Musterbau GmbH – Niederlassung Musterstadt","first_name":"","last_name":"Musterbau GmbH"}""",
        )
        assertTrue(p.isOrganization)
        assertEquals("Musterbau GmbH – Niederlassung Musterstadt", p.name)
    }
}
