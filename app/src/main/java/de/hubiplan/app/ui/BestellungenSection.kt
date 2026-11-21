package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.MemberOrder
import de.hubiplan.app.data.OrderArticle
import de.hubiplan.app.data.OrderCampaign
import de.hubiplan.app.data.OrderCampaignList
import de.hubiplan.app.data.OrderInput
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.tr
import de.hubiplan.app.trn
import kotlinx.coroutines.launch

/** Kurzstand rechts neben „Bestellungen“. */
internal fun ordersSummary(list: OrderCampaignList): String = when {
    list.openCount > 0 -> trn("1 offen, jetzt bestellen", "{n} offen, jetzt bestellen", list.openCount)
    list.campaigns.isEmpty() -> tr("Gerade keine Sammelbestellung")
    else -> tr("Vereinskleidung und mehr gemeinsam bestellen")
}

/** Grenzen eines Artikels in einem Satz (wie im Browser). */
internal fun orderLimitText(a: OrderArticle): String = listOfNotNull(
    a.maxPerMember?.let { tr("höchstens {n} je Mitglied", "n" to it) },
    a.maxTotal?.let { total ->
        a.left?.let { tr("noch {n} von {m} verfügbar", "n" to it, "m" to total) } ?: tr("insgesamt {n}", "n" to total)
    },
).joinToString(" · ")

/** Zeilen zum Senden aus den Eingaben je Artikel und Auswahl (leere und 0 fallen weg). */
internal fun orderInputs(articles: List<OrderArticle>, values: Map<String, Int>): List<OrderInput> =
    articles.flatMap { a ->
        (a.variants.ifEmpty { listOf("") }).mapNotNull { v ->
            val n = values[orderKey(a.id, v)] ?: 0
            if (n > 0) OrderInput(a.id, v, n) else null
        }
    }

/** Summe der Eingaben in Euro als Text („60.00“), leer ohne Preise. */
internal fun orderTotal(articles: List<OrderArticle>, inputs: List<OrderInput>): String? {
    var total = java.math.BigDecimal.ZERO
    var priced = false
    inputs.forEach { line ->
        val price = articles.firstOrNull { it.id == line.article }?.price?.toBigDecimalOrNull() ?: return@forEach
        priced = true
        total += price * java.math.BigDecimal(line.quantity)
    }
    return if (priced) total.setScale(2).toPlainString() else null
}

internal fun orderKey(article: Int, variant: String) = "$article|$variant"

/** Bestellte Positionen als Text, je Zeile „2 × Poloshirt (M)“. */
internal fun orderLinesText(order: MemberOrder): String = order.lines.joinToString("\n") { l ->
    "${l.quantity} × ${l.name}" + if (l.variant.isNotEmpty()) " (${l.variant})" else ""
}

/**
 * Sammelbestellungen unter „Ich“ (HUBIplan Bestellungen): offene zuerst, bis zur Frist bestellen,
 * ändern und zurückziehen. Wie unter „Mein Bereich › Bestellungen“ im Browser.
 */
