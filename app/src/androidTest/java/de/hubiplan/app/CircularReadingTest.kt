package de.hubiplan.app

import android.graphics.Bitmap
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.hubiplan.app.data.CircularItem
import de.hubiplan.app.data.CircularFile
import de.hubiplan.app.ui.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CircularReadingTest {
    @get:Rule val compose=createComposeRule()
    @Before fun english() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        Texte.setzeFuerTest("en",ctx.assets.open("texte/en.json").bufferedReader().use {Texte.parse(it.readText())})
    }
    private val item=CircularItem(17,"Invitation",body="**Important**\n\n- Paper\n- Pen\n\n1. Arrive\n2. Sign in\n\n[Plan](https://club.example.org/plan)",attachments=listOf(CircularFile(8,"Invitation.pdf")))
    @Test fun zeigtGegliedertenTextUndOeffnetDasGewaehlteSchreiben() {
        val urls=mutableListOf<String>()
        compose.setContent { HubiplanTheme(null) { CompositionLocalProvider(LocalUriHandler provides object:UriHandler {override fun openUri(uri:String) {urls+=uri}}) {
            Column(Modifier.verticalScroll(rememberScrollState())) { CircularContent(item,"https://club.example.org",9) }
        } } }
        compose.onNodeWithText("Important").assertExists()
        compose.onNodeWithText("• Paper").assertExists()
        compose.onNodeWithText("2. Sign in").assertExists()
        compose.onNodeWithText("Invitation.pdf").assertExists()
        compose.onNodeWithText("Open this circular with attachments in the browser").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(listOf("https://club.example.org/mein-bereich/rundschreiben?zugang=9&schreiben=17"),urls)}
        val out=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
        if(out!=null){out.mkdirs(); val image=compose.onRoot().captureToImage().asAndroidBitmap(); File(out,"ux-test-circular-reading-${System.currentTimeMillis()}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }
    @Test fun fehlenderBrowserHatEinenKopierbarenRueckwegUndKeinVeraltetesZiel() {
        val current=mutableStateOf(item)
        compose.setContent {HubiplanTheme(null) {CompositionLocalProvider(LocalUriHandler provides object:UriHandler {override fun openUri(uri:String){throw IllegalArgumentException("No browser in fixture")}}) {
            Column(Modifier.verticalScroll(rememberScrollState())) {CircularContent(current.value,"https://club.example.org",9)}
        }}}
        compose.onNodeWithText("Open this circular with attachments in the browser").performScrollTo().performClick()
        compose.onNodeWithText("The browser could not be opened. Open this address in a browser:").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("https://club.example.org/mein-bereich/rundschreiben?zugang=9&schreiben=17").assertExists()
        compose.runOnIdle {current.value=item.copy(id=18)}
        compose.onNodeWithText("https://club.example.org/mein-bereich/rundschreiben?zugang=9&schreiben=17").assertDoesNotExist()
    }
    @Test fun produktiverUriHandlerOeffnetDenGenauenBrowserIntentOhneZugangsdaten() {
        compose.setContent {HubiplanTheme(null) {
            Column(Modifier.verticalScroll(rememberScrollState())) {CircularContent(item,"https://club.example.org",9)}
        }}
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val intents=mutableListOf<Intent>()
        val monitor=object:Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                if(intent.action!=Intent.ACTION_VIEW)return null
                intents+=Intent(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithText("Open this circular with attachments in the browser").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(1,intents.size)
                assertEquals("https://club.example.org/mein-bereich/rundschreiben?zugang=9&schreiben=17",intents.single().dataString)
                assertEquals(null,intents.single().extras)
            }
            compose.onNodeWithText("Invitation.pdf").assertExists()
        } finally {instrumentation.removeMonitor(monitor)}
    }
}
