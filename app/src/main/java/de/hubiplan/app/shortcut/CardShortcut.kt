package de.hubiplan.app.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import de.hubiplan.app.MainActivity
import de.hubiplan.app.R
import de.hubiplan.app.tr

/**
 * App-Verknüpfung „Ausweis zeigen“ (lange auf das App-Symbol drücken, Konzept app-ich): öffnet
 * den Reiter „Ich“ mit dem großen Ausweis des gewählten Kontos. Nur, solange ein Zugang einen
 * Ausweis hat.
 */
object CardShortcut {
    const val ID = "ausweis-zeigen"
    const val ACTION = "de.hubiplan.app.AUSWEIS_ZEIGEN"

    fun update(context: Context, show: Boolean) {
        if (!show) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(ID))
            return
        }
        val intent = Intent(context, MainActivity::class.java).setAction(ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val info = ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(tr("Ausweis"))
            .setLongLabel(tr("Ausweis zeigen"))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_ausweis))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, info)
    }
}
