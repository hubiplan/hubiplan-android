package de.hubiplan.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Mappen abgleichen und Dateien laden; gemeinsam für Oberfläche und Hintergrund-Abgleich. */
class NotenSync(private val api: Api, private val store: NotenStore, private val accessId: Int) {

    /** Mappen vom Server holen, speichern und Verwaistes löschen. */
    suspend fun refresh(): ScoreFolders {
        val folders = api.scoreFolders(accessId)
        withContext(Dispatchers.IO) {
            store.saveFolders(accessId, folders)
            store.prune(accessId, folders)
        }
        return folders
    }

    /**
     * Notenbild einer Datei: aus dem Speicher oder vom Server (danach gespeichert). [transpose]
     * und [layout] nur für vom Server gesetzte Noten (MusicXML usw.).
     */
    suspend fun sheet(score: ScoreFile, transpose: String = "", layout: String = ""): ByteArray {
        val format = score.sheetFormat
        val pdf = format == "pdf"
        return load(score, variantOf(score, transpose, layout), format, if (pdf) transpose else "", if (pdf) layout else "")
    }

    /** Hörbeispiel (MIDI): Originaldatei oder vom Server aus den Noten gesetzt. */
    suspend fun audio(score: ScoreFile): ByteArray {
        val format = if (score.isAudio) "" else "midi"
        return load(score, "midi", format, "", "")
    }

    private suspend fun load(score: ScoreFile, variant: String, format: String, transpose: String, layout: String): ByteArray {
        withContext(Dispatchers.IO) { store.read(accessId, score, variant) }?.let { return it }
        val download = api.scoreFile(accessId, score.url, format, transpose, layout = layout)
        withContext(Dispatchers.IO) { runCatching { store.save(accessId, score, variant, download.body) } }
        return download.body
    }

    /**
     * Alle Notenbilder (und Hörbeispiele) einer Mappe aufs Gerät; Rückgabe: Zahl der Fehler.
     * [layout]: zuletzt im Notenpult genutzter Satz; vom Server gesetzte Noten kommen dann
     * zusätzlich in dieser Form (passend zum Bildschirm) mit.
     */
    suspend fun download(mappe: Mappe, layout: String = "", onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val files = mappe.pieces.flatMap { it.scores }.distinctBy { it.id }
        var failed = 0
        files.forEachIndexed { n, score ->
            onProgress(n, files.size)
            val ok = runCatching {
                if (score.isSheet) sheet(score)
                if (score.isSheet && score.sheetFormat == "pdf" && layout.isNotEmpty()) sheet(score, layout = layout)
                if (score.isAudio) audio(score)
            }
            if (ok.isFailure) failed++
        }
        onProgress(files.size, files.size)
        return failed
    }

    /**
     * Hintergrund (Konzept E10): Mappen abgleichen, behaltene Mappen und Termine der nächsten
     * Stunden laden. [unmetered]: nur dann wird geladen.
     */
    suspend fun background(unmetered: Boolean, prefetch: Boolean, layout: String = "") {
        val folders = refresh()
        if (!unmetered || !prefetch) return
        val kept = store.kept(accessId)
        for (mappe in folders.mappen) {
            if (mappe.key in kept || NotenStore.dueForPrefetch(mappe)) download(mappe, layout)
        }
    }

    companion object {
        /** Name der gespeicherten Fassung: Format, Transposition, Satz (Bildschirm und Notengröße). */
        fun variantOf(score: ScoreFile, transpose: String = "", layout: String = ""): String {
            val format = score.sheetFormat
            if (format != "pdf") return format
            return format + (if (transpose.isNotEmpty()) "-t$transpose" else "") + (if (layout.isNotEmpty()) "-l$layout" else "")
        }

        /**
         * Satz für eine Fläche von [widthPx] × [heightPx] und Notengröße [size] (Verovio-Skala):
         * „<Höhe je 1000 Breite>x<Größe>“, so wie der Server es annimmt.
         */
        fun layoutFor(widthPx: Int, heightPx: Int, size: Int): String {
            if (widthPx <= 0 || heightPx <= 0) return ""
            val ratio = (heightPx * 1000L / widthPx).toInt().coerceIn(800, 3000)
            return "${ratio}x${size.coerceIn(25, 120)}"
        }
    }
}
