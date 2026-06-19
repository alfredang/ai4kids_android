package sg.com.tertiarycourses.ai4kids.ui.activities.phonics

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import sg.com.tertiarycourses.ai4kids.ai.GeminiClient
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * On-device speech for hearing letters, sounds and words. Uses the Android
 * TextToSpeech engine — works offline once a voice is installed, no network.
 * Returns a `speak` function; silently no-ops if TTS isn't ready/available.
 */
@Composable
fun rememberSpeaker(): (String) -> Unit {
    val context = LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val e = tts ?: return@TextToSpeech
                // Use whichever English voice is actually installed; an emulator
                // may have none, in which case speech stays silent (no crash).
                val available = listOf(Locale.UK, Locale.US, Locale.ENGLISH, Locale.getDefault())
                    .any { loc ->
                        val r = e.setLanguage(loc)
                        r == TextToSpeech.LANG_AVAILABLE ||
                            r == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                            r == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                    }
                e.setSpeechRate(0.85f)
                ready = available
            }
        }
        engine = tts
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    return remember {
        { text: String -> if (ready) engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text) }
    }
}

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

/**
 * The Gemini-powered "Phonics Buddy": a button that asks Gemini for a short,
 * kid-friendly hint about the current round and reads it aloud. Hidden entirely
 * when no API key is configured (the games stay fully playable without it).
 * [promptKey] resets the hint when the round changes.
 */
@Composable
private fun PhonicsBuddy(promptKey: String, prompt: String, color: Color, speak: (String) -> Unit) {
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
                        val reply = GeminiClient.generate(prompt)
                        busy = false
                        if (reply != null) {
                            hint = reply
                            speak(reply)
                        } else {
                            hint = "Listen to the word again and sound it out slowly!"
                        }
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
    speak: (String) -> Unit,
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

    LaunchedEffect(index) {
        onProgress(index, rounds.size)
        delay(250)
        speak(round.word)
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
            HearButton(onClick = { speak(round.word) }, color = color)
        }
        Text(
            "Hear each sound, then pick the one it starts with!",
            color = Theme.Ink.copy(alpha = 0.7f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, c ->
                NumberedSoundOption(
                    number = i + 1,
                    isWrong = wrong == i,
                    color = color,
                    onHear = { speak(phonemeOf(c)) },
                    onPick = { pick(i) },
                )
            }
        }
        RoundFeedback(solved = solved, showWrong = wrong != null, isLast = isLast, color = color) {
            if (isLast) onFinish(mistakes) else index += 1
        }
        PhonicsBuddy(
            promptKey = "pop-${round.word}",
            prompt = "You are a cheerful phonics tutor for a 5-year-old child. In ONE short sentence (max 15 words, simple words), help them hear that the word \"${round.word}\" starts with the letter \"${round.answer}\". Be warm and playful. No emojis.",
            color = color,
            speak = speak,
        )
    }
}

/** A phonics-style utterance for a letter sound (e.g. B → "buh", S → "suh"),
 *  so TextToSpeech reads the *sound* as a single syllable rather than spelling
 *  out the letter (repeated letters like "sss" get read as "es es"). */
private fun phonemeOf(letter: Char): String = when (letter.uppercaseChar()) {
    'A' -> "ah"; 'B' -> "buh"; 'C' -> "kah"; 'D' -> "duh"; 'E' -> "eh"
    'F' -> "fuh"; 'G' -> "guh"; 'H' -> "huh"; 'I' -> "e"; 'J' -> "juh"
    'K' -> "kuh"; 'L' -> "luh"; 'M' -> "muh"; 'N' -> "nuh"; 'O' -> "oh"
    'P' -> "puh"; 'Q' -> "kwuh"; 'R' -> "ruh"; 'S' -> "suh"; 'T' -> "tuh"
    'U' -> "uh"; 'V' -> "vuh"; 'W' -> "wuh"; 'X' -> "ksuh"; 'Y' -> "yuh"; 'Z' -> "zuh"
    else -> letter.toString()
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
    speak: (String) -> Unit,
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
    val built = target.take(used.size)
    val isLast = index + 1 >= rounds.size

    LaunchedEffect(index) {
        onProgress(index, rounds.size)
        delay(250)
        speak(round.word)
    }
    LaunchedEffect(wrongTile) { if (wrongTile != null) { delay(500); wrongTile = null } }

    fun tap(i: Int, ch: Char) {
        if (i in used || wrongTile != null || solved) return
        if (ch == target[built.length]) {
            used = used + i
            speak(ch.toString())
            if (used.size == target.length) {
                speak(target)
                solved = true
            }
        } else {
            wrongTile = i; mistakes += 1
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Spell the word!", color = Theme.Ink.copy(alpha = 0.7f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().kidCard().padding(20.dp),
        ) {
            Text(round.emoji, fontSize = 76.sp)
            HearButton(onClick = { speak(round.word) }, color = color)
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .alpha(if (isUsed) 0.25f else 1f)
                        .softShadow(RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (wrongTile == i) Theme.Red else Color.White)
                        .clickable(enabled = !isUsed) { tap(i, ch) },
                ) {
                    Text("$ch", color = if (wrongTile == i) Color.White else Theme.Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        RoundFeedback(solved = solved, showWrong = wrongTile != null, isLast = isLast, color = color) {
            if (isLast) onFinish(mistakes) else index += 1
        }
        PhonicsBuddy(
            promptKey = "build-$target",
            prompt = "You are a cheerful phonics tutor for a 5-year-old. In ONE short sentence (max 15 words, simple words), help them sound out and spell the word \"$target\" letter by letter. Be warm. No emojis.",
            color = color,
            speak = speak,
        )
    }
}

/* ----------------------------- Rhyme Time ----------------------------- */

@Composable
fun RhymeGame(
    rounds: List<RhymeRound>,
    color: Color,
    speak: (String) -> Unit,
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
        speak(round.word)
    }
    LaunchedEffect(wrong) { if (wrong != null) { delay(1300); wrong = null } }

    fun pick(orig: Int) {
        if (wrong != null || solved) return
        if (orig == round.answer) {
            speak(round.options[orig].second)
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
            HearButton(onClick = { speak(round.word) }, color = color)
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
                            .clickable { speak(word) }
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
            color = color,
            speak = speak,
        )
    }
}

/* ----------------------------- Listen & Find ----------------------------- */

@Composable
fun ListenFindGame(
    rounds: List<ListenRound>,
    color: Color,
    speak: (String) -> Unit,
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
        speak(round.word)
    }
    LaunchedEffect(wrong) { if (wrong != null) { delay(1300); wrong = null } }

    fun pick(orig: Int) {
        if (wrong != null || solved) return
        if (orig == round.answer) {
            speak(round.options[orig])
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
                    .clickable { speak(round.word) },
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
                            .clickable { speak(word) }
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
            color = color,
            speak = speak,
        )
    }
}
