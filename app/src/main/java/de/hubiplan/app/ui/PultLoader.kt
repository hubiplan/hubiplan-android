package de.hubiplan.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import de.hubiplan.app.data.FolderItem
import de.hubiplan.app.data.NotenStore
import de.hubiplan.app.data.NotenSync
import de.hubiplan.app.data.PultDateien
import de.hubiplan.app.data.SafeBitmap
import de.hubiplan.app.data.ScoreFile
import de.hubiplan.app.data.Zuschnitt
import de.hubiplan.app.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Eine Notendatei im Notenpult: PDF (mehrere Seiten) oder ein Bild. */
internal sealed class SheetDoc(val id: String) {
    abstract val pages: Int
    val lock = Mutex()
    open fun close() {}
}

internal class PdfDoc(id: String, private val file: File, private val fd: ParcelFileDescriptor, val renderer: PdfRenderer) : SheetDoc(id) {
    override val pages: Int get() = renderer.pageCount

    override fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
        file.delete()
    }
}

internal class ImageDoc(id: String, val bitmap: Bitmap) : SheetDoc(id) {
    override val pages: Int get() = 1
}

/** Alle Notenbilder eines Stücks hintereinander; [error] statt Noten, wenn nichts da ist. */
internal class PieceDocs(val key: String, val docs: List<SheetDoc>, val error: String?) {
    val pages: Int get() = docs.sumOf { it.pages }.coerceAtLeast(1)

    /** Datei und Seite darin für eine Seite des Stücks. */
    fun locate(page: Int): Pair<SheetDoc, Int>? {
        var rest = page
        for (doc in docs) {
            if (rest < doc.pages) return doc to rest
            rest -= doc.pages
        }
        return null
    }

    fun close() = docs.forEach { it.close() }
}

/**
 * Lädt Stücke (aus dem verschlüsselten Speicher, sonst vom Server) und setzt Seiten als Bilder.
 * Der Android-PDF-Renderer verträgt nur eine Seite gleichzeitig je Datei: dafür die Sperre.
 */
