package sg.com.tertiarycourses.ai4kids.ui.activities.buddy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sg.com.tertiarycourses.ai4kids.ai.GeminiClient
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme
import java.util.Locale
import kotlin.random.Random

private const val BUDDY_SYSTEM =
    "You are a friendly, cheerful buddy for a young child (ages 5-10) having an ongoing chat. " +
        "Reply in 1-3 short, simple, positive sentences. Remember what was said earlier in the conversation. " +
        "Never discuss anything scary, violent, sexual, or unsafe. If asked something inappropriate, gently " +
        "redirect to something fun. No links, no complex words."

private data class Turn(val fromUser: Boolean, val text: String)

/**
 * The Talking Buddy: chat with a friendly AI pal by voice or text. Text replies
 * come from Gemini (kid-safe system prompt + history); speech-in uses on-device
 * [SpeechRecognizer] and speech-out uses on-device TextToSpeech ([BuddyVoice]),
 * so the only thing that leaves the phone is the chat text. Android port of the
 * website's `/learn/buddy`.
 */
@Composable
fun TalkingBuddyScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val accent = Activity.BUDDY.color

    if (!GeminiClient.isConfigured()) {
        NotConfigured(onClose, accent)
        return
    }

    val context = LocalContext.current
    val progress = LocalProgressStore.current
    val scope = rememberCoroutineScope()
    val voice = rememberBuddyVoice()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf(listOf<Turn>()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var mouth by remember { mutableStateOf(0.1f) }
    var blink by remember { mutableStateOf(false) }
    var earned by remember { mutableStateOf(0) }

    // Flap the mouth while the buddy speaks; rest closed otherwise.
    LaunchedEffect(voice.speaking) {
        while (voice.speaking) { mouth = Random.nextFloat() * 0.7f + 0.2f; delay(110) }
        mouth = 0.1f
    }
    // Gentle blink.
    LaunchedEffect(Unit) { while (true) { delay(3200); blink = true; delay(140); blink = false } }
    // Keep the newest message in view.
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    fun ask(message: String) {
        val m = message.trim()
        if (m.isEmpty() || busy) return
        input = ""
        busy = true
        val history = messages
        messages = messages + Turn(fromUser = true, text = m)
        scope.launch {
            val convo = (history + Turn(true, m)).takeLast(10).joinToString("\n\n") {
                (if (it.fromUser) "Child: " else "Buddy: ") + it.text.trim()
            }
            val reply = GeminiClient.generateReply(BUDDY_SYSTEM, convo, model = GeminiClient.FLASH_LITE)
                ?: "Hmm, my ears are sleepy! Can you say that again?"
            messages = messages + Turn(fromUser = false, text = reply)
            busy = false
            voice.speak(reply)
            if (earned < 3) { progress.award(1, Activity.BUDDY); earned++ }
        }
    }

    // Tap-to-talk via on-device speech recognition (prefers offline).
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    fun startListening() {
        val r = recognizer ?: return
        voice.stop() // don't transcribe the buddy's own voice
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (said.isNotEmpty()) ask(said)
            }
            override fun onError(error: Int) { listening = false }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        listening = true
        r.startListening(intent)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
    }
    fun onTalk() {
        if (listening) { recognizer?.stopListening(); listening = false; return }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val status = when {
        busy -> "Thinking… 💭"
        listening -> "I'm listening — tap Stop when you're done! 🎤"
        messages.isEmpty() -> "Tap the mic and say hi, or type below! 👋"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background)
            .systemBarsPadding()
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                StarBadge(count = progress.totalStars)
            }

            // Buddy stage.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().kidCard().padding(vertical = 16.dp, horizontal = 20.dp),
            ) {
                Text("🤖 Talking Buddy", color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                BuddyFace(mouthOpen = mouth, blink = blink, modifier = Modifier.size(160.dp))
                if (status.isNotEmpty()) {
                    Text(status, color = Theme.Ink.copy(alpha = 0.6f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Chat.
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).kidCard().padding(14.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            "Your chat will show up here 💬",
                            color = Theme.Ink.copy(alpha = 0.4f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        )
                    }
                }
                itemsIndexed(messages) { _, turn -> ChatBubble(turn, accent) }
            }

            // Controls.
            if (recognizer != null) {
                KidButton(
                    title = if (listening) "Stop" else "Tap to talk",
                    icon = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    color = if (listening) Theme.Red else accent,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTalk() },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(500) },
                    placeholder = { Text(if (recognizer != null) "…or type a message" else "Type a message…") },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Theme.Ink.copy(alpha = 0.15f),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { ask(input) }),
                    modifier = Modifier.weight(1f),
                )
                KidButton(
                    title = "Send",
                    icon = Icons.AutoMirrored.Filled.Send,
                    color = Theme.Purple,
                    enabled = input.isNotBlank() && !busy,
                    onClick = { ask(input) },
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(turn: Turn, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!turn.fromUser) {
            Text("🤖", fontSize = 22.sp, modifier = Modifier.padding(end = 6.dp, top = 4.dp))
        }
        Box(
            modifier = Modifier
                .widthInFraction()
                .background(
                    color = if (turn.fromUser) accent else accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                turn.text,
                color = if (turn.fromUser) Color.White else Theme.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Cap a bubble to ~85% of the row width so long lines wrap nicely. */
private fun Modifier.widthInFraction(): Modifier = this.fillMaxWidth(0.85f)

@Composable
private fun NotConfigured(onClose: () -> Unit, accent: Color) {
    Box(
        modifier = Modifier.fillMaxSize().background(Theme.Background).systemBarsPadding().padding(18.dp),
    ) {
        CloseButton(onClick = onClose)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().kidCard().padding(28.dp),
        ) {
            Text("🤖", fontSize = 72.sp)
            Text("Talking Buddy needs a grown-up", color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(
                "Ask a grown-up to add an AI key so your buddy can chat!",
                color = Theme.Ink.copy(alpha = 0.6f), fontSize = 17.sp, textAlign = TextAlign.Center,
            )
            KidButton(title = "Okay", color = accent, onClick = onClose)
        }
    }
}