package de.hubiplan.app

import android.app.Application
import de.hubiplan.app.data.AccountStore
import de.hubiplan.app.data.Accounts
import de.hubiplan.app.sync.Notifications

class HubiplanApp : Application() {
    val accounts: Accounts by lazy { Accounts(this) }

    /** Konto, das die App gerade zeigt. */
    val store: AccountStore get() = accounts.activeStore()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        de.hubiplan.app.push.GooglePush.init(this)
    }
}
