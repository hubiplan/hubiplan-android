package de.hubiplan.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import de.hubiplan.app.tr
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** Ein Entscheider für sichtbares/System-Zurück und alle Kontextwechsel. */
class FormLeaveGuard {
    internal data class Entry(val dirty: Boolean, val busy: Boolean, val discard: () -> Unit)
    internal val entries = mutableStateMapOf<Any, Entry>()
    var pending by mutableStateOf<(() -> Unit)?>(null)
        private set
    val hasDraft: Boolean get() = entries.values.any { it.dirty }
    val busy: Boolean get() = entries.values.any { it.busy }
    private var cancellation: () -> Unit = {}
    fun request(action: () -> Unit) = request(action, {})
    fun request(action: () -> Unit, onStay: () -> Unit) {
        // Laufende Schreibvorgänge weder abbrechen noch nach Bestätigung doppelt senden.
        if (busy || pending != null) return
        if (entries.values.any { it.dirty }) { cancellation = onStay; pending = action } else action()
    }
    fun stay() { pending = null; val cancel = cancellation; cancellation = {}; cancel() }
    fun discard() {
        if (busy) return
        val action = pending ?: return
        pending = null
        cancellation = {}
        entries.values.toList().forEach { it.discard() }
        entries.clear()
        action()
    }
}

val LocalFormLeaveGuard = staticCompositionLocalOf<FormLeaveGuard?> { null }

@Composable
fun FormLeaveGuardHost(content: @Composable (FormLeaveGuard) -> Unit) {
    val guard = remember { FormLeaveGuard() }
    val dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var redispatch by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = guard.hasDraft && !redispatch) { guard.request { redispatch = true } }
    androidx.compose.runtime.LaunchedEffect(redispatch) {
        if (redispatch) { dispatcher?.onBackPressed(); redispatch = false }
    }
    CompositionLocalProvider(LocalFormLeaveGuard provides guard) { content(guard) }
    if (guard.pending != null) AlertDialog(
        onDismissRequest = guard::stay,
        title = { Text(tr("Eingaben verwerfen?")) },
        text = { Text(tr("Was du in diesem Formular eingegeben hast, geht verloren.")) },
        confirmButton = { TextButton(onClick = guard::discard, enabled = !guard.busy) { Text(tr("Verwerfen")) } },
        dismissButton = { TextButton(onClick = guard::stay) { Text(tr("Weiter bearbeiten")) } },
    )
}

/** Ausgangswerte überstehen Activity-Neuanlage, ohne restaurierte Eingaben als leer anzusehen. */
@Composable
fun rememberFormDraft(vararg values: Any?, busy: Boolean = false, retainOnDispose: Boolean = true, onDiscard: () -> Unit = {}): () -> Unit {
    val value = JsonArray(values.map { JsonPrimitive(it?.toString()) }).toString()
    var baseline by rememberSaveable { mutableStateOf(value) }
    val guard = LocalFormLeaveGuard.current
    val token = rememberSaveable { java.util.UUID.randomUUID().toString() }
    val discard = rememberUpdatedState(onDiscard)
    val entry = remember(value != baseline, busy) { FormLeaveGuard.Entry(value != baseline, busy) { discard.value() } }
    SideEffect { guard?.entries?.set(token, entry) }
    DisposableEffect(guard, token, retainOnDispose) { onDispose {
        // Bei automatischer Verwaltungssperre bleibt der Entwurf im SaveableStateHolder.
        // Derselbe gespeicherte Token übernimmt ihn nach dem Entsperren wieder.
        val current = guard?.entries?.get(token)
        if (current?.dirty == true && retainOnDispose) guard.entries[token] = current.copy(busy = false)
        else guard?.entries?.remove(token)
    } }
    return {
        baseline = value
        guard?.entries?.set(token, FormLeaveGuard.Entry(false, false) { discard.value() })
    }
}
