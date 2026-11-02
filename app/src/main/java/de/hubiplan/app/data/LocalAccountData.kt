package de.hubiplan.app.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import de.hubiplan.app.tr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Lokale Daten eines Kontos (Entwürfe, Quittungen, Ausweise) eindeutig getrennt
 * (Gesamtaudit 30.10., AND-S02): Ordnername und Keystore-Schlüssel kommen aus einer Prüfsumme
 * der vollständigen Kontokennung (Server und Gerät), nicht aus gekürzten Buchstaben. Beim
 * Abmelden werden Ordner und Schlüssel gelöscht.
 */
object LocalAccountData {
    private val FOLDERS = listOf("entwuerfe", "quittungen", "ausweise", ClubLogo.FOLDER)

    /** Kontokennung wie bisher: Serveradresse und Geräte-Nummer. */
    fun key(server: String, deviceId: Int) = "$server-$deviceId"

    fun id(accountKey: String): String =
        MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    fun dir(context: Context, folder: String, accountKey: String) = File(context.filesDir, "$folder/k-${id(accountKey)}")

    fun alias(accountKey: String) = "hubiplan-daten-${id(accountKey)}"

    /** Bisheriger Ordnername (bis App 0.18): konnte bei verschiedenen Konten gleich sein. */
    private fun legacyName(accountKey: String) = accountKey.filter { it.isLetterOrDigit() }.take(60)

    /** Abmelden: alle lokalen Daten dieses Kontos und seinen Schlüssel löschen. */
    fun forget(context: Context, accountKey: String) {
        FOLDERS.forEach { dir(context, it, accountKey).deleteRecursively() }
        ReceiptCache.forget(context.cacheDir, accountKey)
        DraftCrypto.deleteKey(alias(accountKey))
    }

    /**
     * Konto ohne Rückfrage entfernt (Gerät auf dem Server abgemeldet, Audit 31.10. C-D01):
     * Ausweise löschen; noch nicht gesendete Buchungen und Quittungen nicht vernichten, sondern
     * verschlüsselt in die Quarantäne legen. Der Schlüssel bleibt nur, wenn dort etwas liegt.
     */
    fun retire(context: Context, accountKey: String, server: String = "", email: String = "") {
        dir(context, "ausweise", accountKey).deleteRecursively()
        ReceiptCache.forget(context.cacheDir, accountKey)
        var kept = false
        for (folder in listOf("entwuerfe", "quittungen")) {
            val source = dir(context, folder, accountKey)
            val files = source.listFiles().orEmpty().filter { !it.name.endsWith(".tmp") }
            if (files.isEmpty()) {
                source.deleteRecursively()
                continue
            }
            val target = File(File(context.filesDir, QUARANTINE), "$folder-k-${id(accountKey)}").apply { mkdirs() }
            for (file in files) {
                val dest = File(target, file.name)
                if (!dest.exists()) file.renameTo(dest)
            }
            kept = true
            // Wem die Belege gehören, damit sie nach erneuter Anmeldung zurückkommen (Fachfrage 16)
            File(target, OWNER).writeText(listOf(server, email.lowercase(), alias(accountKey), System.currentTimeMillis()).joinToString("\n"))
            if (source.listFiles().orEmpty().none { !it.name.endsWith(".tmp") }) source.deleteRecursively()
        }
        if (!kept) DraftCrypto.deleteKey(alias(accountKey))
    }

    private const val OWNER = ".konto"

    /** Nach dieser Zeit fragt die App, ob alte Belege aus der Quarantäne gelöscht werden dürfen. */
    const val STALE_DAYS = 90L

    /**
     * Spätestens nach dieser Zeit löscht die App sie auch ohne Zustimmung (Fachfrage FQ24,
     * Audit 02.11.): 30 Tage nach der ersten Rückfrage, in der man sie exportieren kann.
     */
    const val FINAL_DAYS = 120L

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private data class Owner(val server: String, val email: String, val alias: String, val since: Long)

    private fun owner(dir: File): Owner? = runCatching {
        val lines = File(dir, OWNER).readLines()
        Owner(lines[0], lines[1], lines[2], lines[3].toLong())
    }.getOrNull()

