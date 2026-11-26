package de.hubiplan.app.data

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Dateien aus dem Mitgliederbereich (Rechnung, Protokoll, Dokument) nur zum Ansehen: die App
 * legt sie kurz in den Cache, damit ein PDF-Betrachter sie öffnen kann, und löscht sie beim
 * nächsten Öffnen, spätestens nach etwa einer Stunde, beim Start und beim Abmelden. Offline gespeichert wird nichts (Konzept app-ich,
 * Entscheidung 3).
 *
 * Je Konto ein eigener Ordner und je Datei ein zufälliger Unterordner (Sicherheitsprüfung
 * 26.11.2026, SEC-05, wie [ReceiptCache]): Eine Adresse, die ein Betrachter einmal bekommen hat,
 * zeigt nie eine spätere Datei oder die eines anderen Kontos.
 */
object Ansicht {
    const val FOLDER = "ansicht"

    /** Erlaubte Zeichen im Dateinamen; der Rest wird zu „-“. */
    fun safeName(name: String, fallback: String = "datei.pdf"): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "-").trim('.', '-').take(80)
        return cleaned.ifBlank { fallback }
    }

    /** Vorige Ansicht dieses Kontos löschen und [bytes] als [name] ablegen. */
    fun store(context: Context, accountKey: String, name: String, bytes: ByteArray): File =
        Einmalablage.write(context.cacheDir, FOLDER, accountKey, safeName(name), bytes).also { GeteilteDateien.schedule(context) }

    fun forget(context: Context, accountKey: String) = Einmalablage.forget(context.cacheDir, FOLDER, accountKey)

    fun clear(context: Context) {
        File(context.cacheDir, FOLDER).deleteRecursively()
    }
}

/**
 * Ablage im Cache für Dateien, die über den FileProvider an andere Apps gehen: je Konto ein
 * Ordner `k-<Kennung>`, je Datei ein zufälliger Unterordner, die vorige Datei des Kontos wird
 * gelöscht. So ist keine Adresse vorhersagbar oder wiederverwendet.
 */
object Einmalablage {
    fun accountDir(cacheDir: File, folder: String, accountKey: String) =
        File(File(cacheDir, folder), "k-${LocalAccountData.id(accountKey)}")

    fun write(cacheDir: File, folder: String, accountKey: String, name: String, bytes: ByteArray): File {
        val account = accountDir(cacheDir, folder, accountKey)
        account.deleteRecursively()
        val dir = File(account, UUID.randomUUID().toString()).apply { mkdirs() }
        return File(dir, name).apply { writeBytes(bytes) }
    }

    fun forget(cacheDir: File, folder: String, accountKey: String) {
        accountDir(cacheDir, folder, accountKey).deleteRecursively()
    }
}
