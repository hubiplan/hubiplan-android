package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Beitragsnachweise im Mitgliederbereich (GET /api/portal/<Zugang>/fee-proofs/, Beschreibung in
 * hubiplan/docs/beitragsnachweise.md): Nachweis für einen ermäßigten Beitrag einreichen und den
 * Stand je Beitragsjahr sehen. Dateien bleiben nur bis zum Senden im Speicher.
 */
@Serializable
data class FeeProofOverview(
    val states: List<FeeYearState> = emptyList(),
    val proofs: List<FeeProof> = emptyList(),
)

@Serializable
data class FeeYearState(
    val discount: Int,
    @SerialName("discount_name") val discountName: String = "",
    val kinds: List<String> = emptyList(),
    val year: Int,
    @SerialName("year_label") val yearLabel: String = "",
    val due: String = "",
    val final: String = "",
    /** accepted, pending, question, open, missing */
    val state: String = "open",
    val applies: Boolean = false,
    val proof: FeeProof? = null,
) {
    /** Hier lässt sich (noch) etwas einreichen */
    val canSubmit: Boolean get() = state != "accepted"

    /** Braucht das Mitglied etwas zu tun? */
    val needsMe: Boolean get() = state in setOf("open", "question", "missing")
}

@Serializable
data class FeeProof(
    val id: Int,
    @SerialName("discount_name") val discountName: String = "",
    val year: Int = 0,
    @SerialName("year_label") val yearLabel: String = "",
    val kind: String = "",
    /** received, question, accepted, rejected */
    val status: String = "received",
    @SerialName("received_on") val receivedOn: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
    @SerialName("file_name") val fileName: String = "",
    val note: String = "",
)

/** Erlaubte Dateien wie auf dem Server (PDF oder Foto, höchstens 10 MB) */
const val FEE_PROOF_MAX_BYTES = 10 * 1024 * 1024
