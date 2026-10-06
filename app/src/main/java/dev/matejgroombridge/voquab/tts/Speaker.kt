package dev.matejgroombridge.voquab.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Thin wrapper around the platform [TextToSpeech] engine so a word can be
 * heard with one tap — no extra libraries, works offline with the phone's
 * installed voices. Prefers an Australian voice, then British, then the
 * engine default.
 *
 * Calls made before the engine finishes initialising are dropped rather than
 * queued; in practice the engine is ready long before anyone taps 🔊.
 */
class Speaker(context: Context) {

    private var ready = false

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            pickVoice()
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voquab-$text")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun pickVoice() {
        val preferred = listOf(Locale.forLanguageTag("en-AU"), Locale.UK, Locale.US)
        val locale = preferred.firstOrNull {
            tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
        } ?: return
        tts.language = locale
        // Slightly slower than default so unfamiliar words are easy to catch.
        tts.setSpeechRate(0.9f)
    }
}

/** A [Speaker] tied to the composition — shut down when it leaves. */
@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember(context) { Speaker(context) }
    DisposableEffect(speaker) {
        onDispose { speaker.shutdown() }
    }
    return speaker
}
