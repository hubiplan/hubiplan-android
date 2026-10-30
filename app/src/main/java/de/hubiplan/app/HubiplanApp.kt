package de.hubiplan.app

import android.app.Application
import de.hubiplan.app.data.AccountStore
import de.hubiplan.app.sync.Notifications

class HubiplanApp : Application() {
    val store: AccountStore by lazy { AccountStore(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        de.hubiplan.app.push.GooglePush.init(this)
    }
}
