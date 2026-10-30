package de.hubiplan.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.hubiplan.app.HubiplanApp
import de.hubiplan.app.calendar.DeviceCalendar
import de.hubiplan.app.calendar.needsRsvpReminder
import de.hubiplan.app.calendar.toCalendarEntry
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.ui.DEFAULT_PRIMARY
import de.hubiplan.app.ui.formatWhen
import de.hubiplan.app.ui.parseHexColor
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Abgleich im Hintergrund: Termine aller Einheiten in den Gerätekalender schreiben und
 * Hinweise „Rückmeldung fehlt“ zeigen. Läuft alle 6 Stunden und nach Änderungen.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = (applicationContext as HubiplanApp).store
        val account = store.account.first() ?: return Result.success()
        val settings = store.settings.first()
        val api = Api(account.server, account.token)
        val calendar = DeviceCalendar(applicationContext)
        val writeCalendar = settings.calendarOn && calendar.hasPermission()
        return try {
            val me = api.me()
            val today = LocalDate.now()
            val keep = mutableSetOf<String>()
            val hints = mutableListOf<Triple<Int, de.hubiplan.app.data.Occurrence, String>>()
            for (access in me.portal.filter { it.eventsEnabled }) {
                val items = api.events(access.id, today.minusDays(DAYS_BACK).toString(), today.plusDays(DAYS_AHEAD).toString())
                if (writeCalendar) {
                    val name = DeviceCalendar.calendarName(account.server, access.id)
                    keep += name
                    val color = (parseHexColor(access.theme?.primary) ?: DEFAULT_PRIMARY).toInt()
                    val id = calendar.ensureCalendar(name, access.title, color)
                    calendar.sync(id, items.map { it.toCalendarEntry(access.title, settings.reminderMinutes) })
                }
                val now = OffsetDateTime.now()
                items.filter { it.needsRsvpReminder(now) }.forEach { hints += Triple(access.id, it, access.title) }
            }
            if (settings.calendarOn) calendar.removeCalendarsExcept(keep)
            if (settings.rsvpHints) showHints(store, hints)
            store.markSynced(System.currentTimeMillis())
            Result.success()
        } catch (e: ApiException) {
            when (e.status) {
                401 -> {
                    // Gerät abgemeldet: Kalender und Konto entfernen, die App fragt beim Öffnen neu
                    calendar.removeCalendarsExcept(emptySet())
                    store.clear()
                    Result.success()
                }
                0, in 500..599 -> Result.retry()
                else -> Result.failure()
            }
        } catch (e: SecurityException) {
            // Kalendererlaubnis entzogen
            store.setCalendarOn(false)
            Result.success()
        }
    }

    private suspend fun showHints(
        store: de.hubiplan.app.data.AccountStore,
        hints: List<Triple<Int, de.hubiplan.app.data.Occurrence, String>>,
    ) {
        val done = store.notifiedKeys()
        val shown = mutableSetOf<String>()
        for ((accessId, occ, unit) in hints) {
            val key = "$accessId:${occ.key}"
            if (key in done) continue
            Notifications.showRsvpMissing(
                applicationContext,
                accessId,
                occ.eventId,
                occ.title,
                "${formatWhen(occ.startsAt, occ.endsAt, occ.allDay)} · $unit. Anmeldeschluss ist bald.",
            )
            shown += key
        }
        if (shown.isNotEmpty()) store.addNotified(shown)
    }

    companion object {
        private const val DAYS_BACK = 30L
        private const val DAYS_AHEAD = 400L
        private const val PERIODIC = "abgleich"
        private const val NOW = "abgleich-jetzt"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Regelmäßigen Abgleich einplanen (beim Start der App). */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(network).build(),
            )
        }

        /** Sofort abgleichen (nach Anmeldung, Rückmeldung, geänderter Einstellung). */
        fun now(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
        }
    }
}
