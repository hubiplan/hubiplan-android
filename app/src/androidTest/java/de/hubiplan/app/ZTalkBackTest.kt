package de.hubiplan.app

import android.app.UiAutomation
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Echter TalkBack-Dienst und native Accessibility-Aktionen; kein Hör-/Nutzertest. */
class ZTalkBackTest {
    @Test fun talkBackBleibtAktivUndErreichtAuswahlDropdownUndStatus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Nur auf dem eigenen dafür freigegebenen Emulator", Build.HARDWARE.contains("ranchu") && InstrumentationRegistry.getArguments().getString("fixtureTalkBack") == "1")
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        val oldServices = shell("settings get secure enabled_accessibility_services").trim()
        val oldEnabled = shell("settings get secure accessibility_enabled").trim()
        val context = instrumentation.targetContext
        Texte.setzeFuerTest("en", context.assets.open("texte/en.json").bufferedReader().use { Texte.parse(it.readText()) })
        fun waitFor(message: String, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 20_000
            while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
            assertTrue(message, condition())
        }
        fun find(node: AccessibilityNodeInfo?, label: String, actionable: Boolean): AccessibilityNodeInfo? {
            if (node == null) return null
            if ((node.text?.toString() == label || node.contentDescription?.toString() == label) && (!actionable || node.isClickable)) return node
            for (i in 0 until node.childCount) find(node.getChild(i), label, actionable)?.let { return it }
            return null
        }
        fun node(label: String, actionable: Boolean = true): AccessibilityNodeInfo {
            waitFor("Zugänglicher Knoten: " + label) { find(automation.rootInActiveWindow, label, actionable) != null }
            return requireNotNull(find(automation.rootInActiveWindow, label, actionable)).also { it.refresh(); android.util.Log.i("HUBIplanA11y", "label=$label class=${it.className} clickable=${it.isClickable} state=${it.stateDescription} selected=${it.isSelected} live=${it.liveRegion}") }
        }
        try {
            shell("settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService")
            shell("settings put secure accessibility_enabled 1")
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            waitFor("TalkBack-Dienst ist wirklich aktiv") { manager.getEnabledAccessibilityServiceList(-1).any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" } }
            ActivityScenario.launch(AccessibilityTestActivity::class.java).use {
                val picker = node("Account: nothing selected")
                waitFor("Nativer Dropdown-Zustand collapsed") { node("Account: nothing selected").stateDescription?.toString() == "collapsed" }
                assertTrue(picker.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                assertTrue(picker.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                val cash = node("Cash")
                assertTrue(cash.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                assertEquals("collapsed", node("Account: cash").stateDescription?.toString())
                val event = node("Event notice")
                assertTrue(event.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                assertTrue(event.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitFor("Mitteilung ist semantisch ausgewählt") { node("Event notice").isSelected }
                assertFalse(node("Board notice").isSelected)
                val save = node("Save")
                assertTrue(save.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                assertTrue(save.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                assertEquals(1, node("Saved.", actionable = false).liveRegion)
                assertTrue(node("Save").isAccessibilityFocused)
                assertTrue(manager.getEnabledAccessibilityServiceList(-1).any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" })
            }
        } finally {
            if (oldServices == "null" || oldServices.isBlank()) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services " + oldServices)
            shell("settings put secure accessibility_enabled " + if (oldEnabled == "1") "1" else "0")
        }
    }
}
