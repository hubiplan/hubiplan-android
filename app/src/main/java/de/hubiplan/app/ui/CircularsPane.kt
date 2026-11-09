package de.hubiplan.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.CircularItem
import de.hubiplan.app.data.PortalAccess
import de.hubiplan.app.tr

/** Rundschreiben und Mitteilungen der Einheit (wie „Rundschreiben“ im Mitgliederbereich). */
@Composable
fun CircularsPane(access: PortalAccess, vm: AppViewModel) {
    val leave = LocalFormLeaveGuard.current
    // Liste bleibt beim Aktualisieren sichtbar; Ziehen lädt neu (UX-Audit 08.11. A-02)
    val circulars = remember(access.id) { Loadable<List<CircularItem>>() }
    var selected by rememberSaveable(access.id) { mutableStateOf<Int?>(null) }
    val canSend by vm.canSend.collectAsStateWithLifecycle()
    var notice by remember(access.id) { mutableStateOf<String?>(null) }
    val openRequest by vm.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest, access.id, leave?.busy) {
        val request = openRequest ?: return@LaunchedEffect
        if (request.first == access.id) {
            selected = request.second
            vm.openHandled()
        }
    }

    LaunchedEffect(access.id, circulars.tick) {
        val api = vm.api() ?: return@LaunchedEffect
        circulars.load(vm::deviceRevoked) { api.circulars(access.id) }
    }

    ListDetail(
        hasDetail = selected != null,
        onBack = { selected = null },
        emptyDetail = tr("Mitteilung auswählen"),
        list = {
            LoadableList(
                circulars,
                empty = tr("Noch keine Mitteilungen."),
                isEmpty = { it.isEmpty() },
                header = {
                    if (canSend) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Abstand.rand, vertical = Abstand.klein), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { if (selected != SEND) { notice = null; if (leave == null) selected = SEND else leave.request { selected = SEND } } }) { Text(tr("Kurzmitteilung senden")) }
                        }
                    }
                    notice?.let { StatusNotice(it, Modifier.padding(horizontal = Abstand.rand)) }
                },
            ) { list ->
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { c ->
                        CircularRow(c, c.id == selected) {
                            if (leave == null) selected = c.id else leave.request { selected = c.id }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        detail = {
            when (val id = selected) {
                null -> {}
                SEND -> SendPane(
                    vm,
                    onDone = { text ->
                        notice = text
                        selected = null
                        circulars.reload()
                    },
                    onCancel = { selected = null },
                )
                else -> CircularDetail(access, id, vm) { selected = null }
            }
        },
    )
}

/** Auswahlzustand gehört zur selben bedienbaren Zeile wie Betreff und Absender. */
@Composable
internal fun CircularRow(c: CircularItem, isSelected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.semantics { selected = isSelected }.clickable(role = Role.Button, onClick = onClick),
        colors = if (isSelected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ListItemDefaults.colors(),
        overlineContent = { Text(listOf(c.kindLabel, formatDate(c.sentAt)).filter { it.isNotBlank() }.joinToString(" · ")) },
        headlineContent = { Text(c.subject.ifBlank { tr("(ohne Betreff)") }) },
        supportingContent = { if (c.sender.isNotBlank()) Text(c.sender) },
    )
}

// Statt einer Nummer: Kurzmitteilung verfassen (Vorstand, Zugführung)
private const val SEND = -1

@Composable
private fun CircularDetail(access: PortalAccess, id: Int, vm: AppViewModel, onBack: () -> Unit) {
    var item by remember(access.id, id) { mutableStateOf<CircularItem?>(null) }
    var error by remember(access.id, id) { mutableStateOf<Problem?>(null) }
    var attempt by remember(access.id, id) { mutableStateOf(0) }
    LaunchedEffect(access.id, id, attempt) {
        val api = vm.api() ?: return@LaunchedEffect
        error = null
        try {
            item = api.circular(access.id, id)
        } catch (e: ApiException) {
            if (e.status == 401) vm.deviceRevoked(e) else error = e.problem()
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Abstand.rand),
        verticalArrangement = Arrangement.spacedBy(Abstand.mittel),
    ) {
        BackHead(item?.subject, onBack = onBack)
        val c = item
        error?.let { ProblemNotice(it, onRetry = if (c == null) ({ attempt++ }) else null, inset = false) }
        if (c == null) {
            if (error == null) Loading()
            return@Column
        }
        Text(c.subject, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
        Text(listOf(c.sender, formatDate(c.sentAt)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        CircularContent(c, (vm.screen.value as? Screen.Main)?.account?.server, access.id)
    }
}
