@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.hubiplan.app.ui

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.tr

class ServerFields {
    var messages by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    internal var revision by mutableIntStateOf(0)
    internal data class Target(val focus: FocusRequester, val scroll: BringIntoViewRequester)
    internal val targets = linkedMapOf<String, Target>()
    fun clear(field: String) { messages = messages - field }
    fun receive(e: ApiException, errors: AdminErrors? = null): String {
        val fallback = errors?.message(e) ?: e.message.orEmpty()
        messages = e.fieldErrors
        revision++
        return if (messages.isNotEmpty() && messages.keys.all { it in targets } && e.generalError == null) tr("Bitte prüfe die markierten Angaben.") else fallback
    }
    @Composable
    fun modifier(field: String, base: Modifier = Modifier): Modifier {
        val target = remember(field) { Target(FocusRequester(), BringIntoViewRequester()) }
        DisposableEffect(this, field) { targets[field] = target; onDispose { targets.remove(field) } }
        val message = messages[field]
        return base.focusRequester(target.focus).bringIntoViewRequester(target.scroll).semantics { if (message != null) error(message) }
    }
}

@Composable
fun rememberServerFields(): ServerFields {
    val fields = remember { ServerFields() }
    LaunchedEffect(fields.revision) {
        fields.targets.entries.firstOrNull { it.key in fields.messages }?.value?.let {
            it.focus.requestFocus()
            it.scroll.bringIntoView()
        }
    }
    return fields
}

/** Derselbe Fehler gehört sichtbar und in der Semantik an dasselbe benannte Feld. */
@Composable
fun ServerTextField(
    field: String,
    errors: ServerFields,
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
) {
    val message = errors.messages[field]
    OutlinedTextField(
        value = value,
        onValueChange = { errors.clear(field); onValueChange(it) },
        label = label,
        modifier = errors.modifier(field, modifier),
        singleLine = singleLine,
        minLines = minLines,
        isError = isError || message != null,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        supportingText = if (message != null) ({ Text(message) }) else supportingText,
    )
}
