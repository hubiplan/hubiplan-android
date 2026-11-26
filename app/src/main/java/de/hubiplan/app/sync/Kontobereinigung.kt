package de.hubiplan.app.sync

import android.content.Context
import de.hubiplan.app.data.Account
import de.hubiplan.app.data.LocalAccountData
import de.hubiplan.app.shortcut.CardShortcut
import de.hubiplan.app.shortcut.ClubShortcut
import de.hubiplan.app.ui.Sichtschutz

/**
 * Was auf dem Gerät von einem Konto bleibt, wenn es entfernt wird: eine Stelle für das eigene
 * Abmelden, die Abmeldung durch den Server in der offenen App und die im Hintergrund erkannte
 * (Sicherheitsprüfung 26.11.2026, SEC-03). Vorher räumte der Abgleich im Hintergrund weniger
 * auf als die offene App.
 */
object Kontobereinigung {
    /**
     * Innerhalb des Entfernens des Kontos aufrufen. [byServer]: Der Server hat das Gerät
     * abgemeldet; dann kommen ungesendete Belege verschlüsselt in die Quarantäne (Fachfrage 16),
     * sonst wird alles gelöscht (AND-S02).
     */
    fun local(context: Context, account: Account, byServer: Boolean) {
        runCatching { ClubShortcut.forget(context, account) }
        // Ein anderes Konto mit Ausweis legt die Verknüpfung beim Laden wieder an
        runCatching { CardShortcut.update(context, false) }
        val key = LocalAccountData.key(account.server, account.deviceId)
        runCatching {
            if (byServer) LocalAccountData.retire(context, key, account.server, account.email)
            else LocalAccountData.forget(context, key)
        }
        // Noch wartende Zu-/Absagen dieses Kontos verwerfen (C-F02)
        runCatching { RsvpActionReceiver.cancelFor(context, account) }
    }

    /**
     * Nach dem Entfernen: Speicher der eingebetteten Webansicht (Standmodus) nicht über die
     * Abmeldung hinaus behalten (Gesamtaudit 19.11.2026, S-09). [remaining]: die Konten, die
     * noch verbunden sind.
     */
    fun webData(context: Context, server: String, remaining: List<Account>) {
        Sichtschutz.clearWebData(context, server, othersOnServer = remaining.any { it.server == server }, anyLeft = remaining.isNotEmpty())
    }
}
