package de.hubiplan.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ein Meldungsweg für Rückmeldungen nach Aktionen im Mitgliederteil: kurze Hinweisleiste unten
 * im sichtbaren Bereich, wie in der Verwaltung, statt eines Textes am Seitenanfang, der nach
 * dem Rollen nicht zu sehen ist (UX-Audit 16.11. AND-08). Ladefehler einer Seite bleiben Flächen
 * ([ProblemNotice]).
 */
class ActionMessages(val host: SnackbarHostState, private val scope: CoroutineScope) {
    /** Zeigt [text]; läuft im Rahmen der Hauptansicht weiter, auch wenn die Detailansicht schließt. */
    fun show(text: String, error: Boolean = false) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(text, withDismissAction = true, duration = if (error) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }
}

val LocalActionMessages = staticCompositionLocalOf<ActionMessages?> { null }

/**
 * Rückmeldung nach einer Aktion. Mit Hinweisleiste (Hauptansicht) erscheint [text] dort und
 * [onShown] setzt den Zustand zurück; ohne (z. B. Vorschau) steht er wie bisher an Ort und Stelle.
 */
@Composable
fun ActionNotice(text: String?, modifier: Modifier = Modifier, error: Boolean = false, onShown: () -> Unit = {}) {
    if (text == null) return
    val messages = LocalActionMessages.current
    if (messages == null) {
        if (error) {
            Text(text, color = MaterialTheme.colorScheme.error, modifier = modifier.announce(urgent = true))
        } else {
            StatusNotice(text, modifier)
        }
        return
    }
    val shown by rememberUpdatedState(onShown)
    LaunchedEffect(text) {
        messages.show(text, error)
        shown()
    }
}
