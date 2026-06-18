package sg.com.tertiarycourses.ai4kids.cards

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

private val PAIR_CHOICES = listOf(6, 8, 10, 12)

/**
 * Drives one card game across every phase — menu, lobby, in-play board and
 * results — and every mode. The backend is authoritative: each action POSTs to
 * CardApi and the returned, viewer-redacted [CardState] replaces ours. A ~1.2s
 * sync poll keeps players in step. Android port of the web `CardGamePlayer`.
 */
@Composable
fun CardGameScreen(game: CardGameMeta, loggedIn: Boolean, onClose: () -> Unit) {
    var state by remember { mutableStateOf<CardState?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var local by remember { mutableStateOf<LocalSession?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pairs by remember { mutableStateOf(8) }
    val isMemory = game.slug == "memory-match"
    val scope = rememberCoroutineScope()

    fun leave() {
        code = null
        state = null
        local = null
        error = null
    }

    BackHandler { if (code != null) leave() else onClose() }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                block()
            } catch (e: CardApi.ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    // Solo plays fully offline on-device — no network, no sign-in.
    fun startSolo() {
        error = null
        val session = LocalSession(game, if (isMemory) pairs else 8)
        local = session
        code = "SOLO"
        state = session.state()
    }

    fun hostGame(mode: CardMode) = run {
        val s = withContext(Dispatchers.IO) { CardApi.create(game.slug, mode, if (isMemory) pairs else null) }
        code = s.code
        state = s
    }

    fun joinGame(raw: String) = run {
        val s = withContext(Dispatchers.IO) { CardApi.join(raw, game.slug) }
        code = s.code
        state = s
    }

    fun startGame() = run {
        val c = code ?: return@run
        state = withContext(Dispatchers.IO) { CardApi.start(c, if (isMemory) pairs else null) }
    }

    fun sendMove(move: JSONObject) {
        val session = local
        if (session != null) {
            error = null
            try {
                session.apply(move)
                state = session.state()
            } catch (e: Exception) {
                error = e.message ?: "That move isn't allowed."
            }
            return
        }
        run {
            val c = code ?: return@run
            state = withContext(Dispatchers.IO) { CardApi.move(c, move) }
        }
    }

    // Poll for shared state until the game is done — network games only.
    LaunchedEffect(code) {
        val c = code ?: return@LaunchedEffect
        if (local != null) return@LaunchedEffect
        while (true) {
            delay(1200)
            if (state?.status == "done") break
            val fresh = withContext(Dispatchers.IO) { runCatching { CardApi.sync(c) }.getOrNull() }
            if (fresh != null) state = fresh
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title bar (pinned at the top).
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = { if (code != null) leave() else onClose() })
                Spacer(Modifier.weight(1f))
                Text("${game.emoji} ${game.title}", color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.widthIn(min = 48.dp))
            }

            // Game content — vertically centred in the remaining space, and
            // scrollable when a board is taller than the screen.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                error?.let {
                    Text(
                        it,
                        color = Theme.Red,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Theme.Red.copy(alpha = 0.1f))
                            .padding(12.dp),
                    )
                }

                val s = state
                when {
                    s == null || code == null -> Menu(
                        game = game, busy = busy, pairs = pairs, onPairs = { pairs = it },
                        showMultiplayer = loggedIn,
                        onSolo = { startSolo() },
                        onHost = { hostGame(it) },
                        onJoin = { joinGame(it) },
                    )
                    s.status == "lobby" -> Lobby(
                        game = game, state = s, code = code!!, busy = busy,
                        pairs = pairs, onPairs = { pairs = it },
                        onStart = { startGame() }, onLeave = { leave() },
                    )
                    s.status == "done" -> Results(state = s, onAgain = { leave() })
                    else -> Board(state = s, busy = busy, onMove = { sendMove(it) })
                }
            }
        }
    }
}

/* --------------------------- Menu --------------------------- */

