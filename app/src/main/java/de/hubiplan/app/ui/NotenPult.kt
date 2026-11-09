package de.hubiplan.app.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.data.Blaettern
import de.hubiplan.app.data.FolderItem
import de.hubiplan.app.data.LiveCue
import de.hubiplan.app.data.Mappe
import de.hubiplan.app.data.NotenStore
import de.hubiplan.app.data.NotenSuche
import de.hubiplan.app.data.NotenSync
import de.hubiplan.app.data.PultDateien
import de.hubiplan.app.data.PultEinstellungen
import de.hubiplan.app.data.PultOffen
import de.hubiplan.app.data.PultStelle
import de.hubiplan.app.data.PultTasten
import de.hubiplan.app.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Geöffnetes Notenpult: Konto, Zugang, alle Mappen (für Suche und Sprünge) und Startstelle. */
data class PultSession(
    val accountKey: String,
    val localKey: String,
    val accessId: Int,
    val mappen: List<Mappe>,
    val mappeKey: String,
    val start: PultStelle,
)

/** Tasten aus der Activity ans offene Notenpult (Pedal, Pfeile, Lautstärke). */
object PultKeys {
    @Volatile
    var handler: ((KeyEvent) -> Boolean)? = null
}

/** Weiß auf Schwarz für dunkle Bühnen (Nachtmodus). */
private val INVERT = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** Transpositionen zur Auswahl (Halbtöne, wie der Server sie annimmt). */
internal val TRANSPOSITIONEN = listOf("", "2", "-3", "-5", "1", "-1", "-2", "3", "4", "-4", "5", "6", "-6")

internal fun transpositionLabel(value: String): String = when (value) {
    "" -> tr("Wie notiert")
    "2" -> tr("B-Stimmung (+2)")
    "-3" -> tr("Es-Stimmung (−3)")
    "-5" -> tr("F-Stimmung (−5)")
    else -> tr("{n} Halbtöne", "n" to (if (value.startsWith("-")) "−" + value.drop(1) else "+$value"))
}

/** Hinweis der Leitung, bevor das Pult springt (E15). */
private data class Vorgabe(val cue: LiveCue, val mappeKey: String?, val piece: Int)

