package sg.com.tertiarycourses.ai4kids.ui.activities.phonics

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * All sound for Phonics Quest: whole *words* through the device's TextToSpeech,
 * isolated *sounds* through pre-recorded clips in `res/raw`.
 *
 * Mirrors the web app's `useSpeaker` + `usePhonemePlayer` pair
 * (`src/app/learn/phonics/PhonicsQuest.tsx`), which is the source of truth for the
 * quest's behaviour. They're one class here because the two channels have to be
 * coordinated — see [play], which silences speech before sounding a clip.
 *
 * Audio is keyed by *phoneme*, never by letter — a letter has many sounds
 * (c=/k/ or /s/, g=/g/ or /dʒ/, every vowel), so only a phoneme id is
 * unambiguous. The clips were generated once from cloud TTS with correct SSML
 * `<phoneme>` pronunciation (see `tools/phoneme-tts/`) and ship in the APK, so
 * playback is fully **on-device**: nothing leaves the phone, keeping the
 * offline-first / no-collection posture of the phonics activity. TTS can't stand
 * in for them — it reads text *as words*, so "ah" ≠ /æ/.
 */
@Stable
class PhonicsAudio internal constructor(context: Context) {
    // Hold the app context (not an Activity) since this outlives recompositions.
    private val appContext = context.applicationContext

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // One reusable player: release the previous clip before the next so at most
    // one MediaPlayer is alive and sounds don't overlap.
    private var player: MediaPlayer? = null

    // Whoever is awaiting the current clip, so a superseded sound can be resolved
    // rather than left hanging — see [cut].
    private var pending: CancellableContinuation<Unit>? = null

    internal fun start() {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val e = engine ?: return@TextToSpeech
            // Use whichever English voice is actually installed; an emulator may
            // have none, in which case speech stays silent (no crash).
            val available = listOf(Locale.UK, Locale.US, Locale.ENGLISH, Locale.getDefault())
                .any { loc ->
                    val r = e.setLanguage(loc)
                    r == TextToSpeech.LANG_AVAILABLE ||
                        r == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                        r == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                }
            e.setSpeechRate(0.85f)
            ttsReady = available
        }
        tts = engine
    }

    internal fun release() {
        cut()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    /** Speak a whole word. Silently no-ops if TTS isn't ready/available. */
    fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
    }

    /**
     * Play the phoneme clip for [slug], suspending until the sound actually
     * finishes.
     *
     * Callers time their pauses from the *silence* rather than from the call, so
     * a sound-out stays smooth at any [PHONEME_RATE]: a fixed gap shorter than a
     * slowed clip would chop each sound off partway.
     *
     * An empty [slug] (a silent letter, or an unknown letter team) plays nothing
     * and returns at once — that silence is the lesson in Whisper Woods.
     */
    suspend fun play(slug: String) {
        if (slug.isEmpty()) return
        val resId = appContext.resources.getIdentifier(slug, "raw", appContext.packageName)
        if (resId == 0) return // unknown slug — degrade silently, never crash

        // Silence any in-flight spoken word first. The recorded phoneme and the TTS
        // voice are separate channels: left alone they overlap, or the voice ducks
        // the clip, so the same clean sound comes out muddied in the worlds that
        // speak the word a lot (e.g. Letters Land).
        tts?.stop()
        cut()

        val mp = MediaPlayer.create(appContext, resId) ?: return
        player = mp
        try {
            suspendCancellableCoroutine { cont ->
                pending = cont
                // "completion" is the normal path; "error" covers a bad decode —
                // without it a stalled clip would hang the sequence awaiting it.
                mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                mp.setOnErrorListener { _, _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                // Leaving a round mid-sound-out cancels the caller; cut the clip so
                // it can't play on over the next round.
                cont.invokeOnCancellation { stop() }
                // Phonemes play a touch under real-time so a young child can catch
                // each sound; pitch is pinned to the recording's so it slows without
                // dropping low and muddy.
                mp.playbackParams = PlaybackParams().setSpeed(PHONEME_RATE).setPitch(1f)
                mp.start()
            }
        } finally {
            // Only clean up if we still own the player; if a newer sound superseded
            // us, `cut` already released this clip and the newer one owns the field.
            if (player === mp) {
                mp.release()
                player = null
                pending = null
            }
        }
    }

    /**
     * Release the current clip, resolving whoever was awaiting it.
     *
     * The resume matters: `release()` fires no completion callback, so a caller
     * suspended on a clip we drop would wait forever — a tap on a sound dot
     * mid-blend would stall the blend for good. (The web gets this free: pausing
     * its audio element emits "pause", which settles the promise.)
     */
    private fun cut() {
        pending?.let { if (it.isActive) it.resume(Unit) }
        pending = null
        player?.release()
        player = null
    }

    /**
     * Cut everything now: the current clip and any spoken word. Used when the
     * child leaves a round mid-sound-out, so nothing bleeds into the next.
     */
    fun stop() {
        cut()
        tts?.stop()
    }

    companion object {
        /** Phonemes play a touch under real-time so a young child can catch each
         *  sound. 1 = the raw recording; lower = slower. Tune here. Matches the
         *  web's `PHONEME_RATE`. */
        const val PHONEME_RATE = 0.67f
    }
}

/**
 * Remembers a [PhonicsAudio] for the quest, shutting its engines down when the
 * composable leaves the tree.
 */
@Composable
fun rememberPhonicsAudio(): PhonicsAudio {
    val context = LocalContext.current
    val audio = remember { PhonicsAudio(context) }
    DisposableEffect(audio) {
        audio.start()
        onDispose { audio.release() }
    }
    return audio
}
