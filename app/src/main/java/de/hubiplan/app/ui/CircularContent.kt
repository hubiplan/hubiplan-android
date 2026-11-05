package de.hubiplan.app.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.hubiplan.app.data.CircularItem
import de.hubiplan.app.data.normalizeServer
import de.hubiplan.app.tr

/** Sitzung und Berechtigungen prüft weiterhin der normale Mitgliederbereich im Browser. */
internal fun circularWebUrl(server: String, access: Int, circular: Int): String? {
    if (access <= 0 || circular <= 0) return null
    return normalizeServer(server)?.let { "$it/mein-bereich/rundschreiben?zugang=$access&schreiben=$circular" }
}

internal data class CircularBlock(val kind: String, val lines: List<String>)
private val bullet = Regex("^\\s*[-•]\\s+(.*)$")
private val number = Regex("^\\s*\\d+[.)]\\s+(.*)$")
private val token = Regex("\\[([^\\]\\n]+)\\]\\(((?:https?://|mailto:)[^)\\s]+)\\)|\\*\\*(.+?)\\*\\*|\\*(?!\\s)(.+?)\\*")

/** Derselbe begrenzte Textumfang wie die Web-Rundschreiben; HTML bleibt lesbarer Text. */
internal fun circularBlocks(text: String): List<CircularBlock> {
    val result = mutableListOf<CircularBlock>()
    var current: CircularBlock? = null
    for (raw in text.replace("\r\n", "\n").split('\n')) {
        val line = raw.trimEnd()
        if (line.isBlank()) { current = null; continue }
        val item = bullet.matchEntire(line)
        val ordered = number.matchEntire(line)
        val kind = if (item != null) "ul" else if (ordered != null) "ol" else "p"
        val content = (item ?: ordered)?.groupValues?.get(1) ?: line
        val next = if (current?.kind == kind) current.copy(lines = current.lines + content) else CircularBlock(kind, listOf(content))
        if (current?.kind == kind) result[result.lastIndex] = next else result += next
        current = next
    }
    return result
}

internal fun circularInline(text: String, linkStyle: TextLinkStyles, onOpen: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    fun AnnotatedString.Builder.add(source: String, depth: Int = 0) {
        if (depth > 8) { append(source); return }
        var end = 0
        for (match in token.findAll(source)) {
            append(source.substring(end, match.range.first))
            val g = match.groupValues
            when {
                g[1].isNotEmpty() -> withLink(LinkAnnotation.Clickable(g[2], linkStyle, LinkInteractionListener { onOpen(g[2]) })) { append(g[1]) }
                g[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { add(g[3], depth + 1) }
                else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { add(g[4], depth + 1) }
            }
            end = match.range.last + 1
        }
        append(source.substring(end))
    }
    add(text)
}

@Composable
internal fun CircularBody(text: String, onOpen: (String) -> Unit) {
    val blocks = remember(text) { circularBlocks(text) }
    val style = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (block in blocks) {
                if (block.kind == "p") Text(circularInline(block.lines.joinToString("\n"), style, onOpen))
                else Column(Modifier.semantics { collectionInfo = CollectionInfo(block.lines.size, 1) }) {
                    block.lines.forEachIndexed { index, line ->
                        val prefix = if (block.kind == "ol") "${index + 1}. " else "• "
                        Text(
                            buildAnnotatedString { append(prefix); append(circularInline(line, style, onOpen)) },
                            Modifier.semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CircularContent(item: CircularItem, server: String?, access: Int) {
    val handler = LocalUriHandler.current
    var failedUrl by remember(item.id, server, access) { mutableStateOf<String?>(null) }
    fun open(url: String) {
        failedUrl = null
        try { handler.openUri(url) }
        catch (_: IllegalArgumentException) { failedUrl = url }
        catch (_: ActivityNotFoundException) { failedUrl = url }
        catch (_: SecurityException) { failedUrl = url }
    }
    CircularBody(item.body.orEmpty(), ::open)
    if (item.attachments.isNotEmpty()) {
        Text(tr("Anhänge"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        item.attachments.forEach { attachment -> Text(attachment.name) }
    }
    val target = server?.let { circularWebUrl(it, access, item.id) }
    if (target != null) {
        OutlinedButton(onClick = { open(target) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Dieses Schreiben mit Anhängen im Browser öffnen")) }
        Text(tr("Falls du dich im Browser anmelden musst, öffnet sich danach dieses Schreiben. Die App bleibt hier geöffnet."), style = MaterialTheme.typography.bodySmall)
    }
    failedUrl?.let { url ->
        Text(tr("Der Browser konnte nicht geöffnet werden. Öffne diese Adresse in einem Browser:"), color = MaterialTheme.colorScheme.error, modifier = Modifier.announce(urgent = true))
        SelectionContainer { Text(url) }
    }
}
