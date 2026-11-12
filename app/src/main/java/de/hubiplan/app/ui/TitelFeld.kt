package de.hubiplan.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.hubiplan.app.tr

/** Übliche Titel in der Auswahl; andere trägt man frei ein (wie im Browser, höchstens 30 Zeichen). */
val TITLES = listOf("Dr.", "Dr. Dr.", "Prof.", "Prof. Dr.")
const val TITLE_MAX_LENGTH = 30
private const val ANDERER = "\u0000anderer"

/** true, wenn der Titel nicht aus der Auswahlliste stammt (Textfeld zeigen). */
fun isFreeTitle(value: String): Boolean = value.isNotEmpty() && value !in TITLES

/**
 * Titel vor dem Namen (Dr., Prof. …): Auswahlliste oder frei eingetragen.
 * Steht in Anrede und Anschrift; die Vereinswebseite nennt ihn nur mit eigener Einstellung.
 */
@Composable
fun TitleInput(value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    // „anderer Titel …“ gewählt, aber noch nichts eingetragen: Textfeld trotzdem zeigen
    var other by rememberSaveable { mutableStateOf(isFreeTitle(value)) }
    val free = other || isFreeTitle(value)
    val options = listOf("" to tr("kein Titel")) + TITLES.map { it to it } + listOf(ANDERER to tr("anderer Titel …"))
    ChoicePicker(
        tr("Titel"),
        options,
        if (free) ANDERER else value,
        { v -> options.firstOrNull { it.first == v }?.second ?: v },
        enabled = enabled,
    ) { picked ->
        if (picked == ANDERER) {
            // bisherigen Titel zum Bearbeiten übernehmen
            other = true
        } else {
            other = false
            onChange(picked)
        }
    }
    if (free) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(TITLE_MAX_LENGTH)) },
            label = { Text(tr("Anderer Titel")) },
            placeholder = { Text(tr("z. B. Dipl.-Ing.")) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
