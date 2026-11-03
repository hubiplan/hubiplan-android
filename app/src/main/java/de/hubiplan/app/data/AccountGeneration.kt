package de.hubiplan.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialisiert lokale Seiteneffekte mit dem Ersetzen/Entfernen eines Kontos.
 * Netzabrufe gehören vor [ifCurrent], damit sie eine neue Kopplung nicht blockieren.
 */
class AccountGeneration {
    private val mutex = Mutex()

    suspend fun <T> change(action: suspend () -> T): T = mutex.withLock { action() }

    suspend fun <T> ifCurrent(
        expected: String,
        current: suspend () -> String?,
        action: suspend () -> T,
    ): T? = mutex.withLock {
        if (current() == expected) action() else null
    }
}
