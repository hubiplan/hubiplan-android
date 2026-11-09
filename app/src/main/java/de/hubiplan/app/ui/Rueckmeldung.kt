package de.hubiplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.Network
import de.hubiplan.app.tr

// Laden, Aktualisieren und Fehler in Nutzersprache (UX-Audit 08.11. A-02, A-03).

/** Was beim Laden schiefging, in Worten für Mitglieder; Technisches nur unter „Details“. */
data class Problem(val text: String, val offline: Boolean = false, val detail: String? = null)

/**
 * Fehler für die Oberfläche statt `e.message`: offline, Zeitüberschreitung und Serverfehler
 * unterscheiden sich; der Statuscode steht nur in [Problem.detail].
 */
fun ApiException.problem(): Problem = when (network) {
    Network.OFFLINE -> Problem(tr("Keine Verbindung zu HUBIplan. Bitte die Internetverbindung prüfen."), offline = true)
    Network.TIMEOUT -> Problem(tr("HUBIplan antwortet gerade nicht. Bitte später erneut versuchen."), detail = tr("Zeitüberschreitung"))
    null -> Problem(
        message?.takeIf { it.isNotBlank() } ?: tr("Etwas ist schiefgegangen. Bitte erneut versuchen."),
        detail = if (status > 0) tr("Antwort des Servers: {status}", "status" to status) else null,
    )
}

/** Nur der Text von [problem], z. B. für Felder oder Hinweiszeilen. */
fun ApiException.userText(): String = problem().text

/** „Offline – du siehst den Stand von 14:32 Uhr.“; ohne gespeicherten Stand nur „Offline“. */
fun offlineText(since: Long?, now: Long = System.currentTimeMillis()): String =
    if (since == null) {
        tr("Offline – keine Verbindung zu HUBIplan.")
    } else {
        tr("Offline – du siehst den Stand von {time}.", "time" to formatSince(since, now))
    }

/** Offline-Hinweis als neutrale Fläche mit Wolke, nicht in Fehlerrot (A-03). */
@Composable
fun OfflineBanner(since: Long?, onRetry: (() -> Unit)?, modifier: Modifier = Modifier, inset: Boolean = true) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(horizontal = if (inset) Abstand.rand else 0.dp, vertical = Abstand.klein),
    ) {
        Row(
            Modifier.padding(start = Abstand.mittel, end = Abstand.klein, top = Abstand.klein, bottom = Abstand.klein),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Abstand.mittel),
        ) {
            Icon(Symbole.Offline, contentDescription = null)
            Text(offlineText(since), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).announce())
            onRetry?.let {
                TextButton(onClick = it, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
                    Text(tr("Erneut laden"))
                }
            }
        }
    }
}

/** Fehler als Fläche mit Symbol, „Erneut laden“ und aufklappbaren Details; offline als [OfflineBanner].
 * [inset] = false in Ansichten, die selbst schon einen Seitenrand haben. */
@Composable
fun ProblemNotice(problem: Problem, onRetry: (() -> Unit)?, modifier: Modifier = Modifier, since: Long? = null, inset: Boolean = true) {
    if (problem.offline) {
        OfflineBanner(since, onRetry, modifier, inset)
        return
    }
    var details by remember(problem) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(horizontal = if (inset) Abstand.rand else 0.dp, vertical = Abstand.klein),
    ) {
        Column(Modifier.padding(start = Abstand.mittel, end = Abstand.klein, top = Abstand.mittel, bottom = Abstand.eng)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Abstand.mittel)) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Text(problem.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).announce(urgent = true))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                val colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)
                onRetry?.let { TextButton(onClick = it, colors = colors) { Text(tr("Erneut laden")) } }
                if (problem.detail != null) {
                    TextButton(onClick = { details = !details }, colors = colors) {
                        Text(if (details) tr("Details ausblenden") else tr("Details"))
                    }
                }
            }
            if (details) problem.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = Abstand.klein)) }
        }
    }
}

