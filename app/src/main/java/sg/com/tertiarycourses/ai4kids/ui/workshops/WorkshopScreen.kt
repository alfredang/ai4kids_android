package sg.com.tertiarycourses.ai4kids.ui.workshops

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sg.com.tertiarycourses.ai4kids.ai.DeepSeekClient
import sg.com.tertiarycourses.ai4kids.ai.KidSafety
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.data.ParentAuth
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidSecondaryButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

private const val NAP_MESSAGE = "The AI is taking a nap. Ask a grown-up to check the internet or the key."
private const val CREDIT_MESSAGE = "Out of credit! Ask a grown-up to top up their DeepSeek account."

/**
 * One AI Workshop: quick-start chips → the child's first message → a chat with
 * "Sunny" (DeepSeek). Every message is screened by [KidSafety] before sending;
 * history lives only in this screen's memory and is dropped on close. Must only
 * be shown while [ParentAuth] is unlocked (RootScreen routes to the Parent Login
 * otherwise). Mirrors the iOS `WorkshopView`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkshopScreen(workshop: Workshop, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val progress = LocalProgressStore.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val turns = remember { mutableStateListOf<DeepSeekClient.Turn>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // One pick per setup group (null = not yet chosen), plus an optional typed custom pick.
    val picks = remember { mutableStateListOf<String?>().apply { repeat(workshop.setup.size) { add(null) } } }
    var customPick by remember { mutableStateOf("") }

    LaunchedEffect(turns.size, busy) {
        val count = turns.size + if (busy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun send(raw: String) {
        if (busy) return
        val text = raw.trim()
        val verdict = KidSafety.check(text)
        if (verdict != KidSafety.Verdict.Ok) {
            notice = KidSafety.messageFor(verdict)
            return
        }
        val key = ParentAuth.apiKey() ?: return // locked: RootScreen shows the Parent Login
        notice = null
        input = ""
        turns.add(DeepSeekClient.Turn(fromKid = true, text = text))
        busy = true
        scope.launch {
            val reply = DeepSeekClient.chat(key, workshop.systemPrompt, turns.toList())
            busy = false
            when (reply) {
                is DeepSeekClient.Reply.Ok -> {
                    turns.add(DeepSeekClient.Turn(fromKid = false, text = reply.text))
                    ParentAuth.tryAwardStar(workshop.id, progress)
                }
                DeepSeekClient.Reply.Unauthorized -> {
                    // Auto-lock; the Parent Login then explains the key needs replacing.
                    turns.removeAt(turns.lastIndex)
                    ParentAuth.markKeyRejected()
                }
                DeepSeekClient.Reply.OutOfCredit -> {
                    turns.removeAt(turns.lastIndex)
                    input = text
                    notice = CREDIT_MESSAGE
                }
                is DeepSeekClient.Reply.Failed -> {
                    turns.removeAt(turns.lastIndex)
                    input = text
                    notice = NAP_MESSAGE
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.Background)
            .systemBarsPadding()
            .imePadding()
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            // Header.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CloseButton(onClick = onClose)
                Image(painterResource(workshop.art), contentDescription = null, modifier = Modifier.size(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(workshop.title, color = workshop.color, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = Theme.DisplayFont)
                    Text(workshop.tagline, color = Theme.BodyInk, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                StarBadge(count = progress.totalStars)
            }

            // Chat.
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .kidCard()
                    .padding(14.dp),
            ) {
                if (turns.isEmpty() && !busy) {
                    item { SetupPanel(workshop, picks, customPick, { customPick = it.take(40) }, onStart = { send(it) }) }
                }
                itemsIndexed(turns) { _, turn -> Bubble(turn, workshop.color) }
                if (busy) {
                    item { Bubble(DeepSeekClient.Turn(false, "Sunny is thinking… 💭"), workshop.color) }
                }
            }

            if (notice != null) {
                Text(
                    notice!!,
                    color = Theme.Red,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Quick replies once the chat has started.
            if (turns.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    workshop.followUps.forEach { chip ->
                        KidSecondaryButton(title = chip, fontSize = 16.sp, enabled = !busy, onClick = { send(chip) })
                    }
                }
            }

            // Typing.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(KidSafety.MAX_CHARS); notice = null },
                    placeholder = { Text(if (turns.isEmpty()) "…or type your own idea" else "Type a message…") },
                    supportingText = { Text("${input.length}/${KidSafety.MAX_CHARS}") },
                    singleLine = true,
                    shape = Theme.InputShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = workshop.color,
                        unfocusedBorderColor = Theme.InputBorder,
                        focusedContainerColor = Theme.CardFill,
                        unfocusedContainerColor = Theme.CardFill,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(input) }),
                    modifier = Modifier.weight(1f),
                )
                KidButton(
                    title = "Send",
                    icon = Icons.AutoMirrored.Filled.Send,
                    enabled = input.isNotBlank() && !busy,
                    onClick = { send(input) },
                )
            }

            Text(
                "AI can make mistakes — check answers with a grown-up.",
                color = Theme.BodyInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The quick-start chips shown before the first message. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupPanel(
    workshop: Workshop,
    picks: MutableList<String?>,
    customPick: String,
    onCustomChange: (String) -> Unit,
    onStart: (String) -> Unit,
) {
    val single = workshop.setup.size == 1 && workshop.setup[0].customLabel == null
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "Hi, I'm Sunny! Let's get started.",
            color = Theme.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            fontFamily = Theme.DisplayFont,
        )
        workshop.setup.forEachIndexed { index, group ->
            Text(group.label, color = Theme.Purple, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.options.forEach { option ->
                    PickChip(option, selected = picks[index] == option, tint = workshop.color) {
                        if (single) onStart(workshop.firstMessage(listOf(option))) else picks[index] = option
                    }
                }
                if (group.customLabel != null) {
                    PickChip(group.customLabel, selected = picks[index] == CUSTOM, tint = workshop.color) { picks[index] = CUSTOM }
                }
            }
            if (group.customLabel != null && picks[index] == CUSTOM) {
                OutlinedTextField(
                    value = customPick,
                    onValueChange = onCustomChange,
                    placeholder = { Text("e.g. a sleepy panda") },
                    singleLine = true,
                    shape = Theme.InputShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = workshop.color,
                        unfocusedBorderColor = Theme.InputBorder,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (!single) {
            val chosen = picks.map { if (it == CUSTOM) customPick.trim().ifEmpty { null } else it }
            KidButton(
                title = "Start!",
                color = workshop.color,
                enabled = chosen.all { it != null },
                onClick = { onStart(workshop.firstMessage(chosen.map { it!! })) },
            )
        }
    }
}

private const val CUSTOM = "\u0000custom"

@Composable
private fun PickChip(label: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    val shape = Theme.SecondaryButtonShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) tint else Theme.ButtonCream)
            .border(2.dp, if (selected) tint else Theme.Ink, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (selected) Color.White else Theme.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Bubble(turn: DeepSeekClient.Turn, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.fromKid) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = if (turn.fromKid) accent else accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                turn.text,
                color = if (turn.fromKid) Color.White else Theme.Ink,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
