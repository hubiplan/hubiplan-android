package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Startseite der App in einem Abruf (`GET /api/portal/<Zugang>/start/`, HUBIplan `docs/app.md`):
 * der nächste Termin, was gerade wartet, und die Kacheln. Ältere HUBIplan-Stände kennen den
 * Abruf nicht (404); dann baut die App die Seite aus der Terminliste.
 */
@Serializable
data class StartSeite(
    @SerialName("next_event") val nextEvent: Occurrence? = null,
    val todo: List<StartTodo> = emptyList(),
    val tiles: List<HomeTile> = emptyList(),
)

/** Eine Zeile unter „Für dich zu tun“. [target] nennt den Bereich; unbekannte zeigt die App nicht. */
@Serializable
data class StartTodo(
    val key: String,
    val title: String = "",
    val count: Int = 0,
    val target: String = "",
)
