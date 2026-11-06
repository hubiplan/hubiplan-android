package de.hubiplan.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.MemberTodo
import de.hubiplan.app.data.TodoTarget
import de.hubiplan.app.data.memberAccess
import de.hubiplan.app.tr
import kotlinx.coroutines.launch

/** Mitgliedersicht ohne Vorstandsschlüssel; Inhalte werden nie auf Datenträger gespeichert. */
@Composable
fun MemberTodosPane(main: Screen.Main, vm: AppViewModel) {
    val target by vm.selectedTodo.collectAsStateWithLifecycle()
    val portalTarget = target?.takeIf { it.scope == "p" }
    val access = if (portalTarget != null) portalTarget.memberAccess(main.me?.portal.orEmpty()) else main.access
    if (access == null) {
        Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.mittel)) {
            Text(tr("Die Aufgabe ist nicht mehr verfügbar oder nicht freigegeben."))
            TextButton(onClick = { vm.selectTodo(null) }) { Text(tr("‹ Zurück")) }
        }
        return
    }
    // Ein anderes Konto oder ein anderer Mitgliedszugang besitzt einen neuen Ansichtsbaum.
    key(main.account.server, main.account.deviceId, main.account.generation, access.id) {
        val api = remember(main.account) { Api(main.account.server, main.account.token) }
        // Helferdienste als zweite Ansicht im selben Reiter (höchstens fünf Ziele in der Leiste)
        var helpers by rememberSaveable(access.id) { mutableStateOf(false) }
        val withHelpers = access.helpersEnabled && portalTarget == null
        val switch: (@Composable () -> Unit)? = if (withHelpers) {
            { TaskSwitch(helpers) { helpers = it } }
        } else null
        if (withHelpers && helpers) MemberHelpers(api, access.id, main.account.server, vm) { switch?.invoke() }
        else MemberTaskList(api, access.id, portalTarget?.todoId, main.account.server, vm, switch)
    }
}

@Composable
private fun MemberTaskList(api: Api, access: Int, selected: Int?, server: String, vm: AppViewModel,
    switch: (@Composable () -> Unit)? = null) {
    var rows by remember { mutableStateOf<List<MemberTodo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reload++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(api, access, reload) {
        rows = null
        error = null
        try { rows = api.memberTodos(access).items }
        catch (e: ApiException) { if (e.status == 401) vm.deviceRevoked(e) else error = e.message }
    }
    ListDetail(
        hasDetail = selected != null,
        onBack = { vm.selectTodo(null) },
        emptyDetail = tr("Aufgabe auswählen"),
        list = {
            Column(Modifier.fillMaxSize()) {
                Text(tr("Aufgaben"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading().padding(Abstand.rand))
                switch?.invoke()
                Text(tr("Für dich freigegebene Aufgaben und offene Angebote deiner Einheit."), modifier = Modifier.padding(horizontal = Abstand.rand))
                Row(Modifier.padding(horizontal = Abstand.rand), horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                    FilterChip(selected = !done, onClick = { done = false }, label = { Text(tr("Offen")) })
                    FilterChip(selected = done, onClick = { done = true }, label = { Text(tr("Erledigt")) })
                    TextButton(onClick = { reload++ }) { Text(tr("Aktualisieren")) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true).padding(Abstand.rand)) }
                if (rows == null && error == null) Loading()
                rows?.let { current ->
                    val visible = current.filter { (it.status == "done") == done }
                    if (visible.isEmpty()) Text(tr("Keine Aufgaben in dieser Ansicht."), modifier = Modifier.padding(Abstand.rand))
                    LazyColumn {
                        items(visible, key = { it.id }) { todo ->
                            ListItem(headlineContent = { Text(todo.title) },
                                supportingContent = { Text(todo.dueLabel.ifBlank { tr("ohne Frist") }) },
                                modifier = Modifier.openRow { vm.selectTodo(TodoTarget("p", access, todo.id)) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        detail = {
            selected?.let { id -> MemberTaskDetail(api, access, id, server, reload, vm,
                onBack = { vm.selectTodo(null) }, onChanged = { reload++ },
                onInvalidated = { message -> rows = null; error = message }) }
        },
    )
}

@Composable
private fun MemberTaskDetail(api: Api, access: Int, id: Int, server: String, reloadKey: Int,
    vm: AppViewModel, onBack: () -> Unit, onChanged: () -> Unit, onInvalidated: (String) -> Unit) {
    var task by remember(api, access, id) { mutableStateOf<MemberTodo?>(null) }
    var error by remember(api, access, id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(api, access, id, reloadKey, reload) {
        task = null
        error = null
        try { task = api.memberTodo(access, id) }
        catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else {
                val message = e.message ?: tr("Die Aufgabe ist nicht mehr verfügbar oder nicht freigegeben.")
                error = message; onInvalidated(message)
            }
        }
    }
    var notice by remember(api, access, id) { mutableStateOf<String?>(null) }
    fun change(action: String) {
        val current = task ?: return
        if (busy) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                task = if (action == "claim" || action == "release")
                    api.claimMemberTodo(access, id, current.version, action == "claim")
                else api.completeMemberTodo(access, id, current.version, current.status != "done")
                notice = when (action) {
                    "claim" -> tr("Aufgabe übernommen.")
                    "release" -> tr("Übernahme freigegeben.")
                    else -> if (task?.status == "done") tr("Aufgabe erledigt.") else tr("Aufgabe wieder geöffnet.")
                }
                onChanged()
            } catch (e: ApiException) {
                task = null
                if (e.status == 401) vm.deviceRevoked(e) else {
                    val message = e.message ?: tr("Die Aufgabe ist nicht mehr verfügbar oder nicht freigegeben.")
                    error = message; onInvalidated(message)
                }
            } finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.mittel)) {
        TextButton(onClick = onBack, enabled = !busy) { Text(tr("‹ Zurück")) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
        if (task == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) TextButton(onClick = { reload++ }, enabled = !busy) { Text(tr("Erneut laden")) }
        notice?.let { StatusNotice(it) }
        task?.let { current ->
            Text(current.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
            Text(if (current.status == "done") tr("Erledigt") else tr("Offen"))
            Text(current.dueLabel.ifBlank { tr("ohne Frist") })
            if (current.description.isNotBlank()) Text(current.description)
            if (current.claimed) Text(if (current.claimedByMe) tr("Von dir übernommen") else tr("Bereits übernommen"))
            if (current.canClaim) Button(onClick = { change("claim") }, enabled = !busy) { Text(tr("Aufgabe übernehmen")) }
            if (current.claimedByMe && current.status == "open") TextButton(onClick = { change("release") }, enabled = !busy) { Text(tr("Übernahme freigeben")) }
            if (current.canComplete) Button(onClick = { change("status") }, enabled = !busy) { Text(if (current.status == "done") tr("Wieder öffnen") else tr("Erledigen")) }
            if (current.attachments.isNotEmpty()) {
                Text(tr("Anhänge"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
                current.attachments.forEach { Text(it.name) }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$server/mein-bereich/aufgaben?zugang=$access&aufgabe=$id"))) }) {
                    Text(tr("Mit Anhängen im Browser öffnen"))
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
