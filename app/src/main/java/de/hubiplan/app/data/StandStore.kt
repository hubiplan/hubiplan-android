package de.hubiplan.app.data

import android.content.Context
import java.io.File
import java.time.OffsetDateTime

/**
 * Standzugänge, die diese App angelegt hat, bis zu ihrem Ablauf (Ende des Wettbewerbstags).
 * Der Standmodus hält noch nicht übertragene Eingaben je Standzugang; öffnet die App denselben
 * Wettbewerb erneut, nimmt sie deshalb denselben Zugang statt eines neuen. Gelöscht beim
 * Beenden, nach Ablauf und beim Abmelden des Kontos.
 *
 * Der Zugang enthält einen gültigen Schlüssel für den Standmodus. Er liegt deshalb wie
 * Entwürfe und Ausweise verschlüsselt im Ordner des Kontos (Sicherheitsprüfung 26.11.2026,
 * SEC-03), nicht mehr im Klartext in den Einstellungen.
 */
object StandStore {
    const val FOLDER = "standzugaenge"

    /** Ablage bis App 1.12 (Klartext); wird beim ersten Zugriff gelöscht. */
    private const val LEGACY_PREFS = "standzugaenge"

    private fun dir(context: Context, accountKey: String) = LocalAccountData.dir(context, FOLDER, accountKey)

    private fun file(context: Context, accountKey: String, unitId: Int, competition: Int) =
        File(dir(context, accountKey), "s-$unitId-$competition.bin")

    private fun dropLegacy(context: Context) {
        runCatching { context.deleteSharedPreferences(LEGACY_PREFS) }
    }

    private fun read(alias: String, file: File): StandAccess? = runCatching {
        json.decodeFromString<StandAccess>(String(DraftCrypto.open(alias, file.readBytes())))
    }.getOrNull()

    fun get(context: Context, accountKey: String, unitId: Int, competition: Int, now: OffsetDateTime = OffsetDateTime.now()): StandAccess? {
        dropLegacy(context)
        prune(context, accountKey, now)
        val file = file(context, accountKey, unitId, competition)
        if (!file.exists()) return null
        return read(LocalAccountData.alias(accountKey), file)?.takeIf { it.token != null }
    }

    fun put(context: Context, accountKey: String, unitId: Int, competition: Int, access: StandAccess) {
        if (access.token == null) return
        dropLegacy(context)
        runCatching {
            val target = file(context, accountKey, unitId, competition)
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".tmp")
            tmp.writeBytes(DraftCrypto.seal(LocalAccountData.alias(accountKey), json.encodeToString(StandAccess.serializer(), access).toByteArray()))
            LocalAccountData.replace(tmp, target)
        }
    }

    fun remove(context: Context, accountKey: String, unitId: Int, competition: Int) {
        file(context, accountKey, unitId, competition).delete()
    }

    /** Ist der Zugang noch gültig? Unlesbares Datum gilt als abgelaufen. */
    fun valid(access: StandAccess, now: OffsetDateTime): Boolean =
        runCatching { OffsetDateTime.parse(access.expiresAt).isAfter(now) }.getOrDefault(false)

    /** Abgelaufene und unlesbare Zugänge dieses Kontos löschen. */
    private fun prune(context: Context, accountKey: String, now: OffsetDateTime) {
        val alias = LocalAccountData.alias(accountKey)
        for (file in dir(context, accountKey).listFiles().orEmpty()) {
            val access = if (file.name.endsWith(".bin")) read(alias, file) else null
            if (access == null || !valid(access, now)) file.delete()
        }
    }

    /** Abmelden: Zugänge dieses Kontos vergessen (auch die frühere Klartext-Ablage). */
    fun forget(context: Context, accountKey: String) {
        dir(context, accountKey).deleteRecursively()
        dropLegacy(context)
    }
}
