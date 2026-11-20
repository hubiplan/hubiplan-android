package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pinnwand im Mitgliederbereich (GET /api/portal/<Zugang>/pinboard/, Beschreibung in
 * hubiplan/docs/app.md): kurze Beiträge des Vereins und der eigenen Untereinheiten. Kein Chat;
 * wer in HUBIplan schreiben darf, bekommt [PinboardList.targets]. Nichts wird gespeichert.
 */
@Serializable
data class PinboardList(
    val notices: List<PinNotice> = emptyList(),
    val targets: List<PinTarget> = emptyList(),
    @SerialName("max_photos") val maxPhotos: Int = 6,
    @SerialName("max_text") val maxText: Int = 2000,
) {
    /** Zahl der seit dem letzten Lesen neuen Beiträge */
    val newCount: Int get() = notices.count { it.new }
}

@Serializable
data class PinTarget(val department: Int? = null, val name: String = "")

@Serializable
data class PinNotice(
    val id: Int,
    val text: String = "",
    val unit: String = "",
    val department: Int? = null,
    val from: String = "",
    val author: String = "",
    val pinned: Boolean = false,
    @SerialName("visible_until") val visibleUntil: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    val pushed: Boolean = false,
    val version: Int = 1,
    @SerialName("can_edit") val canEdit: Boolean = false,
    val photos: List<PinPhoto> = emptyList(),
    val new: Boolean = false,
)

@Serializable
data class PinPhoto(val id: Int, val url: String, val width: Int = 0, val height: Int = 0)
