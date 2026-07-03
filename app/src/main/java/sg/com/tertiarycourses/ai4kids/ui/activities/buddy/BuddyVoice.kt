package sg.com.tertiarycourses.ai4kids.ui.activities.buddy

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Strip emoji / pictographs so the buddy doesn't read them aloud (display keeps them). */
fun stripForSpeech(text: String): String =
    text
        .replace(Regex("[\\uD800-\\uDFFF]+"), "") // surrogate-pair emoji
        .replace(Regex("[\\u2190-\\u21FF\\u2300-\\u27BF\\u2B00-\\u2BFF\\uFE0F\\u20E3]"), "") // arrows, symbols, VS/keycap
        .replace(Regex("\\s{2,}"), " ")
        .trim()

/**
 * On-device speech for the Talking Buddy. Wraps Android [TextToSpeech] (offline
 * once a voice is installed — no network, no key) and exposes a [speaking] flag so
 * the animated face can flap its mouth while the buddy talks. Mirrors the website's
 * TalkingBuddy, minus the server TTS chain that Android doesn't need.
 */
class BuddyVoice(private val onDone: () -> Unit) {
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())

    var ready by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set

    fun attach(engine: TextToSpeech, ok: Boolean) {
        tts = engine
        ready = ok
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { speaking = true } }
            override fun onDone(utteranceId: String?) { main.post { speaking = false; onDone() } }
            @Deprecated("deprecated in API 21")
            override fun onError(utteranceId: String?) { main.post { speaking = false; onDone() } }
        })
    }

    /** Speak [text] (emoji stripped). Interrupts any current utterance. */
    fun speak(text: String) {
        val clean = stripForSpeech(text)
        if (!ready || clean.isEmpty()) return
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, clean.hashCode().toString())
    }

    fun stop() {
        tts?.stop()
        speaking = false
    }
}

/**
 * Remember a [BuddyVoice], initialising TextToSpeech and shutting it down with the
 * composition. [onDone] fires when an utterance finishes (e.g. to re-enable input).
 */
@Composable
fun rememberBuddyVoice(onDone: () -> Unit = {}): BuddyVoice {
    val context = LocalContext.current
    val voice = remember { BuddyVoice(onDone) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val e = engine ?: return@TextToSpeech
                val ok = listOf(Locale.UK, Locale.US, Locale.ENGLISH, Locale.getDefault()).any { loc ->
                    val r = e.setLanguage(loc)
                    r == TextToSpeech.LANG_AVAILABLE ||
                        r == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                        r == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                }
                e.setSpeechRate(0.95f)
                e.setPitch(1.15f)
                voice.attach(e, ok)
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }
    return voice
}
