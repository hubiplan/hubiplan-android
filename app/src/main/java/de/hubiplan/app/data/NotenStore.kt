package de.hubiplan.app.data

import android.content.Context
import java.io.File
import java.time.OffsetDateTime

/**
 * Noten auf dem Gerät (Konzept Abschnitt 5): Mappen und Dateien verschlüsselt im App-Speicher
 * (AES-GCM, Schlüssel des Kontos im Android-Schlüsselspeicher wie [DraftStore]), nie in
 * Downloads oder Galerie, ohne Sicherung. Je Konto ein Ordner; beim Abmelden weg.
 *
 * Gelöscht wird beim Abgleich alles, was der Server nicht mehr liefert (Stimme entfernt,
 * Freigabe entzogen, Termin abgesagt oder länger als 7 Tage vorbei).
 */
class NotenStore(private val context: Context, accountKey: String) {
    private val dir = LocalAccountData.dir(context, FOLDER, accountKey)
    private val alias = LocalAccountData.alias(accountKey)
    private val prefs = context.getSharedPreferences("noten-" + LocalAccountData.id(accountKey), Context.MODE_PRIVATE)

    // --- Mappen (Stand des letzten Abgleichs, für die Anzeige ohne Netz) ---

    fun saveFolders(accessId: Int, folders: ScoreFolders) {
        dir.mkdirs()
        val tmp = File(dir, "mappen-$accessId.tmp")
        tmp.writeBytes(DraftCrypto.seal(alias, json.encodeToString(ScoreFolders.serializer(), folders).toByteArray()))
        LocalAccountData.replace(tmp, File(dir, "mappen-$accessId.bin"))
    }

    /** Gibt es einen gespeicherten Stand (ohne Entschlüsseln)? */
    fun hasFolders(accessId: Int): Boolean = File(dir, "mappen-$accessId.bin").exists()

    /** Langsam (entschlüsseln): nicht im UI-Thread. */
    fun folders(accessId: Int): ScoreFolders? = runCatching {
        val file = File(dir, "mappen-$accessId.bin")
        if (!file.exists()) null else json.decodeFromString(ScoreFolders.serializer(), String(DraftCrypto.open(alias, file.readBytes())))
    }.getOrNull()

    // --- Dateien ---

    private fun fileOf(accessId: Int, score: ScoreFile, variant: String) =
        File(dir, "d-$accessId-${score.id}-v${score.version}-${safe(variant)}.bin")

    fun has(accessId: Int, score: ScoreFile, variant: String): Boolean = fileOf(accessId, score, variant).exists()

    fun save(accessId: Int, score: ScoreFile, variant: String, data: ByteArray) {
        dir.mkdirs()
        val target = fileOf(accessId, score, variant)
        val tmp = File(dir, target.name.removeSuffix(".bin") + ".tmp")
        tmp.writeBytes(DraftCrypto.seal(alias, data))
        LocalAccountData.replace(tmp, target)
    }

    /** Entschlüsselte Datei oder null. Langsam: nicht im UI-Thread. */
    fun read(accessId: Int, score: ScoreFile, variant: String): ByteArray? = runCatching {
        val file = fileOf(accessId, score, variant)
        if (!file.exists()) null else DraftCrypto.open(alias, file.readBytes())
    }.getOrNull()

    /**
     * Nach einem Abgleich: Dateien entfernen, die in [folders] nicht mehr vorkommen oder deren
     * Fassung sich geändert hat. Liefert die Zahl gelöschter Dateien.
     */
    fun prune(accessId: Int, folders: ScoreFolders): Int {
        val keep = folders.mappen.flatMap { m -> m.items.flatMap { it.scores } }.map { "d-$accessId-${it.id}-v${it.version}-" }.toSet()
        return dir.listFiles().orEmpty().count { file ->
            val name = file.name
            name.startsWith("d-$accessId-") && keep.none { name.startsWith(it) } && file.delete()
        }
    }

    /** Alles eines Zugangs, der nicht mehr Musiker ist (oder nicht mehr besteht). */
    fun forgetAccess(accessId: Int) {
        dir.listFiles().orEmpty().filter { it.name.startsWith("d-$accessId-") || it.name.startsWith("mappen-$accessId.") }.forEach { it.delete() }
    }

