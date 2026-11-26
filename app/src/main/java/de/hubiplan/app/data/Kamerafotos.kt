package de.hubiplan.app.data

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Aufnahmen der Kamera-App (Beleg, Nachweis, Profilfoto, Standfoto) liegen kurz und
 * unverschlüsselt im Cache, bis die App sie verkleinert und ohne Aufnahmedaten übernommen hat.
 * Jede Aufnahme bekommt einen zufälligen Namen. Bleibt eine liegen (Kamera abgebrochen, App
 * beendet), löscht [GeteilteDateien] sie nach etwa einer Stunde, und beim Abmelden gehen alle
 * (Sicherheitsprüfung 26.11.2026, SEC-05).
 */
object Kamerafotos {
    const val FOLDER = "fotos"

    fun dir(cacheDir: File) = File(cacheDir, FOLDER)

    /** Neue leere Datei `<prefix>-<Zufall>.jpg`; frühere Aufnahmen mit [prefix] werden gelöscht. */
    fun neu(context: Context, prefix: String): File {
        val dir = dir(context.cacheDir).apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("$prefix-") }?.forEach { it.delete() }
        val file = File(dir, "$prefix-${UUID.randomUUID()}.jpg")
        runCatching { file.createNewFile() }
        GeteilteDateien.schedule(context)
        return file
    }

    /** Aufnahmen mit [prefix] löschen (Ansicht geschlossen, Aufnahme abgebrochen). */
    fun clear(context: Context, prefix: String) {
        dir(context.cacheDir).listFiles { f -> f.name.startsWith("$prefix-") }?.forEach { it.delete() }
    }

    /** Abmelden: alle Aufnahmen löschen. */
    fun clearAll(cacheDir: File) {
        dir(cacheDir).deleteRecursively()
    }
}
