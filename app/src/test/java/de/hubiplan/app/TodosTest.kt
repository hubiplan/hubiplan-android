package de.hubiplan.app

import de.hubiplan.app.data.AdminDepartment
import de.hubiplan.app.data.AdminUnit
import de.hubiplan.app.data.TodoItem
import de.hubiplan.app.data.TodoTarget
import de.hubiplan.app.data.MemberTodo
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.memberAccess
import de.hubiplan.app.data.decodeApi
import de.hubiplan.app.push.PushMessage
import de.hubiplan.app.ui.showsTodos
import de.hubiplan.app.ui.todoInput
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class TodosTest {
    @Test fun mitgliederHinweisHatEigenenZugangOhneVerwaltungsFallback() {
        val target = TodoTarget.fromPush(7, 9, 12, 5)!!
        assertEquals(TodoTarget("p", 5, 12), target)
        assertEquals(5, target.memberAccess(listOf(PortalAccess(5, "Musterverein")))?.id)
        assertNull(target.memberAccess(listOf(PortalAccess(7, "Andere Mitgliedschaft"))))
        assertFalse(target.permittedBy(AdminUnit(5, "Musterverein", permissions = listOf("todos.edit"))))
        assertFalse(target.permittedBy(AdminUnit(5, "Ungültige Verwaltungsart", permissions = listOf("todos.edit"), scope = "p")))
        assertNull(TodoTarget.fromPush(7, null, 12, 0))
        assertNull(TodoTarget.fromPush(7, null, 12, -1))
        assertNull(TodoTarget("u", 5, 12).memberAccess(listOf(PortalAccess(5, "Musterverein"))))
    }

    @Test fun mitgliederErledigungBrauchtNurExplizitesPortalrecht() {
        assertFalse(decodeApi<MemberTodo>("""{"id":12}""").canComplete)
        assertTrue(decodeApi<MemberTodo>("""{"id":12,"can_complete":true}""").canComplete)
        val offered = decodeApi<MemberTodo>("""{"id":12,"can_claim":true,"can_complete":false}""")
        assertTrue(offered.canClaim)
        assertFalse(offered.canComplete)
    }
    @Test fun nurAufgabenrechtErreichtBereichAuchInAbteilung() {
        assertTrue(showsTodos(AdminUnit(7, "Verein", permissions = listOf("todos.view"))))
        assertTrue(showsTodos(AdminDepartment(9, "Gruppe", permissions = listOf("todos.edit")).asUnit()))
        assertFalse(showsTodos(AdminUnit(7, "Verein", permissions = listOf("members.edit"))))
        assertFalse(showsTodos(AdminUnit(7, "Verein", permissions = emptyList())))
    }

    @Test fun pushPrueftKennungenUndGenauenBereich() {
        val message = PushMessage.parse("""{"v":1,"t":"todo","org":7,"department":9,"todo":12,"title":"Probe"}""")!!
        val target = TodoTarget.fromPush(message.org, message.department, message.todo)!!
        assertEquals(TodoTarget("d", 9, 12), target)
        assertTrue(target.permittedBy(AdminDepartment(9, "Gruppe", permissions = listOf("todos.view")).asUnit()))
        assertFalse(target.permittedBy(AdminUnit(9, "Andere Einheit", permissions = listOf("todos.view"))))
        assertFalse(target.permittedBy(AdminDepartment(9, "Gruppe", permissions = emptyList()).asUnit()))
        assertEquals("u:7", TodoTarget.fromPush(7, null, 12)?.unitKey)
        assertNull(TodoTarget.fromPush(null, 9, 12))
        assertNull(TodoTarget.fromPush(7, 0, 12))
        assertNull(TodoTarget.fromPush(7, null, -1))
        assertNull(TodoTarget.create("https://example.invalid", 7, 12))
        assertNull(PushMessage.parse("""{"t":"todo","todo":"falsch"}"""))
    }

    @Test fun leserUndZentraleVorlageKoennenNichtErledigen() {
        assertFalse(decodeApi<TodoItem>("""{"id":12,"can_edit":false,"can_complete":true}""").mayComplete)
        assertFalse(TodoItem(12, canEdit = true, canComplete = false).mayComplete)
        assertFalse(TodoItem(12, canEdit = true, canComplete = true, perUnit = true).mayComplete)
        assertTrue(decodeApi<TodoItem>("""{"id":12,"can_edit":true,"can_complete":true,"distributed":true}""").mayComplete)
        assertFalse(TodoItem(12).mayComplete)
    }

    @Test fun fristEingabeIstKalendertagMitFesterZone() {
        val noDate = todoInput("  Probe  ", "Notiz", "", "")!!
        assertEquals("Probe", noDate["title"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, noDate["due_date"])
        assertEquals(JsonNull, noDate["due_time"])
        val timed = todoInput("Probe", "", "29.2.2028", "9:30")!!
        assertEquals("2028-02-29", timed["due_date"]?.jsonPrimitive?.content)
        assertEquals("09:30", timed["due_time"]?.jsonPrimitive?.content)
        assertEquals("Europe/Berlin", timed["timezone"]?.jsonPrimitive?.content)
        assertNull(todoInput("Probe", "", "29.2.2027", ""))
        assertNull(todoInput("Probe", "", "", "9:30"))
        assertNull(todoInput("Probe", "", "31.12.2026", "25:00"))
        assertNull(todoInput(" ", "", "", ""))
        assertNull(todoInput("x".repeat(201), "", "", ""))
    }
}