internal class PultLoader(
    private val context: Context,
    private val sync: NotenSync?,
    private val store: NotenStore,
    private val accessId: Int,
) {
    private val pieces = mutableMapOf<String, PieceDocs>()
    private val loading = Mutex()
    private val rendered = LruCache<String, Bitmap>(10)

    fun key(item: FolderItem, transpose: String) = "${item.arrangement}-$transpose"

    fun known(key: String): PieceDocs? = pieces[key]

    suspend fun piece(item: FolderItem, transpose: String): PieceDocs = loading.withLock {
        val key = key(item, transpose)
        pieces[key]?.let { return@withLock it }
        val loaded = withContext(Dispatchers.IO) { load(key, item, transpose) }
        pieces[key] = loaded
        loaded
    }

    private suspend fun load(key: String, item: FolderItem, transpose: String): PieceDocs {
        val sheets = item.sheets
        if (sheets.isEmpty()) return PieceDocs(key, emptyList(), tr("Keine Noten für deine Stimme"))
        val docs = mutableListOf<SheetDoc>()
        var missing = false
        for (score in sheets) {
            val bytes = bytes(score, transpose)
            if (bytes == null) {
                missing = true
                continue
            }
            open("$key-${score.id}", bytes)?.let { docs += it } ?: run { missing = true }
        }
        val error = when {
            docs.isNotEmpty() -> null
            sync == null -> tr("Diese Noten sind nicht auf dem Gerät. Bitte mit Verbindung öffnen.")
            missing -> tr("Die Noten ließen sich nicht laden.")
            else -> null
        }
        return PieceDocs(key, docs, error)
    }

    private suspend fun bytes(score: ScoreFile, transpose: String): ByteArray? {
        val variant = score.sheetFormat + if (transpose.isNotEmpty() && score.sheetFormat == "pdf") "-t$transpose" else ""
        store.read(accessId, score, variant)?.let { return it }
        return runCatching { sync?.sheet(score, transpose) }.getOrNull()
    }

    private fun open(id: String, bytes: ByteArray): SheetDoc? = runCatching {
        if (bytes.size > 4 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-") {
            val file = PultDateien.write(context, bytes)
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                PdfDoc(id, file, fd, PdfRenderer(fd))
            } catch (e: Exception) {
                fd.close()
                file.delete()
                throw e
            }
        } else {
            SafeBitmap.decode(bytes, MAX_EDGE)?.let { ImageDoc(id, it) }
        }
    }.getOrNull()

    /** Bereits gesetzte Seite ohne Warten (oder null). */
    fun cached(docs: PieceDocs, page: Int, width: Int, crop: Boolean): Bitmap? = rendered.get(cacheKey(docs, page, width, crop))

    private fun cacheKey(docs: PieceDocs, page: Int, width: Int, crop: Boolean) = "${docs.key}:$page:$width:$crop"

    /** Seite [page] des Stücks in etwa [width] Bildpunkten Breite setzen. */
    suspend fun render(docs: PieceDocs, page: Int, width: Int, crop: Boolean): Bitmap? {
        val cacheKey = cacheKey(docs, page, width, crop)
        rendered.get(cacheKey)?.let { return it }
        val (doc, index) = docs.locate(page) ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            doc.lock.withLock {
                runCatching {
                    val raw = when (doc) {
                        is PdfDoc -> renderPdf(doc, index, width)
                        is ImageDoc -> doc.bitmap
                    }
                    if (crop) cropped(raw) else raw
                }.getOrNull()
            }
        } ?: return null
        rendered.put(cacheKey, bitmap)
        return bitmap
    }

    private fun renderPdf(doc: PdfDoc, index: Int, width: Int): Bitmap {
        val page = doc.renderer.openPage(index)
        try {
            val scale = width.coerceIn(400, MAX_EDGE) / page.width.toFloat()
            var w = (page.width * scale).toInt().coerceAtLeast(1)
            var h = (page.height * scale).toInt().coerceAtLeast(1)
            // Sehr lange Seiten begrenzen (Speicher)
            val pixels = w.toLong() * h
            if (pixels > MAX_PIXELS) {
                val f = kotlin.math.sqrt(MAX_PIXELS.toDouble() / pixels)
                w = (w * f).toInt().coerceAtLeast(1)
                h = (h * f).toInt().coerceAtLeast(1)
            }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    /** Weiße Ränder abschneiden, auf einer kleinen Kopie gemessen. */
    private fun cropped(bitmap: Bitmap): Bitmap {
        val probeW = 240.coerceAtMost(bitmap.width)
        val probeH = (bitmap.height.toLong() * probeW / bitmap.width).toInt().coerceAtLeast(1)
        val probe = Bitmap.createScaledBitmap(bitmap, probeW, probeH, true)
        val pixels = IntArray(probeW * probeH)
        probe.getPixels(pixels, 0, probeW, 0, 0, probeW, probeH)
        if (probe !== bitmap) probe.recycle()
        val b = Zuschnitt.grenzen(pixels, probeW, probeH) ?: return bitmap
        val sx = bitmap.width.toFloat() / probeW
        val sy = bitmap.height.toFloat() / probeH
        val left = (b[0] * sx).toInt().coerceIn(0, bitmap.width - 1)
        val top = (b[1] * sy).toInt().coerceIn(0, bitmap.height - 1)
        val right = (b[2] * sx).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (b[3] * sy).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /** Nur diese Stücke offen halten (aktuelles und Nachbarn), den Rest schließen. */
    suspend fun retain(keys: Set<String>) = loading.withLock {
        val drop = pieces.keys - keys
        for (k in drop) {
            val docs = pieces.remove(k) ?: continue
            withContext(Dispatchers.Default) { docs.docs.forEach { d -> d.lock.withLock { d.close() } } }
        }
    }

    fun closeAll() {
        pieces.values.forEach { it.close() }
        pieces.clear()
        rendered.evictAll()
    }

    companion object {
        const val MAX_EDGE = 3000
        const val MAX_PIXELS = 9_000_000L
    }
}
