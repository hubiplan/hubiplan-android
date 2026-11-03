package de.hubiplan.app.push

import de.hubiplan.app.data.Account
import de.hubiplan.app.data.AccountMutationScope
import de.hubiplan.app.data.ApiException

/** Keys und Ergebnis lokal sperren, den Netzabruf dagegen außerhalb ausführen.
 * Selbst wenn der Server/Gateway-Abruf schon vor dem Aufruf verspätet war, werden
 * Schlüssel und Zugangsdaten niemals aus einer inzwischen neuen Verbindung gelesen.
 */
internal suspend fun <K> registerAccountPush(
    scope: AccountMutationScope,
    expected: Account,
    endpoint: String,
    keys: suspend () -> K,
    send: suspend (Account, K) -> Unit,
    save: suspend (String?, String?) -> Unit,
) {
    val capturedKeys = scope.ifCurrent(expected) { keys() } ?: return
    try {
        send(expected, capturedKeys)
        scope.ifCurrent(expected) { save(endpoint, null) }
    } catch (error: ApiException) {
        scope.ifCurrent(expected) { save(null, error.message) }
    }
}
