package sg.com.tertiarycourses.ai4kids.ui.activities.phonics

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Plays a pre-recorded phoneme clip from `res/raw` by its slug (e.g.
 * `v_a_short` for /æ/, `c_s` for /s/).
 *
 * Unlike TextToSpeech — which reads text *as words* and so mangles isolated
 * sounds ("ah" ≠ /æ/) — these clips were generated once from cloud TTS with
 * correct SSML `<phoneme>` pronunciation (see `tools/phoneme-tts/`) and ship in
 * the APK. Playback is therefore fully **on-device**: nothing leaves the phone,
 * keeping the offline-first / no-collection posture of the phonics activity.
 *
 * Audio is keyed by *phoneme*, never by letter — a letter has many sounds
 * (c=/k/ or /s/, g=/g/ or /dʒ/, every vowel), so only a phoneme id is
 * unambiguous.
 */
class PhonemePlayback(context: Context) {
    // Hold the app context (not an Activity) since this outlives recompositions.
    private val appContext = context.applicationContext
    // One reusable player: release the previous clip before the next so at most
    // one MediaPlayer is alive and sounds don't overlap.
    private var player: MediaPlayer? = null

    fun play(slug: String) {
        val resId = appContext.resources.getIdentifier(slug, "raw", appContext.packageName)
        if (resId == 0) return // unknown slug — degrade silently, never crash
        player?.release()
        player = MediaPlayer.create(appContext, resId)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}

/**
 * Remembers a [PhonemePlayback] and returns its `play` function, releasing the
 * underlying player when the composable leaves the tree.
 */
@Composable
fun rememberPhonemePlayer(): (String) -> Unit {
    val context = LocalContext.current
    val playback = remember { PhonemePlayback(context) }
    DisposableEffect(playback) { onDispose { playback.release() } }
    return remember(playback) { playback::play }
}