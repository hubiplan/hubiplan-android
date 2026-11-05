package de.hubiplan.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.hubiplan.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PasswordRecoveryTest {
    @get:Rule val compose = createComposeRule()
    @Before fun english() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = context.assets.open("texte/en.json").bufferedReader().use { Texte.parse(it.readText()) }
        Texte.setzeFuerTest("en", catalog)
    }
    @Test fun oeffnetNurDieGewaehlteInstanzUndErhaeltDieAnmeldeeingaben() {
        val urls = mutableListOf<String>()
        compose.setContent { HubiplanTheme(null) {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { urls += uri }
            }) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PasswordLogin(ConnectState()) { _, _, _ -> error("Keine Anmeldung bei Wiederherstellung") }
                }
            }
        } }
        val recover = compose.onNodeWithText("Forgot password? Reset in browser")
        recover.performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("HUBIplan address").performTextInput("http://verein.example.de")
        recover.performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("HUBIplan address").performTextReplacement("https://verein.example.de:8443/mein-konto?email=erika@example.org")
        compose.onNodeWithText("Email").performTextInput("erika@example.org")
        recover.performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf("https://verein.example.de:8443/passwort-vergessen"), urls)
        compose.onNodeWithText("erika@example.org").assertExists()
        compose.onNodeWithText("Reset your password on your club’s website in the browser. Then return to the app and sign in with the new password.").assertExists()
    }
    @Test fun fehlenderBrowserZeigtEineWiederherstellbareZieladresse() {
        compose.setContent { HubiplanTheme(null) {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { throw IllegalArgumentException("Kein Browser im Prüfaufbau") }
            }) {
                Column(Modifier.verticalScroll(rememberScrollState())) { PasswordLogin(ConnectState(server = "https://verein.example.de")) { _, _, _ -> } }
            }
        } }
        compose.onNodeWithText("Forgot password? Reset in browser").performScrollTo().performClick()
        compose.onNodeWithText("The browser could not be opened. Open this address in a browser, then return to the app:").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("https://verein.example.de/passwort-vergessen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("HUBIplan address").performTextReplacement("anderer-verein.example.org")
        compose.onNodeWithText("https://verein.example.de/passwort-vergessen").assertDoesNotExist()
    }
    @Test fun laufendeAnmeldungVerhindertDenBrowserwechsel() {
        compose.setContent { HubiplanTheme(null) {
            Column(Modifier.verticalScroll(rememberScrollState())) { PasswordLogin(ConnectState(server = "https://verein.example.de", busy = true)) { _, _, _ -> } }
        } }
        compose.onNodeWithText("Forgot password? Reset in browser").performScrollTo().assertIsNotEnabled()
    }
}
