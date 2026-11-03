package de.hubiplan.app.data

/** Lokale Seiteneffekte einer erfassten Verbindung atomar an ihre Generation binden. */
interface AccountMutationScope {
    suspend fun <T> ifCurrent(expected: Account, action: suspend () -> T): T?
}
