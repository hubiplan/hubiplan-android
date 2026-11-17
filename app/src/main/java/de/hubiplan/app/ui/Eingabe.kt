package de.hubiplan.app.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

// Bildschirmtastatur je Feldart (UX-Audit 16.11., AND-09): Jedes Feld öffnet die passende
// Tastatur, Freitext beginnt groß, die Eingabetaste führt zum nächsten Feld.

/** Freitext: Satzanfang groß. */
val FREITEXT = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/** Einzeilige Überschrift (Betreff, Titel): Satzanfang groß, Eingabetaste führt weiter. */
val TITELZEILE = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)

/** IBAN: Großbuchstaben und Ziffern, keine Wortvorschläge. */
val IBAN_TASTATUR = KeyboardOptions(
    capitalization = KeyboardCapitalization.Characters,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Ascii,
)

/** Tastatur zu einem Stammdatenfeld (Schlüssel wie in der Schnittstelle). */
fun keyboardTypeFor(field: String): KeyboardType = when (field) {
    "email" -> KeyboardType.Email
    "phone" -> KeyboardType.Phone
    "postal_code" -> KeyboardType.Number
    else -> KeyboardType.Text
}

/** Tastatur samt Großschreibung zu einem Stammdatenfeld: Namen und Orte beginnen groß. */
fun keyboardFor(field: String): KeyboardOptions = KeyboardOptions(
    keyboardType = keyboardTypeFor(field),
    capitalization = if (keyboardTypeFor(field) == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
    imeAction = ImeAction.Next,
)

/**
 * Passwortfeld mit „anzeigen/verbergen“ (AND-09). [imeAction] und [onImeAction] für die
 * Eingabetaste, etwa „Los“ am letzten Feld der Anmeldung.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    val toggle = if (shown) tr("Passwort verbergen") else tr("Passwort anzeigen")
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { shown = !shown }, modifier = Modifier.semantics { contentDescription = toggle }) {
                Text(if (shown) tr("Verbergen") else tr("Anzeigen"))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier,
    )
}