/**
 * Notenpult im Vollbild (Konzept 4.2, N4–N6): Bildschirm bleibt an, Blättern per Tippen,
 * Wischen, Lautstärketaste oder Fußpedal, nächstes Stück am Ende, Schnellwahl, Live-Vorgabe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotenPult(session: PultSession, vm: AppViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val store = remember(session.localKey) { NotenStore(context, session.localKey) }
    val api = remember(session) { vm.api() }
    val sync = remember(api) { api?.let { NotenSync(it, store, session.accessId) } }
    val loader = remember(session) { PultLoader(context, sync, store, session.accessId) }
    val audio = remember { Hoerbeispiel() }
    var settings by remember { mutableStateOf(PultEinstellungen.load(context)) }
    fun change(next: PultEinstellungen) {
        settings = next
        next.save(context)
    }

    var mappeKey by remember { mutableStateOf(session.mappeKey) }
    val mappe = session.mappen.firstOrNull { it.key == mappeKey } ?: session.mappen.first()
    val pieces = mappe.pieces
    var stelle by remember {
        mutableStateOf(session.start.copy(piece = session.start.piece.coerceIn(0, (pieces.size - 1).coerceAtLeast(0))))
    }
    // Für „Zurück zu …“ nach einem Sprung (z. B. Zwischenmarsch)
    var vorher by remember { mutableStateOf<Pair<String, PultStelle>?>(null) }
    val transpose = remember { mutableStateMapOf<Int, String>() }
    val item: FolderItem? = pieces.getOrNull(stelle.piece)
    val itemTranspose = item?.arrangement?.let { transpose[it] }.orEmpty()

    var menu by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var auswahl by remember { mutableStateOf<Boolean?>(null) } // null zu, false Stück wählen, true an alle senden
    var hint by remember { mutableStateOf<String?>(null) }
    var twoUp by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var tempo by remember { mutableStateOf(1f) }

    // --- Gerät: Bildschirm an, Vollbild, eigene Hinweise still, Helligkeit, Drehung ---
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        PultOffen.offen = true
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            PultOffen.offen = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            audio.release()
            loader.closeAll()
            PultDateien.clear(context)
        }
    }
    DisposableEffect(settings.bright) {
        val window = activity?.window
        val before = window?.attributes?.screenBrightness
        if (settings.bright) window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL } }
        onDispose {
            if (settings.bright) {
                window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
            }
        }
    }
    DisposableEffect(settings.lockRotation) {
        if (settings.lockRotation) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // --- Stück laden, Nachbarn vorbereiten ---
    var docs by remember { mutableStateOf<PieceDocs?>(null) }
    LaunchedEffect(mappeKey, stelle.piece, itemTranspose) {
        val current = item ?: return@LaunchedEffect
        val key = loader.key(current, itemTranspose)
        if (docs?.key != key) docs = loader.known(key)
        val loaded = loader.piece(current, itemTranspose)
        docs = loaded
        // Rückwärts ins vorige Stück: auf dessen letzte Seite
        if (stelle.page >= loaded.pages) {
            val step = if (twoUp) 2 else 1
            stelle = stelle.copy(page = if (step == 2) ((loaded.pages - 1) / 2) * 2 else loaded.pages - 1, half = false)
        }
        val keep = mutableSetOf(key)
        pieces.getOrNull(stelle.piece + 1)?.let { next ->
            keep += loader.key(next, next.arrangement?.let { transpose[it] }.orEmpty())
            launch { loader.piece(next, next.arrangement?.let { transpose[it] }.orEmpty()) }
        }
        pieces.getOrNull(stelle.piece - 1)?.let { prev -> keep += loader.key(prev, prev.arrangement?.let { transpose[it] }.orEmpty()) }
        loader.retain(keep)
    }
    LaunchedEffect(mappeKey, stelle) {
        store.savePosition(session.accessId, mappeKey, stelle)
    }
    LaunchedEffect(mappeKey, stelle.piece) {
        item?.arrangement?.let { store.played(session.accessId, it) }
    }

    fun pagesOf(piece: Int): Int {
        if (piece == stelle.piece) return docs?.pages ?: 1
        val other = pieces.getOrNull(piece) ?: return 1
        return loader.known(loader.key(other, other.arrangement?.let { transpose[it] }.orEmpty()))?.pages ?: Int.MAX_VALUE
    }

    fun turn(direction: Int) {
        val step = if (twoUp) 2 else 1
        val next = if (direction > 0) {
            Blaettern.vor(stelle, ::pagesOf, pieces.size, step, settings.halfTurn && !twoUp)
        } else {
            Blaettern.zurueck(stelle, ::pagesOf, step)
        }
        if (next == null) {
            hint = if (direction > 0) tr("Ende der Mappe") else tr("Anfang der Mappe")
        } else {
            hint = null
            stelle = next
        }
    }

    fun jump(targetMappe: String, piece: Int) {
        vorher = mappeKey to stelle
        mappeKey = targetMappe
        stelle = PultStelle(piece, 0)
        auswahl = null
        menu = false
    }

    // --- Tasten: Pedal, Pfeile, Lautstärke (E9), langes Drücken: Schnellwahl ---
    val latestTurn by rememberUpdatedState(::turn)
    val keySettings by rememberUpdatedState(settings)
    val keyBlocked by rememberUpdatedState(auswahl != null)
    val keyPlaying by rememberUpdatedState(playing)
    DisposableEffect(Unit) {
        var longPressed = false
        val handler: (KeyEvent) -> Boolean = handler@{ event ->
            if (keyBlocked) return@handler false
            val volumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            val dir = PultTasten.richtung(event.keyCode, keySettings.volumeKeys && !keyPlaying, keySettings.swapVolume)
            if (dir == 0) return@handler false
            if (volumeKey) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) latestTurn(dir)
                return@handler true
            }
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) longPressed = false
                    if (event.repeatCount == 1 && dir > 0) {
                        longPressed = true
                        auswahl = false
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (!longPressed) latestTurn(dir)
                    longPressed = false
                }
            }
            true
        }
        PultKeys.handler = handler
        onDispose { if (PultKeys.handler === handler) PultKeys.handler = null }
    }

    // --- Live-Vorgabe der Leitung (N6) ---
    var canSend by remember(mappe.liveKey) { mutableStateOf(mappe.canLead) }
    var liveOk by remember(mappe.liveKey) { mutableStateOf<Boolean?>(null) }
    var vorgabe by remember { mutableStateOf<Vorgabe?>(null) }
    var ownSeq by remember { mutableStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(mappe.liveKey, api) {
        val client = api ?: return@LaunchedEffect
        var lastSeq: Long? = null
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val wait = try {
                    val state = client.live(session.accessId, mappe.liveKey)
                    liveOk = true
                    canSend = state.canSend
                    val cue = state.cue
                    // Beim Öffnen nur merken, was schon vorgegeben war
                    if (cue != null && lastSeq != null && cue.seq > lastSeq!! && cue.seq != ownSeq) {
                        val here = pieces.indexOfFirst { it.arrangement == cue.arrangement }
                        val place = if (here >= 0) mappeKey to here else session.mappen.firstNotNullOfOrNull { m ->
                            m.pieces.indexOfFirst { it.arrangement == cue.arrangement }.takeIf { it >= 0 }?.let { m.key to it }
                        }
                        vorgabe = Vorgabe(cue, place?.first, place?.second ?: -1)
                    }
                    if (cue != null) lastSeq = maxOf(lastSeq ?: 0L, cue.seq) else if (lastSeq == null) lastSeq = 0L
                    state.pollSeconds.coerceIn(2, 30) * 1000L
                } catch (e: ApiException) {
                    liveOk = false
                    if (e.status == 404 || e.status == 403) return@repeatOnLifecycle
                    5000L
                }
                delay(wait)
            }
        }
    }
    // Nach 3 Sekunden springen, außer „Bleiben“ (E15)
    LaunchedEffect(vorgabe) {
        val v = vorgabe ?: return@LaunchedEffect
        if (!settings.autoJump || v.mappeKey == null) return@LaunchedEffect
        delay(3000)
        if (vorgabe == v) {
            jump(v.mappeKey, v.piece)
            vorgabe = null
        }
    }

    BackHandler {
        when {
            auswahl != null -> auswahl = null
            menu -> menu = false
            else -> onClose()
        }
    }

    val background = if (settings.night) Color.Black else Color.White
    Surface(Modifier.fillMaxSize(), color = background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val wide = maxWidth > maxHeight && maxWidth >= 600.dp
            val two = settings.twoPages && wide
            SideEffect { twoUp = two }
            val widthPx = with(density) { (if (two) maxWidth / 2 else maxWidth).roundToPx() }.coerceAtLeast(200)
            val current = docs
            val showHalf = settings.halfTurn && !two && stelle.half
            val first = pageBitmap(loader, current, stelle.page, widthPx, settings.crop)
            val second = pageBitmap(loader, current, if (two || showHalf) stelle.page + 1 else -1, widthPx, settings.crop)
            // Nächste Seiten vorab setzen, damit Blättern sofort geht
            LaunchedEffect(current, stelle, widthPx, settings.crop) {
                val d = current ?: return@LaunchedEffect
                for (p in stelle.page + 1..stelle.page + 3) if (p < d.pages) loader.render(d, p, widthPx, settings.crop)
            }
            val filter = if (settings.night) INVERT else null
            Canvas(
                Modifier.fillMaxSize()
                    .semantics {
                        contentDescription = tr("Noten: {title}, Seite {page} von {pages}", "title" to (item?.title ?: ""), "page" to stelle.page + 1, "pages" to (current?.pages ?: 1))
                    }
                    .pointerInput(locked, settings.tap) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (locked) {
                                    hint = tr("Gesperrt: zum Entsperren lange drücken")
                                    return@detectTapGestures
                                }
                                when (val zone = PultTasten.tippen(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())) {
                                    PultTasten.MITTE -> menu = !menu
                                    0 -> {}
                                    else -> if (settings.tap) turn(zone) else menu = !menu
                                }
                            },
                            onLongPress = {
                                if (locked) {
                                    locked = false
                                    hint = tr("Entsperrt")
                                }
                            },
                        )
                    }
                    .pointerInput(locked, settings.swipe) {
                        if (!settings.swipe) return@pointerInput
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (!locked && kotlin.math.abs(total) > size.width / 8f) turn(if (total < 0) 1 else -1)
                                total = 0f
                            },
                            onHorizontalDrag = { _, amount -> total += amount },
                        )
                    },
            ) {
                when {
                    first == null -> {}
                    two -> {
                        drawFit(first, Offset(0f, 0f), size.width / 2f, size.height, filter)
                        second?.let { drawFit(it, Offset(size.width / 2f, 0f), size.width / 2f, size.height, filter) }
                    }
                    showHalf && second != null -> drawHalf(first, second, filter, if (settings.night) Color.White else Color.Black)
                    else -> drawFit(first, Offset(0f, 0f), size.width, size.height, filter)
                }
            }

            val pages = current?.pages ?: 1
            if (current?.error != null) {
                Column(Modifier.align(Alignment.Center).padding(Abstand.gross), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = if (settings.night) Color.White else Color.Black)
                    Text(current.error, color = if (settings.night) Color.White else Color.Black)
                }
            } else if (current == null || first == null) {
                Loading()
            }
            if (pieces.isEmpty()) {
                Text(tr("In dieser Mappe ist kein Stück."), Modifier.align(Alignment.Center), color = if (settings.night) Color.White else Color.Black)
            }

            // Am Ende des Stücks: was als Nächstes kommt
            val next = pieces.getOrNull(stelle.piece + 1)
            if (next != null && current != null && Blaettern.letzteSeite(stelle, pages, if (two) 2 else 1)) {
                Text(
                    tr("Nächstes: {title}", "title" to listOf(next.number, next.title).filter { it.isNotBlank() }.joinToString(" ")),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Abstand.klein)
                        .background(Color(0xCC333333), MaterialTheme.shapes.small).padding(horizontal = Abstand.klein, vertical = Abstand.eng),
                )
            }
            if (locked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = tr("Gesperrt"),
                    tint = Color(0x99888888),
                    modifier = Modifier.align(Alignment.TopEnd).padding(Abstand.klein).size(20.dp),
                )
            }
            if (liveOk == false && !menu) {
                Text(
                    tr("Live getrennt"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0x99888888),
                    modifier = Modifier.align(Alignment.TopStart).padding(Abstand.klein),
                )
            }
            hint?.let { text ->
                LaunchedEffect(text) { delay(2000); if (hint == text) hint = null }
                Text(
                    text,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(Abstand.gross)
                        .background(Color(0xCC333333), MaterialTheme.shapes.small).padding(Abstand.klein).announce(),
                )
            }

            if (menu) {
                PultKopf(
                    mappe = mappe,
                    item = item,
                    stelle = stelle,
                    pages = pages,
                    canSend = canSend,
                    settings = settings,
                    playing = playing,
                    hasAudio = item?.audio != null,
                    transpose = itemTranspose,
                    canTranspose = item?.sheets?.any { it.sheetFormat == "pdf" } == true && sync != null,
                    onClose = onClose,
                    onLock = { locked = true; menu = false; hint = tr("Gesperrt: zum Entsperren lange drücken") },
                    onChoose = { auswahl = false },
                    onSend = { auswahl = true },
                    onSettings = ::change,
                    onTranspose = { value -> item?.arrangement?.let { transpose[it] = value } },
                    onAudio = {
                        val score = item?.audio
                        if (playing) {
                            audio.stop(); playing = false
                        } else if (score != null) {
                            scope.launch {
                                val bytes = runCatching {
                                    sync?.audio(score) ?: store.read(session.accessId, score, "midi") ?: error("offline")
                                }.getOrNull()
                                if (bytes == null) {
                                    hint = tr("Das Hörbeispiel ließ sich nicht laden.")
                                } else {
                                    playing = true
                                    audio.play(context, bytes, tempo) { playing = false }
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            if (playing) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(Abstand.klein)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).padding(Abstand.eng),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Abstand.eng),
                ) {
                    Text(tr("Tempo"), style = MaterialTheme.typography.labelMedium)
                    listOf(0.5f, 0.75f, 1f).forEach { t ->
                        FilterChip(tempo == t, onClick = { tempo = t; audio.setTempo(t) }, label = { Text("${(t * 100).toInt()} %") })
                    }
                    IconButton(onClick = { audio.stop(); playing = false }) { Icon(Icons.Filled.Close, contentDescription = tr("Anhören beenden")) }
                }
            }

            vorgabe?.let { v ->
                Card(Modifier.align(Alignment.TopCenter).padding(Abstand.mittel).widthIn(max = 560.dp).fillMaxWidth()) {
                    Column(Modifier.padding(Abstand.rand).announce(urgent = true), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        Text(
                            tr("{sender}: {piece}", "sender" to v.cue.sender.ifBlank { tr("Leitung") }, "piece" to listOf(v.cue.number.takeIf { it.isNotBlank() }?.let { tr("Nr. {number}", "number" to it) }, v.cue.title).filterNotNull().joinToString(" · ")),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (v.mappeKey == null) Text(tr("Dieses Stück ist nicht in deinen Noten."))
                        Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                            if (v.mappeKey != null) Button(onClick = { jump(v.mappeKey, v.piece); vorgabe = null }) { Text(tr("Jetzt öffnen")) }
                            OutlinedButton(onClick = { vorgabe = null }) { Text(if (v.mappeKey != null && settings.autoJump) tr("Bleiben") else tr("Schließen")) }
                        }
                    }
                }
            }

            // Einmaliger Tipp zu „Bitte nicht stören“ (E6: nur ein Knopf zu den Einstellungen)
            if (!settings.dndHintShown && !menu && auswahl == null) {
                Card(Modifier.align(Alignment.BottomCenter).padding(Abstand.mittel).widthIn(max = 560.dp)) {
                    Column(Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                        Text(tr("Der Bildschirm bleibt an, solange die Noten offen sind. HUBIplan meldet sich dabei nur still. Für Anrufe und andere Apps hilft „Bitte nicht stören“."))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                            TextButton(onClick = { openDnd(context); change(settings.copy(dndHintShown = true)) }) { Text(tr("„Bitte nicht stören“ öffnen")) }
                            TextButton(onClick = { change(settings.copy(dndHintShown = true)) }) { Text(tr("Verstanden")) }
                        }
                    }
                }
            }

            auswahl?.let { send ->
                Schnellwahl(
                    mappe = mappe,
                    mappen = session.mappen,
                    send = send,
                    current = item,
                    back = vorher?.let { (key, at) -> session.mappen.firstOrNull { it.key == key }?.pieces?.getOrNull(at.piece)?.title },
                    onBack = {
                        vorher?.let { (key, at) ->
                            vorher = mappeKey to stelle
                            mappeKey = key
                            stelle = at
                        }
                        auswahl = null
                    },
                    onPick = { target, piece -> jump(target.key, piece) },
                    onSendPiece = { picked ->
                        val client = api ?: return@Schnellwahl
                        val arrangement = picked.arrangement ?: return@Schnellwahl
                        scope.launch {
                            try {
                                val cue = client.sendLive(session.accessId, mappe.liveKey, arrangement, picked.number)
                                ownSeq = cue?.seq ?: 0L
                                hint = tr("An alle gesendet")
                                val here = pieces.indexOfFirst { it.arrangement == arrangement }
                                if (here >= 0) jump(mappeKey, here) else auswahl = null
                            } catch (e: ApiException) {
                                hint = e.generalError ?: e.message
                            }
                        }
                    },
                    onDismiss = { auswahl = null },
                )
            }
        }
    }
}

/** Gesetzte Seite (oder die vorige, bis die neue fertig ist). */
@Composable
private fun pageBitmap(loader: PultLoader, docs: PieceDocs?, page: Int, width: Int, crop: Boolean): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, docs, page, width, crop) {
        if (docs == null || page < 0 || page >= docs.pages) {
            value = null
            return@produceState
        }
        loader.cached(docs, page, width, crop)?.let { value = it.asImageBitmap(); return@produceState }
        value = loader.render(docs, page, width, crop)?.asImageBitmap()
    }
    return state.value
}

