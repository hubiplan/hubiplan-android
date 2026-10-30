package de.hubiplan.app

import de.hubiplan.app.data.MemberDetail
import de.hubiplan.app.data.DepartmentMembership
import de.hubiplan.app.data.adminPath
import de.hubiplan.app.data.json
import de.hubiplan.app.data.obj
import de.hubiplan.app.data.FieldDef
import de.hubiplan.app.data.FieldEntry
import de.hubiplan.app.ui.displayValue
import de.hubiplan.app.ui.fieldChanges
import de.hubiplan.app.ui.fieldText
import de.hubiplan.app.ui.formatIban
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import de.hubiplan.app.ui.masterDataChanges
import de.hubiplan.app.ui.parseGermanDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdminFormatTest {
    @Test
    fun datumEingeben() {
        assertEquals("2026-12-31", parseGermanDate("31.12.2026"))
        assertEquals("2026-02-01", parseGermanDate(" 1.2.2026 "))
        assertEquals("2026-02-01", parseGermanDate("2026-02-01"))
        assertNull(parseGermanDate("30.02.2026"))
        assertNull(parseGermanDate(""))
    }

    @Test
    fun nurGeaenderteStammdaten() {
        val before = mapOf("city" to "Neuss", "birth_date" to "1980-05-17", "phone" to "")
        val after = mapOf("city" to " Neuss ", "birth_date" to "17.05.1980", "phone" to "0211 1")
        assertEquals(mapOf("phone" to "0211 1"), masterDataChanges(before, after))
        assertEquals(mapOf("birth_date" to "1981-05-17"), masterDataChanges(before, after + mapOf("phone" to "", "birth_date" to "17.5.1981")))
        assertNull(masterDataChanges(before, mapOf("birth_date" to "17.13.1980")))
    }

    @Test
    fun werteLesbar() {
        assertEquals("17.05.1980", displayValue("birth_date", "1980-05-17"))
        assertEquals("–", displayValue("city", ""))
    }

    @Test
    fun bildpfadFuerVerwaltung() {
        assertEquals(
            "/api/app/v1/admin/u/7/members/3/photo/?status=pending&v=1",
            adminPath("/api/orgs/7/members/3/photo/?status=pending&v=1", 7),
        )
        // fremde Einheit bleibt unverändert (Server lehnt ab)
        assertEquals("/api/orgs/8/members/3/photo/", adminPath("/api/orgs/8/members/3/photo/", 7))
    }

    @Test
    fun laufendeUntereinheiten() {
        val d = MemberDetail(
            id = 1,
            departments = listOf(
                DepartmentMembership(1, 10, "Jägerzug", "2020-01-01", "2026-10-31"),
                DepartmentMembership(2, 11, "Grenadiere", "2026-11-01", null),
            ),
        )
        assertEquals(listOf("Jägerzug"), d.currentDepartments("2026-10-30").map { it.departmentName })
        assertEquals(listOf("Grenadiere"), d.currentDepartments("2026-11-01").map { it.departmentName })
    }

    @Test
    fun akteLesen() {
        val d = json.decodeFromString(
            MemberDetail.serializer(),
            """{"id":5,"first_name":"Anna","last_name":"Beispiel","current_type":{"id":1,"member_type":2,"member_type_name":"aktiv","valid_from":"2015-01-01"},"photo":{"approved":null,"pending":{"url":"/x","thumbnail_url":"/y"}},"exit_reasons":[{"value":"resignation","label":"Kündigung"}],"unbekannt":1}""",
        )
        assertEquals("Anna Beispiel", d.name)
        assertEquals("aktiv", d.currentType?.memberTypeName)
        assertEquals("/x", d.photo?.pending?.url)
    }

    @Test
    fun jsonBauen() {
        assertEquals("""{"a":1,"b":true,"c":"x","d":null}""", obj("a" to 1, "b" to true, "c" to "x", "d" to null).toString())
    }

    @Test
    fun zusatzfelder() {
        val date = FieldEntry(FieldDef(1, "Taufe", "date"), JsonPrimitive("2001-04-03"))
        val number = FieldEntry(FieldDef(2, "Schuh", "number"), JsonPrimitive(43.5))
        val flag = FieldEntry(FieldDef(3, "Fahnenträger", "bool"), JsonPrimitive(true))
        val list = FieldEntry(FieldDef(4, "Orden", repeatable = true), null)
        assertEquals("03.04.2001", fieldText(date.definition, date.value))
        assertEquals("43,5", fieldText(number.definition, number.value))
        assertEquals("ja", fieldText(flag.definition, flag.value))
        val entries = listOf(date, number, flag, list)
        val unchanged = mapOf(1 to "03.04.2001", 2 to "43,5", 3 to "ja", 4 to "x")
        assertEquals(emptyMap<String, Any>(), fieldChanges(entries, unchanged).first)
        val (changes, fault) = fieldChanges(entries, mapOf(1 to "4.4.2001", 2 to "44", 3 to ""))
        assertNull(fault)
        assertEquals(mapOf("1" to JsonPrimitive("2001-04-04"), "2" to JsonPrimitive("44"), "3" to JsonNull), changes)
        assertEquals("Taufe: bitte als TT.MM.JJJJ eingeben.", fieldChanges(entries, mapOf(1 to "4.13.2001")).second)
    }

    @Test
    fun ibanInGruppen() {
        assertEquals("DE89 3704 0044 0532 0130 00", formatIban("DE89370400440532013000"))
    }
}
