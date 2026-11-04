package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TodoItem(
    val id: Int,
    val title: String = "",
    val description: String = "",
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("due_time") val dueTime: String? = null,
    val timezone: String = "Europe/Berlin",
    val status: String = "open",
    val shared: Boolean = false,
    val version: Int = 1,
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_complete") val canComplete: Boolean? = null,
    @SerialName("per_unit") val perUnit: Boolean = false,
    @SerialName("local_note") val localNote: String = "",
    @SerialName("due_label") val dueLabel: String = "",
    val highlight: Boolean = false,
    val overdue: Boolean = false,
) {
    val mayComplete: Boolean get() = canEdit && canComplete != false && !perUnit
}

@Serializable
data class TodoList(val items: List<TodoItem> = emptyList())

fun canReadTodos(permissions: List<String>): Boolean =
    "todos.view" in permissions || "todos.edit" in permissions

/** Nur Kennungen der eigenen Instanz; Berechtigung und Inhalt kommen frisch vom Server. */
data class TodoTarget(val scope: String, val unitId: Int, val todoId: Int) {
    val unitKey: String get() = "$scope:$unitId"

    fun permittedBy(unit: AdminUnit): Boolean = scope in setOf("u", "d") && unit.key == unitKey && canReadTodos(unit.permissions)

    companion object {
        fun create(scope: String?, unitId: Int?, todoId: Int?): TodoTarget? =
            if (scope in setOf("u", "d", "p") && unitId != null && unitId > 0 && todoId != null && todoId > 0)
                TodoTarget(scope!!, unitId, todoId) else null

        fun fromPush(org: Int?, department: Int?, todo: Int?, access: Int? = null): TodoTarget? {
            // Mitgliederzugang ist eine eigene Sicht; nie auf Verwaltungsrechte zurückfallen.
            if (access != null) return create("p", access, todo)
            if (org == null || org <= 0 || department != null && department <= 0) return null
            return create(if (department == null) "u" else "d", department ?: org, todo)
        }
    }
}


/** Mitgliederrechte sind unabhängig von Verwaltungsrollen und bleiben explizit. */
@Serializable
data class MemberTodo(
    val id: Int,
    val title: String = "",
    val description: String = "",
    val status: String = "open",
    val version: Int = 1,
    @SerialName("due_label") val dueLabel: String = "",
    @SerialName("can_complete") val canComplete: Boolean = false,
    @SerialName("can_claim") val canClaim: Boolean = false,
    @SerialName("claimed_by_me") val claimedByMe: Boolean = false,
    val claimed: Boolean = false,
    val attachments: List<MemberTodoFile> = emptyList(),
)

@Serializable
data class MemberTodoFile(val id: Int, val name: String = "", val available: Boolean = false)

@Serializable
data class MemberTodoList(val items: List<MemberTodo> = emptyList())

fun TodoTarget.memberAccess(accesses: List<PortalAccess>): PortalAccess? =
    if (scope == "p" && unitId > 0 && todoId > 0) accesses.firstOrNull { it.id == unitId } else null
