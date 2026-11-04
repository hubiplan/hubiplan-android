package de.hubiplan.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.FragmentActivity
import de.hubiplan.app.data.*
import de.hubiplan.app.ui.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File

/** Nicht exportierte Debug-Prüfansicht: echte Form und Geräte-PIN, keine Konten oder Echtdaten.
 * Der Transport beantwortet ausschließlich synthetische HTTPS-Anfragen. Die produktiven
 * Anmelde-, Tresor- und Berechtigungsprüfungen werden nicht verändert. */
class InterruptionTestActivity : FragmentActivity() {
    private lateinit var fixtureKey: String
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        fixtureKey = "ui-interruption-" + taskId
        val photo = File(cacheDir, "fixture-receipt.png")
        Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
            .let { bitmap -> photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val registry = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(photo)))
                }
            }
        }
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val body = if (chain.request().url.encodedPath.endsWith("accounts/"))
                """[{"id":1,"name":"Kasse A","kind":"cash"},{"id":2,"name":"Kasse B","kind":"cash"}]"""
            else """[{"id":7,"name":"Auslagen","kind":"expense"}]"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("Prüfantwort").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = AdminClient(Api("https://example.invalid", transport = transport), 7, accountKey = fixtureKey)
        setContent {
            HubiplanTheme(null) {
                FormLeaveGuardHost { leave ->
                    var unlocked by rememberSaveable { mutableStateOf(false) }
                    val retained = rememberAdminDraftState(fixtureKey)
                    Column(Modifier.fillMaxSize()) {
                        if (!unlocked) {
                            Text("Synthetic device-lock fixture", Modifier.padding(16.dp))
                            Button(onClick = {
                                DeviceUnlock.ask(this@InterruptionTestActivity, "Unlock fixture", onSuccess = { unlocked = true }, onError = {})
                            }) { Text("Unlock fixture") }
                        } else {
                            Button(onClick = { unlocked = false }) { Text("Lock fixture") }
                            Box(Modifier.weight(1f)) {
                                retained.SaveableStateProvider(fixtureKey) {
                                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                                        BookingForm(client, "Musterverein", DraftStore(this@InterruptionTestActivity, fixtureKey), AdminErrors {},
                                            onBack = { leave.request { finish() } }, onDone = { finish() })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onDestroy() {
        if (isFinishing && ::fixtureKey.isInitialized) LocalAccountData.forget(this, fixtureKey)
        super.onDestroy()
    }
}
