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

class ZTalkBackSpeechTest {
    @Test fun echterTalkBackUebergibtErfolgAnTtsOhneDenAktionsfokusZuVerschieben() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        assumeTrue("Nur eigener Emulator mit ausdrücklicher Fixture-Freigabe",Build.HARDWARE.contains("ranchu") && InstrumentationRegistry.getArguments().getString("fixtureTalkBack")=="1")
        val automation=instrument.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use {it.readText().trim()}
        fun waitFor(message:String, condition:()->Boolean) {
            val until=SystemClock.elapsedRealtime()+25000
            while(!condition() && SystemClock.elapsedRealtime()<until) SystemClock.sleep(100)
            assertTrue(message+"; TTS="+SpeechRecorderService.utterances.joinToString(" | "),condition())
        }
        fun find(node:AccessibilityNodeInfo?,label:String):AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.text?.toString()==label || node.contentDescription?.toString()==label) {
                var parent:AccessibilityNodeInfo?=node
                while(parent!=null && !parent.isClickable)parent=parent.parent
                return parent ?: node
            }
            for(i in 0 until node.childCount)find(node.getChild(i),label)?.let{return it}
            return null
        }
        val keys=listOf("enabled_accessibility_services","accessibility_enabled","tts_default_synth","tts_enabled_plugins")
        val previous=keys.associateWith {shell("settings get secure $it")}
        val context=instrument.targetContext
        Texte.setzeFuerTest("en",context.assets.open("texte/en.json").bufferedReader().use {Texte.parse(it.readText())})
        try {
            shell("settings put secure accessibility_enabled 0")
            shell("pm enable de.hubiplan.app/.SpeechRecorderService")
            shell("settings put secure tts_default_synth de.hubiplan.app")
            shell("settings put secure tts_enabled_plugins de.hubiplan.app")
            shell("settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService")
            shell("settings put secure accessibility_enabled 1")
            val manager=context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            waitFor("TalkBack wirklich aktiv") {manager.getEnabledAccessibilityServiceList(-1).any {it.resolveInfo.serviceInfo.packageName=="com.google.android.marvin.talkback"}}
            ActivityScenario.launch(AccessibilityTestActivity::class.java).use {
                waitFor("Speichern erreichbar") {find(automation.rootInActiveWindow,"Save")!=null}
                val save=requireNotNull(find(automation.rootInActiveWindow,"Save"))
                save.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                waitFor("Fokus auf Aktion") {find(automation.rootInActiveWindow,"Save")?.isAccessibilityFocused==true}
                waitFor("TalkBack nutzt Prüfsynthese") {SpeechRecorderService.utterances.any {it.contains("Save",true)}}
                SpeechRecorderService.utterances.clear()
                assertTrue(save.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitFor("TalkBack sagt den Erfolg") {SpeechRecorderService.utterances.any {it.contains("Saved.")}}
                assertTrue(find(automation.rootInActiveWindow,"Save")?.isAccessibilityFocused==true)
                android.util.Log.i("HUBIplanSpeechResult","PASS success="+SpeechRecorderService.utterances.joinToString(" | "))
            }
        } finally {
            shell("settings put secure accessibility_enabled 0")
            for((key,value) in previous) if(value=="null" || value.isBlank())shell("settings delete secure $key") else shell("settings put secure $key $value")
            shell("pm default-state de.hubiplan.app/.SpeechRecorderService")
        }
    }
}
