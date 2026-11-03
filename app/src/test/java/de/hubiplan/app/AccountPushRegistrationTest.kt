package de.hubiplan.app

import de.hubiplan.app.data.Account
import de.hubiplan.app.data.AccountGeneration
import de.hubiplan.app.data.AccountMutationScope
import de.hubiplan.app.data.ApiException
import de.hubiplan.app.push.registerAccountPush
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AccountPushRegistrationTest {
    private val a = Account("https://a.example.invalid", "token-a", 1, "a@example.invalid", "A", null, generation = "A")
    private val b = a.copy(server = "https://b.example.invalid", token = "token-b", generation = "B")
    private class Scope(var account: Account) : AccountMutationScope {
        val guard = AccountGeneration()
        var endpoint = "before"
        override suspend fun <T> ifCurrent(expected: Account, action: suspend () -> T): T? =
            guard.ifCurrent(expected.generation, { account.generation }, action)
        suspend fun replace(account: Account) = guard.change { this.account = account; endpoint = "B-endpoint" }
    }

    @Test fun lateServerGatewayResponseCannotRegisterNewAccountWithOldGateway() = runBlocking {
        val scope = Scope(a)
        val gatewayResponse = CompletableDeferred<String>()
        var readKeys = false
        var sent = false
        val setup = async {
            val oldGateway = gatewayResponse.await()
            registerAccountPush(scope, a, oldGateway, { readKeys = true; "keys-a" },
                { _, _ -> sent = true }, { endpoint, _ -> scope.endpoint = endpoint.orEmpty() })
        }
        scope.replace(b)
        gatewayResponse.complete("https://gateway-a.example.invalid/push")
        setup.await()
        assertFalse(readKeys)
        assertFalse(sent)
        assertEquals("B-endpoint", scope.endpoint)
    }

    @Test fun lateRegistrationSuccessOrFailureDoesNotChangeReplacementEndpoint() = runBlocking {
        for (fails in listOf(false, true)) {
            val scope = Scope(a)
            val sent = CompletableDeferred<Account>()
            val response = CompletableDeferred<Unit>()
            val registration = async {
                registerAccountPush(scope, a, "gateway-a", { "keys-a" }, { captured, keys ->
                    assertEquals("keys-a", keys)
                    sent.complete(captured)
                    response.await()
                    if (fails) throw ApiException(503, "synthetic")
                }, { endpoint, error -> scope.endpoint = endpoint ?: error.orEmpty() })
            }
            assertEquals(a, sent.await())
            withTimeout(1000) { scope.replace(b) }
            response.complete(Unit)
            registration.await()
            assertEquals("B-endpoint", scope.endpoint)
        }
    }

    @Test fun currentAccountRegistersAndStoresEndpoint() = runBlocking {
        val scope = Scope(a)
        registerAccountPush(scope, a, "gateway-a", { "keys-a" }, { captured, _ ->
            assertEquals("token-a", captured.token)
        }, { endpoint, _ -> scope.endpoint = endpoint.orEmpty() })
        assertEquals("gateway-a", scope.endpoint)
    }
}
