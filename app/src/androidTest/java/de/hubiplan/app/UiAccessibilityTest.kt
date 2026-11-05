package de.hubiplan.app

import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.hubiplan.app.data.CircularItem
import de.hubiplan.app.ui.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UiAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Before fun english() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = context.assets.open("texte/en.json").bufferedReader().use { Texte.parse(it.readText()) }
        Texte.setzeFuerTest("en", catalog)
    }
    @Test fun dropdownZustandIstInDerAktivenSpracheUndFolgtDemOeffnen() {
        compose.setContent { HubiplanTheme(null) {
            var choice by remember { mutableStateOf<String?>(null) }
            ChoicePicker("Account", listOf("cash" to "Cash"), choice, { it }) { choice = it }
        } }
        val picker = compose.onNodeWithContentDescription("Account: nothing selected")
        picker.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "collapsed"))
        picker.performClick()
        picker.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded"))
        compose.onNodeWithText("Cash").performClick()
        compose.onNodeWithContentDescription("Account: cash").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "collapsed"))
    }
    @Test fun mitteilungsauswahlHatGenauEineSemantischAusgewaehlteZeile() {
        compose.setContent { HubiplanTheme(null) {
            var selected by remember { mutableIntStateOf(1) }
            Column {
                CircularRow(CircularItem(1, "Board notice"), selected == 1) { selected = 1 }
                CircularRow(CircularItem(2, "Event notice"), selected == 2) { selected = 2 }
            }
        } }
        compose.onNodeWithText("Board notice").assertIsSelected()
        compose.onNodeWithText("Event notice").assertIsNotSelected().performClick()
        compose.onNodeWithText("Board notice").assertIsNotSelected()
        compose.onNodeWithText("Event notice").assertIsSelected()
    }
    @Test fun erfolgsnachrichtIstEineEinzelneHoeflicheLiveRegionOhneFokuswechsel() {
        compose.setContent { HubiplanTheme(null) {
            var saved by remember { mutableStateOf(false) }
            Column {
                Button(onClick = { saved = true }) { Text("Save") }
                if (saved) StatusNotice(tr("Gespeichert."))
            }
        } }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Saved.").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(1)
        compose.onNodeWithText("Save").assertExists().assertIsEnabled()
    }
    @Test fun unterschriftAktionenBleibenBeiDoppelterSchriftInSchmalemFeldErreichbar() {
        compose.setContent { HubiplanTheme(null) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.requiredSize(320.dp, 480.dp).testTag("signature-fixture")) {
                    SignatureFullscreen("Treasurer", "Test receipt", "ui-a11y-signature", onConfirm = {}, onCancel = {})
                }
            }
        } }
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "ux-test-signature-200.png").outputStream().use {
            compose.onNodeWithTag("signature-fixture").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val root = compose.onNodeWithTag("signature-fixture").fetchSemanticsNode().boundsInRoot
        for (text in listOf("Delete", "Cancel", "Confirm")) {
            val action = compose.onNodeWithText(text)
            action.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            org.junit.Assert.assertTrue("$text bleibt ungetrennt lesbar: ${layouts.map { "lines=${it.lineCount}, widthOver=${it.didOverflowWidth}, heightOver=${it.didOverflowHeight}, size=${it.size}, right=${it.getLineRight(0)}, bottom=${it.getLineBottom(0)}" }}", layouts.isNotEmpty() && layouts.all { it.lineCount == 1 && !it.hasVisualOverflow })
            val bounds = action.fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("$text bleibt innerhalb des 320-dp-Felds: $bounds, Feld: $root", bounds.left >= root.left && bounds.right <= root.right + 1f && bounds.top >= root.top && bounds.bottom <= root.bottom + 1f)
        }
    }

}
