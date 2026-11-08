package de.hubiplan.app.data

import android.content.Context
import java.time.OffsetDateTime

/**
 * Standzugänge, die diese App angelegt hat, bis zu ihrem Ablauf (Ende des Wettbewerbstags).
 * Der Standmodus hält noch nicht übertragene Eingaben je Standzugang; öffnet die App denselben
 * Wettbewerb erneut, nimmt sie deshalb denselben Zugang statt eines neuen. Gelöscht beim
 * Beenden, nach Ablauf und beim Abmelden des Kontos.
 */
object StandStore {
    private const val PREFS = "standzugaenge"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(accountKey: String, unitId: Int, competition: Int) =
        "${LocalAccountData.id(accountKey)}:$unitId:$competition"

    fun get(context: Context, accountKey: String, unitId: Int, competition: Int, now: OffsetDateTime = OffsetDateTime.now()): StandAccess? {
        prune(context, now)
        val raw = prefs(context).getString(key(accountKey, unitId, competition), null) ?: return null
        return runCatching { json.decodeFromString<StandAccess>(raw) }.getOrNull()?.takeIf { it.token != null }
    }

    fun put(context: Context, accountKey: String, unitId: Int, competition: Int, access: StandAccess) {
        if (access.token == null) return
        prefs(context).edit().putString(key(accountKey, unitId, competition), json.encodeToString(StandAccess.serializer(), access)).apply()
    }

    fun remove(context: Context, accountKey: String, unitId: Int, competition: Int) {
        prefs(context).edit().remove(key(accountKey, unitId, competition)).apply()
    }

    /** Ist der Zugang noch gültig? Unlesbares Datum gilt als abgelaufen. */
    fun valid(access: StandAccess, now: OffsetDateTime): Boolean =
        runCatching { OffsetDateTime.parse(access.expiresAt).isAfter(now) }.getOrDefault(false)

    private fun prune(context: Context, now: OffsetDateTime) {
        val p = prefs(context)
        val stale = p.all.filter { (_, v) ->
            val a = (v as? String)?.let { runCatching { json.decodeFromString<StandAccess>(it) }.getOrNull() }
            a == null || !valid(a, now)
        }.keys
        if (stale.isNotEmpty()) p.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    /** Abmelden: Zugänge dieses Kontos vergessen. */
    fun forget(context: Context, accountKey: String) {
        val prefix = "${LocalAccountData.id(accountKey)}:"
        val p = prefs(context)
        val keys = p.all.keys.filter { it.startsWith(prefix) }
        if (keys.isNotEmpty()) p.edit().apply { keys.forEach { remove(it) } }.apply()
    }
}