@Composable
internal fun BestellungenSection(access: PortalAccess, vm: AppViewModel) {
    var list by remember(access.id) { mutableStateOf<OrderCampaignList?>(null) }
    var error by remember(access.id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id) { mutableIntStateOf(0) }
    var notice by remember(access.id) { mutableStateOf<String?>(null) }
    var open by remember(access.id) { mutableStateOf<OrderCampaign?>(null) }
    var busy by remember { mutableStateOf(false) }
    var withdraw by remember { mutableStateOf<OrderCampaign?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(access.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            list = api.memberOrders(access.id)
            error = null
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    fun act(done: String?, action: suspend (de.hubiplan.app.data.Api) -> Unit) {
        val api = vm.api() ?: return
        if (busy) return
        busy = true
        notice = null
        error = null
        scope.launch {
            try {
                action(api)
                notice = done
            } catch (e: ApiException) {
                if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
            } finally {
                busy = false
            }
        }
    }
    SectionColumn {
        error?.let { ProblemNotice(it, onRetry = { attempt++ }, inset = false) }
        ActionNotice(notice) { notice = null }
        val l = list
        if (l == null) {
            if (error == null) Loading()
            return@SectionColumn
        }
        val current = open
        if (current != null) {
            OrderFormCard(
                current, busy,
                onSave = { lines, note ->
                    act(tr("Danke, deine Bestellung ist gespeichert.")) {
                        it.placeOrder(access.id, current.id, lines, note)
                        open = null
                        attempt++
                    }
                },
                onWithdraw = { withdraw = current },
                onCancel = { open = null },
            )
            return@SectionColumn
        }
        if (l.campaigns.isEmpty()) {
            Text(tr("Gerade läuft keine Sammelbestellung für dich."))
        }
        val (running, done) = l.campaigns.partition { it.state == "open" }
        running.forEach { c ->
            CampaignCard(c, busy) {
                act(null) { open = it.memberOrderCampaign(access.id, c.id) }
            }
        }
        if (done.isNotEmpty()) {
            Text(tr("Beendete Sammelbestellungen"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            done.forEach { c -> CampaignCard(c, busy, onOrder = null) }
        }
    }
    withdraw?.let { c ->
        AlertDialog(
            onDismissRequest = { withdraw = null },
            title = { Text(tr("Bestellung zurückziehen?")) },
            text = { Text(c.title) },
            confirmButton = {
                TextButton(onClick = {
                    withdraw = null
                    act(tr("Bestellung zurückgezogen.")) {
                        it.withdrawOrder(access.id, c.id)
                        open = null
                        attempt++
                    }
                }) { Text(tr("Bestellung zurückziehen")) }
            },
            dismissButton = { TextButton(onClick = { withdraw = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

@Composable
private fun CampaignCard(c: OrderCampaign, busy: Boolean, onOrder: (() -> Unit)?) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            val line = listOfNotNull(
                c.from.ifBlank { null },
                c.deadline?.let {
                    if (c.state == "open") tr("Bestellen bis {date}", "date" to formatPlainDate(it))
                    else tr("Bestellfrist vorbei ({date})", "date" to formatPlainDate(it))
                },
                if (c.clubPays) tr("Der Verein bezahlt") else null,
            ).joinToString(" · ")
            Text(line, style = MaterialTheme.typography.bodySmall)
            val order = c.order
            if (order != null) {
                Text(tr("Deine Bestellung"), style = MaterialTheme.typography.labelLarge)
                Text(orderLinesText(order))
                if (!c.clubPays && order.total != null) Text(tr("Summe: {x}", "x" to formatMoney(order.total)))
                order.invoice?.let { inv ->
                    val paid = inv.outstanding?.toBigDecimalOrNull()?.signum() == 0
                    Text(
                        tr("Rechnung {number}", "number" to inv.number) + if (paid) " · " + tr("bezahlt") else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (order.handedOut) Text(tr("Ausgegeben"), style = MaterialTheme.typography.bodySmall)
            } else if (c.state != "open") {
                Text(tr("Du hast nichts bestellt."), style = MaterialTheme.typography.bodySmall)
            }
            if (onOrder != null) {
                Button(onClick = onOrder, enabled = !busy) {
                    Text(if (order != null) tr("Bestellung ansehen oder ändern") else tr("Jetzt bestellen"))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderFormCard(
    c: OrderCampaign,
    busy: Boolean,
    onSave: (List<OrderInput>, String) -> Unit,
    onWithdraw: () -> Unit,
    onCancel: () -> Unit,
) {
    val values = remember(c.id) {
        mutableStateMapOf<String, Int>().apply {
            c.order?.lines?.forEach { put(orderKey(it.article, it.variant), it.quantity) }
        }
    }
    var note by remember(c.id) { mutableStateOf(c.order?.note ?: "") }
    val prices = !c.clubPays
    Column(verticalArrangement = Arrangement.spacedBy(Abstand.mittel)) {
        Text(c.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
        if (c.text.isNotBlank()) Text(c.text)
        if (!c.canOrder) {
            Text(tr("Die Bestellung lässt sich nicht mehr ändern. Bitte wende dich an deinen Verein."))
            OutlinedButton(onClick = onCancel) { Text(tr("Zurück")) }
            return@Column
        }
        c.articles.forEach { a ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Abstand.mittel), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    val price = if (prices && a.price != null) " · " + formatMoney(a.price) else ""
                    Text(a.name + price, style = MaterialTheme.typography.titleMedium)
                    if (a.text.isNotBlank()) Text(a.text, style = MaterialTheme.typography.bodySmall)
                    orderLimitText(a).ifBlank { null }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        a.variants.ifEmpty { listOf("") }.forEach { v ->
                            val key = orderKey(a.id, v)
                            val label = if (v.isEmpty()) tr("Menge") else v
                            val description = if (v.isEmpty()) {
                                tr("{name}: Menge", "name" to a.name)
                            } else {
                                tr("{name}, {label} {value}: Menge", "name" to a.name, "label" to a.variantLabel.ifBlank { tr("Auswahl") }, "value" to v)
                            }
                            OutlinedTextField(
                                value = values[key]?.takeIf { it > 0 }?.toString() ?: "",
                                onValueChange = { text -> values[key] = text.filter(Char::isDigit).take(2).toIntOrNull() ?: 0 },
                                label = { Text(label) },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.width(96.dp).semantics { contentDescription = description },
                            )
                        }
                    }
                    if (a.variants.isNotEmpty() && a.variantLabel.isNotBlank()) {
                        Text(tr("Auswahl: {label}", "label" to a.variantLabel), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(500) },
            label = { Text(tr("Bemerkung (optional)")) },
            placeholder = { Text(tr("z. B. Name zum Besticken")) },
            keyboardOptions = FREITEXT,
            modifier = Modifier.fillMaxWidth(),
        )
        val inputs = orderInputs(c.articles, values)
        if (prices) orderTotal(c.articles, inputs)?.let { Text(tr("Summe: {x}", "x" to formatMoney(it)), style = MaterialTheme.typography.titleMedium) }
        Button(onClick = { onSave(inputs, note) }, enabled = !busy && inputs.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (c.order != null) tr("Bestellung ändern") else tr("Bestellen"))
        }
        if (c.order != null) {
            OutlinedButton(onClick = onWithdraw, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Bestellung zurückziehen"))
            }
        }
        TextButton(onClick = onCancel) { Text(tr("Abbrechen")) }
    }
}
