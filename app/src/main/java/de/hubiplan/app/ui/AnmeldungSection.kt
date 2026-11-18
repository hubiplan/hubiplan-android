package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import de.hubiplan.app.Texte
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.EventDetail
import de.hubiplan.app.data.MemberRegistration
import de.hubiplan.app.data.MyRegistration
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.data.RegistrationChoice
import de.hubiplan.app.data.RegistrationGap
import de.hubiplan.app.data.RegistrationPrice
import de.hubiplan.app.data.registrationMissing
import de.hubiplan.app.data.registrationPayload
import de.hubiplan.app.data.registrationSum
import de.hubiplan.app.tr
import de.hubiplan.app.trk
import kotlinx.coroutines.launch

// Verbindliche Anmeldung mit Teilnahmegebühr am Termin, wie im Mitgliederbereich (HUBIplan A1/A2).

private val english: Boolean get() = Texte.sprache == "en"

/** Gast im Formular: Name, Tarif, Geburtsdatum (Eingabe wie im Feld, „31.12.2010“). */
private class GuestRow(name: String = "", priceId: Int? = null) {
    var name by mutableStateOf(name)
    var priceId by mutableStateOf(priceId)
    var birth by mutableStateOf("")
}

@Composable
internal fun RegistrationSection(access: PortalAccess, e: EventDetail, vm: AppViewModel, onChanged: () -> Unit) {
    val brief = e.registration ?: return
    if (!brief.enabled || !brief.canRegister) return
    var data by remember(e.id) { mutableStateOf<MemberRegistration?>(null) }
    var error by remember(e.id) { mutableStateOf<Problem?>(null) }
    var notice by remember(e.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var cancelling by remember { mutableStateOf<MyRegistration?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(access.id, e.id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        error = null
        try {
            data = api.memberRegistration(access.id, e.id)
        } catch (x: ApiException) {
            if (x.status == 401) vm.deviceRevoked(x) else error = x.problem()
        }
    }

    fun run(done: String, action: suspend () -> MemberRegistration) {
        if (busy) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                data = action()
                notice = done
                onChanged()
                vm.syncSoon()
            } catch (x: ApiException) {
                if (x.status == 401) vm.deviceRevoked(x) else error = x.problem()
            } finally {
                busy = false
            }
        }
    }

    HorizontalDivider()
    Text(tr("Anmeldung"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    error?.let { ProblemNotice(it, onRetry = if (data == null) ({ attempt++ }) else null, inset = false) }
    ActionNotice(notice) { notice = null }
    val d = data
    if (d == null) {
        if (error == null) Loading()
        return
    }
    val offer = d.offer
    if (offer.stateText.isNotBlank()) Text(offer.stateText)
    offer.closesAt?.let { Text(tr("Anmeldeschluss: {date}", "date" to formatDate(it)), style = MaterialTheme.typography.bodySmall) }
    offer.free?.let { Text(tr("Noch {n} Plätze frei.", "n" to it), style = MaterialTheme.typography.bodySmall) }
    offer.prices.filter { it.isActive }.forEach { PriceLine(it) }

    d.mine.forEach { reg ->
        MyRegistrationCard(reg, canCancel = d.canCancel && reg.active, busy = busy) { cancelling = reg }
    }
    cancelling?.let { reg ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text(tr("Anmeldung {number} abmelden?", "number" to reg.number)) },
            text = {
                Text(
                    if (reg.invoice != null && (reg.invoice.paid.toBigDecimalOrNull()?.signum() ?: 0) > 0) {
                        tr("Die Rechnung wird storniert; den gezahlten Betrag erstattet der Verein.")
                    } else if (reg.invoice != null) {
                        tr("Die Rechnung wird storniert.")
                    } else {
                        tr("Du bist dann nicht mehr angemeldet.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    cancelling = null
                    val api = vm.api() ?: return@TextButton
                    run(tr("Du bist abgemeldet. Die Bestätigung kommt per E-Mail.")) { api.cancelRegistration(access.id, e.id, reg.id) }
                }) { Text(trk("anmeldung", "Abmelden")) }
            },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text(tr("Abbrechen")) } },
        )
    }

    val open = offer.state == "open"
    val free = d.persons.filter { !it.registered }
    if (!open || (free.isEmpty() && !offer.guestsAllowed)) return

    // Auswahl: Mitglied → Tarif; Geburtsdatum nur, wenn ein Alterstarif es braucht
    val chosen = remember(e.id) { mutableStateMapOf<Int, Int?>() }
    val births = remember(e.id) { mutableStateMapOf<Int, String>() }
    val guests = remember(e.id) { mutableStateListOf<GuestRow>() }
    var note by remember(e.id) { mutableStateOf("") }
    var terms by remember(e.id) { mutableStateOf(false) }
    var gap by remember(e.id) { mutableStateOf<RegistrationGap?>(null) }
    val memberPrices = offer.pricesFor(member = true)
    val guestPrices = offer.pricesFor(member = false)

    Text(tr("Personen anmelden"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
    free.forEach { person ->
        val on = chosen.containsKey(person.membershipId)
        LabeledCheckbox(person.name, on, { checked ->
            if (checked) chosen[person.membershipId] = memberPrices.singleOrNull()?.id else chosen.remove(person.membershipId)
        }, enabled = !busy && memberPrices.isNotEmpty())
        if (on) {
            Column(Modifier.padding(start = Abstand.gross)) {
                PriceChoice(memberPrices, chosen[person.membershipId], !busy) { chosen[person.membershipId] = it }
                val price = memberPrices.firstOrNull { it.id == chosen[person.membershipId] }
                if (price?.hasAge == true && !person.hasBirthDate) {
                    BirthField(births[person.membershipId].orEmpty()) { births[person.membershipId] = it }
                }
            }
        }
    }
    if (offer.guestsAllowed && guestPrices.isNotEmpty()) {
        guests.forEachIndexed { i, g ->
            Column(verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                OutlinedTextField(
                    value = g.name,
                    onValueChange = { g.name = it.take(200) },
                    label = { Text(tr("Gast {n}: Name", "n" to i + 1)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = TITELZEILE,
                    modifier = Modifier.fillMaxWidth(),
                )
                PriceChoice(guestPrices, g.priceId, !busy) { g.priceId = it }
                if (guestPrices.firstOrNull { it.id == g.priceId }?.hasAge == true) BirthField(g.birth) { g.birth = it }
                TextButton(onClick = { guests.removeAt(i) }, enabled = !busy) { Text(tr("Gast entfernen")) }
            }
        }
        OutlinedButton(
            onClick = { guests.add(GuestRow(priceId = guestPrices.singleOrNull()?.id)) },
            enabled = !busy && chosen.size + guests.size < offer.maxPersons,
        ) { Text(tr("Gast hinzufügen")) }
    }

    OutlinedTextField(
        value = note,
        onValueChange = { note = it.take(500) },
        label = { Text(tr("Bemerkung (optional)")) },
        enabled = !busy,
        keyboardOptions = TITELZEILE,
        modifier = Modifier.fillMaxWidth(),
    )
    if (offer.terms.isNotBlank()) {
        Text(tr("Teilnahmebedingungen"), style = MaterialTheme.typography.titleSmall)
        Text(offer.terms, style = MaterialTheme.typography.bodySmall)
        LabeledCheckbox(tr("Ich habe die Teilnahmebedingungen gelesen und akzeptiere sie."), terms, { terms = it }, enabled = !busy)
    }

    val persons = chosen.mapNotNull { (id, price) ->
        price?.let { RegistrationChoice(id, "", it, births[id]?.let(::parseGermanDate)) }
    } + guests.mapNotNull { g -> g.priceId?.let { RegistrationChoice(null, g.name, it, parseGermanDate(g.birth)) } }
    val unpriced = chosen.values.any { it == null } || guests.any { it.priceId == null }
    val sum = registrationSum(offer, persons)
    if (persons.isNotEmpty()) Text(tr("Summe: {sum}", "sum" to formatMoney(sum.toPlainString())), style = MaterialTheme.typography.titleSmall)
    offer.closesAt?.let { Text(tr("Abmelden ist bis {when} möglich.", "when" to formatDate(it)), style = MaterialTheme.typography.bodySmall) }
    gap?.let { Text(gapText(it, offer.maxPersons), color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
    if (unpriced) Text(tr("Bitte für jede Person einen Tarif wählen."), style = MaterialTheme.typography.bodySmall)
    Button(
        onClick = {
            val needs = { c: RegistrationChoice ->
                val price = offer.prices.firstOrNull { it.id == c.priceId }
                val known = c.membershipId != null && d.persons.any { it.membershipId == c.membershipId && it.hasBirthDate }
                price?.hasAge == true && !known
            }
            gap = registrationMissing(offer, persons, needs, terms)
            if (gap != null || unpriced) return@Button
            val api = vm.api() ?: return@Button
            run(tr("Angemeldet. Die Bestätigung kommt per E-Mail.")) {
                api.register(access.id, e.id, registrationPayload(persons, note, terms)).also {
                    chosen.clear()
                    births.clear()
                    guests.clear()
                    note = ""
                    terms = false
                }
            }
        },
        enabled = !busy && persons.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (sum.signum() > 0) tr("Zahlungspflichtig anmelden") else tr("Verbindlich anmelden"))
    }
}

private fun gapText(gap: RegistrationGap, max: Int): String = when (gap) {
    RegistrationGap.NOBODY -> tr("Bitte mindestens eine Person anmelden.")
    RegistrationGap.TOO_MANY -> tr("Höchstens {n} Personen je Anmeldung.", "n" to max)
    RegistrationGap.NAME -> tr("Bitte für jede Person den Namen angeben.")
    RegistrationGap.BIRTH_DATE -> tr("Für diesen Tarif brauchen wir das Geburtsdatum.")
    RegistrationGap.TERMS -> tr("Bitte die Teilnahmebedingungen bestätigen.")
}

/** „Mitglieder: 29,00 € (Early Bird) · danach 35,00 €“ und Altersgrenzen. */
@Composable
private fun PriceLine(p: RegistrationPrice) {
    val now = p.current
    val head = p.label(english) + ": " + (now?.let { formatMoney(it.amount) } ?: tr("nicht mehr buchbar"))
    val step = now?.name?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    val next = p.next?.let { tr(" · danach {amount}", "amount" to formatMoney(it.amount)) }.orEmpty()
    Column {
        Text(head + step + next)
        val extra = listOfNotNull(
            ageText(p),
            when (p.applies) {
                "members" -> tr("nur Mitglieder")
                "guests" -> tr("nur Gäste")
                else -> null
            },
            now?.remaining?.let { tr("noch {n} zu diesem Preis", "n" to it) },
        )
        if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    }
}

private fun ageText(p: RegistrationPrice): String? = when {
    p.ageFrom != null && p.ageTo != null -> tr("{a} bis {b} Jahre", "a" to p.ageFrom, "b" to p.ageTo)
    p.ageFrom != null -> tr("ab {a} Jahren", "a" to p.ageFrom)
    p.ageTo != null -> tr("bis {b} Jahre", "b" to p.ageTo)
    else -> null
}

@Composable
private fun PriceChoice(prices: List<RegistrationPrice>, selected: Int?, enabled: Boolean, onPick: (Int) -> Unit) {
    prices.forEach { p ->
        LabeledRadio(
            p.label(english) + " · " + formatMoney(p.current?.amount),
            selected = selected == p.id,
            onClick = { onPick(p.id) },
            enabled = enabled,
        )
    }
}

@Composable
private fun BirthField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(tr("Geburtsdatum")) },
        singleLine = true,
        isError = value.isNotBlank() && parseGermanDate(value) == null,
        trailingIcon = { DatePickButton(value, onChange) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MyRegistrationCard(reg: MyRegistration, canCancel: Boolean, busy: Boolean, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
        Text(tr("Deine Anmeldung {number}", "number" to reg.number), style = MaterialTheme.typography.titleSmall)
        Text(paidText(reg.paidState), style = MaterialTheme.typography.bodySmall)
        reg.participants.forEach { p ->
            val price = if ((p.amount.toBigDecimalOrNull()?.signum() ?: 0) > 0) "${p.priceName}, ${formatMoney(p.amount)}" else p.priceName
            val mark = if (p.status == "active") "" else " (" + tr("abgemeldet") + ")"
            Text("${p.name}: $price$mark")
        }
        reg.invoice?.let { inv ->
            Text(
                tr("Rechnung {number} über {total}, zahlbar bis {due}", "number" to inv.number, "total" to formatMoney(inv.total), "due" to formatDate(inv.dueDate)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (canCancel) TextButton(onClick = onCancel, enabled = !busy) { Text(trk("anmeldung", "Abmelden")) }
    }
}

private fun paidText(state: String): String = when (state) {
    "free" -> tr("kostenlos")
    "paid" -> tr("bezahlt")
    "partly" -> tr("teilweise bezahlt")
    "cancelled" -> tr("abgemeldet")
    else -> tr("offen")
}
