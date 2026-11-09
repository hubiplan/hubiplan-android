package de.hubiplan.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.hubiplan.app.MainActivity
import de.hubiplan.app.R
import de.hubiplan.app.tr

/** Benachrichtigungskanäle (je Art einzeln abschaltbar in den Android-Einstellungen). */
object Notifications {
    const val CHANNEL_RSVP = "rueckmeldung"
    const val CHANNEL_MESSAGES = "mitteilungen"
    const val CHANNEL_TODOS = "aufgaben"
    const val EXTRA_TODO = "aufgabe"
    const val EXTRA_TODO_SCOPE = "aufgabe_bereich"
    const val EXTRA_TODO_UNIT = "aufgabe_einheit"
    const val EXTRA_ACCESS = "zugang"
    const val EXTRA_CIRCULAR = "mitteilung"
    const val EXTRA_SLOT = "konto"
    const val EXTRA_ACCOUNT = "hinweis_konto"

    fun tag(accountId: String) = "hubiplan-konto-$accountId"

    fun cancelFor(context: Context, account: de.hubiplan.app.data.Account) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val accountTag = tag(RsvpActionReceiver.accountId(account))
        for (notification in manager.activeNotifications) {
            if (notification.tag == accountTag) manager.cancel(accountTag, notification.id)
        }
    }

    fun openUri(accountId: String, kind: String, access: Int, item: Int): Uri =
        Uri.Builder().scheme("hubiplan-hinweis").authority(accountId)
            .appendPath(kind).appendPath(access.toString()).appendPath(item.toString()).build()
    /** Verknüpfung auf dem Startbildschirm: Prüfsumme des Kontos (Platz kann sich ändern). */
    const val EXTRA_SHORTCUT_ACCOUNT = "verknuepfung_konto"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TODOS, tr("Aufgaben"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = tr("Erinnerungen und Freigaben für Aufgaben")
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, tr("Mitteilungen"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Mitteilungen und Rundschreiben des Vorstands")
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RSVP, tr("Rückmeldung fehlt"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = tr("Hinweis vor dem Anmeldeschluss, wenn du noch nicht zu- oder abgesagt hast")
            },
        )
        // Vor Kontotags erzeugte Hinweise haben keine überprüfbare Kontozuordnung.
        // Einmalig nur unsere beiden Kanäle bereinigen, keine fremden Hinweise.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("kontotags_migriert", false)) {
            for (notification in manager.activeNotifications) {
                if (notification.tag == null && notification.notification.channelId in setOf(CHANNEL_RSVP, CHANNEL_MESSAGES)) {
                    manager.cancel(notification.id)
                }
            }
            prefs.edit().putBoolean("kontotags_migriert", true).apply()
        }
    }

    private const val PREFS = "hinweise"

    /** Nur zur gesicherten Detailansicht navigieren; keine Aktion umgeht die Entsperrung. */
    fun showTodo(context: Context, slot: Int, accountId: String, target: de.hubiplan.app.data.TodoTarget, title: String, text: String) {
        if (!allowed(context)) return
        val kind = "todo-${target.scope}"
        val id = (notificationId(slot, target.unitId, target.todoId) xor
            when (target.scope) { "p" -> 0x1000_0000; "d" -> 0x0800_0000; else -> 0x0400_0000 }) and 0x3FFFFFFF
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .setData(openUri(accountId, kind, target.unitId, target.todoId))
                .putExtra(EXTRA_ACCOUNT, accountId)
                .putExtra(EXTRA_TODO, target.todoId)
                .putExtra(EXTRA_TODO_SCOPE, target.scope)
                .putExtra(EXTRA_TODO_UNIT, target.unitId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_TODOS)
            // Während das Notenpult offen ist: still, ohne Einblenden (Noten in der App, E6)
            .setSilent(de.hubiplan.app.data.PultOffen.offen)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { tr("Aufgabe") })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .lockScreen(context, CHANNEL_TODOS, tr("Neuer Aufgabenhinweis"))
            .build()
        try { NotificationManagerCompat.from(context).notify(tag(accountId), id, notification) } catch (e: SecurityException) {
            // Berechtigung inzwischen entzogen.
        }
    }
    private const val KEY_CONTENT_LOCKED = "inhalt_gesperrt"

    /**
     * Inhalt auch auf dem gesperrten Bildschirm zeigen? Standard nein (Härtung H3): dort steht dann
     * nur „Neue Mitteilung in HUBIplan“, der Inhalt erst nach dem Entsperren. Gilt für das ganze Gerät.
     */
    fun contentWhenLocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CONTENT_LOCKED, false)

    fun setContentWhenLocked(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CONTENT_LOCKED, on).apply()
    }

    /** Sperrbildschirm: privat mit neutraler Fassung, außer das Mitglied will den Inhalt sehen. */
    private fun NotificationCompat.Builder.lockScreen(context: Context, channel: String, neutral: String): NotificationCompat.Builder {
        if (contentWhenLocked(context)) return setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("HUBIplan")
            .setContentText(neutral)
            .build()
        return setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
    }

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Hinweis mit Knöpfen Zusage/Absage; die Antwort geht ohne Öffnen der App an HUBIplan. */
    fun showRsvpMissing(
        context: Context,
        slot: Int,
        accountId: String,
        accessId: Int,
        eventId: Int,
        title: String,
        text: String,
        logo: Bitmap? = null,
    ) {
        if (!allowed(context)) return
        val id = notificationId(slot, accessId, eventId)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setData(openUri(accountId, "rsvp", accessId, eventId))
                .putExtra(EXTRA_ACCOUNT, accountId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_SLOT, slot),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(status: String, label: String, request: Int) = NotificationCompat.Action(
            0,
            label,
            PendingIntent.getBroadcast(
                context,
                request,
                RsvpActionReceiver.intent(context, slot, accountId, accessId, eventId, status, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RSVP)
            // Während das Notenpult offen ist: still, ohne Einblenden (Noten in der App, E6)
            .setSilent(de.hubiplan.app.data.PultOffen.offen)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Rückmeldung fehlt: {title}", "title" to title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            // Wappen der Einheit als großes Bild (das kleine Symbol muss einfarbig sein und bleibt HUBIplan)
            .setLargeIcon(logo)
            .setAutoCancel(true)
            .addAction(action("yes", tr("Zusage"), id * 2))
            .addAction(action("no", tr("Absage"), id * 2 + 1))
            .lockScreen(context, CHANNEL_RSVP, tr("Rückmeldung fehlt"))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag(accountId), id, notification)
        } catch (e: SecurityException) {
            // Erlaubnis inzwischen entzogen
        }
    }

    /** Mitteilung des Vorstands; Antippen öffnet sie in der App. */
    fun showCircular(
        context: Context,
        slot: Int,
        accountId: String,
        accessId: Int,
        circularId: Int,
        title: String,
        text: String,
        from: String,
        short: Boolean,
        logo: Bitmap? = null,
    ) {
        if (!allowed(context)) return
        val id = (notificationId(slot, accessId, circularId) xor 0x2000_0000) and 0x3FFFFFFF
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setData(openUri(accountId, "circular", accessId, circularId))
                .putExtra(EXTRA_ACCOUNT, accountId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_SLOT, slot)
                .putExtra(EXTRA_ACCESS, accessId)
                .putExtra(EXTRA_CIRCULAR, circularId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = if (short) text else text.ifBlank { tr("Neues Rundschreiben") }
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            // Während das Notenpult offen ist: still, ohne Einblenden (Noten in der App, E6)
            .setSilent(de.hubiplan.app.data.PultOffen.offen)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { tr("Mitteilung") })
            .setContentText(body)
            .setSubText(from.ifBlank { null })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            // Wappen der Einheit als großes Bild (das kleine Symbol muss einfarbig sein und bleibt HUBIplan)
            .setLargeIcon(logo)
            .setAutoCancel(true)
            .lockScreen(context, CHANNEL_MESSAGES, tr("Neue Mitteilung"))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag(accountId), id, notification)
        } catch (e: SecurityException) {
        }
    }

    fun showText(context: Context, accountId: String, id: Int, title: String, text: String) {
        if (!allowed(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_RSVP)
            // Während das Notenpult offen ist: still, ohne Einblenden (Noten in der App, E6)
            .setSilent(de.hubiplan.app.data.PultOffen.offen)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .lockScreen(context, CHANNEL_RSVP, tr("Neuer Hinweis"))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag(accountId), id, notification)
        } catch (e: SecurityException) {
        }
    }

    /** Hinweis von HUBIplan (z. B. Foto freigegeben, Änderung übernommen); öffnet die App. */
    fun showInfo(context: Context, slot: Int, accountId: String, accessId: Int, title: String, text: String, logo: Bitmap? = null) {
        if (!allowed(context)) return
        val id = (notificationId(slot, accessId, (title + text).hashCode()) xor 0x1000_0000) and 0x3FFFFFFF
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setData(openUri(accountId, "info", accessId, id))
                .putExtra(EXTRA_ACCOUNT, accountId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_SLOT, slot),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            // Während das Notenpult offen ist: still, ohne Einblenden (Noten in der App, E6)
            .setSilent(de.hubiplan.app.data.PultOffen.offen)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "HUBIplan" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setLargeIcon(logo)
            .setAutoCancel(true)
            .lockScreen(context, CHANNEL_MESSAGES, tr("Neuer Hinweis"))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag(accountId), id, notification)
        } catch (e: SecurityException) {
        }
    }

    /** Kennung je Konto, Zugang und Termin/Mitteilung (Konto 0 wie in früheren Versionen). */
    fun notificationId(slot: Int, accessId: Int, itemId: Int): Int =
        (accessId * 100_003 + itemId + slot * 7_919_993) and 0x3FFFFFFF
}
