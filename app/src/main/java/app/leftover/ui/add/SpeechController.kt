package app.leftover.ui.add

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Wraps Android's [SpeechRecognizer]. It uses the on-device recognizer when the phone has one (Android 13+) and otherwise
 * asks the system recognizer to prefer offline mode. Depending on the phone's speech service, audio may still be
 * processed by that service; Leftover itself never stores or sends audio.
 */
class SpeechController(private val context: Context, private val language: String, private val onFinalText: (String) -> Unit) {
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        message = null
        partial = ""
        if (!available) {
            message = "Speech recognition isn't available on this phone. Type it instead."
            return
        }
        destroy()
        val created = if (Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        created.setRecognitionListener(listener)
        recognizer = created
        listening = true
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            },
        )
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    fun permissionDenied() {
        message = "Leftover needs the microphone to hear you. You can allow it, or type the entry instead."
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { listening = true }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isBlank()) message = "Didn't catch the amount. Try: \"Spent 200 on lunch today\"." else onFinalText(text)
        }

        override fun onError(error: Int) {
            listening = false
            message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch the amount. Try: \"Spent 200 on lunch today\"."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Leftover needs the microphone to hear you."
                else -> "Speech recognition isn't working right now. You can type the entry instead."
            }
        }
    }
}
