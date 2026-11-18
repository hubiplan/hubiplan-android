package de.hubiplan.app.data

import android.content.Context
import java.io.File

/**
 * Welche Mitteilungen auf diesem Gerät schon geöffnet wurden (UX-Audit 16.11. AND-15). Beim
 * ersten Abruf gilt alles Vorhandene als gelesen, damit nach dem Verbinden nicht die ganze Liste
 * „neu“ ist. Nur Nummern, keine Inhalte; wird beim Abmelden mit dem Konto gelöscht.
 */
class ReadStore(context: Context, accountKey: String) {
    private val dir = LocalAccountData.dir(context, FOLDER, accountKey)

    private fun file(accessId: Int) = File(dir, "mitteilungen-$accessId.txt")

    /** Gelesene Nummern; null, solange für diesen Zugang noch nichts festgehalten ist. */
    fun read(accessId: Int): Set<Int>? = runCatching {
        val f = file(accessId)
        if (!f.exists()) null else f.readText().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }.getOrNull()

    /** Ungelesene Nummern aus [ids]; beim ersten Mal wird alles als gelesen festgehalten. */
    fun unread(accessId: Int, ids: List<Int>): Set<Int> {
        val known = read(accessId)
        if (known == null) {
            write(accessId, ids.toSet())
            return emptySet()
        }
        return ids.filterTo(mutableSetOf()) { it !in known }
    }

    fun markRead(accessId: Int, id: Int) {
        val known = read(accessId).orEmpty()
        if (id !in known) write(accessId, known + id)
    }

    private fun write(accessId: Int, ids: Set<Int>) {
        runCatching {
            dir.mkdirs()
            // Die neuesten behalten; ältere Mitteilungen fallen ohnehin aus der Liste
            file(accessId).writeText(ids.sortedDescending().take(MAX).joinToString(","))
        }
    }

    companion object {
        const val FOLDER = "gelesen"
        private const val MAX = 500
    }
}
