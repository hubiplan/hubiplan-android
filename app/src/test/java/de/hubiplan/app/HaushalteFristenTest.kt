package de.hubiplan.app

import de.hubiplan.app.data.AdminUnit
import de.hubiplan.app.data.CompanyMember
import de.hubiplan.app.data.CompanyOverview
import de.hubiplan.app.data.MemberDetail
import de.hubiplan.app.data.MemberQualification
import de.hubiplan.app.data.ObligationItem
import de.hubiplan.app.data.ObligationList
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.QualificationRow
import de.hubiplan.app.data.json
import de.hubiplan.app.ui.IchBereich
import de.hubiplan.app.ui.companyMemberLine
import de.hubiplan.app.ui.householdRoleLabel
import de.hubiplan.app.ui.ichBereiche
import de.hubiplan.app.ui.obligationLine
import de.hubiplan.app.ui.qualificationStatusLabel
import de.hubiplan.app.ui.qualificationUntil
import de.hubiplan.app.ui.showsObligations
import de.hubiplan.app.ui.showsQualifications
import de.hubiplan.app.ui.sortObligations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Haushalte in der Akte, Qualifikationen, Fristen und Firmen im Mitgliederbereich. Daten erfunden. */
class HaushalteFristenTest {
    private val katalog = Texte.parse(File("src/main/assets/texte/en.json").readText())

    @After
    fun deutsch() = Texte.setzeFuerTest("de", katalog)

    @Test
    fun haushaltInDerAkte() {
        val d = json.decodeFromString(
            MemberDetail.serializer(),
            """{"id": 7, "first_name": "Erika", "last_name": "Muster", "households": [
                {"id": 3, "name": "Familie Muster", "kind": "family", "role": "adult", "since": "2026-01-01",
                 "others": [{"member_id": 8, "name": "Lisa Muster", "role": "child"}]},
                {"id": 4, "name": "Musterbau GmbH", "kind": "company", "role": "adult", "since": "2026-02-01", "others": []}
            ]}""",
        )
        assertEquals(listOf("Familie Muster", "Musterbau GmbH"), d.households.map { it.name })
        assertFalse(d.households[0].isCompany)
        assertTrue(d.households[1].isCompany)
        assertEquals(8, d.households[0].others.single().memberId)
        assertEquals("Kind", householdRoleLabel("child"))
        // Ältere Server ohne Haushalte
        assertTrue(json.decodeFromString(MemberDetail.serializer(), """{"id": 7}""").households.isEmpty())
    }

    @Test
    fun bereicheNurMitModulUndRecht() {
        val unit = AdminUnit(1, "Verein", permissions = listOf("members.view", "obligations.view"), stufen = true, modules = listOf("qualifications", "obligations"))
        assertTrue(showsQualifications(unit))
        assertTrue(showsObligations(unit))
        assertFalse(showsQualifications(unit.copy(modules = listOf("obligations"))))
        assertFalse(showsObligations(unit.copy(permissions = listOf("members.view"))))
        assertFalse(showsObligations(unit.copy(scope = "d")))
    }

    @Test
    fun qualifikationen() {
        val rows = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(QualificationRow.serializer()),
            """[{"id": 1, "member": 7, "first_name": "Erika", "last_name": "Muster", "kind_name": "Erste Hilfe",
                 "acquired_on": "2024-05-01", "valid_until": "2026-12-01", "status": "expiring"}]""",
        )
        assertEquals("Erika Muster", rows.single().name)
        assertEquals("läuft bald ab", qualificationStatusLabel("expiring"))
        assertEquals("bis 01.12.2026", qualificationUntil("2026-12-01"))
        assertEquals("unbefristet", qualificationUntil(null))
        val own = json.decodeFromString(MemberQualification.serializer(), """{"id": 2, "kind_name": "Führungszeugnis", "inspection_only": true, "current": false}""")
        assertTrue(own.inspectionOnly)
        assertFalse(own.current)
    }

    @Test
    fun fristenUeberfaelligZuerst() {
        val list = json.decodeFromString(
            ObligationList.serializer(),
            """{"items": [
                {"id": 1, "title": "Versicherung prüfen", "due_date": "2027-03-01", "status": "planned"},
                {"id": 2, "title": "Steuererklärung", "due_date": "2026-05-31", "status": "overdue", "role_name": "Kassierer"},
                {"id": 3, "title": "Registergericht", "status": "election"}
            ], "suggestions": [], "roles": [], "registered": true, "can_edit": false}""",
        )
        assertEquals(listOf(2, 1, 3), sortObligations(list.items).map(ObligationItem::id))
        assertEquals("überfällig · Frist 31.05.2026 · Kassierer", obligationLine(list.items[1]))
    }

    @Test
    fun firmaImMitgliederbereich() {
        val access = PortalAccess(5, "Schützenverein Musterstadt", modules = emptyList(), company = true)
        assertTrue(IchBereich.MITARBEITER in ichBereiche(access))
        assertFalse(IchBereich.MITARBEITER in ichBereiche(access.copy(company = false)))
        val data = json.decodeFromString(
            CompanyOverview.serializer(),
            """{"can_report": true, "companies": [{"id": 3, "name": "Musterbau GmbH", "members": [
                {"link": 11, "name": "Ina Beispiel", "since": "2025-01-01", "until": null, "pays_via": true}]}]}""",
        )
        val m: CompanyMember = data.companies.single().members.single()
        assertEquals("seit 01.01.2025 · über die Firma", companyMemberLine(m))
    }

    @Test
    fun englisch() {
        Texte.setzeFuerTest("en", katalog)
        assertEquals("expiring soon", qualificationStatusLabel("expiring"))
        assertEquals("Child", householdRoleLabel("child"))
    }
}
