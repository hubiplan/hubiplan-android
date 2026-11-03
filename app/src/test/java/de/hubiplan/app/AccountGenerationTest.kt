package de.hubiplan.app

import de.hubiplan.app.data.AccountGeneration
import de.hubiplan.app.data.Account
import de.hubiplan.app.sync.Notifications
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Netzantworten gezielt erst nach einer neuen Kopplung freigeben. */
class AccountGenerationTest {
    @Test fun late401DoesNotRetireNewAccountInSameSlot() = runBlocking {
        val guard = AccountGeneration()
        var current = "A"
        val response = CompletableDeferred<Unit>()
        var retired = false
        val worker = async {
            response.await()
            guard.ifCurrent("A", { current }) { retired = true; current = "" }
        }
        guard.change { current = "B" }
        response.complete(Unit)
        worker.await()
        assertFalse(retired)
        assertEquals("B", current)
    }

    @Test fun lateSuccessCannotWriteCalendarNotificationOrSyncMarker() = runBlocking {
        val guard = AccountGeneration()
        var current = "A"
        val response = CompletableDeferred<Unit>()
        val effects = mutableListOf<String>()
        val worker = async {
            response.await()
            guard.ifCurrent("A", { current }) { effects += listOf("calendar", "notification", "lastSync") }
        }
        guard.change { current = "B" }
        response.complete(Unit)
        worker.await()
        assertTrue(effects.isEmpty())
        guard.ifCurrent("B", { current }) { effects += "B" }
        assertEquals(listOf("B"), effects)
    }

    @Test fun replacingAccountWaitsForAnAlreadyStartedLocalWrite() = runBlocking {
        val guard = AccountGeneration()
        var current = "A"
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val effects = mutableListOf<String>()
        val write = async {
            guard.ifCurrent("A", { current }) { entered.complete(Unit); finish.await(); effects += "A" }
        }
        entered.await()
        val replace = async { guard.change { effects.clear(); current = "B" } }
        finish.complete(Unit)
        write.await(); replace.await()
        assertEquals("B", current)
        assertTrue(effects.isEmpty())
    }

    @Test fun cancellationTagsKeepOtherAccountsAndUnrelatedNotificationsSeparate() {
        assertNotEquals(Notifications.tag("A"), Notifications.tag("B"))
        assertNotEquals(Notifications.tag("A"), "other-notification")
        assertEquals("hubiplan-konto-A", Notifications.tag("A"))
    }

    @Test fun lateRefreshAndNotificationOpenRejectRepairedSameServerAndDevice() = runBlocking {
        val guard = AccountGeneration()
        val old = Account("https://club.example.invalid", "old", 1, "a@example.invalid", "A", null, generation = "old-pairing")
        var current = old
        val response = CompletableDeferred<Unit>()
        val effects = mutableListOf<String>()
        val refresh = async {
            response.await()
            guard.ifCurrent(old.generation, { current.generation }) { effects += "refresh-success" }
            guard.ifCurrent(old.generation, { current.generation }) { effects += "401-retire" }
        }
        guard.change { current = old.copy(token = "new", generation = "new-pairing") }
        guard.ifCurrent(old.generation, { current.generation }) { effects += "notification-open" }
        response.complete(Unit)
        refresh.await()
        assertTrue(effects.isEmpty())
        assertEquals(old.server, current.server)
        assertEquals(old.deviceId, current.deviceId)
    }

    @Test fun networkCleanupAfterRetirementDoesNotHoldTheAccountLock() = runBlocking {
        val guard = AccountGeneration()
        var current: String? = "A"
        val cleanupStarted = CompletableDeferred<Unit>()
        val cleanupComplete = CompletableDeferred<Unit>()
        val removal = async {
            guard.ifCurrent("A", { current }) { current = null }
            cleanupStarted.complete(Unit)
            cleanupComplete.await()
        }
        cleanupStarted.await()
        withTimeout(1000) { guard.change { current = "B" } }
        assertEquals("B", current)
        cleanupComplete.complete(Unit)
        removal.await()
    }
}