/** Bild in die Fläche einpassen (ganz sichtbar, mittig). */
private fun DrawScope.drawFit(image: ImageBitmap, origin: Offset, w: Float, h: Float, filter: ColorFilter?) {
    val scale = minOf(w / image.width, h / image.height)
    val dw = (image.width * scale).toInt().coerceAtLeast(1)
    val dh = (image.height * scale).toInt().coerceAtLeast(1)
    val left = (origin.x + (w - dw) / 2f).toInt()
    val top = (origin.y + (h - dh) / 2f).toInt()
    drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset(left, top), IntSize(dw, dh), colorFilter = filter)
}

/** Halbe Seite gewendet: oben der Anfang der nächsten Seite, unten das Ende der aktuellen. */
private fun DrawScope.drawHalf(current: ImageBitmap, next: ImageBitmap, filter: ColorFilter?, line: Color) {
    val scale = minOf(size.width / current.width, size.height / current.height)
    val dw = (current.width * scale).toInt().coerceAtLeast(1)
    val dh = (current.height * scale).toInt().coerceAtLeast(2)
    val left = ((size.width - dw) / 2f).toInt()
    val top = ((size.height - dh) / 2f).toInt()
    val half = dh / 2
    drawImage(next, IntOffset.Zero, IntSize(next.width, next.height / 2), IntOffset(left, top), IntSize(dw, half), colorFilter = filter)
    drawImage(
        current,
        IntOffset(0, current.height / 2),
        IntSize(current.width, current.height - current.height / 2),
        IntOffset(left, top + half),
        IntSize(dw, dh - half),
        colorFilter = filter,
    )
    drawLine(line, Offset(left.toFloat(), (top + half).toFloat()), Offset((left + dw).toFloat(), (top + half).toFloat()), strokeWidth = 3f)
}