@Composable
private fun Menu(
    game: CardGameMeta,
    busy: Boolean,
    pairs: Int,
    onPairs: (Int) -> Unit,
    showMultiplayer: Boolean,
    onSolo: () -> Unit,
    onHost: (CardMode) -> Unit,
    onJoin: (String) -> Unit,
) {
    var joinCode by remember { mutableStateOf("") }
    val multiModes = game.modes.filter { it != CardMode.SOLO }

    Column(Modifier.fillMaxWidth().kidCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("How to play", color = Theme.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
        game.how.forEachIndexed { i, line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${i + 1}.", color = game.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(line, color = Theme.Ink.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }
    }

    if (game.slug == "memory-match") {
        PairSelector(pairs = pairs, onPairs = onPairs, enabled = !busy)
    }

    if (game.modes.contains(CardMode.SOLO)) {
        KidButton(
            title = "🎯 Play Solo",
            color = Theme.Purple,
            enabled = !busy,
            onClick = onSolo,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showMultiplayer && multiModes.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().kidCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Play with friends", color = Theme.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Up to ${game.maxPlayers} players, on their own devices.", color = Theme.Ink.copy(alpha = 0.45f), fontSize = 12.sp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                multiModes.forEach { m ->
                    KidButton(
                        title = "Host ${m.label}",
                        color = game.accent,
                        enabled = !busy,
                        onClick = { onHost(m) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text("Got a code?", color = Theme.Ink.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it.uppercase() },
                    singleLine = true,
                    placeholder = { Text("LION42") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.widthIn(max = 160.dp),
                )
                KidButton(
                    title = "Join",
                    color = Theme.Blue,
                    enabled = !busy && joinCode.isNotBlank(),
                    onClick = { if (joinCode.isNotBlank()) onJoin(joinCode) },
                )
            }
        }
    }
}

@Composable
private fun PairSelector(pairs: Int, onPairs: (Int) -> Unit, enabled: Boolean) {
    Column(Modifier.fillMaxWidth().kidCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("How many cards?", color = Theme.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PAIR_CHOICES.forEach { n ->
                val on = pairs == n
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Theme.Purple else Theme.Ink.copy(alpha = 0.08f))
                        .let { if (enabled) it.clickable { onPairs(n) } else it }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("${n * 2}", color = if (on) Color.White else Theme.Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* --------------------------- Lobby --------------------------- */

@Composable
private fun Lobby(
    game: CardGameMeta,
    state: CardState,
    code: String,
    busy: Boolean,
    pairs: Int,
    onPairs: (Int) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
) {
    val youHost = state.hostId == state.you
    val enough = state.players.size >= game.minPlayers

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().kidCard().padding(24.dp),
    ) {
        Text("Share this code with your friends", color = Theme.Ink.copy(alpha = 0.5f), fontSize = 14.sp)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.Blue.copy(alpha = 0.12f))
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(code, color = Theme.Blue, fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
        Text("${modeLabel(state.mode)} · ${game.title}", color = Theme.Ink.copy(alpha = 0.45f), fontSize = 12.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            state.players.forEach { p ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Theme.Ink.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        "${p.avatar ?: "🙂"} ${p.name}${if (p.learnerId == state.you) " (you)" else ""}${if (p.isHost) " ★" else ""}",
                        color = Theme.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (game.slug == "memory-match" && youHost) {
            PairSelector(pairs = pairs, onPairs = onPairs, enabled = !busy)
        }

        if (youHost) {
            KidButton(
                title = if (enough) "Start game ▶" else "Need ${game.minPlayers}+ players",
                color = if (enough && !busy) game.accent else Theme.Ink.copy(alpha = 0.3f),
                enabled = !busy && enough,
                onClick = onStart,
            )
        } else {
            Text("Waiting for the host to start… 🕒", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 15.sp)
        }
        TextButton(onClick = onLeave) { Text("Leave", color = Theme.Ink.copy(alpha = 0.5f)) }
    }
}

/* --------------------------- Results --------------------------- */

@Composable
private fun Results(state: CardState, onAgain: () -> Unit) {
    val nameById = state.players.associate { it.learnerId to it.name }
    val youWon = state.winners.firstOrNull() == state.you
    val solo = state.mode == "solo"
    val coop = state.mode == "coop"
    // A solo game that finished with no winner = the player ran out of time.
    val soloLost = solo && state.winners.isEmpty()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().kidCard().padding(28.dp),
    ) {
        Text(if (soloLost) "😮" else if (youWon || coop || solo) "🏆" else "🎉", fontSize = 56.sp)
        Text(
            when {
                soloLost -> "So close!"
                solo -> "You did it!"
                coop -> "You cleared it together!"
                youWon -> "You win! 🎉"
                else -> "${nameById[state.winners.firstOrNull()] ?: "Someone"} wins!"
            },
            color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
        )

        if (solo && state.bestMs != null) {
            StarBadge(count = 0)
            Text("🏆 Best: ${fmtTime(state.bestMs)}", color = Theme.Orange, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        if (!solo && !coop) {
            state.winners.forEachIndexed { i, id ->
                val medal = listOf("🥇", "🥈", "🥉").getOrElse(i) { "🎖️" }
                Text(
                    "$medal ${nameById[id] ?: "Player"}${if (id == state.you) " (you)" else ""}",
                    color = if (i == 0) Theme.Orange else Theme.Ink.copy(alpha = 0.7f),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        KidButton(title = "Play again ▶", color = Theme.Pink, onClick = onAgain)
    }
}

/* --------------------------- Board dispatcher --------------------------- */

@Composable
private fun Board(state: CardState, busy: Boolean, onMove: (JSONObject) -> Unit) {
    val game = state.game ?: return
    val nameById = state.players.associate { it.learnerId to it.name }
    val noBanner = game is ShowdownView || game is MatchColoursView
    val showBanner = state.mode != "solo" && !noBanner

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (showBanner) {
            val turnName = if (game.yourTurn) "Your turn" else "${nameById[game.turnPlayerId] ?: "…"}'s turn"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (game.yourTurn) Theme.Green.copy(alpha = 0.15f) else Theme.Ink.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    (if (game.yourTurn) "🟢 " else "🕒 ") + turnName,
                    color = if (game.yourTurn) Theme.Green else Theme.Ink.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        when (game) {
            is MemoryView -> MemoryBoard(game, busy, onMove)
            is DiscardView -> DiscardBoard(game, busy, onMove)
            is MathView -> MathBoard(game, busy, onMove)
            is BeatDieView -> BeatDieBoard(game, busy, onMove)
            is ShowdownView -> ShowdownBoard(game, state.players, busy, onMove)
            is MatchColoursView -> MatchColoursBoard(game, state.players, busy, onMove)
            is MakeTenView -> MakeTenBoard(game, busy, onMove)
            is WheelView -> WheelBoard(game, busy, onMove)
            is OddOneView -> OddOneOutBoard(game, busy, onMove)
            is SeqView -> AlphabetLockBoard(game, busy, onMove)
            else -> Text("Loading…", color = Theme.Ink.copy(alpha = 0.5f))
        }
    }
}

/* --------------------------- shared helpers --------------------------- */

fun modeLabel(mode: String): String = when (mode) {
    "solo" -> "Solo"; "coop" -> "Co-op"; else -> "Versus"
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
