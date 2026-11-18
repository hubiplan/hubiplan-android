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
    private val FOLDERS = listOf("entwuerfe", "quittungen", "ausweise", ClubLogo.FOLDER, FormAttachments.FOLDER, OfflineStore.FOLDER, ReadStore.FOLDER)

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
        File(context.cacheDir, WALLET_PASS).deleteRecursively()
        Ansicht.clear(context)
        DraftCrypto.deleteKey(alias(accountKey))
        StandStore.forget(context, accountKey)
        LastUnitStore.forget(context, accountKey)
        NotenStore.forget(context, accountKey)
    }

    /**
     * Konto ohne Rückfrage entfernt (Gerät auf dem Server abgemeldet, Audit 31.10. C-D01):
     * Ausweise löschen; noch nicht gesendete Buchungen und Quittungen nicht vernichten, sondern
     * verschlüsselt in die Quarantäne legen. Der Schlüssel bleibt nur, wenn dort etwas liegt.
     */
    fun retire(context: Context, accountKey: String, server: String = "", email: String = "") {
        dir(context, "ausweise", accountKey).deleteRecursively()
        dir(context, OfflineStore.FOLDER, accountKey).deleteRecursively()
        dir(context, ReadStore.FOLDER, accountKey).deleteRecursively()
        // Noten nie behalten, wenn das Gerät abgemeldet ist (Konzept Noten, Abschnitt 5)
        NotenStore.forget(context, accountKey)
        LastUnitStore.forget(context, accountKey)
        ReceiptCache.forget(context.cacheDir, accountKey)
        File(context.cacheDir, WALLET_PASS).deleteRecursively()
        Ansicht.clear(context)
        var kept = false
        for (folder in listOf("entwuerfe", "quittungen")) {
            val source = dir(context, folder, accountKey)
            val target = File(File(context.filesDir, QUARANTINE), "$folder-k-${id(accountKey)}")
            if (quarantine(source, target, server, email.lowercase(), alias(accountKey), System.currentTimeMillis())) kept = true
        }
        if (!kept) DraftCrypto.deleteKey(alias(accountKey))
    }

    /**
     * Vorgänge aus [source] nach [target] in die Quarantäne legen. `.konto` wird zuerst
     * geschrieben: Endet die App mitten im Verschieben, liegt nie eine Quarantäne ohne
     * Eigentümer da. Liegen dort schon Vorgänge desselben Kontos, bleibt deren älteres `since`,
     * damit sie nicht über [FINAL_DAYS] hinaus verlängert werden. Rückgabe: ob noch etwas mit
     * [alias] verschlüsselt ist (in der Quarantäne oder nicht verschiebbar in [source]).
     */
    internal fun quarantine(
        source: File,
        target: File,
        server: String,
        email: String,
        alias: String,
        now: Long,
        move: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
    ): Boolean {
        val files = entries(source)
        if (files.isEmpty()) {
            source.deleteRecursively()
            return entries(target).isNotEmpty()
        }
        target.mkdirs()
        val earlier = owner(target)
        val since = when {
            earlier != null && earlier.server.equals(server, ignoreCase = true) &&
                earlier.email.equals(email, ignoreCase = true) -> minOf(now, earlier.since)
            // Schon Vorgänge ohne lesbaren Eigentümer da: deren Alter nicht zurücksetzen
            earlier == null && entries(target).isNotEmpty() -> minOf(now, lastChange(target) ?: now)
            else -> now
        }
        // Wem die Belege gehören, damit sie nach erneuter Anmeldung zurückkommen (Fachfrage 16)
        File(target, OWNER).writeText(listOf(server, email, alias, since).joinToString("\n"))
        for (file in files) {
            val dest = File(target, file.name)
            if (!dest.exists()) move(file, dest)
        }
        val kept = entries(target).isNotEmpty()
        if (!kept) {
            File(target, OWNER).delete()
            target.delete()
        }
        val left = entries(source).isNotEmpty()
        if (!left) source.deleteRecursively()
        return kept || left
    }

    private const val OWNER = ".konto"

    /** Cache-Ordner für den zuletzt geöffneten Wallet-Pass (beim nächsten Öffnen überschrieben, nach etwa einer Stunde gelöscht). */
    const val WALLET_PASS = "ausweis"

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
        val inUse = quarantine.listFiles().orEmpty().mapNotNull { keyAlias(it, owner(it)) }.toSet()
        (freed - inUse).forEach { DraftCrypto.deleteKey(it) }
        return moved
    }

    /** Letzte Änderung des Ordners; null, wenn sie sich nicht bestimmen lässt. */
    private fun lastChange(box: File): Long? = box.lastModified().takeIf { it > 0L }

    /**
     * Seit wann die Quarantäne besteht: aus `.konto`, sonst (fehlt oder unlesbar, z. B. nach
     * einem Absturz) die letzte Änderung des Ordners. null: unbekannt, dann gilt sie nie als alt.
     */
    private fun since(box: File): Long? = owner(box)?.since ?: lastChange(box)

    private fun olderThan(box: File, days: Long, now: Long): Boolean =
        since(box)?.let { it < now - days * DAY_MS } ?: false

    /**
     * Schlüssel der Quarantäne: aus `.konto`, sonst aus dem Ordnernamen (`<art>-k-<id>`, wie
     * [alias]); null für Altbestände ohne Kontokennung.
     */
    private fun keyAlias(box: File, who: Owner?): String? =
        who?.alias ?: box.name.substringAfter("-k-", "").takeIf { it.isNotEmpty() }?.let { "hubiplan-daten-$it" }

    private fun entries(box: File) = box.listFiles().orEmpty().filter { it.name != OWNER && !it.name.endsWith(".tmp") }

    /** Ergebnis von [sweep]: endgültig gelöscht nach [FINAL_DAYS] bzw. weil unlesbar. */
    data class Sweep(val expired: Int, val unreadable: Int)

    /**
     * Beim Start (Fachfrage FQ24): Vorgänge älter als [FINAL_DAYS] Tage endgültig löschen, und
     * solche, die sich nie mehr öffnen lassen (Schlüssel weg, z. B. nach dem Entfernen der
     * Bildschirmsperre), sofort. Die App sagt danach, was weg ist.
     */
    fun sweep(context: Context, now: Long = System.currentTimeMillis()): Sweep =
        sweep(context.filesDir, context.cacheDir, now, DraftCrypto::hasKey, DraftCrypto::open, DraftCrypto::deleteKey)

    internal fun sweep(
        filesDir: File,
        cacheDir: File,
        now: Long,
        hasKey: (String) -> Boolean,
        open: (String, ByteArray) -> ByteArray,
        deleteKey: (String) -> Unit,
    ): Sweep {
        val quarantine = File(filesDir, QUARANTINE)
        val freed = mutableSetOf<String>()
        var expired = 0
        var unreadable = 0
        for (box in quarantine.listFiles().orEmpty()) {
            val who = owner(box)
            val count = entries(box).size
            val old = olderThan(box, FINAL_DAYS, now)
            // Ohne lesbares `.konto` nie sofort löschen, nur nach FINAL_DAYS ab der letzten Änderung
            val lost = !old && who != null && unreadableBox(box, who.alias, hasKey, open)
            if (!old && !lost) continue
            if (box.deleteRecursively()) {
                if (old) expired += count else unreadable += count
                keyAlias(box, who)?.let { freed += it }
            }
        }
        val inUse = quarantine.listFiles().orEmpty().mapNotNull { keyAlias(it, owner(it)) }.toSet()
        (freed - inUse).forEach(deleteKey)
        File(cacheDir, EXPORT).deleteRecursively()
        return Sweep(expired, unreadable)
    }

    /**
     * Nur nachgewiesener Schlüsselverlust betrifft den ganzen Ordner. Ein ungültiger
     * GCM-Tag kann eine einzelne beschädigte Datei sein; sie und ihre Nachbarn bleiben
     * zur Rettung in der Quarantäne. Auch bei gesperrtem Telefon nichts löschen.
     */
    private fun unreadableBox(
        box: File,
        alias: String,
        hasKey: (String) -> Boolean,
        open: (String, ByteArray) -> ByteArray,
    ): Boolean {
        if (!hasKey(alias)) return true
        val first = entries(box).firstOrNull() ?: return false
        return try {
            open(alias, first.readBytes())
            false
        } catch (e: Exception) {
            DraftCrypto.isPermanent(e)
        }
    }

    /** Wann die ältesten Vorgänge der Rückfrage endgültig gelöscht werden (null: keine alten). */
    fun finalDeletion(context: Context, now: Long = System.currentTimeMillis()): Long? = finalDeletion(context.filesDir, now)

    internal fun finalDeletion(filesDir: File, now: Long): Long? =
        File(filesDir, QUARANTINE).listFiles().orEmpty()
            .filter { olderThan(it, STALE_DAYS, now) }
            .mapNotNull(::since)
            .minOrNull()
            ?.let { it + FINAL_DAYS * DAY_MS }

    /** Ordner im Cache für den Export (geteilt über FileProvider, nach etwa einer Stunde und beim nächsten Start gelöscht). */
    const val EXPORT = "export"

    /**
     * Alte Vorgänge (älter als [STALE_DAYS]) entschlüsselt als JSON-Datei, um sie zu teilen
     * oder zu speichern, bevor sie gelöscht werden (Fachfrage FQ24). Rückgabe: Datei und Zahl
     * der Vorgänge darin; null, wenn sich nichts öffnen ließ.
     */
    fun exportStale(context: Context, now: Long = System.currentTimeMillis()): Pair<File, Int>? {
        val items = mutableListOf<JsonElement>()
        for (box in File(context.filesDir, QUARANTINE).listFiles().orEmpty()) {
            if (!olderThan(box, STALE_DAYS, now)) continue
            val who = owner(box)
            val alias = keyAlias(box, who) ?: DraftCrypto.LEGACY_ALIAS
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
        GeteilteDateien.schedule(context)
        return file to items.size
    }

    /** Vorgänge in der Quarantäne, die älter als [STALE_DAYS] Tage sind. */
    fun staleCount(context: Context, now: Long = System.currentTimeMillis()): Int = staleCount(context.filesDir, now)

    internal fun staleCount(filesDir: File, now: Long): Int =
        File(filesDir, QUARANTINE).listFiles().orEmpty()
            .filter { olderThan(it, STALE_DAYS, now) }
            .sumOf { entries(it).size }

    /** Nach ausdrücklicher Zustimmung: alte Vorgänge aus der Quarantäne löschen. */
    fun discardStale(context: Context, now: Long = System.currentTimeMillis()) {
        val quarantine = File(context.filesDir, QUARANTINE)
        val freed = mutableSetOf<String>()
        for (box in quarantine.listFiles().orEmpty()) {
            if (!olderThan(box, STALE_DAYS, now)) continue
            val who = owner(box)
            if (box.deleteRecursively()) keyAlias(box, who)?.let { freed += it }
        }
        // Schlüssel gelöschter Konten freigeben (den Altschlüssel räumt migrate() auf)
        val inUse = quarantine.listFiles().orEmpty().mapNotNull { keyAlias(it, owner(it)) }.toSet()
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