private fun openDnd(context: android.content.Context) {
    val intents = listOf(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS), Intent(Settings.ACTION_SOUND_SETTINGS))
    for (intent in intents) {
        val ok = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (ok) return
    }
}

/** Kopfzeile auf Tipp in die Bildmitte: Stück, Seite, Uhr, Akku, Knöpfe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PultKopf(
    mappe: Mappe,
    item: FolderItem?,
    stelle: PultStelle,
    pages: Int,
    canSend: Boolean,
    settings: PultEinstellungen,
    playing: Boolean,
    hasAudio: Boolean,
    transpose: String,
    canTranspose: Boolean,
    onClose: () -> Unit,
    onLock: () -> Unit,
    onChoose: () -> Unit,
    onSend: () -> Unit,
    onSettings: (PultEinstellungen) -> Unit,
    onTranspose: (String) -> Unit,
    onAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var clock by remember { mutableStateOf("") }
    var battery by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        val manager = context.getSystemService(BatteryManager::class.java)
        while (true) {
            clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            battery = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            delay(30_000)
        }
    }
    var more by remember { mutableStateOf(false) }
    var transposeMenu by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = Abstand.klein, vertical = Abstand.eng)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = tr("Noten schließen")) }
                Column(Modifier.weight(1f)) {
                    val number = item?.number?.takeIf { it.isNotBlank() }
                    Text(
                        listOfNotNull(number?.let { tr("Nr. {number}", "number" to it) }, item?.title).joinToString(" · "),
                        style = if (mappe.isMarchbook) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.asHeading(),
                    )
                    val pageText = if (stelle.half) {
                        tr("Seite {a}–{b} von {n}", "a" to stelle.page + 1, "b" to stelle.page + 2, "n" to pages)
                    } else {
                        tr("Seite {a} von {n}", "a" to stelle.page + 1, "n" to pages)
                    }
                    Text(
                        listOf(
                            mappe.name,
                            tr("Stück {a} von {n}", "a" to stelle.piece + 1, "n" to mappe.pieces.size),
                            pageText,
                            clock,
                            if (battery in 0..100) "$battery %" else "",
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (battery in 0 until 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, contentDescription = tr("Einstellungen des Notenpults")) }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        ToggleItem(tr("Nachtmodus"), settings.night) { onSettings(settings.copy(night = !settings.night)) }
                        ToggleItem(tr("Volle Helligkeit"), settings.bright) { onSettings(settings.copy(bright = !settings.bright)) }
                        ToggleItem(tr("Ränder abschneiden"), settings.crop) { onSettings(settings.copy(crop = !settings.crop)) }
                        ToggleItem(tr("Halbe Seite wenden"), settings.halfTurn) { onSettings(settings.copy(halfTurn = !settings.halfTurn)) }
                        ToggleItem(tr("Zwei Seiten nebeneinander (quer)"), settings.twoPages) { onSettings(settings.copy(twoPages = !settings.twoPages)) }
                        ToggleItem(tr("Drehung sperren"), settings.lockRotation) { onSettings(settings.copy(lockRotation = !settings.lockRotation)) }
                        ToggleItem(tr("Tippen am Rand blättert"), settings.tap) { onSettings(settings.copy(tap = !settings.tap)) }
                        ToggleItem(tr("Wischen blättert"), settings.swipe) { onSettings(settings.copy(swipe = !settings.swipe)) }
                        ToggleItem(tr("Lautstärketasten blättern"), settings.volumeKeys) { onSettings(settings.copy(volumeKeys = !settings.volumeKeys)) }
                        ToggleItem(tr("Lautstärketasten vertauschen"), settings.swapVolume) { onSettings(settings.copy(swapVolume = !settings.swapVolume)) }
                        ToggleItem(tr("Bei Vorgabe der Leitung selbst springen"), settings.autoJump) { onSettings(settings.copy(autoJump = !settings.autoJump)) }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(tr("„Bitte nicht stören“ öffnen")) }, onClick = { more = false; openDnd(context) })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Abstand.klein), verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                FilledTonalButton(onClick = onChoose) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text(tr("Stück wählen"), Modifier.padding(start = Abstand.eng))
                }
                if (canSend) {
                    FilledTonalButton(onClick = onSend) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Text(tr("An alle senden"), Modifier.padding(start = Abstand.eng))
                    }
                }
                if (hasAudio) {
                    OutlinedButton(onClick = onAudio) {
                        Icon(if (playing) Icons.Filled.Close else Icons.Filled.PlayArrow, contentDescription = null)
                        Text(if (playing) tr("Anhören beenden") else tr("Anhören"), Modifier.padding(start = Abstand.eng))
                    }
                }
                if (canTranspose) {
                    Box {
                        OutlinedButton(onClick = { transposeMenu = true }) { Text(transpositionLabel(transpose)) }
                        DropdownMenu(expanded = transposeMenu, onDismissRequest = { transposeMenu = false }) {
                            TRANSPOSITIONEN.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text((if (value == transpose) "✓ " else "    ") + transpositionLabel(value)) },
                                    onClick = { transposeMenu = false; onTranspose(value) },
                                )
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onLock) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Text(tr("Sperren"), Modifier.padding(start = Abstand.eng))
                }
            }
        }
    }
}

@Composable
private fun ToggleItem(label: String, on: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text((if (on) "✓ " else "    ") + label) }, onClick = onClick)
}

/**
 * Schnellwahl über den Noten (Konzept 4.5): Nummernfeld, Suche über alle Mappen, Liste der
 * aktuellen Mappe, „Zurück zu …“. Mit [send] wählt die Leitung das Stück für alle Geräte.
 */
