package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kacheln der Übersicht (GET/PUT /api/portal/<Zugang>/home-tiles/ für Mitglieder,
 * /api/app/v1/admin/u/<Einheit>/home-tiles/ für die Verwaltung; hubiplan docs/app.md). Dieselben
 * Kacheln wie auf der Startseite im Browser: Der Server liefert fertige Texte und ob gerade ein
 * Anlass besteht. Ohne eigene Wahl („bei Anlass“) erscheint eine Kachel nur mit Anlass.
 */
@Serializable
data class HomeTiles(
    val scope: String = "",
    val tiles: List<HomeTile> = emptyList(),
) {
    /** Kacheln, die die Übersicht zeigt (Reihenfolge wie vom Server) */
    val visible: List<HomeTile> get() = tiles.filter { it.shown }
}

@Serializable
data class HomeTile(
    val key: String,
    val title: String = "",
    val description: String = "",
    /** „auto“ (bei Anlass), „on“ (immer), „off“ (nie) */
    val state: String = TileState.AUTO,
    val urgent: Boolean = false,
    val shown: Boolean = false,
    val content: TileContent? = null,
)

@Serializable
data class TileContent(
    val figures: List<TileFigure> = emptyList(),
    val rows: List<TileRow> = emptyList(),
    val text: String = "",
    val link: String? = null,
    @SerialName("link_label") val linkLabel: String = "",
    /** Aktueller Anlass; leer = keiner */
    val reason: String = "",
)

@Serializable
data class TileFigure(val label: String = "", val value: String = "", val tone: String? = null)

@Serializable
data class TileRow(val title: String = "", val detail: String = "", val link: String? = null, val tone: String? = null)

object TileState {
    const val AUTO = "auto"
    const val ON = "on"
    const val OFF = "off"
    val ALL = listOf(AUTO, ON, OFF)
}
