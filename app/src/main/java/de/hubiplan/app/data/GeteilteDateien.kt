package de.hubiplan.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Geteilte oder zum Ansehen geöffnete Dateien (Quittungen, Export alter Belege, Wallet-Pass,
 * Rechnungen und Dokumente aus „Ich“) bleiben höchstens etwa eine Stunde im App-Speicher
 * (Gesamtaudit 10.11., V04, Fachfrage 9). Aufgeräumt wird beim Start der App und durch eine
 * Hintergrundarbeit, die die App nach jedem Ablegen für den Ablauf der ältesten Datei plant.
 * Das Notenpult ist ausgenommen: Es löscht seine Kopien selbst beim Schließen.
 */
object GeteilteDateien {
    const val MAX_AGE_MS = 60 * 60 * 1000L

    /** Kleiner Puffer, damit die Arbeit nicht kurz vor dem Ablauf läuft und nichts findet. */
    private const val SLACK_MS = 60 * 1000L

    private const val WORK = "geteilte-dateien-aufraeumen"

    val FOLDERS = listOf(ReceiptCache.FOLDER, LocalAccountData.EXPORT, LocalAccountData.WALLET_PASS, Ansicht.FOLDER)

    private fun files(cacheDir: File) = FOLDERS.asSequence()
        .map { File(cacheDir, it) }
        .flatMap { it.walkBottomUp() }
        .filter { it.isFile }

    /** Dateien älter als [maxAgeMillis] löschen, leere Unterordner gleich mit. */
    fun clear(cacheDir: File, now: Long = System.currentTimeMillis(), maxAgeMillis: Long = MAX_AGE_MS): Int {
        val limit = now - maxAgeMillis
        var deleted = 0
        for (folder in FOLDERS.map { File(cacheDir, it) }) {
            folder.walkBottomUp().forEach { file ->
                when {
                    file.isFile && file.lastModified() < limit -> if (file.delete()) deleted++
                    file.isDirectory && file != folder && file.listFiles().isNullOrEmpty() -> file.delete()
                }
            }
        }
        return deleted
    }

    /** Wann die nächste Datei abläuft (null: nichts mehr da). */
    fun nextExpiry(cacheDir: File, maxAgeMillis: Long = MAX_AGE_MS): Long? =
        files(cacheDir).map { it.lastModified() }.minOrNull()?.let { it + maxAgeMillis }

    /** Aufräumen und, falls noch etwas liegt, die nächste Runde planen. */
    fun clearAndSchedule(context: Context, now: Long = System.currentTimeMillis()) {
        clear(context.cacheDir, now)
        schedule(context, now)
    }

    /** Nach dem Ablegen einer Datei aufrufen: plant das Löschen der ältesten Datei. */
    fun schedule(context: Context, now: Long = System.currentTimeMillis()) {
        val due = nextExpiry(context.cacheDir) ?: return
        val delay = (due - now).coerceAtLeast(0) + SLACK_MS
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<Aufraeumen>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
            )
        }
    }

    class Aufraeumen(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            runCatching { clearAndSchedule(applicationContext) }
            return Result.success()
        }
    }
}
