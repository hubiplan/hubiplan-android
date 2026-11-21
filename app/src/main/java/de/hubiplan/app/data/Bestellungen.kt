package de.hubiplan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sammelbestellungen im Mitgliederbereich (GET /api/portal/<Zugang>/orders/, Beschreibung in
 * hubiplan/docs/app.md): Vereinskleidung, Festschrift oder reine Größenabfragen. Bis zur Frist
 * bestellen, ändern und zurückziehen. Preise fehlen, wenn der Verein bezahlt. Nichts wird
 * gespeichert.
 */
@Serializable
data class OrderCampaignList(val campaigns: List<OrderCampaign> = emptyList()) {
    /** Offene Sammelbestellungen ohne eigene Bestellung */
    val openCount: Int get() = campaigns.count { it.state == "open" && !it.ordered }
}

@Serializable
data class OrderCampaign(
    val id: Int,
    val title: String = "",
    val deadline: String? = null,
    /** draft | open | closed */
    val state: String = "open",
    val from: String = "",
    @SerialName("club_pays") val clubPays: Boolean = false,
    val ordered: Boolean = false,
    @SerialName("can_order") val canOrder: Boolean = false,
    val order: MemberOrder? = null,
    /** nur in der Einzelansicht */
    val text: String = "",
    val articles: List<OrderArticle> = emptyList(),
)

@Serializable
data class OrderArticle(
    val id: Int,
    val name: String = "",
    val text: String = "",
    /** Preis als Text („25.00“), leer ohne Preis oder wenn der Verein bezahlt */
    val price: String? = null,
    val variants: List<String> = emptyList(),
    @SerialName("variant_label") val variantLabel: String = "",
    @SerialName("max_per_member") val maxPerMember: Int? = null,
    @SerialName("max_total") val maxTotal: Int? = null,
    val left: Int? = null,
)

@Serializable
data class MemberOrder(
    val id: Int,
    val lines: List<MemberOrderLine> = emptyList(),
    val note: String = "",
    val total: String? = null,
    @SerialName("handed_out") val handedOut: Boolean = false,
    val invoice: OrderInvoice? = null,
)

@Serializable
data class MemberOrderLine(
    val article: Int,
    val name: String = "",
    val variant: String = "",
    val quantity: Int = 0,
    val amount: String? = null,
)

@Serializable
data class OrderInvoice(
    val id: Int,
    val number: String = "",
    val status: String = "",
    val outstanding: String? = null,
)

/** Eine Zeile der Bestellung zum Senden (leer insgesamt: zurückziehen) */
data class OrderInput(val article: Int, val variant: String, val quantity: Int)