    /**
     * Erneut angemeldet (Fachfrage 16, Audit 31.10.): Belege desselben Mitglieds auf derselben
     * Instanz aus der Quarantäne in das neue Konto übernehmen, damit die App sie senden kann.
     * Rückgabe: Zahl der übernommenen Vorgänge.
     */
    fun adopt(context: Context, accountKey: String, server: String, email: String): Int {
        val quarantine = File(context.filesDir, QUARANTINE)
        var moved = 0
        val freed = mutableSetOf<String>()
        for (box in quarantine.listFiles().orEmpty()) {
            val who = owner(box) ?: continue
            if (who.server != server || who.email != email.lowercase() || who.email.isEmpty()) continue
            val folder = box.name.substringBefore("-k-")
            if (folder !in FOLDERS) continue
            val target = dir(context, folder, accountKey).apply { mkdirs() }
            var complete = true
            for (file in box.listFiles().orEmpty()) {
                if (file.name == OWNER || file.name.endsWith(".tmp")) continue
                val ok = runCatching {
                    val plain = DraftCrypto.open(who.alias, file.readBytes())
                    val tmp = File(target, file.name + ".tmp")
                    tmp.writeBytes(DraftCrypto.seal(alias(accountKey), plain))
                    replace(tmp, File(target, file.name))
                    file.delete()
                }.isSuccess
                if (ok) moved++ else complete = false
            }
            if (complete) {
                box.deleteRecursively()
                freed += who.alias
            }
        }
        // Alten Schlüssel nur löschen, wenn nichts mehr mit ihm verschlüsselt ist
        val inUse = quarantine.listFiles().orEmpty().mapNotNull { owner(it)?.alias }.toSet()
        (freed - inUse).forEach { DraftCrypto.deleteKey(it) }
        return moved
    }

    private fun since(box: File) = owner(box)?.since ?: box.lastModified()

    private fun entries(box: File) = box.listFiles().orEmpty().filter { it.name != OWNER && !it.name.endsWith(".tmp") }

    /** Ergebnis von [sweep]: endgültig gelöscht nach [FINAL_DAYS] bzw. weil unlesbar. */
    data class Sweep(val expired: Int, val unreadable: Int)

    /**
     * Beim Start (Fachfrage FQ24): Vorgänge älter als [FINAL_DAYS] Tage endgültig löschen, und
     * solche, die sich nie mehr öffnen lassen (Schlüssel weg, z. B. nach dem Entfernen der
     * Bildschirmsperre), sofort. Die App sagt danach, was weg ist.
     */
    fun sweep(context: Context, now: Long = System.currentTimeMillis()): Sweep {
        val quarantine = File(context.filesDir, QUARANTINE)
        val freed = mutableSetOf<String>()
        var expired = 0
        var unreadable = 0
        for (box in quarantine.listFiles().orEmpty()) {
            val who = owner(box)
            val count = entries(box).size
            val old = since(box) < now - FINAL_DAYS * DAY_MS
            val lost = !old && unreadableBox(box, who?.alias ?: DraftCrypto.LEGACY_ALIAS)
            if (!old && !lost) continue
            if (box.deleteRecursively()) {
                if (old) expired += count else unreadable += count
                who?.alias?.let { freed += it }
            }
        }
        val inUse = quarantine.listFiles().orEmpty().mapNotNull { owner(it)?.alias }.toSet()
        (freed - inUse).forEach { DraftCrypto.deleteKey(it) }
        File(context.cacheDir, EXPORT).deleteRecursively()
        return Sweep(expired, unreadable)
    }

    /** Unlesbar nur, wenn der Schlüssel fehlt oder nicht zur Datei passt, nie bei gesperrtem Telefon. */
    private fun unreadableBox(box: File, alias: String): Boolean {
        if (!DraftCrypto.hasKey(alias)) return true
        val first = entries(box).firstOrNull() ?: return false
        return try {
            DraftCrypto.open(alias, first.readBytes())
            false
        } catch (e: Exception) {
            DraftCrypto.isPermanent(e)
        }
    }

    /** Wann die ältesten Vorgänge der Rückfrage endgültig gelöscht werden (null: keine alten). */
    fun finalDeletion(context: Context, now: Long = System.currentTimeMillis()): Long? =
        File(context.filesDir, QUARANTINE).listFiles().orEmpty()
            .map(::since)
            .filter { it < now - STALE_DAYS * DAY_MS }
            .minOrNull()
            ?.let { it + FINAL_DAYS * DAY_MS }

    /** Ordner im Cache für den Export (geteilt über FileProvider, beim nächsten Start gelöscht). */
    const val EXPORT = "export"

