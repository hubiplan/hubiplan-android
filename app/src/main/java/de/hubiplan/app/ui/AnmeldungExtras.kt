package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import de.hubiplan.app.Texte
import de.hubiplan.app.data.Guardian
import de.hubiplan.app.data.RegistrationExtras
import de.hubiplan.app.data.RegistrationOffer
import de.hubiplan.app.tr
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// Zusatzleistungen, Zusatzfragen, Allergien und Erziehungsberechtigte bei der Anmeldung
// (HUBIplan A5, F11, F13), wie im Mitgliederbereich.

private val english: Boolean get() = Texte.sprache == "en"

/** Auswahl der Zusatzleistungen und Antworten auf die Zusatzfragen, je Person oder Anmeldung. */
@Composable
internal fun ExtrasEditor(
    offer: RegistrationOffer,
    per: String,
    value: RegistrationExtras,
    enabled: Boolean,
    minor: Boolean = false,
    onChange: (RegistrationExtras) -> Unit,
) {
    val addons = offer.addonsFor(per)
    val questions = offer.questionsFor(per)
    if (addons.isEmpty() && questions.isEmpty() && !minor) return
    Column(verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
        addons.forEach { a ->
            Text(a.label(english), style = MaterialTheme.typography.titleSmall)
            if (!a.required) {
                LabeledRadio(tr("nicht gewünscht"), selected = value.addons[a.id] == null, onClick = {
                    onChange(value.copy(addons = value.addons - a.id))
                }, enabled = enabled)
            }
            a.choices.filter { it.isActive }.forEach { c ->
                val price = if (c.price.signum() > 0) " (+" + formatMoney(c.amount) + ")" else ""
                val full = if (c.full) " – " + tr("ausgebucht") else ""
                LabeledRadio(
                    c.label(english) + price + full,
                    selected = value.addons[a.id] == c.id,
                    onClick = { onChange(value.copy(addons = value.addons + (a.id to c.id))) },
                    enabled = enabled && (!c.full || value.addons[a.id] == c.id),
                )
            }
        }
        questions.forEach { q ->
            val label = q.label(english) + if (q.required && q.kind != "health") "" else " " + tr("(optional)")
            val answer = value.answers[q.id]
            when (q.kind) {
                "health" -> {
                    OutlinedTextField(
                        value = value.health,
                        onValueChange = { onChange(value.copy(health = it.take(500))) },
                        label = { Text(label) },
                        supportingText = {
                            Text(tr("Nur für die Planung des Essens; sieht nur die Verwaltung, wird 14 Tage nach der Veranstaltung gelöscht."))
                        },
                        enabled = enabled,
                        keyboardOptions = TITELZEILE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                "yesno" -> {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    listOf(true to tr("ja"), false to tr("nein")).forEach { (yes, text) ->
                        LabeledRadio(text, selected = answer?.booleanOrNull == yes, onClick = {
                            onChange(value.copy(answers = value.answers + (q.id to JsonPrimitive(yes))))
                        }, enabled = enabled)
                    }
                }
                "choice" -> {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    q.choices.forEach { c ->
                        LabeledRadio(c, selected = answer != null && answer.isString && answer.content == c, onClick = {
                            onChange(value.copy(answers = value.answers + (q.id to JsonPrimitive(c))))
                        }, enabled = enabled)
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = answer?.takeIf { it.isString }?.content.orEmpty(),
                        onValueChange = { onChange(value.copy(answers = value.answers + (q.id to JsonPrimitive(it.take(500))))) },
                        label = { Text(label) },
                        singleLine = true,
                        enabled = enabled,
                        keyboardOptions = TITELZEILE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (minor) GuardianFields(value.guardians.firstOrNull() ?: Guardian(), enabled) { g ->
            onChange(value.copy(guardians = listOf(g)))
        }
    }
}

@Composable
private fun GuardianFields(g: Guardian, enabled: Boolean, onChange: (Guardian) -> Unit) {
    Text(tr("Erziehungsberechtigte"), style = MaterialTheme.typography.titleSmall)
    Text(
        tr("Unter 18: Bitte Name, Telefon (während der Veranstaltung erreichbar) und E-Mail angeben; die Anschrift nur, wenn sie abweicht."),
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = g.name,
        onValueChange = { onChange(g.copy(name = it.take(200))) },
        label = { Text(tr("Name")) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = TITELZEILE,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = g.phone,
        onValueChange = { onChange(g.copy(phone = it.take(50))) },
        label = { Text(tr("Telefon")) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = g.email,
        onValueChange = { onChange(g.copy(email = it.take(254))) },
        label = { Text(tr("E-Mail")) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = g.address,
        onValueChange = { onChange(g.copy(address = it.take(1000))) },
        label = { Text(tr("Anschrift (optional)")) },
        enabled = enabled,
        keyboardOptions = TITELZEILE,
        modifier = Modifier.fillMaxWidth(),
    )
}
