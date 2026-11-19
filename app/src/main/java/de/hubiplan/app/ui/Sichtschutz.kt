package de.hubiplan.app.ui

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Sichtschutz (Gesamtaudit 19.11.2026, S-09): keine Bildschirmfotos und keine Vorschau im
 * App-Wechsler, in der ganzen App. Ausnahme ist der groß gezeigte Ausweis, den man vorzeigen
 * und abfotografieren will. Das Mitglied kann den Schutz in den Einstellungen abschalten; im
 * Verwaltungsbereich gilt er immer.
 */
object Sichtschutz {
    private const val PREFS = "sichtschutz"
    private const val KEY_ALLOWED = "bildschirmfotos_erlaubt"

    private var forced = 0
    private var exempt = 0

    fun screenshotsAllowed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ALLOWED, false)

    fun setScreenshotsAllowed(activity: Activity?, context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ALLOWED, on).apply()
        apply(activity)
    }

    /** Verwaltung erzwingt den Schutz; sonst gilt die Einstellung, außer eine Ausnahme ist offen. */
    internal fun secure(allowed: Boolean, forced: Int, exempt: Int): Boolean =
        forced > 0 || (!allowed && exempt == 0)

    fun apply(activity: Activity?) {
        val window = activity?.window ?: return
        if (secure(screenshotsAllowed(activity), forced, exempt)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    internal fun force(activity: Activity?, on: Boolean) {
        forced = (forced + if (on) 1 else -1).coerceAtLeast(0)
        apply(activity)
    }

    internal fun exempt(activity: Activity?, on: Boolean) {
        exempt = (exempt + if (on) 1 else -1).coerceAtLeast(0)
        apply(activity)
    }

    /**
     * Nach dem Abmelden: was die eingebettete Webansicht (Standmodus) gespeichert hat, löschen.
     * [server]: Adresse der Instanz des abgemeldeten Kontos; [othersOnServer]: Ein weiteres Konto
     * nutzt dieselbe Instanz, dann bleibt deren Speicher (dort kann noch etwas Ungesendetes
     * liegen). [anyLeft]: Es gibt noch irgendein Konto; ohne Konto wird alles geleert.
     */
    fun clearWebData(server: String, othersOnServer: Boolean, anyLeft: Boolean) {
        runCatching {
            if (!anyLeft) {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            } else if (!othersOnServer) {
                WebStorage.getInstance().deleteOrigin(server.trimEnd('/'))
            }
        }
    }
}

/** Solange sichtbar: Schutz erzwingen (Verwaltungsbereich), unabhängig von der Einstellung. */
@Composable
fun SichtschutzErzwingen() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        Sichtschutz.force(activity, true)
        onDispose { Sichtschutz.force(activity, false) }
    }
}

/** Solange sichtbar: Ausnahme vom Schutz (groß gezeigter Ausweis). */
@Composable
fun SichtschutzAusnahme() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        Sichtschutz.exempt(activity, true)
        onDispose { Sichtschutz.exempt(activity, false) }
    }
}
