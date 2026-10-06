package dev.matejgroombridge.voquab.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.matejgroombridge.voquab.BuildConfig
import java.util.Locale

/**
 * Thin wrapper around the platform [TextToSpeech] engine so a word can be
 * heard with one tap — no extra libraries, no respellings to read.
 *
 * Always Australian or British English, never American: an installed
 * en-AU voice if there is one, then en-GB, then a network voice for
 * either. Only if the engine has no AU/GB voice listed at all does it fall
 * back to asking for the language by locale (still AU, then UK).
 *
 * Calls made before the engine finishes initialising are dropped rather
 * than queued; in practice it's ready long before anyone taps 🔊.
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
        val english = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == "en" && !it.isNotInstalled() }
        // Offline voices first (they work on a plane), then network ones;
        // within each, Australian before British, best quality first.
        val voice = listOf(false, true).firstNotNullOfOrNull { network ->
            COUNTRIES.firstNotNullOfOrNull { codes ->
                english
                    .filter { it.isNetworkConnectionRequired == network && it.locale.country.uppercase() in codes }
                    .maxByOrNull { it.quality }
            }
        }
        if (voice != null) {
            tts.voice = voice
        } else {
            val locale = FALLBACK_LOCALES.firstOrNull {
                tts.isLanguageAvailable(it) >= TextToSpeech.LANG_COUNTRY_AVAILABLE
            } ?: Locale.UK
            tts.language = locale
        }
        // Slightly slower than default so unfamiliar words are easy to catch.
        tts.setSpeechRate(0.9f)
        if (BuildConfig.DEBUG) Log.d(TAG, "voice=${tts.voice?.name} locale=${tts.voice?.locale}")
    }

    private fun Voice.isNotInstalled(): Boolean =
        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in features.orEmpty()

    private companion object {
        const val TAG = "Speaker"
        /** Engines report either 2- or 3-letter country codes. */
        val COUNTRIES = listOf(setOf("AU", "AUS"), setOf("GB", "GBR"))
        val FALLBACK_LOCALES = listOf(Locale.forLanguageTag("en-AU"), Locale.UK)
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
