package de.hubiplan.app

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import java.util.concurrent.CopyOnWriteArrayList

/** Nur Debug und standardmäßig deaktiviert. Zeichnet TalkBack→TTS auf, kein Hörtest. */
class SpeechRecorderService : TextToSpeechService() {
    companion object { val utterances = CopyOnWriteArrayList<String>() }
    override fun onGetLanguage() = arrayOf("eng", "GBR", "")
    override fun onIsLanguageAvailable(language: String?, country: String?, variant: String?) = TextToSpeech.LANG_COUNTRY_AVAILABLE
    override fun onLoadLanguage(language: String?, country: String?, variant: String?) = TextToSpeech.LANG_COUNTRY_AVAILABLE
    override fun onStop() {}
    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val text=request.charSequenceText.toString()
        utterances += text
        android.util.Log.i("HUBIplanSpeech",text)
        // Stille PCM-Daten: geprüft wird der echte Ausgabetext des Dienstes.
        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1)
        val silence=ByteArray(640)
        callback.audioAvailable(silence,0,silence.size)
        callback.done()
    }
}