    /** Sind alle Notenbilder der Mappe schon auf dem Gerät? */
    fun complete(accessId: Int, mappe: Mappe): Boolean =
        mappe.pieces.flatMap { it.sheets }.all { has(accessId, it, it.sheetFormat) }

    /** Zugänge, für die etwas gespeichert ist. */
    fun storedAccesses(): Set<Int> = dir.listFiles().orEmpty().mapNotNull { f ->
        Regex("^(?:mappen|d)-(\\d+)[.-]").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
    }.toSet()

    fun bytes(): Long = dir.listFiles().orEmpty().sumOf { it.length() }

    fun clearFiles() {
        dir.listFiles().orEmpty().filter { it.name.startsWith("d-") }.forEach { it.delete() }
    }

    // --- Merken auf dem Gerät (Konzept 4.7): Favoriten, zuletzt gespielt, Stelle, behalten ---

    fun favorites(accessId: Int): Set<Int> = ids("fav-$accessId").toSet()

    fun toggleFavorite(accessId: Int, arrangement: Int) {
        val now = favorites(accessId)
        putIds("fav-$accessId", if (arrangement in now) (now - arrangement).toList() else (now + arrangement).toList())
    }

    fun recent(accessId: Int): List<Int> = ids("recent-$accessId")

    fun played(accessId: Int, arrangement: Int) {
        putIds("recent-$accessId", (listOf(arrangement) + recent(accessId).filter { it != arrangement }).take(RECENT))
    }

    /** Feste Mappen, die dauerhaft auf dem Gerät bleiben sollen („Für unterwegs laden“). */
    fun kept(accessId: Int): Set<String> = prefs.getStringSet("keep-$accessId", emptySet()).orEmpty()

    fun keep(accessId: Int, mappeKey: String, on: Boolean) {
        val now = kept(accessId)
        prefs.edit().putStringSet("keep-$accessId", if (on) now + mappeKey else now - mappeKey).apply()
    }

    fun position(accessId: Int, mappeKey: String): PultStelle? {
        val raw = prefs.getString("pos-$accessId-$mappeKey", null) ?: return null
        val parts = raw.split(":").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 2) PultStelle(parts[0], parts[1]) else null
    }

    fun savePosition(accessId: Int, mappeKey: String, at: PultStelle) {
        prefs.edit().putString("pos-$accessId-$mappeKey", "${at.piece}:${at.page}").apply()
    }

    private fun ids(key: String): List<Int> =
        prefs.getString(key, "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }

    private fun putIds(key: String, ids: List<Int>) {
        prefs.edit().putString(key, ids.joinToString(",")).apply()
    }

    fun forgetPrefs() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val FOLDER = "noten"
        private const val RECENT = 12

        /** Termin-Mappen so lange vorher automatisch laden (Konzept E10: am Vortag). */
        const val PREFETCH_HOURS = 36L

        fun safe(s: String) = s.filter { it.isLetterOrDigit() || it == '-' }.take(40).ifEmpty { "o" }

        /** Beim Abmelden: Dateien, Merkliste und offene Kopien löschen. */
        fun forget(context: Context, accountKey: String) {
            LocalAccountData.dir(context, FOLDER, accountKey).deleteRecursively()
            context.getSharedPreferences("noten-" + LocalAccountData.id(accountKey), Context.MODE_PRIVATE).edit().clear().apply()
            PultDateien.clear(context)
        }

        /** Soll diese Termin-Mappe jetzt vorgeladen werden? */
        fun dueForPrefetch(mappe: Mappe, now: OffsetDateTime = OffsetDateTime.now()): Boolean {
            if (!mappe.isEvent || mappe.cancelled) return false
            val start = runCatching { OffsetDateTime.parse(mappe.startsAt) }.getOrNull() ?: return false
            return start.isAfter(now.minusHours(6)) && start.isBefore(now.plusHours(PREFETCH_HOURS))
        }
    }
}

/**
 * Entschlüsselte Kopie einer PDF-Datei, nur solange das Notenpult sie zeigt (der Android-Renderer
 * braucht eine Datei). Liegt im Cache ohne Sicherung und wird beim Schließen und beim Start gelöscht.
 */
object PultDateien {
    private const val DIR = "notenpult"

    fun write(context: Context, data: ByteArray, suffix: String = ".pdf"): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        return File.createTempFile("n", suffix, dir).apply { writeBytes(data) }
    }

    fun clear(context: Context) {
        File(context.cacheDir, DIR).deleteRecursively()
    }
}