    /**
     * Alte Vorgänge (älter als [STALE_DAYS]) entschlüsselt als JSON-Datei, um sie zu teilen
     * oder zu speichern, bevor sie gelöscht werden (Fachfrage FQ24). Rückgabe: Datei und Zahl
     * der Vorgänge darin; null, wenn sich nichts öffnen ließ.
     */
    fun exportStale(context: Context, now: Long = System.currentTimeMillis()): Pair<File, Int>? {
        val items = mutableListOf<JsonElement>()
        for (box in File(context.filesDir, QUARANTINE).listFiles().orEmpty()) {
            if (since(box) >= now - STALE_DAYS * DAY_MS) continue
            val who = owner(box)
            val alias = who?.alias ?: DraftCrypto.LEGACY_ALIAS
            for (file in entries(box)) {
                val plain = runCatching { String(DraftCrypto.open(alias, file.readBytes())) }.getOrNull() ?: continue
                val content = runCatching { Json.parseToJsonElement(plain) }.getOrElse { JsonPrimitive(plain) }
                items += buildJsonObject {
                    put("art", box.name.substringBefore("-k-").substringBefore("-"))
                    put("server", who?.server ?: "")
                    put("konto", who?.email ?: "")
                    put("inhalt", content)
                }
            }
        }
        if (items.isEmpty()) return null
        val doc = buildJsonObject {
            put("hinweis", tr("Nicht gesendete Vorgänge aus HUBIplan. Enthält Namen und Beträge; nach der Übergabe an den Verein bitte löschen."))
            put("exportiert", java.time.Instant.ofEpochMilli(now).toString())
            put("vorgaenge", JsonArray(items))
        }
        val dir = File(context.cacheDir, EXPORT).apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "hubiplan-belege.json")
        file.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), doc))
        return file to items.size
    }

    /** Vorgänge in der Quarantäne, die älter als [STALE_DAYS] Tage sind. */
    fun staleCount(context: Context): Int {
        val limit = System.currentTimeMillis() - STALE_DAYS * 24 * 60 * 60 * 1000
        return File(context.filesDir, QUARANTINE).listFiles().orEmpty()
            .filter { (owner(it)?.since ?: it.lastModified()) < limit }
            .sumOf { box -> box.listFiles().orEmpty().count { it.name != OWNER && !it.name.endsWith(".tmp") } }
    }

    /** Nach ausdrücklicher Zustimmung: alte Vorgänge aus der Quarantäne löschen. */
    fun discardStale(context: Context) {
        val limit = System.currentTimeMillis() - STALE_DAYS * 24 * 60 * 60 * 1000
        val quarantine = File(context.filesDir, QUARANTINE)
        val freed = mutableSetOf<String>()
        for (box in quarantine.listFiles().orEmpty()) {
            val who = owner(box)
            if ((who?.since ?: box.lastModified()) >= limit) continue
            if (box.deleteRecursively()) who?.alias?.let { freed += it }
        }
        // Schlüssel gelöschter Konten freigeben (den Altschlüssel räumt migrate() auf)
        val inUse = quarantine.listFiles().orEmpty().mapNotNull { owner(it)?.alias }.toSet()
        (freed - inUse).forEach { DraftCrypto.deleteKey(it) }
    }

    /** Altbestände, die keinem Konto sicher gehören: unverändert verschlüsselt, nie angezeigt. */
    const val QUARANTINE = "quarantaene"

    /**
     * Einmalig beim Start: Ordner im alten Namensschema übernehmen, aber nur, wenn genau ein
     * angemeldetes Konto dazu passt und der Name nicht gekürzt war. Mehrdeutige oder
     * herrenlose Ordner werden nicht gelöscht (Audit 31.10., CL-02), sondern unverändert in
     * die Quarantäne verschoben; der alte Schlüssel bleibt, solange dort noch etwas liegt.
     */
    fun migrate(context: Context, accountKeys: List<String>) {
        val byLegacy = accountKeys.groupBy(::legacyName)
        val quarantine = File(context.filesDir, QUARANTINE)
        for (folder in FOLDERS) {
            val root = File(context.filesDir, folder)
            for (old in root.listFiles().orEmpty()) {
                if (!old.isDirectory || old.name.startsWith("k-")) continue
                val owners = byLegacy[old.name].orEmpty()
                val owner = owners.singleOrNull()?.takeIf { legacyName(it).length < 60 }
                if (owner == null) {
                    val target = File(quarantine, "$folder-${old.name}")
                    quarantine.mkdirs()
                    if (!target.exists() && old.renameTo(target)) continue
                    // Umbenennen ging nicht: Dateien einzeln dazulegen, nichts löschen
                    target.mkdirs()
                    for (file in old.listFiles().orEmpty()) {
                        val dest = File(target, file.name)
                        if (!dest.exists()) file.renameTo(dest)
                    }
                    if (old.listFiles().isNullOrEmpty()) old.delete()
                    continue
                }
                var complete = true
                val target = dir(context, folder, owner).apply { mkdirs() }
                for (file in old.listFiles().orEmpty()) {
                    if (file.name.endsWith(".tmp")) continue
                    val moved = runCatching {
                        val plain = DraftCrypto.openLegacy(file.readBytes())
                        val tmp = File(target, file.name + ".tmp")
                        tmp.writeBytes(DraftCrypto.seal(alias(owner), plain))
                        replace(tmp, File(target, file.name))
                        file.delete()
                    }
                    if (moved.isFailure) complete = false
                }
                // Nicht lesbar (z. B. Telefon gesperrt): beim nächsten Start erneut versuchen
                if (complete) old.deleteRecursively()
            }
        }
        val legacyLeft = FOLDERS.any { folder ->
            File(context.filesDir, folder).listFiles().orEmpty().any { it.isDirectory && !it.name.startsWith("k-") }
        } || quarantine.listFiles().orEmpty().any { !it.name.contains("-k-") }
        if (!legacyLeft) DraftCrypto.deleteKey(DraftCrypto.LEGACY_ALIAS)
    }

    /** Datei sicher ersetzen: erst vollständig schreiben, dann umbenennen (Fehler melden). */
    fun replace(tmp: File, target: File) {
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException(tr("Die Datei ließ sich nicht speichern."))
        }
    }
}
