package de.hubiplan.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Echte Activity-Neuanlage, Systemschrift, Bildschirmtastatur und Geräte-PIN im eigenen Emulator.
 * Kein Gesamtnachweis für Anmeldung, Tresor, Mehrkonto oder den Fünf-Minuten-Timer. */
class UiInterruptionTest {
    @get:Rule val compose = createAndroidComposeRule<InterruptionTestActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val pin get() = requireNotNull(InstrumentationRegistry.getArguments().getString("fixturePin")).also { require(it.matches(Regex("[0-9]{6}"))) }
    @Before fun isolatedDevice() {
        assumeTrue("Nur auf dem eigens eingerichteten Emulator mit Test-PIN", Build.HARDWARE.contains("ranchu") && InstrumentationRegistry.getArguments().containsKey("fixturePin"))
        Texte.setzeFuerTest("de", emptyMap())
    }
    private fun shell(command: String): String {
        val fd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
    private fun editable(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isEditable) return true
        return (0 until node.childCount).any { editable(node.getChild(it)) }
    }
    private fun unlock() {
        compose.onNodeWithText("Unlock fixture").performClick()
        val start = SystemClock.elapsedRealtime()
        while (!editable(instrumentation.uiAutomation.rootInActiveWindow) && SystemClock.elapsedRealtime() - start < 20_000) SystemClock.sleep(100)
        assertTrue("System-PIN-Feld muss erscheinen", editable(instrumentation.uiAutomation.rootInActiveWindow))
        shell("input text " + pin)
        shell("input keyevent 66")
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Kasse A ▾").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Kasse B ▾").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun fill() {
        compose.onNodeWithText("Betrag in €").performScrollTo().performTextInput("23,50")
        compose.onNodeWithText("Buchungstext").performScrollTo().performTextInput("Testauslagen")
        closeSoftKeyboard()
        compose.onNodeWithText("Kasse A ▾").performScrollTo().performClick()
        compose.onNodeWithText("Kasse B").performClick()
        compose.onNodeWithText("Bitte wählen ▾").performScrollTo().performClick()
        compose.onNodeWithText("Auslagen").performClick()
        compose.onNodeWithText("Aus der Galerie").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Beleg").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun echteRotationUndVerwaltungssperreErhaltenDenEntwurfMitGeraetePin() {
        unlock(); fill()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(20_000) { instrumentation.targetContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Kasse B ▾").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithContentDescription("Beleg").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("23,50").assertExists()
        compose.onNodeWithText("Kasse B ▾").assertExists()
        compose.onNodeWithText("Auslagen ▾").assertExists()
        compose.onNodeWithContentDescription("Beleg").assertExists()
        compose.onNodeWithText("Lock fixture").performClick()
        compose.onNodeWithText("23,50").assertDoesNotExist()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        unlock()
        compose.onNodeWithText("23,50").assertExists()
        compose.onNodeWithText("Kasse B ▾").assertExists()
        compose.onNodeWithContentDescription("Beleg").assertExists()
    }
    @Test fun zweihundertProzentSchriftUndBildschirmtastaturLassenDieBuchungBedienbar() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        val scale = shell("settings get system font_scale").trim()
        try {
            shell("settings put system font_scale 2.0")
            compose.waitUntil(20_000) { instrumentation.targetContext.resources.configuration.fontScale >= 1.99f }
            unlock()
            compose.onNodeWithText("Buchungstext").performScrollTo().performTextInput("Test mit großer Schrift")
            compose.onNodeWithText("Test mit großer Schrift").assertIsDisplayed()
            assertTrue(shell("dumpsys input_method").let { it.contains("mInputShown=true") || it.contains("isInputViewShown=true") })
            closeSoftKeyboard()
            compose.onNodeWithText("Buchen", substring = false).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithText("Kasse A ▾").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("Kasse B").performClick()
            compose.onNodeWithText("Kasse B ▾").assertIsDisplayed()
        } finally { shell("settings put system font_scale " + scale.toFloatOrNull().let { it ?: 1f }) }
    }
}