/** Ziehen zum Aktualisieren um eine Liste (Material 3 *Pull to refresh*, A-02). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) { content() }
}

/** Fläche ohne Liste (leer, Fehler), die sich trotzdem herunterziehen lässt. */
@Composable
fun PullableArea(center: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = height),
            verticalArrangement = if (center) Arrangement.Center else Arrangement.Top,
            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
            content = content,
        )
    }
}

/** Wie [Message], aber in einer [PullableArea]. */
@Composable
fun PullableMessage(text: String) {
    PullableArea(center = true) {
        Box(Modifier.padding(Abstand.gross)) { Text(text, style = MaterialTheme.typography.bodyLarge) }
    }
}

/**
 * „Stand: 14:32 Uhr“ über einer Liste. TalkBack bietet hier die Aktion „Aktualisieren“ an, als
 * Ersatz für die Zug-Geste (A-02).
 */
@Composable
fun FreshnessLine(loadedAt: Long, onRefresh: () -> Unit) {
    val action = tr("Aktualisieren")
    Text(
        tr("Stand: {time}", "time" to formatSince(loadedAt)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Abstand.rand, vertical = Abstand.eng)
            .semantics { customActions = listOf(CustomAccessibilityAction(action) { onRefresh(); true }) },
    )
}

/**
 * Daten einer Liste mit Stand und letztem Problem. Beim Aktualisieren bleibt die alte Liste
 * sichtbar; schlägt es fehl, steht darüber, von wann sie ist.
 */
@Stable
class Loadable<T> {
    var value by mutableStateOf<T?>(null)
    var problem by mutableStateOf<Problem?>(null)
    var loadedAt by mutableStateOf<Long?>(null)
    var refreshing by mutableStateOf(false)
        private set

    /** Wechselt bei jedem Neuladen; gehört in den Schlüssel des `LaunchedEffect`. */
    var tick by mutableIntStateOf(0)
        private set

    /** Neu laden mit sichtbarer Anzeige (Geste, „Erneut laden“). */
    fun refresh() {
        refreshing = true
        tick++
    }

    /** Still neu laden, z. B. nach einer eigenen Änderung oder beim Zurückkehren in die App. */
    fun reload() {
        tick++
    }

    /** Ganz neu beginnen (andere Auswahl): ohne alte Liste, mit Ladeanzeige. */
    fun reset() {
        value = null
        problem = null
        tick++
    }

    suspend fun load(onRevoked: (ApiException) -> Unit, fetch: suspend () -> T) {
        val mine = tick
        try {
            value = fetch()
            problem = null
            loadedAt = System.currentTimeMillis()
        } catch (e: ApiException) {
            if (e.status == 401) onRevoked(e) else problem = e.problem()
        } finally {
            if (tick == mine) refreshing = false
        }
    }
}

/**
 * Inhalt einer [Loadable]-Liste: Laden, Fehler, leer oder [content], alles zum Herunterziehen.
 * [header] steht immer darüber (Überschrift, Filter, Knöpfe).
 */
@Composable
fun <T> LoadableList(
    state: Loadable<T>,
    empty: String,
    isEmpty: (T) -> Boolean,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable (T) -> Unit,
) {
    Refreshable(state.refreshing, state::refresh) {
        Column(Modifier.fillMaxSize()) {
            header()
            val value = state.value
            val problem = state.problem
            when {
                value == null && problem == null -> Loading()
                value == null -> PullableArea { ProblemNotice(problem!!, onRetry = state::refresh) }
                else -> {
                    if (problem != null) {
                        ProblemNotice(problem, onRetry = state::refresh, since = state.loadedAt)
                    } else {
                        state.loadedAt?.let { FreshnessLine(it, state::refresh) }
                    }
                    if (isEmpty(value)) PullableMessage(empty) else content(value)
                }
            }
        }
    }
}
