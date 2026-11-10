package de.hubiplan.app.data

import android.content.Context
import java.io.File

/**
 * Dateien aus dem Mitgliederbereich (Rechnung, Protokoll, Dokument) nur zum Ansehen: die App
 * legt sie kurz in den Cache, damit ein PDF-Betrachter sie öffnen kann, und löscht sie beim
 * nächsten Öffnen, spätestens nach etwa einer Stunde, beim Start und beim Abmelden. Offline gespeichert wird nichts (Konzept app-ich,
 * Entscheidung 3).
 */
object Ansicht {
    const val FOLDER = "ansicht"

    /** Erlaubte Zeichen im Dateinamen; der Rest wird zu „-“. */
    fun safeName(name: String, fallback: String = "datei.pdf"): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "-").trim('.', '-').take(80)
        return cleaned.ifBlank { fallback }
    }

    /** Vorige Ansicht löschen und [bytes] als [name] ablegen. */
    fun store(context: Context, name: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, FOLDER)
        dir.deleteRecursively()
        dir.mkdirs()
        return File(dir, safeName(name)).apply { writeBytes(bytes) }.also { GeteilteDateien.schedule(context) }
    }

    fun clear(context: Context) {
        File(context.cacheDir, FOLDER).deleteRecursively()
    }
}
