package de.hubiplan.app.ui

/** Identität der Rechnungsauswahl, einschließlich expliziter Neuabfragen. */
internal data class InvoiceQuery(
    val unitId: Int,
    val search: String,
    val openOnly: Boolean,
    val reload: Int,
    val attempt: Int,
)
