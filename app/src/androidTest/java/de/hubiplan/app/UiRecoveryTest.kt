package de.hubiplan.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import de.hubiplan.app.data.*
import de.hubiplan.app.ui.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Reale Compose-Oberflächen im Wegwerf-Emulator; nur synthetische Antworten im Testtransport. */
class UiRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun language() { Texte.setzeFuerTest("de", emptyMap()) }
    private fun client(reply: (okhttp3.Request) -> Pair<Int, String>): AdminClient {
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, json) = reply(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code)
                .message("Prüfantwort").body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return AdminClient(Api("https://example.invalid", transport = transport), 7, accountKey = "ui-test-" + UUID.randomUUID())
    }
    private fun waitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun image(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.filesDir
        check(directory.isDirectory || directory.mkdirs())
        val file = File(directory, name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun invoice(name: String, id: Int = 1, more: Boolean = false) =
        """{"count":${if (more) 2 else 1},"next":${if (more) "\"next\"" else "null"},"results":[{"id":$id,"recipient_name":"$name","number":"R-$id","status":"open","status_label":"offen","outstanding":"25.00"}]}"""

    @Test fun rechnungssucheVerwirftVerspaeteteSeiteUndKannFehlerWiederholen() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = AtomicBoolean(false)
        val pages = AtomicInteger(0)
        val failures = AtomicInteger(0)
        val client = client { request ->
            val search = request.url.queryParameter("suche").orEmpty()
            when {
                search == "Neu" -> 200 to invoice("Erika Neu", 3)
                search == "Fehler" && failures.incrementAndGet() == 1 -> 503 to """{"detail":"Vorübergehend nicht verfügbar."}"""
                search == "Fehler" -> 200 to invoice("Erika Wiederholung", 4)
                request.url.queryParameter("page") == "2" -> {
                    pages.incrementAndGet(); started.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    returned.set(true)
                    200 to invoice("Überholte Seite", 2)
                }
                request.url.queryParameter("offen") == null -> 200 to invoice("Alle Rechnungen", 5)
                else -> 200 to invoice("Erika Alt", more = true)
            }
        }
        compose.setContent { HubiplanTheme(null) { BillingSection(client, AdminErrors {}, 0) {} } }
        try {
            waitText("Erika Alt")
            compose.onNodeWithText("Weitere laden").performScrollTo().performClick()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            compose.onNodeWithText("Wird geladen …").assertIsNotEnabled()
            compose.onNodeWithText("Name, Betreff oder Rechnungsnummer").performScrollTo().performTextInput("Neu")
            waitText("Erika Neu")
            compose.onNodeWithText("Erika Alt").assertDoesNotExist()
            release.countDown()
            compose.waitUntil(5_000) { returned.get() }
            compose.waitForIdle()
            compose.onNodeWithText("Überholte Seite").assertDoesNotExist()
            assertEquals(1, pages.get())
            compose.onNodeWithText("Name, Betreff oder Rechnungsnummer").performTextReplacement("Fehler")
            waitText("Vorübergehend nicht verfügbar.")
            compose.onNodeWithText("Erika Neu").assertDoesNotExist()
            compose.onNodeWithText("Erneut laden").performClick()
            waitText("Erika Wiederholung")
            closeSoftKeyboard()
            image("ux-test-rechnungssuche.png")
        } finally { release.countDown() }
    }

    @Test fun buchungBehaeltFotoKontoKategorieUndSchuetztSichtbaresZurueck() {
        val client = client { request ->
            200 to when {
                request.url.encodedPath.endsWith("accounts/") -> """[{"id":1,"name":"Barkasse A","kind":"cash"},{"id":2,"name":"Barkasse B","kind":"cash"}]"""
                else -> """[{"id":7,"name":"Auslagen","kind":"expense"}]"""
            }
        }
        val file = File(context.cacheDir, "ui-test-beleg.png")
        Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.BLUE) }
            .let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
                }
            }
        }
        var closed by mutableStateOf(false)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            HubiplanTheme(null) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    FormLeaveGuardHost { leave ->
                        if (!closed) BookingForm(client, "Musterverein", DraftStore(context, client.accountKey), AdminErrors {},
                            onBack = { leave.request { closed = true } }, onDone = {})
                    }
                }
            }
        }
        waitText("Barkasse A ▾")
        compose.onNodeWithText("Betrag in €").performTextInput("23,50")
        compose.onNodeWithText("Buchungstext").performTextInput("Testauslagen")
        closeSoftKeyboard()
        compose.onNodeWithText("Barkasse A ▾").performScrollTo().performClick()
        compose.onNodeWithText("Barkasse B").performClick()
        compose.onNodeWithText("Bitte wählen ▾").performScrollTo().performClick()
        compose.onNodeWithText("Auslagen", substring = false).performClick()
        compose.onNodeWithText("Aus der Galerie").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Beleg").fetchSemanticsNodes().isNotEmpty() }
        restoration.emulateSavedInstanceStateRestore()
        waitText("Barkasse B ▾")
        compose.onNodeWithText("Auslagen ▾").assertExists()
        compose.onNodeWithText("23,50").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Beleg").fetchSemanticsNodes().isNotEmpty() }
        closeSoftKeyboard()
        pressBack()
        compose.onNodeWithText("Weiter bearbeiten").performClick()
        assertFalse(closed)
        compose.onNodeWithText("23,50").assertExists()
        compose.onNodeWithText("‹ Zurück").performScrollTo().performClick()
        compose.onNodeWithText("Verwerfen", substring = false).performClick()
        assertTrue(closed)
    }

    @Test fun laufendeUnterschriftUeberstehtZustandswiederherstellungUndGroessenaenderung() {
        val restoration = StateRestorationTester(compose)
        var wide by mutableStateOf(false)
        var result: SignatureStrokes? = null
        val key = "ui-signatur-" + UUID.randomUUID()
        restoration.setContent {
            HubiplanTheme(null) {
                Box(Modifier.requiredWidth(if (wide) 380.dp else 280.dp)) {
                    SignatureFullscreen("Kassierer", "23,50 € für Testauslagen", key, onConfirm = { result = it }, onCancel = {})
                }
            }
        }
        val pad = compose.onNodeWithContentDescription("Unterschriftsfeld: Kassierer")
        val before = pad.fetchSemanticsNode().boundsInRoot.width
        pad.performTouchInput { down(Offset(30f, 60f)); moveTo(Offset(140f, 90f), delayMillis = 80) }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        // Die unterbrochene Berührung auch im Eingabesimulator beenden; die Striche sind bereits restauriert.
        compose.onNodeWithContentDescription("Unterschriftsfeld: Kassierer").performTouchInput { cancel() }
        compose.onNodeWithText("Bestätigen").assertIsEnabled()
        compose.runOnIdle { wide = true }
        compose.waitForIdle()
        val after = compose.onNodeWithContentDescription("Unterschriftsfeld: Kassierer").fetchSemanticsNode().boundsInRoot.width
        image("ux-test-unterschrift.png")
        compose.onNodeWithText("Bestätigen").performClick()
        val signed = requireNotNull(result)
        assertTrue(signed.strokes.isNotEmpty())
        assertEquals(30f * after / before, signed.strokes.first().first().first().toFloat(), 2f)
    }
    @Test fun feldfehlerSindZugeordnetUndDasErsteFehlerfeldErhaeltFokus() {
        compose.setContent {
            HubiplanTheme(null) {
                val fields = rememberServerFields()
                var email by androidx.compose.runtime.remember { mutableStateOf("erika") }
                var amount by androidx.compose.runtime.remember { mutableStateOf("abc") }
                androidx.compose.foundation.layout.Column {
                    ServerTextField("email", fields, email, { email = it }, label = { androidx.compose.material3.Text("E-Mail") })
                    ServerTextField("amount", fields, amount, { amount = it }, label = { androidx.compose.material3.Text("Betrag") })
                    androidx.compose.material3.Button(onClick = {
                        val body = """{"email":["Format prüfen."],"amount":["Format prüfen."]}"""
                        fields.receive(ApiException(400, errorMessage(400, body), body))
                    }) { androidx.compose.material3.Text("Prüfen") }
                }
            }
        }
        compose.onNodeWithText("Prüfen").performClick()
        compose.onNodeWithText("E-Mail").assertIsFocused()
        compose.onAllNodesWithText("Format prüfen.").assertCountEquals(2)
        compose.onNodeWithText("E-Mail").performTextReplacement("erika@example.invalid")
        compose.onAllNodesWithText("Format prüfen.").assertCountEquals(1)
        compose.onNodeWithText("abc").assertExists()
        closeSoftKeyboard()
        image("ux-test-feldfehler.png")
    }

}
