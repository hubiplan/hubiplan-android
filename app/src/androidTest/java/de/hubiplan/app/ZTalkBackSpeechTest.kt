package de.hubiplan.app

import android.app.UiAutomation
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.concurrent.atomic.AtomicInteger
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
            var clearedSystemDialog=false
            while(!condition() && SystemClock.elapsedRealtime()<until) {
                // Nur der bekannte System-UI-Startdialog des eigenen Emulators,
                // niemals einen Fehlerdialog der App verdecken.
                val root=automation.rootInActiveWindow
                if(message=="Speichern erreichbar" && !clearedSystemDialog && root?.packageName=="android" && root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty()) {
                    val wait=root.findAccessibilityNodeInfosByText("Wait").firstOrNull {it.isClickable}
                    if(wait!=null) {
                        android.util.Log.i("HUBIplanSpeech","Fixture: vorübergehenden System-UI-Startdialog mit Wait bestätigt")
                        wait.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        clearedSystemDialog=true
                    }
                }
                SystemClock.sleep(100)
            }
            fun dump(node:AccessibilityNodeInfo?, depth:Int=0) {
                if(node==null || depth>8)return
                android.util.Log.i("HUBIplanSpeech", "node depth=$depth package=${node.packageName} text=${node.text} desc=${node.contentDescription} class=${node.className} clickable=${node.isClickable} focus=${node.isAccessibilityFocused} actions=${node.actionList.map {it.id}}")
                for(i in 0 until node.childCount)dump(node.getChild(i),depth+1)
            }
            if(!condition())dump(automation.rootInActiveWindow)
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
        val component=ComponentName(context,SpeechRecorderService::class.java)
        val previousComponent=context.packageManager.getComponentEnabledSetting(component)
        Texte.setzeFuerTest("en",context.assets.open("texte/en.json").bufferedReader().use {Texte.parse(it.readText())})
        try {
            shell("settings put secure accessibility_enabled 0")
            context.packageManager.setComponentEnabledSetting(component,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP)
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED,context.packageManager.getComponentEnabledSetting(component))
            waitFor("Prüfsynthese über System-Intent erreichbar") {context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).setPackage(context.packageName),PackageManager.MATCH_DEFAULT_ONLY).size==1}
            shell("settings put secure tts_default_synth de.hubiplan.app")
            shell("settings put secure tts_enabled_plugins de.hubiplan.app")
            // Getrennte Fixture-Selbstprüfung; dieser Text ist ausdrücklich keine TalkBack-Ausgabe.
            val init=AtomicInteger(Int.MIN_VALUE)
            val probe=TextToSpeech(context,{init.set(it)},"de.hubiplan.app")
            try {
                assertTrue("Prüfsynthese ist als Engine registriert",probe.engines.any {it.name=="de.hubiplan.app"})
                waitFor("Prüfsynthese initialisiert") {init.get()!=Int.MIN_VALUE}
                assertEquals(TextToSpeech.SUCCESS,init.get())
                probe.speak("Fixture engine ready",TextToSpeech.QUEUE_FLUSH,null,"fixture-probe")
                waitFor("Prüfsynthese empfängt Kontrolltext") {SpeechRecorderService.utterances.any {it=="Fixture engine ready"}}
            } finally {probe.shutdown()}
            SpeechRecorderService.utterances.clear()
            shell("settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService")
            shell("settings put secure accessibility_enabled 1")
            val manager=context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            waitFor("TalkBack wirklich aktiv") {manager.getEnabledAccessibilityServiceList(-1).any {it.resolveInfo.serviceInfo.packageName=="com.google.android.marvin.talkback"}}
            // Der erste Start des Dienstes darf seine Initialisierung abschließen,
            // bevor die nicht exportierte Prüfansicht in den Vordergrund kommt.
            SystemClock.sleep(2000)
            ActivityScenario.launch(AccessibilityTestActivity::class.java).use {
                waitFor("Speichern erreichbar") {find(automation.rootInActiveWindow,"Save")!=null}
                val save=requireNotNull(find(automation.rootInActiveWindow,"Save"))
                assertTrue("Aktion unterstützt Accessibility-Fokus",save.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                waitFor("Fokus auf Aktion") {find(automation.rootInActiveWindow,"Save")?.isAccessibilityFocused==true}
                waitFor("TalkBack nutzt Prüfsynthese") {SpeechRecorderService.utterances.any {it.contains("Save",true)}}
                fun verify(wanted:String) {
                    SpeechRecorderService.utterances.clear()
                    assertTrue(requireNotNull(find(automation.rootInActiveWindow,"Save")).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    waitFor("TalkBack sagt den Erfolg: $wanted") {SpeechRecorderService.utterances.any {it.contains(wanted)}}
                    SystemClock.sleep(500)
                    assertEquals("Erfolg genau einmal",1,SpeechRecorderService.utterances.count {it.contains(wanted)})
                    assertTrue(find(automation.rootInActiveWindow,"Save")?.isAccessibilityFocused==true)
                    android.util.Log.i("HUBIplanSpeechResult","PASS success="+SpeechRecorderService.utterances.joinToString(" | "))
                }
                fun reset(wanted:String) {
                    fun focusButton(label:String) {
                        SpeechRecorderService.utterances.clear()
                        requireNotNull(find(automation.rootInActiveWindow,label)).performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                        waitFor("Fokus auf $label") {find(automation.rootInActiveWindow,label)?.isAccessibilityFocused==true}
                        waitFor("TalkBack liest $label") {SpeechRecorderService.utterances.any {it.contains(label) && !it.contains(wanted)}}
                    }
                    // Die Fixture wie mit TalkBack bedienen: erst das Reset-Ziel
                    // fokussieren, danach die nächste Aktion wieder aufsuchen.
                    focusButton("Reset")
                    assertTrue(requireNotNull(find(automation.rootInActiveWindow,"Reset")).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    waitFor("Vorheriger Erfolg entfernt") {find(automation.rootInActiveWindow,wanted)==null}
                    focusButton("Save")
                }
                verify("Saved.")
                reset("Saved.")
                verify("Saved.")
                reset("Saved.")
                Texte.setzeFuerTest("de")
                requireNotNull(find(automation.rootInActiveWindow,"Save")).performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                waitFor("Aktionsfokus für zweite Rückmeldung") {find(automation.rootInActiveWindow,"Save")?.isAccessibilityFocused==true}
                verify("Gespeichert.")
            }
        } finally {
            shell("settings put secure accessibility_enabled 0")
            for((key,value) in previous) if(value=="null" || value.isBlank())shell("settings delete secure $key") else shell("settings put secure $key $value")
            context.packageManager.setComponentEnabledSetting(component,previousComponent,PackageManager.DONT_KILL_APP)
        }
    }
}
