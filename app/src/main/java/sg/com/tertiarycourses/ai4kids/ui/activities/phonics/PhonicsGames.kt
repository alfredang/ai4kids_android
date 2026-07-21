package sg.com.tertiarycourses.ai4kids.ui.activities.phonics

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sg.com.tertiarycourses.ai4kids.ai.GeminiClient
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * The Phonics Quest mini-games. Mirrors the web app's `PhonicsQuest.tsx`, which
 * is the source of truth for the quest's behaviour — the Android Buddy differs by
 * calling Gemini on-device rather than the web's `/api/learn/phonics-buddy`, and
 * the star tally stays local (see `PhonicsStore`).
 */

/** Silence held after the last letter finishes, before the word is blended — it
 *  separates "that was the last sound" from "now here's the whole word". Timed
 *  from the end of the clip (see [PhonicsAudio.play]), so it is real silence. */
private const val BLEND_LEAD_IN = 600L

/** Silence *between* sounds in a sound-out, held AFTER each clip has fully played.
 *  Timing from the clip's end rather than a fixed gap keeps the cadence smooth at
 *  any `PHONEME_RATE` — a fixed gap shorter than a slowed clip chops each sound
 *  off partway and sounds choppy. */
private const val BLEND_SOUND_GAP = 50L

/** Small white "hear it" pill that re-speaks the current word. */
@Composable
private fun HearButton(onClick: () -> Unit, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .softShadow(CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear it", tint = color, modifier = Modifier.size(20.dp))
        Text("Hear it", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Shared per-round feedback used by every phonics game: a cheerful "correct"
 * message with a Next button (so the child controls the pace), or a gentle
 * "try again" nudge after a wrong choice.
 */
@Composable
private fun RoundFeedback(
    solved: Boolean,
    showWrong: Boolean,
    isLast: Boolean,
    color: Color,
    onNext: () -> Unit,
) {
    when {
        solved -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Great job! 🎉", color = Theme.Green, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            KidButton(title = if (isLast) "Finish ▶" else "Next ▶", color = color, onClick = onNext)
        }
        showWrong -> Text(
            "Not quite — listen again and try once more! 🙂",
            color = Theme.Red,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** How long to wait for the AI hint before showing a generic one instead. The
 *  Buddy is usually ~1s, but a slow or rate-limited Gemini can take far longer —
 *  a child shouldn't sit watching "Thinking…" that long. */
private const val HINT_TIMEOUT_MS = 3500L

/** A canned, answer-safe hint per game, used when the AI Buddy is too slow or
 *  errors. Never names a specific word, so it can't give a round away. */
private fun genericHint(game: PhonicsKind): String = when (game) {
    PhonicsKind.POP -> "Say the word slowly and listen to its very first sound!"
    PhonicsKind.BUILD -> "Sound out each letter, then push them together!"
    PhonicsKind.RHYME -> "Listen to the ending sound and find one that matches!"
    PhonicsKind.LISTEN -> "Listen carefully and pick the word that sounds just right!"
    PhonicsKind.BLEND -> "Blend the sounds together slowly to hear the word!"
    PhonicsKind.DIGRAPH -> "Two letters can team up to make one brand-new sound!"
}

/**
 * The Gemini-powered "Phonics Buddy": a button that asks Gemini for a short,
 * kid-friendly hint about the current round and reads it aloud. Hidden entirely
 * when no API key is configured (the games stay fully playable without it).
 * [promptKey] resets the hint when the round changes.
 *
 * The hint races a [HINT_TIMEOUT_MS] timeout into [genericHint], so a slow model
 * never leaves a child waiting — whichever lands first is shown *and* spoken.
 */
@Composable
private fun PhonicsBuddy(
    promptKey: String,
    prompt: String,
    game: PhonicsKind,
    color: Color,
    audio: PhonicsAudio,
) {
    if (!GeminiClient.isConfigured()) return
    var hint by remember(promptKey) { mutableStateOf<String?>(null) }
    var busy by remember(promptKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .softShadow(CircleShape)
                .clip(CircleShape)
                .background(if (busy) color.copy(alpha = 0.4f) else color)
                .clickable(enabled = !busy) {
                    scope.launch {
                        busy = true
                        val reply = withTimeoutOrNull(HINT_TIMEOUT_MS) {
                            GeminiClient.generate(prompt, model = GeminiClient.FLASH_LITE)
                        }
                        val msg = reply ?: genericHint(game)
                        busy = false
                        hint = msg
                        audio.speak(msg)
                    }
                }
                .padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(if (busy) "Thinking…" else "Ask Buddy", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        hint?.let {
            Text(
                "🤖 $it",
                color = Theme.Ink.copy(alpha = 0.85f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(14.dp),
            )
        }
    }
}

/* ----------------------------- Pop the Phoneme ----------------------------- */

@Composable
fun PopPhonemeGame(
    rounds: List<PopRound>,
    color: Color,
    audio: PhonicsAudio,
    onProgress: (Int, Int) -> Unit,
    onFinish: (Int) -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf<Int?>(null) }
    var solved by remember(index) { mutableStateOf(false) }
    val round = rounds[index]
    val options = remember(index) { round.options.shuffled() }
    val isLast = index + 1 >= rounds.size
    val scope = rememberCoroutineScope()

    LaunchedEffect(index) {
        onProgress(index, rounds.size)
        delay(250)
        audio.speak(round.word)
    }
    LaunchedEffect(wrong) { if (wrong != null) { delay(1300); wrong = null } }

    fun pick(i: Int) {
        if (wrong != null || solved) return
        if (options[i] == round.answer) solved = true
        else { wrong = i; mistakes += 1 }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().kidCard().padding(20.dp),
        ) {
            Text(round.emoji, fontSize = 84.sp)
            Text(round.word, color = Theme.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            HearButton(onClick = { audio.speak(round.word) }, color = color)
        }
        Text(
            "Hear each sound, then pick the one it starts with!",
            color = Theme.Ink.copy(alpha = 0.7f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, slug ->
                NumberedSoundOption(
                    number = i + 1,
                    isWrong = wrong == i,
                    color = color,
                    onHear = { scope.launch { audio.play(slug) } },
                    onPick = { pick(i) },
                )
            }
        }
        RoundFeedback(solved = solved, showWrong = wrong != null, isLast = isLast, color = color) {
            if (isLast) onFinish(mistakes) else index += 1
        }
        PhonicsBuddy(
            promptKey = "pop-${round.word}",
            prompt = "You are a cheerful phonics tutor for a 5-year-old child. In ONE short sentence (max 15 words, simple words), help them hear that the word \"${round.word}\" starts with the letter \"${round.letter}\". Be warm and playful. No emojis.",
            game = PhonicsKind.POP,
            color = color,
            audio = audio,
        )
    }
}

/** A numbered option that the child can listen to, then choose. The letter is
 *  hidden so the decision is made by ear. */
@Composable
private fun NumberedSoundOption(
    number: Int,
    isWrong: Boolean,
    color: Color,
    onHear: () -> Unit,
    onPick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .width(104.dp)
            .softShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(if (isWrong) Theme.Red.copy(alpha = 0.18f) else Color.White)
            .padding(12.dp),
    ) {
        Text("$number", color = color, fontSize = 34.sp, fontWeight = FontWeight.Black)
        // Hear the sound.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .clickable(onClick = onHear)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear", tint = color, modifier = Modifier.size(18.dp))
            Text("Hear", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        // Choose this one.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(color)
                .clickable(onClick = onPick)
                .padding(vertical = 8.dp),
        ) {
            Text("Pick", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

/* ----------------------------- Build the Word ----------------------------- */

@Composable
fun BuildWordGame(
    rounds: List<BuildRound>,
    color: Color,
    audio: PhonicsAudio,
    onProgress: (Int, Int) -> Unit,
    onFinish: (Int) -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var solved by remember(index) { mutableStateOf(false) }
    val round = rounds[index]
    val target = round.word
    val tiles = remember(index) { target.toList().shuffled() }
    var used by remember(index) { mutableStateOf(setOf<Int>()) }
    var wrongTile by remember { mutableStateOf<Int?>(null) }
    // The tile tapped once — armed so a child can hear it, then tap again to place.
    var pending by remember(index) { mutableStateOf<Int?>(null) }
    val built = target.take(used.size)
    val isLast = index + 1 >= rounds.size
    val scope = rememberCoroutineScope()

    // The sound this letter makes in THIS word (silent letters map to "" → no
    // sound). Keyed by position so e.g. the C in CASTLE is /k/, not the /s/ of C.
    fun soundForLetter(ch: Char): String = round.sounds.getOrElse(target.indexOf(ch)) { "" }

    LaunchedEffect(index) {
        onProgress(index, rounds.size)
        delay(250)
        audio.speak(round.word)
    }
    LaunchedEffect(wrongTile) { if (wrongTile != null) { delay(500); wrongTile = null } }
    // Sound out the finished word. Keyed on `solved`, which resets with `index`, so
    // advancing mid-sound-out cancels this and cuts the audio (see PhonicsAudio.play).
    LaunchedEffect(solved) {
        if (!solved) return@LaunchedEffect
        // The final letter was already heard when it was armed, but not since it
        // landed — sound it once more, then hold a beat of real silence, so the
        // child hears "that's the last letter" and "now the whole word" as two
        // separate things rather than one run-on stream.
        audio.play(round.sounds.last())
        delay(BLEND_LEAD_IN)
        // Blend the whole word: each sound fully, in order, then the word itself.
        round.sounds.forEach { slug ->
            audio.play(slug)
            delay(BLEND_SOUND_GAP)
        }
        delay(150)
        audio.speak(target)
    }
    // Leaving the stage mid-sound-out: cut the audio so it doesn't play on after.
    DisposableEffect(Unit) { onDispose { audio.stop() } }

    fun tap(i: Int, ch: Char) {
        if (i in used || wrongTile != null || solved) return
        // First tap: hear the letter's sound and arm the tile — don't place or judge
        // yet, so a child can listen before committing. (A silent letter plays no
        // sound, but the tile still highlights, so the tap doesn't feel ignored.)
        if (pending != i) {
            pending = i
            scope.launch { audio.play(soundForLetter(ch)) }
            return
        }
        // Second tap on the armed tile: place it.
        pending = null
        if (ch != target[built.length]) {
            wrongTile = i
            mistakes += 1
            return
        }
        used = used + i
        // Already heard on the first tap — don't replay the individual sound.
        if (used.size == target.length) solved = true
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (pending != null) "Tap it again to place it! 👆" else "Tap a letter to hear it",
            color = Theme.Ink.copy(alpha = 0.7f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().kidCard().padding(20.dp),
        ) {
            Text(round.emoji, fontSize = 76.sp)
            HearButton(onClick = { audio.speak(round.word) }, color = color)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                target.forEachIndexed { i, ch ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (i < built.length) color.copy(alpha = 0.2f) else Theme.Ink.copy(alpha = 0.06f)),
                    ) {
                        if (i < built.length) Text("$ch", color = color, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            tiles.forEachIndexed { i, ch ->
                val isUsed = i in used
                val armed = pending == i
                // The armed tile lifts, so "I'm listening to this one" is visible at
                // a glance before the second tap commits it.
                val tileScale by animateFloatAsState(
                    targetValue = if (armed) 1.12f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "armedTile",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .scale(tileScale)
                        .alpha(if (isUsed) 0.25f else 1f)
                        .softShadow(RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                wrongTile == i -> Theme.Red
                                armed -> color.copy(alpha = 0.2f)
                                else -> Color.White
                            },
                        )
                        .border(
                            width = if (armed) 3.dp else 0.dp,
                            color = if (armed) color else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(enabled = !isUsed) { tap(i, ch) },
                ) {
                    Text(
                        "$ch",
                        color = when {
                            wrongTile == i -> Color.White
                            armed -> color
                            else -> Theme.Ink
                        },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        RoundFeedback(solved = solved, showWrong = wrongTile != null, isLast = isLast, color = color) {
            // Stop the completion sound-out first so it can't bleed into the next
            // round — the child chose to move on.
            audio.stop()
            if (isLast) onFinish(mistakes) else index += 1
        }
        PhonicsBuddy(
            promptKey = "build-$target",
            prompt = "You are a cheerful phonics tutor for a 5-year-old. In ONE short sentence (max 15 words, simple words), help them sound out and spell the word \"$target\" letter by letter. Be warm. No emojis.",
            game = PhonicsKind.BUILD,
            color = color,
            audio = audio,
        )
    }
}

/* ----------------------------- Rhyme Time ----------------------------- */

@Composable
fun RhymeGame(
    rounds: List<RhymeRound>,
    color: Color,
    audio: PhonicsAudio,
    onProgress: (Int, Int) -> Unit,
    onFinish: (Int) -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf<Int?>(null) }
    var solved by remember(index) { mutableStateOf(false) }
    val round = rounds[index]
    val order = remember(index) { round.options.indices.shuffled() }
    val isLast = index + 1 >= rounds.size

    LaunchedEffect(index) {
        onProgress(index, rounds.size)
        delay(250)
        audio.speak(round.word)
    }
    LaunchedEffect(wrong) { if (wrong != null) { delay(1300); wrong = null } }

    fun pick(orig: Int) {
        if (wrong != null || solved) return
        if (orig == round.answer) {
            audio.speak(round.options[orig].second)
            solved = true
        } else {
            wrong = orig; mistakes += 1
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Which word rhymes?", color = Theme.Ink.copy(alpha = 0.7f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().kidCard().padding(20.dp),
        ) {
            Text(round.emoji, fontSize = 72.sp)
            Text(round.word, color = Theme.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            HearButton(onClick = { audio.speak(round.word) }, color = color)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            order.forEach { orig ->
                val (emoji, word) = round.options[orig]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .width(100.dp)
                        .softShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (wrong == orig) Theme.Red.copy(alpha = 0.18f) else Color.White)
                        .clickable { pick(orig) }
                        .padding(vertical = 14.dp),
                ) {
                    Text(emoji, fontSize = 40.sp)
                    Text(word, color = Theme.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    // Hear this candidate (nested clickable consumes the tap, so
                    // it plays the word without also choosing the card).
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f))
                            .clickable { audio.speak(word) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear $word", tint = color, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        RoundFeedback(solved = solved, showWrong = wrong != null, isLast = isLast, color = color) {
            if (isLast) onFinish(mistakes) else index += 1
        }
        PhonicsBuddy(
            promptKey = "rhyme-${round.word}",
            prompt = "You are a cheerful phonics tutor for a 5-year-old. In ONE short sentence (max 15 words, simple words), hint at which word rhymes with \"${round.word}\" by describing its ending sound, without naming the answer. Be playful. No emojis.",
            game = PhonicsKind.RHYME,
            color = color,
            audio = audio,
        )
    }
}

/* ----------------------------- Listen & Find ----------------------------- */

@Composable
fun ListenFindGame(
    rounds: List<ListenRound>,
    color: Color,
    audio: PhonicsAudio,
    onProgress: (Int, Int) -> Unit,
    onFinish: (Int) -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf<Int?>(null) }
    var solved by remember(index) { mutableStateOf(false) }
    val round = rounds[index]
    val order = remember(index) { round.options.indices.shuffled() }
    val isLast = index + 1 >= rounds.size

    LaunchedEffect(index) {
        onProgress(index, rounds.size)
        delay(300)
        audio.speak(round.word)
    }
    LaunchedEffect(wrong) { if (wrong != null) { delay(1300); wrong = null } }

    fun pick(orig: Int) {
        if (wrong != null || solved) return
        if (orig == round.answer) {
            audio.speak(round.options[orig])
            solved = true
        } else {
            wrong = orig; mistakes += 1
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Listen, then tap the word you hear!", color = Theme.Ink.copy(alpha = 0.7f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        // Big "listen" card (no word shown — they must listen).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().kidCard().padding(24.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .softShadow(CircleShape)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { audio.speak(round.word) },
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Listen", tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Text("Tap to hear again", color = Theme.Ink.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            order.forEach { orig ->
                val word = round.options[orig]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .width(100.dp)
                        .softShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (wrong == orig) Theme.Red.copy(alpha = 0.18f) else Color.White)
                        .clickable { pick(orig) }
                        .padding(vertical = 16.dp),
                ) {
                    Text(word, color = Theme.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    // Hear this similar-sounding candidate (nested clickable, so
                    // it plays the word without choosing the card).
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f))
                            .clickable { audio.speak(word) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear $word", tint = color, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        RoundFeedback(solved = solved, showWrong = wrong != null, isLast = isLast, color = color) {
            if (isLast) onFinish(mistakes) else index += 1
        }
        PhonicsBuddy(
            promptKey = "listen-${round.word}",
            prompt = "You are a cheerful phonics tutor for a 5-year-old. In ONE short sentence (max 15 words, simple words), give a fun clue about the word \"${round.word}\" so they can pick it, without saying the word. Be playful. No emojis.",
            game = PhonicsKind.LISTEN,
            color = color,
            audio = audio,
        )
    }
}

/* ----------------------------- Sound Blender ----------------------------- */

@Composable
fun BlendGame(
    rounds: List<BlendRound>,
    color: Color,
    audio: PhonicsAudio,
    onProgress: (Int, Int) -> Unit,
    onFinish: (Int) -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf<Int?>(null) }
    var solved by remember(index) { mutableStateOf(false) }
    // Bumped by the "Blend it" button to replay; reset each round so the first
    // auto-play (tick 0) gets its gentle lead-in delay.
    var blendTick by remember(index) { mutableIntStateOf(0) }
    val round = rounds[index]
    val order = remember(index) { round.options.indices.shuffled() }
    val isLast = index + 1 >= rounds.size
    val sounds = remember(index) { round.sounds.filter { it.isNotEmpty() } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(index) { onProgress(index, rounds.size) }
    // Play the sounds in order on entry and on each "Blend it". Keying on index and
    // blendTick cancels a stale blend — and cuts its audio — if the child advances
    // or replays mid-playback.
    LaunchedEffect(index, blendTick) {
        delay(if (blendTick == 0) 350 else 0)
        sounds.forEach { slug ->
            audio.play(slug)
            delay(BLEND_SOUND_GAP)
        }
    }
    LaunchedEffect(wrong) { if (wrong != null) { delay(1300); wrong = null } }
    DisposableEffect(Unit) { onDispose { audio.stop() } }

    fun pick(orig: Int) {
        if (wrong != null || solved) return
        if (orig == round.answer) {
            audio.speak(round.options[orig].second)
            solved = true
        } else {
            wrong = orig; mistakes += 1
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Blend the sounds, then tap the picture!",
            color = Theme.Ink.copy(alpha = 0.7f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        // Tap a dot to hear that sound alone, or "Blend it" to hear them together.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().kidCard().padding(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                sounds.forEachIndexed { i, slug ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .softShadow(CircleShape)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f))
                            .clickable { scope.launch { audio.play(slug) } },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Sound ${i + 1}",
                            tint = color,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
            KidButton(title = "Blend it! 🔊", color = color, onClick = { blendTick += 1 })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            order.forEach { orig ->
                val (emoji, word) = round.options[orig]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .width(100.dp)
                        .softShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (wrong == orig) Theme.Red.copy(alpha = 0.18f) else Color.White)
                        .clickable { pick(orig) }
                        .padding(vertical = 16.dp),
                ) {
                    Text(emoji, fontSize = 44.sp)
                    Text(word, color = Theme.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        RoundFeedback(solved = solved, showWrong = wrong != null, isLast = isLast, color = color) {
            audio.stop()
            if (isLast) onFinish(mistakes) else index += 1
        }
        PhonicsBuddy(
            promptKey = "blend-${round.word}",
            prompt = "You are a cheerful phonics tutor for a 5-year-old. In ONE short sentence (max 15 words, simple words), give a fun clue about the word \"${round.word}\" so they can pick it, without saying the word. Be playful. No emojis.",
            game = PhonicsKind.BLEND,
            color = color,
            audio = audio,
        )
    }
}

/* --------------------------- Buddy Sounds (digraphs) --------------------------- */

/** The res/raw clip for a two-letter team. */
private fun slugForTeam(team: String): String = when (team) {
    "sh" -> "c_sh"
    "ch" -> "c_ch"
    "th" -> "c_th_unvoiced"
    "ng" -> "c_ng"
    else -> ""
}

@Composable
fun DigraphGame(
    rounds: List<DigraphRound>,
    color: Color,
    audio: PhonicsAudio,
    onProgress: (Int, Int) -> Unit,
    onFinish: (Int) -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf<Int?>(null) }
    var solved by remember(index) { mutableStateOf(false) }
    val round = rounds[index]
    val order = remember(index) { round.teams.indices.shuffled() }
    val isLast = index + 1 >= rounds.size
    val scope = rememberCoroutineScope()

    LaunchedEffect(index) {
        onProgress(index, rounds.size)
        delay(350)
        audio.play(round.sound)
    }
    LaunchedEffect(wrong) { if (wrong != null) { delay(1300); wrong = null } }

    fun pick(orig: Int) {
        if (wrong != null || solved) return
        if (orig == round.answer) {
            audio.speak(round.exampleWord)
            solved = true
        } else {
            wrong = orig; mistakes += 1
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Which two letters make this sound?",
            color = Theme.Ink.copy(alpha = 0.7f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        // Big listen button for the target digraph sound (no word — the child must
        // map the sound to its two-letter spelling).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().kidCard().padding(24.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .softShadow(CircleShape)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { scope.launch { audio.play(round.sound) } },
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear the sound", tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Text("Tap to hear again", color = Theme.Ink.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            // On a win, reinforce: the two letters → a word that uses them.
            if (solved) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(round.teams[round.answer], color = color, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("→", color = Theme.Ink.copy(alpha = 0.5f), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(round.exampleEmoji, fontSize = 30.sp)
                    Text(round.exampleWord, color = Theme.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        // Letter-team choices; a small speaker on each lets the child compare.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            order.forEach { orig ->
                val team = round.teams[orig]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .width(96.dp)
                        .softShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (wrong == orig) Theme.Red.copy(alpha = 0.18f) else Color.White)
                        .clickable { pick(orig) }
                        .padding(vertical = 16.dp),
                ) {
                    Text(team, color = Theme.Ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    // Nested clickable consumes the tap, so hearing doesn't also pick.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f))
                            .clickable { scope.launch { audio.play(slugForTeam(team)) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear $team", tint = color, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        RoundFeedback(solved = solved, showWrong = wrong != null, isLast = isLast, color = color) {
            if (isLast) onFinish(mistakes) else index += 1
        }
        PhonicsBuddy(
            promptKey = "digraph-${round.exampleWord}",
            prompt = "You are a cheerful phonics tutor for a 5-year-old. In ONE short sentence (max 15 words, simple words), remind them that the two letters \"${round.teams[round.answer]}\" make ONE sound, like in \"${round.exampleWord}\". Be warm. No emojis.",
            game = PhonicsKind.DIGRAPH,
            color = color,
            audio = audio,
        )
    }
}