@Composable
private fun Schnellwahl(
    mappe: Mappe,
    mappen: List<Mappe>,
    send: Boolean,
    current: FolderItem?,
    back: String?,
    onBack: () -> Unit,
    onPick: (Mappe, Int) -> Unit,
    onSendPiece: (FolderItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var number by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<FolderItem?>(null) }
    val hasNumbers = mappe.pieces.any { it.number.isNotBlank() }
    fun pick(target: Mappe, item: FolderItem) {
        if (send) chosen = item else onPick(target, target.pieces.indexOf(item).coerceAtLeast(0))
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Abstand.rand)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (send) tr("An alle senden") else tr("Stück wählen"),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).asHeading(),
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = tr("Schließen")) }
                }
                if (send) {
                    Text(tr("Alle Geräte, die diese Mappe offen haben, zeigen das Stück an."), style = MaterialTheme.typography.bodySmall)
                    val target = chosen ?: current
                    if (target != null) {
                        Button(onClick = { onSendPiece(target) }, modifier = Modifier.padding(vertical = Abstand.klein)) {
                            Text(
                                tr("Senden: {piece}", "piece" to listOf(target.number, target.title).filter { it.isNotBlank() }.joinToString(" ")),
                            )
                        }
                    }
                }
                if (back != null && !send) {
                    TextButton(onClick = onBack) { Text(tr("Zurück zu {title}", "title" to back)) }
                }
            }
            if (hasNumbers) {
                item {
                    Text(
                        if (number.isEmpty()) tr("Nummer") else tr("Nr. {number}", "number" to number),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(vertical = Abstand.klein).announce(),
                    )
                    val match = NotenSuche.nummer(number, mappe)
                    if (number.isNotEmpty()) {
                        Text(match?.title ?: tr("Keine solche Nummer in {mappe}", "mappe" to mappe.name), style = MaterialTheme.typography.bodyMedium)
                    }
                    Ziffernblock(
                        onDigit = { d ->
                            number = (number + d).take(6)
                            NotenSuche.eindeutigeNummer(number, mappe)?.let { pick(mappe, it) }
                        },
                        onDelete = { number = number.dropLast(1) },
                        onOk = { NotenSuche.nummer(number, mappe)?.let { pick(mappe, it) } },
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(tr("Titel, Komponist oder Nummer")) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = Abstand.klein),
                )
            }
            if (query.isNotBlank()) {
                val hits = NotenSuche.suche(query, mappen)
                if (hits.isEmpty()) item { Text(tr("Kein Stück gefunden.")) }
                items(hits, key = { "s-" + it.arrangement }) { hit ->
                    // In der aktuellen Mappe bleiben, wenn das Stück dort steht
                    val place = hit.stellen.firstOrNull { it.mappe.key == mappe.key } ?: hit.stellen.first()
                    ListItem(
                        modifier = Modifier.clickable { pick(place.mappe, place.item) },
                        headlineContent = { Text(hit.item.title) },
                        supportingContent = { Text(stellenText(hit.stellen)) },
                    )
                }
            } else {
                item { Text(mappe.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Abstand.klein).asHeading()) }
                items(mappe.pieces, key = { "p-${it.arrangement}-${it.position}" }) { p ->
                    ListItem(
                        modifier = Modifier.clickable { pick(mappe, p) },
                        leadingContent = if (p.number.isNotBlank()) {
                            { Text(p.number, style = MaterialTheme.typography.titleMedium) }
                        } else null,
                        headlineContent = { Text(p.title, fontSize = if (p === current) 18.sp else 16.sp) },
                        supportingContent = if (p.composer.isNotBlank()) {
                            { Text(p.composer) }
                        } else null,
                    )
                }
            }
        }
    }
}

/** Großes Nummernfeld, mit Instrument in der Hand gut zu treffen. */
@Composable
private fun Ziffernblock(onDigit: (String) -> Unit, onDelete: () -> Unit, onOk: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
    Column(verticalArrangement = Arrangement.spacedBy(Abstand.klein)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Abstand.klein)) {
                row.forEach { key ->
                    val label = when (key) {
                        "⌫" -> tr("Löschen")
                        "OK" -> tr("OK")
                        else -> key
                    }
                    OutlinedButton(
                        onClick = {
                            when (key) {
                                "⌫" -> onDelete()
                                "OK" -> onOk()
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier.size(width = 96.dp, height = 64.dp).semantics { contentDescription = label },
                    ) { Text(if (key == "⌫" || key == "OK") key else key, style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
    }
}
