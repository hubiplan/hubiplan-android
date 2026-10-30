package de.hubiplan.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.hubiplan.app.MainActivity
import de.hubiplan.app.R

/** Benachrichtigungskanäle (je Art einzeln abschaltbar in den Android-Einstellungen). */
object Notifications {
    const val CHANNEL_RSVP = "rueckmeldung"
    const val CHANNEL_MESSAGES = "mitteilungen"
    const val EXTRA_ACCESS = "zugang"
    const val EXTRA_CIRCULAR = "mitteilung"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Mitteilungen", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Mitteilungen und Rundschreiben des Vorstands"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RSVP, "Rückmeldung fehlt", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Hinweis vor dem Anmeldeschluss, wenn du noch nicht zu- oder abgesagt hast"
            },
        )
    }

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Hinweis mit Knöpfen Zusage/Absage; die Antwort geht ohne Öffnen der App an HUBIplan. */
    fun showRsvpMissing(context: Context, accessId: Int, eventId: Int, title: String, text: String) {
        if (!allowed(context)) return
        val id = notificationId(accessId, eventId)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(status: String, label: String, request: Int) = NotificationCompat.Action(
            0,
            label,
            PendingIntent.getBroadcast(
                context,
                request,
                RsvpActionReceiver.intent(context, accessId, eventId, status, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RSVP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rückmeldung fehlt: $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(action("yes", "Zusage", id * 2))
            .addAction(action("no", "Absage", id * 2 + 1))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Erlaubnis inzwischen entzogen
        }
    }

    /** Mitteilung des Vorstands; Antippen öffnet sie in der App. */
    fun showCircular(context: Context, accessId: Int, circularId: Int, title: String, text: String, from: String, short: Boolean) {
        if (!allowed(context)) return
        val id = (notificationId(accessId, circularId) xor 0x2000_0000) and 0x3FFFFFFF
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ACCESS, accessId)
                .putExtra(EXTRA_CIRCULAR, circularId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = if (short) text else text.ifBlank { "Neues Rundschreiben" }
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Mitteilung" })
            .setContentText(body)
            .setSubText(from.ifBlank { null })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
        }
    }

    fun showText(context: Context, id: Int, title: String, text: String) {
        if (!allowed(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_RSVP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
        }
    }

    fun notificationId(accessId: Int, eventId: Int): Int = (accessId * 100_003 + eventId) and 0x3FFFFFFF
}
