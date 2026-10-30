package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Verwaltung in der App, Schritt B2: Antworten wie im Browser (nur benötigte Felder)

@Serializable
data class MemberRow(
    val id: Int,
    @SerialName("last_name") val lastName: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("member_type") val memberType: String? = null,
    val status: String = "",
    val departments: List<String> = emptyList(),
    @SerialName("photo_url") val photoUrl: String? = null,
    val city: String = "",
)

@Serializable
data class MemberPage(val count: Int = 0, val next: String? = null, val results: List<MemberRow> = emptyList())

@Serializable
data class TypeAssignment(
    @SerialName("member_type") val memberType: Int,
    @SerialName("member_type_name") val memberTypeName: String = "",
    @SerialName("valid_from") val validFrom: String = "",
    @SerialName("valid_until") val validUntil: String? = null,
)

@Serializable
data class DepartmentMembership(
    val id: Int,
    val department: Int,
    @SerialName("department_name") val departmentName: String = "",
    @SerialName("valid_from") val validFrom: String = "",
    @SerialName("valid_until") val validUntil: String? = null,
)

@Serializable
data class MemberPeriod(
    val id: Int,
    @SerialName("entry_date") val entryDate: String = "",
    @SerialName("exit_date") val exitDate: String? = null,
    @SerialName("exit_reason_label") val exitReasonLabel: String? = null,
)

@Serializable
data class Choice(val value: String, val label: String)

@Serializable
data class MemberPhotos(val approved: PhotoVersion? = null, val pending: PhotoVersion? = null)

@Serializable
data class MemberDetail(
    val id: Int,
    @SerialName("last_name") val lastName: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("birth_date") val birthDate: String? = null,
    val gender: String = "",
    @SerialName("club_number") val clubNumber: String = "",
    @SerialName("pays_fee_self") val paysFeeSelf: Boolean = true,
    @SerialName("info_opt_out_at") val infoOptOutAt: String? = null,
    val contact: Contact? = null,
    val status: String = "",
    @SerialName("current_type") val currentType: TypeAssignment? = null,
    val periods: List<MemberPeriod> = emptyList(),
    val departments: List<DepartmentMembership> = emptyList(),
    val photo: MemberPhotos? = null,
    @SerialName("exit_reasons") val exitReasons: List<Choice> = emptyList(),
) {
    val name: String get() = "$firstName $lastName".trim()

    /** Heute laufende Zugehörigkeiten (Untereinheiten). */
    fun currentDepartments(today: String): List<DepartmentMembership> =
        departments.filter { it.validFrom <= today && (it.validUntil == null || it.validUntil >= today) }
}

@Serializable
data class MemberTypeItem(
    val id: Int,
    val name: String,
    @SerialName("is_archived") val isArchived: Boolean = false,
)

@Serializable
data class DepartmentItem(
    val id: Int,
    val name: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
) {
    val label: String get() = displayName.ifBlank { name }
}

@Serializable
data class Preview(val title: String = "", val consequences: List<String> = emptyList())

@Serializable
data class AdminChangeRequest(
    val id: Int,
    @SerialName("member_id") val memberId: Int,
    @SerialName("member_name") val memberName: String = "",
    val data: Map<String, JsonElement> = emptyMap(),
    val previous: Map<String, JsonElement>? = null,
    val message: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CardCheck(
    val status: String = "",
    @SerialName("status_label") val statusLabel: String = "",
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("member_type") val memberType: String? = null,
    val club: String? = null,
    val departments: List<String> = emptyList(),
    @SerialName("photo_url") val photoUrl: String? = null,
    val membership: Int? = null,
)

@Serializable
data class BirthdayRow(
    @SerialName("member_id") val memberId: Int,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val date: String = "",
    val age: Int = 0,
)

@Serializable
data class JubileeRow(
    @SerialName("member_id") val memberId: Int,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val kind: String = "",
    val years: Int = 0,
    val date: String = "",
    @SerialName("organization_short") val organization: String = "",
)

@Serializable
data class Rows<T>(val results: List<T> = emptyList())

// --- B2b: Ehrungen, Zusatzfelder, Bankverbindung, Aufnahme ---

@Serializable
data class AwardItem(
    val id: Int,
    val title: Int,
    @SerialName("title_name") val titleName: String = "",
    @SerialName("valid_from") val validFrom: String = "",
    @SerialName("valid_until") val validUntil: String? = null,
    val note: String = "",
)

@Serializable
data class TitleItem(val id: Int, val name: String, @SerialName("is_archived") val isArchived: Boolean = false)

@Serializable
data class FieldDef(
    val id: Int,
    val label: String,
    @SerialName("field_type") val fieldType: String = "text",
    val choices: List<String> = emptyList(),
    val repeatable: Boolean = false,
    @SerialName("help_text") val helpText: String = "",
)

@Serializable
data class FieldEntry(val definition: FieldDef, val value: JsonElement? = null)

@Serializable
data class MandateItem(
    val id: Int,
    val reference: String = "",
    val holder: String = "",
    val iban: String? = null,
    @SerialName("iban_masked") val ibanMasked: String = "",
    val bic: String = "",
    @SerialName("signed_on") val signedOn: String? = null,
    val status: String = "",
    @SerialName("status_label") val statusLabel: String = "",
)

@Serializable
data class DuplicateItem(
    val id: Int,
    @SerialName("last_name") val lastName: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("already_member_here") val alreadyMemberHere: Boolean = false,
)
