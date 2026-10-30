package de.hubiplan.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.hubiplan.app.HubiplanApp
import de.hubiplan.app.data.Api
import de.hubiplan.app.data.ApiException
import kotlinx.coroutines.flow.first

/** Knopf „Zusage“/„Absage“ in der Benachrichtigung: Rückmeldung senden, auch ohne Netz (später). */
class RsvpActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val access = intent.getIntExtra(EXTRA_ACCESS, -1)
        val event = intent.getIntExtra(EXTRA_EVENT, -1)
        val status = intent.getStringExtra(EXTRA_STATUS) ?: return
        val notification = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        if (access < 0 || event < 0) return
        NotificationManagerCompat.from(context).cancel(notification)
        // Gemerkt und gesendet, sobald Netz da ist
        WorkManager.getInstance(context).enqueueUniqueWork(
            "rueckmeldung-$access-$event",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RsvpWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(EXTRA_ACCESS to access, EXTRA_EVENT to event, EXTRA_STATUS to status, EXTRA_NOTIFICATION to notification))
                .build(),
        )
    }

    companion object {
        const val EXTRA_ACCESS = "zugang"
        const val EXTRA_EVENT = "termin"
        const val EXTRA_STATUS = "status"
        const val EXTRA_NOTIFICATION = "hinweis"

        fun intent(context: Context, accessId: Int, eventId: Int, status: String, notificationId: Int): Intent =
            Intent(context, RsvpActionReceiver::class.java)
                .setAction("de.hubiplan.app.RUECKMELDUNG.$status")
                .putExtra(EXTRA_ACCESS, accessId)
                .putExtra(EXTRA_EVENT, eventId)
                .putExtra(EXTRA_STATUS, status)
                .putExtra(EXTRA_NOTIFICATION, notificationId)
    }
}

class RsvpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = (applicationContext as HubiplanApp).store
        val account = store.account.first() ?: return Result.success()
        val access = inputData.getInt(RsvpActionReceiver.EXTRA_ACCESS, -1)
        val event = inputData.getInt(RsvpActionReceiver.EXTRA_EVENT, -1)
        val status = inputData.getString(RsvpActionReceiver.EXTRA_STATUS) ?: return Result.failure()
        val notification = inputData.getInt(RsvpActionReceiver.EXTRA_NOTIFICATION, 0)
        return try {
            val detail = Api(account.server, account.token).respond(access, event, status)
            Notifications.showText(
                applicationContext,
                notification,
                if (status == "yes") "Zusage gesendet" else "Absage gesendet",
                detail.title,
            )
            SyncWorker.now(applicationContext)
            Result.success()
        } catch (e: ApiException) {
            if (e.status == 0 || e.status >= 500) return Result.retry()
            // Frist vorbei, ausgebucht, nicht eingeladen: Grund zeigen
            Notifications.showText(applicationContext, notification, "Rückmeldung nicht möglich", e.message ?: "")
            Result.failure()
        }
    }
}
