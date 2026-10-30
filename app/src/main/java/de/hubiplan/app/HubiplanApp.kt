package de.hubiplan.app

import android.app.Application
import de.hubiplan.app.data.AccountStore

class HubiplanApp : Application() {
    val store: AccountStore by lazy { AccountStore(this) }
}
