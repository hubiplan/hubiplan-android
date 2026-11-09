package de.hubiplan.app.data

import android.content.Context

/**
 * Zuletzt gewählte Einheit der Verwaltung je Konto, damit die Verwaltung beim nächsten Öffnen
 * dort startet (Ralf 09.11.). Nur auf dem Gerät; beim Abmelden des Kontos gelöscht.
 */
object LastUnitStore {
    private const val PREFS = "verwaltung"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(accountKey: String) = "einheit:${LocalAccountData.id(accountKey)}"

    fun get(context: Context, accountKey: String): String? = prefs(context).getString(key(accountKey), null)

    fun put(context: Context, accountKey: String, unitKey: String) {
        prefs(context).edit().putString(key(accountKey), unitKey).apply()
    }

    /** Abmelden: gemerkte Einheit dieses Kontos vergessen. */
    fun forget(context: Context, accountKey: String) {
        prefs(context).edit().remove(key(accountKey)).apply()
    }

    /** Start-Einheit: die gemerkte, solange es sie noch gibt, sonst die erste. */
    fun startUnit(units: List<AdminUnit>, saved: String?): AdminUnit =
        units.firstOrNull { it.key == saved } ?: units.first()
}
