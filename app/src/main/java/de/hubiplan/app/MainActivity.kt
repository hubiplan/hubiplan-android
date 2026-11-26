package de.hubiplan.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.hubiplan.app.data.PairingLink
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import de.hubiplan.app.ui.AppViewModel
import de.hubiplan.app.ui.ConnectScreen
import de.hubiplan.app.ui.HubiplanTheme
import de.hubiplan.app.ui.Loading
import de.hubiplan.app.ui.MainScreen
import de.hubiplan.app.ui.QuarantineDialogs
import de.hubiplan.app.ui.Screen
import de.hubiplan.app.ui.StatusBarStrip
import de.hubiplan.app.sync.Notifications

// FragmentActivity statt ComponentActivity: nötig für die Abfrage von Fingerabdruck/PIN (Verwaltung)
class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    // Bildschirm aus (auch Sperren des Telefons): Verwaltung sofort sperren (Fachfrage 18)
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vm.lockAdminNow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App-Sprache aus den Android-Einstellungen kann sich geändert haben
        Texte.init(this)
        ContextCompat.registerReceiver(
            this,
            screenOff,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        enableEdgeToEdge()
        // Keine Bildschirmfotos und keine Vorschau im App-Wechsler, außer das Mitglied erlaubt es
        de.hubiplan.app.ui.Sichtschutz.apply(this)
        openFrom(intent)
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            val screen by vm.screen.collectAsStateWithLifecycle()
            val connect by vm.connect.collectAsStateWithLifecycle()
            val settings by vm.settings.collectAsStateWithLifecycle()
            HubiplanTheme(theme, settings.appearance) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        when (val s = screen) {
                            Screen.Loading -> Loading()
                            Screen.Connect -> ConnectScreen(connect, vm)
                            is Screen.Main -> MainScreen(s, vm)
                        }
                        QuarantineDialogs(vm)
                    }
                    // Statusleiste mit eigener Fläche statt durchsichtig über dem Inhalt (A-10)
                    StatusBarStrip(window)
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        vm.cameToForeground()
    }

    override fun onStop() {
        // Drehen des Geräts ist kein Wechsel in den Hintergrund
        if (!isChangingConfigurations) vm.wentToBackground()
        super.onStop()
    }

    // Notenpult: Pedal, Pfeiltasten und Lautstärketasten blättern (Noten in der App, N4).
    // Activity.dispatchKeyEvent ist öffentlich; nur die Überschreibung in androidx.core ist als
    // eingeschränkt markiert. Ohne diesen Weg kämen Pfeiltasten nicht sicher an Compose vorbei.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val pult = de.hubiplan.app.ui.PultKeys.handler
        if (pult != null && pult(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        vm.userInteracted()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    private fun isCalendar(intent: Intent?): Boolean {
        val action = intent?.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_VIEW) return false
        val type = intent.type ?: intent.data?.let { contentResolver.getType(it) }
        val name = intent.data?.lastPathSegment.orEmpty()
        return type?.startsWith("text/calendar") == true || type == "application/ics" || name.endsWith(".ics", ignoreCase = true)
    }

    /** Kalenderdatei lesen, höchstens [IcsLeser.MAX_BYTES]; Fehler → null. */
    private suspend fun readCalendar(uri: android.net.Uri): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Nur Inhalte, die eine andere App freigibt; nie Dateipfade (SEC-08)
        if (uri.scheme != android.content.ContentResolver.SCHEME_CONTENT) return@withContext null
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(de.hubiplan.app.data.IcsLeser.MAX_BYTES + 1)
                if (bytes.size > de.hubiplan.app.data.IcsLeser.MAX_BYTES) null else bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Aus einer Benachrichtigung oder Verknüpfung: Mitteilung öffnen bzw. zum passenden Konto wechseln. */
    private fun openFrom(intent: Intent?) {
        // Verbinden-Link aus dem Browser (X-04); danach entfernen, damit er beim Drehen nicht erneut greift
        if (intent?.action == Intent.ACTION_VIEW && intent.data?.scheme == PairingLink.SCHEME) {
            vm.openPairingLink(intent.dataString.orEmpty())
            intent.data = null
            intent.action = null
            return
        }
        // Kalenderdatei (.ics) aus Mailprogramm oder Kalender geteilt oder geöffnet (W4)
        if (isCalendar(intent)) {
            val uri = if (intent?.action == Intent.ACTION_SEND) {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            } else {
                intent?.data
            }
            val inline = intent?.getStringExtra(Intent.EXTRA_TEXT)
            intent?.action = null
            intent?.data = null
            lifecycleScope.launch {
                // Mitgegebener Text mit derselben Obergrenze wie eine Datei (SEC-08)
                val text = inline?.takeIf { it.length <= de.hubiplan.app.data.IcsLeser.MAX_BYTES && "BEGIN:VEVENT" in it }
                    ?: uri?.let { readCalendar(it) }
                vm.openSharedEvent(text)
            }
            return
        }
        // App-Verknüpfung „Ausweis zeigen“ (lange auf das App-Symbol drücken)
        if (intent?.action == de.hubiplan.app.shortcut.CardShortcut.ACTION) {
            vm.openCard()
            intent.action = null
            return
        }
        val access = intent?.getIntExtra(Notifications.EXTRA_ACCESS, -1) ?: -1
        val circular = intent?.getIntExtra(Notifications.EXTRA_CIRCULAR, -1) ?: -1
        val shortcut = intent?.getStringExtra(Notifications.EXTRA_SHORTCUT_ACCOUNT)
        val account = intent?.getStringExtra(Notifications.EXTRA_ACCOUNT)
        val todo = de.hubiplan.app.data.TodoTarget.create(
            intent?.getStringExtra(Notifications.EXTRA_TODO_SCOPE),
            intent?.getIntExtra(Notifications.EXTRA_TODO_UNIT, -1),
            intent?.getIntExtra(Notifications.EXTRA_TODO, -1),
        )
        if (account != null && todo != null) {
            vm.openTodoNotification(account, todo)
        } else if (account != null && !intent.hasExtra(Notifications.EXTRA_TODO)) {
            vm.openNotification(account, access, circular)
        } else if (shortcut != null && access > 0) {
            // Verknüpfung auf dem Startbildschirm: Konto und Einheit dieser Verknüpfung
            vm.openShortcut(shortcut, access)
        }
        // Alte Hinweise ohne Kontokennung öffnen nur die App. Ein Platz allein
        // beweist nicht, dass dort noch die ursprüngliche Verbindung liegt.
        intent?.removeExtra(Notifications.EXTRA_CIRCULAR)
        intent?.removeExtra(Notifications.EXTRA_TODO)
        intent?.removeExtra(Notifications.EXTRA_TODO_SCOPE)
        intent?.removeExtra(Notifications.EXTRA_TODO_UNIT)
        intent?.removeExtra(Notifications.EXTRA_SLOT)
        intent?.removeExtra(Notifications.EXTRA_SHORTCUT_ACCOUNT)
        intent?.removeExtra(Notifications.EXTRA_ACCOUNT)
    }
}
