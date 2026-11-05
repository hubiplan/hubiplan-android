package de.hubiplan.app.ui

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.hubiplan.app.data.AdminClient
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.TodoItem
import de.hubiplan.app.data.obj
import de.hubiplan.app.tr
import kotlinx.coroutines.launch

/** Inhalte bleiben nur in der entsperrten Ansicht, ohne lokale Speicherung. */
@Composable
fun TodoListScreen(client: AdminClient, errors: AdminErrors, reloadKey: Int, onNew: () -> Unit, onLock: () -> Unit, onOpen: (Int) -> Unit) {
    var rows by remember(client) { mutableStateOf<List<TodoItem>?>(null) }
    var error by remember(client) { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(client, reloadKey, reload) {
        rows = null
        error = null
        try { rows = client.todos().items } catch (e: ApiException) { error = errors.message(e) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !done, onClick = { done = false }, label = { Text(tr("Offen")) })
            FilterChip(selected = done, onClick = { done = true }, label = { Text(tr("Erledigt")) })
        }
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onNew) { Text(tr("Neue Aufgabe")) }
            TextButton(onClick = { reload++ }) { Text(tr("Aktualisieren")) }
        }
        TextButton(onClick = onLock, modifier = Modifier.padding(horizontal = 8.dp)) { Text(tr("Verwaltung auf diesem Telefon sperren")) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true).padding(16.dp)) }
        val list = rows
        if (list == null && error == null) Loading()
        if (list != null) {
            val visible = list.filter { (it.status == "done") == done }
            if (visible.isEmpty()) Text(tr("Keine Aufgaben in dieser Ansicht."), modifier = Modifier.padding(16.dp))
            LazyColumn {
                items(visible, key = { it.id }) { task ->
                    ListItem(
                        headlineContent = { Text(task.title) },
                        overlineContent = { Text(if (task.shared) tr("Geteilt") else tr("Privat")) },
                        supportingContent = {
                            Text(task.dueLabel.ifBlank { tr("ohne Frist") }, color = if (task.highlight || task.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.openRow { onOpen(task.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun TodoScreen(client: AdminClient, id: Int, errors: AdminErrors, reloadKey: Int, onBack: () -> Unit, onChanged: OnChanged) {
    var task by remember(client, id) { mutableStateOf<TodoItem?>(null) }
    var error by remember(client, id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(client, id, reloadKey, reload) {
        task = null
        error = null
        try { task = client.todo(id) } catch (e: ApiException) { error = errors.message(e) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text(tr("‹ Zurück")) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
        if (task == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) TextButton(onClick = { reload++ }, enabled = !busy) { Text(tr("Erneut laden")) }
        task?.let { current ->
            Text(current.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
            Text(if (current.status == "done") tr("Erledigt") else tr("Offen"))
            Text(if (current.shared) tr("Geteilt") else tr("Privat"))
            Text(current.dueLabel.ifBlank { tr("ohne Frist") })
            if (current.dueTime != null) Text(tr("Zeitzone: {zone}", "zone" to current.timezone))
            if (current.description.isNotBlank()) Text(current.description)
            if (current.localNote.isNotBlank()) {
                Text(tr("Lokale Notizen"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
                Text(current.localNote)
            }
            if (current.mayComplete) Button(enabled = !busy, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val changed = client.completeTodo(current.id, current.version, current.status != "done")
                        task = changed.value
                        onChanged(if (changed.value.status == "done") tr("Aufgabe erledigt") else tr("Aufgabe wieder geöffnet"), changed.changeSet)
                    } catch (e: ApiException) {
                        // Nach Entzug oder Versionskonflikt keine alten Daten weiter anbieten.
                        task = null
                        error = errors.message(e)
                    } finally { busy = false }
                }
            }) { Text(if (current.status == "done") tr("Wieder öffnen") else tr("Erledigen")) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/** Leere Frist ist erlaubt, Uhrzeit nur zusammen mit einem gültigen Datum. */
fun todoInput(title: String, description: String, date: String, time: String): kotlinx.serialization.json.JsonObject? {
    val due = if (date.isBlank()) null else parseGermanDate(date) ?: return null
    val at = if (time.isBlank()) null else parseTime(time)?.toString() ?: return null
    if (title.isBlank() || title.trim().length > 200 || description.length > 50000 || at != null && due == null) return null
    return obj("title" to title.trim(), "description" to description, "due_date" to due, "due_time" to at, "timezone" to "Europe/Berlin")
}

@Composable
fun TodoCreateScreen(client: AdminClient, errors: AdminErrors, onBack: () -> Unit, onCreated: (Int, String?) -> Unit) {
    // Keine privaten Entwürfe in SavedState oder auf Datenträger schreiben.
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    rememberFormDraft(title, description, date, time, busy = busy, retainOnDispose = false)
    val fields = rememberServerFields()
    val body = todoInput(title, description, date, time)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text(tr("‹ Zurück")) }
        Text(tr("Neue Aufgabe"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
        Text(tr("Die neue Aufgabe ist zunächst privat. Teilen, Anhänge und Serien verwaltest du im Browser."))
        ServerTextField("title", fields, title, { title = it.take(200) }, label = { Text(tr("Titel")) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        ServerTextField("description", fields, description, { description = it.take(50000) }, label = { Text(tr("Beschreibung")) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        ServerTextField("due_date", fields, date, { date = it }, label = { Text(tr("Frist (TT.MM.JJJJ, optional)")) }, singleLine = true, enabled = !busy,
            trailingIcon = { if (!busy) DatePickButton(date) { date = it } }, modifier = Modifier.fillMaxWidth())
        ServerTextField("due_time", fields, time, { time = it }, label = { Text(tr("Uhrzeit (optional)")) }, singleLine = true, enabled = !busy,
            trailingIcon = { if (!busy) TimePickButton(time) { time = it } }, modifier = Modifier.fillMaxWidth())
        Text(tr("Zeitzone: {zone}", "zone" to "Europe/Berlin"), style = MaterialTheme.typography.bodySmall)
        if (body == null && (date.isNotBlank() || time.isNotBlank())) Text(tr("Bitte Frist und Uhrzeit prüfen. Eine Uhrzeit braucht ein Datum."))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true)) }
        Button(enabled = !busy && body != null, onClick = {
            val values = body ?: return@Button
            busy = true
            error = null
            scope.launch {
                try {
                    val created = client.createTodo(values)
                    onCreated(created.value.id, created.changeSet)
                } catch (e: ApiException) { error = fields.receive(e, errors) } finally { busy = false }
            }
        }) { Text(tr("Anlegen")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
