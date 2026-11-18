package de.hubiplan.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Zuletzt geladene Termine und Mitteilungen eines Zugangs, verschlüsselt auf dem Gerät, damit sie
 * ohne Netz (Halle, Festplatz) lesbar bleiben, mit Standzeit (UX-Audit 16.11. AND-07). Wird beim
 * Abmelden mit den übrigen Daten des Kontos gelöscht.
 */
class OfflineStore(context: Context, accountKey: String) {
    private val dir = LocalAccountData.dir(context, FOLDER, accountKey)
    private val alias = LocalAccountData.alias(accountKey)

    fun events(accessId: Int): Saved<List<Occurrence>>? =
        load("termine-$accessId")?.let { s -> s.events?.let { Saved(it, s.savedAt) } }

    fun circulars(accessId: Int): Saved<List<CircularItem>>? =
        load("mitteilungen-$accessId")?.let { s -> s.circulars?.let { Saved(it, s.savedAt) } }

    fun saveEvents(accessId: Int, items: List<Occurrence>) =
        save("termine-$accessId", Snapshot(System.currentTimeMillis(), events = items))

    // Ohne Text und Anhänge: die Liste reicht zum Wiedererkennen
    fun saveCirculars(accessId: Int, items: List<CircularItem>) =
        save("mitteilungen-$accessId", Snapshot(System.currentTimeMillis(), circulars = items.map { it.copy(body = null) }))

    private fun load(name: String): Snapshot? = runCatching {
        val file = File(dir, "$name.bin")
        json.decodeFromString(Snapshot.serializer(), String(DraftCrypto.open(alias, file.readBytes())))
    }.getOrNull()

    private fun save(name: String, snapshot: Snapshot) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$name.tmp")
            tmp.writeBytes(DraftCrypto.seal(alias, json.encodeToString(Snapshot.serializer(), snapshot).toByteArray()))
            LocalAccountData.replace(tmp, File(dir, "$name.bin"))
        }
    }

    data class Saved<T>(val items: T, val savedAt: Long)

    @Serializable
    private data class Snapshot(
        val savedAt: Long,
        val events: List<Occurrence>? = null,
        val circulars: List<CircularItem>? = null,
    )

    companion object {
        const val FOLDER = "offline"
    }
}
