package sg.com.tertiarycourses.ai4kids.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/* ----------------------------- move builders ----------------------------- */

private fun move(type: String): JSONObject = JSONObject().put("type", type)
private fun cardsMove(type: String, cards: List<Int>): JSONObject {
    val arr = JSONArray()
    cards.forEach { arr.put(it) }
    return JSONObject().put("type", type).put("cards", arr)
}

private fun parseHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Theme.Ink)

/* ----------------------------- Memory ----------------------------- */

@Composable
fun MemoryBoard(view: MemoryView, busy: Boolean, onMove: (JSONObject) -> Unit) {
    // After a mismatch, the player on turn auto-flips the cards back.
    LaunchedEffect(view.mismatch, view.yourTurn) {
        if (view.mismatch && view.yourTurn) {
            delay(1100)
            onMove(move("next"))
        }
    }
    val lock = busy || view.mismatch
    val flippedCount = view.cards.count { it.flipped }
    val total = view.cards.size
    val cols = if (total <= 16) 4 else if (total <= 20) 5 else 6

    Column {
        Text("Pairs found: ${view.pairsFound}/${view.pairsTotal}", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            userScrollEnabled = false,
        ) {
            itemsIndexed(view.cards, key = { _, c -> c.id }) { _, c ->
                val up = c.flipped || c.matched
                val disabled = lock || up || !view.yourTurn || flippedCount >= 2
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                c.matched -> Theme.Green.copy(alpha = 0.2f)
                                up -> Color.White
                                else -> Theme.Purple
                            }
                        )
                        .let { if (!disabled) it.clickable { onMove(JSONObject().put("type", "flip").put("cardId", c.id)) } else it }
                        .padding(4.dp),
                ) {
                    if (up) {
                        Text(
                            c.label ?: "",
                            color = if (c.matched) Theme.Green else Theme.Ink,
                            fontSize = if (c.face == "emoji") 30.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    } else {
                        Text("?", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/* ----------------------------- Tower Tumble (discard) ----------------------------- */

@Composable
fun DiscardBoard(view: DiscardView, busy: Boolean, onMove: (JSONObject) -> Unit) {
    var selected by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Four piles — play higher, or a 10 to clear", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            view.piles.forEachIndexed { i, top ->
                val playable = view.yourTurn && selected != null && !busy
                val shape = RoundedCornerShape(12.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.7f)
                        .clip(shape)
                        .background(if (top == 0) Theme.Blue.copy(alpha = 0.08f) else Color.White)
                        .border(
                            width = if (playable) 2.5.dp else 1.5.dp,
                            color = if (playable) Theme.Blue else Theme.Ink.copy(alpha = 0.12f),
                            shape = shape,
                        )
                        .let {
                            if (playable)
                                it.clickable {
                                    onMove(JSONObject().put("type", "play").put("pile", i).put("card", selected))
                                    selected = null
                                } else it
                        },
                ) {
                    if (top == 0) {
                        Text("any", color = Theme.Blue.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("$top", color = Theme.Ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        Text("Your cards", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        HandRow(view.yourHand, selectedIndices = selected?.let { setOf(view.yourHand.indexOf(it)) } ?: emptySet(), enabled = view.yourTurn && !busy) { idx ->
            selected = if (selected == view.yourHand[idx]) null else view.yourHand[idx]
        }

        KidButton(
            title = "Pass",
            color = if (view.yourTurn && !view.canPlay && !busy) Theme.Orange else Theme.Ink.copy(alpha = 0.3f),
            enabled = view.yourTurn && !view.canPlay && !busy,
            onClick = { onMove(move("pass")); selected = null },
        )
    }
}

/* ----------------------------- Number Hunt (math) ----------------------------- */

@Composable
fun MathBoard(view: MathView, busy: Boolean, onMove: (JSONObject) -> Unit) {
    val picked = remember(view.yourHand, view.target) { mutableStateListOf<Int>() }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TargetChip("🎯 Target", view.target, Theme.Blue, Modifier.align(Alignment.CenterHorizontally))
        Text(
            "Pick 1 card = target, or 2 that add/subtract to it",
            color = Theme.Ink.copy(alpha = 0.6f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        HandRow(view.yourHand, picked.toSet(), enabled = view.yourTurn && !busy) { idx ->
            togglePick(picked, idx, max = 2)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            KidButton(
                title = "Discard",
                color = if (view.yourTurn && picked.isNotEmpty() && !busy) Theme.Green else Theme.Ink.copy(alpha = 0.3f),
                enabled = view.yourTurn && picked.isNotEmpty() && !busy,
                onClick = { onMove(cardsMove("discard", picked.map { view.yourHand[it] })); picked.clear() },
            )
            KidButton(
                title = "Draw",
                color = if (view.yourTurn && !busy) Theme.Orange else Theme.Ink.copy(alpha = 0.3f),
                enabled = view.yourTurn && !busy,
                onClick = { onMove(move("draw")); picked.clear() },
            )
        }
    }
}

/* ----------------------------- Beat the Die ----------------------------- */

@Composable
fun BeatDieBoard(view: BeatDieView, busy: Boolean, onMove: (JSONObject) -> Unit) {
    val picked = remember(view.yourHand, view.die) { mutableStateListOf<Int>() }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.Green.copy(alpha = 0.15f)),
        ) {
            Text(view.die?.let { "🎲 $it" } ?: "🎲", color = Theme.Green, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }

        if (view.yourTurn && view.die == null) {
            KidButton(title = "Roll the die", color = if (busy) Theme.Ink.copy(alpha = 0.3f) else Theme.Green, enabled = !busy, onClick = { onMove(move("roll")) }, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Text("Discard 1–2 cards adding up to at least ${view.die ?: "?"}", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 13.sp)
            HandRow(view.yourHand, picked.toSet(), enabled = view.yourTurn && view.die != null && !busy) { idx ->
                togglePick(picked, idx, max = 2)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                KidButton(
                    title = "Discard",
                    color = if (view.yourTurn && view.die != null && picked.isNotEmpty() && !busy) Theme.Green else Theme.Ink.copy(alpha = 0.3f),
                    enabled = view.yourTurn && view.die != null && picked.isNotEmpty() && !busy,
                    onClick = { onMove(cardsMove("discard", picked.map { view.yourHand[it] })); picked.clear() },
                )
                KidButton(
                    title = "Draw",
                    color = if (view.yourTurn && view.die != null && !busy) Theme.Orange else Theme.Ink.copy(alpha = 0.3f),
                    enabled = view.yourTurn && view.die != null && !busy,
                    onClick = { onMove(move("draw")); picked.clear() },
                )
            }
        }
    }
}

/* ----------------------------- Make Ten ----------------------------- */

@Composable
fun MakeTenBoard(view: MakeTenView, busy: Boolean, onMove: (JSONObject) -> Unit) {
    // Selection resets whenever the board changes (a pair cleared, or a retry).
    val selected = remember(view.cleared, view.cards.size) { mutableStateListOf<Int>() }

    // Per-round countdown: restarts each round, and fires a timeout (loss) at 0.
    var remaining by remember(view.round) { mutableStateOf(view.roundMs) }
    LaunchedEffect(view.round) {
        val deadline = System.currentTimeMillis() + view.roundMs
        while (true) {
            val left = deadline - System.currentTimeMillis()
            remaining = left.coerceAtLeast(0L)
            if (left <= 0L) { onMove(move("timeout")); break }
            delay(50)
        }
    }

    fun tap(id: Int) {
        if (busy) return
        if (selected.contains(id)) { selected.remove(id); return }
        if (selected.size >= 2) return
        selected.add(id)
        if (selected.size == 2) {
            onMove(cardsMove("clear", selected.toList()))
            selected.clear()
        }
    }

    val frac = (remaining.toFloat() / view.roundMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    val secs = Math.ceil(remaining / 1000.0).toInt()
    val urgent = remaining <= 2000L
    val timerColor = if (urgent) Theme.Red else Theme.Teal

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Round ${view.round}/${view.goal}", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("⏱ ${secs}s", color = timerColor, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Theme.Ink.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(50)).background(timerColor))
        }
        Text("Tap two cards that add to 10", color = Theme.Ink.copy(alpha = 0.5f), fontSize = 13.sp)
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            userScrollEnabled = false,
        ) {
            itemsIndexed(view.cards, key = { _, c -> c.id }) { _, c ->
                val on = selected.contains(c.id)
                val shape = RoundedCornerShape(10.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(0.72f)
                        .clip(shape)
                        .background(if (on) Theme.Teal else Color.White)
                        .border(width = if (on) 2.5.dp else 1.5.dp, color = if (on) Theme.Teal else Theme.Ink.copy(alpha = 0.12f), shape = shape)
                        .let { if (!busy) it.clickable { tap(c.id) } else it },
                ) {
                    Text("${c.value}", color = if (on) Color.White else Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/* ----------------------------- Critter Count ----------------------------- */

@Composable
fun WheelBoard(view: WheelView, busy: Boolean, onMove: (JSONObject) -> Unit) {
    // Start each new round already spinning (set during composition, not in the
    // effect) so the next round's cards never flash for a frame before the spin.
    var spinning by remember(view.round) { mutableStateOf(!view.lastRound) }
    var spinIdx by remember { mutableStateOf(0) }

    // Spin the wheel at the start of each round (the last round has one animal,
    // so it just lands without spinning).
    LaunchedEffect(view.round) {
        val targetIdx = view.wheel.indexOf(view.targetAnimal).coerceAtLeast(0)
        if (view.lastRound || view.wheel.size <= 1) {
            spinIdx = targetIdx; spinning = false; return@LaunchedEffect
        }
        val n = view.wheel.size
        var d = 55L
        for (s in 0..(n * 3 + targetIdx)) {
            spinIdx = s % n
            delay(d)
            d += 7 // decelerate
        }
        spinIdx = targetIdx
        spinning = false
    }

    // The last round isn't spun, so it gets a "Final Round!" prompt instead —
    // the player taps to begin, and only then does the countdown start.
    var ready by remember(view.round) { mutableStateOf(!view.lastRound) }

    // Per-sub-round countdown — starts once the spin settles (and the final-round
    // prompt is dismissed); timeout = loss.
    var remaining by remember(view.round, view.subround) { mutableStateOf(view.roundMs) }
    LaunchedEffect(view.round, view.subround, spinning, ready) {
        if (spinning || !ready) return@LaunchedEffect
        val deadline = System.currentTimeMillis() + view.roundMs
        while (true) {
            val left = deadline - System.currentTimeMillis()
            remaining = left.coerceAtLeast(0L)
            if (left <= 0L) { onMove(move("timeout")); break }
            delay(50)
        }
    }

    val frac = (remaining.toFloat() / view.roundMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    val secs = Math.ceil(remaining / 1000.0).toInt()
    val urgent = remaining <= 2000L
    val timerColor = if (urgent) Theme.Red else Theme.Orange

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Round ${view.round}/${view.roundsTotal} · Pick ${view.subround}/${view.subroundsTotal}",
            color = Theme.Ink.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )

        // The wheel — a strip of the remaining animals, one highlighted.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            view.wheel.forEachIndexed { i, a ->
                val on = i == spinIdx
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (on) 56.dp else 44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Theme.Orange.copy(alpha = 0.22f) else Theme.Ink.copy(alpha = 0.05f))
                        .border(if (on) 2.5.dp else 0.dp, if (on) Theme.Orange else Color.Transparent, RoundedCornerShape(14.dp)),
                ) {
                    Text(a, fontSize = if (on) 30.sp else 22.sp)
                }
            }
        }

        if (spinning) {
            Text("Choosing an animal…", color = Theme.Ink.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else if (!ready) {
            // Final-round announcement — no spin this time, so give the player a beat.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().kidCard().padding(24.dp),
            ) {
                Text("🏁", fontSize = 48.sp)
                Text("Final Round!", color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(
                    "Just one animal left — ${view.targetAnimal}. Get this card right to win!",
                    color = Theme.Ink.copy(alpha = 0.6f), fontSize = 15.sp, textAlign = TextAlign.Center,
                )
                KidButton(title = "I'm ready! ▶", color = Theme.Orange, onClick = { ready = true })
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Find the card with", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 15.sp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Theme.Orange.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("${view.targetCount} ${view.targetAnimal}", color = Theme.Orange, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }

            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Theme.Ink.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(50)).background(timerColor))
            }
            Text(
                "⏱ ${secs}s" + if (view.wrongPicks.isEmpty()) "" else "   ❌ no more chances left — choose carefully!",
                color = if (urgent || view.wrongPicks.isNotEmpty()) Theme.Red else Theme.Ink.copy(alpha = 0.6f),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )

            val canTap = !busy && !spinning
            view.cards.chunked(2).forEach { rowCards ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    rowCards.forEach { card ->
                        WheelCard(
                            card = card,
                            wrong = view.wrongPicks.contains(card.id),
                            enabled = canTap,
                            modifier = Modifier.weight(1f),
                            onClick = { onMove(JSONObject().put("type", "pick").put("cardId", card.id)) },
                        )
                    }
                    if (rowCards.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WheelCard(card: WheelCardView, wrong: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Scatter the animals (seeded by id so it stays put across recompositions).
    val emojis = remember(card.id) {
        card.counts.flatMap { (a, n) -> List(n) { a } }.shuffled(kotlin.random.Random(card.id))
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 100.dp)
            .clip(shape)
            .background(if (wrong) Theme.Red.copy(alpha = 0.12f) else Color.White)
            .border(width = if (wrong) 2.dp else 1.5.dp, color = if (wrong) Theme.Red else Theme.Ink.copy(alpha = 0.12f), shape = shape)
            .let { if (enabled && !wrong) it.clickable(onClick = onClick) else it }
            .padding(10.dp),
    ) {
        if (emojis.isEmpty()) {
            Text("—", color = Theme.Ink.copy(alpha = 0.3f), fontSize = 26.sp)
        } else {
            Text(emojis.joinToString(" "), fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
        }
    }
}

/* ----------------------------- Odd One Out ----------------------------- */

@Composable
fun OddOneOutBoard(view: OddOneView, busy: Boolean, onMove: (JSONObject) -> Unit) {
    // Per-sub-round countdown; timeout = loss. No spin — the cards are the whole
    // puzzle, so they're shown straight away and the timer starts immediately.
    var remaining by remember(view.round, view.subround) { mutableStateOf(view.roundMs) }
    LaunchedEffect(view.round, view.subround) {
        val deadline = System.currentTimeMillis() + view.roundMs
        while (true) {
            val left = deadline - System.currentTimeMillis()
            remaining = left.coerceAtLeast(0L)
            if (left <= 0L) { onMove(move("timeout")); break }
            delay(50)
        }
    }

    val frac = (remaining.toFloat() / view.roundMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    val secs = Math.ceil(remaining / 1000.0).toInt()
    val urgent = remaining <= 2000L
    val timerColor = if (urgent) Theme.Red else Theme.Purple

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Round ${view.round}/${view.roundsTotal} · Pick ${view.subround}/${view.subroundsTotal}",
            color = Theme.Ink.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            "Tap the card that's different",
            color = Theme.Ink, fontSize = 17.sp, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )

        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Theme.Ink.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(50)).background(timerColor))
        }
        Text(
            "⏱ ${secs}s" + if (view.wrongPicks.isEmpty()) "" else "   ❌ one mistake — look closely!",
            color = if (urgent || view.wrongPicks.isNotEmpty()) Theme.Red else Theme.Ink.copy(alpha = 0.6f),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )

        val canTap = !busy
        view.cards.chunked(2).forEach { rowCards ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowCards.forEach { card ->
                    OddCard(
                        card = card,
                        wrong = view.wrongPicks.contains(card.id),
                        enabled = canTap,
                        modifier = Modifier.weight(1f),
                        onClick = { onMove(JSONObject().put("type", "pick").put("cardId", card.id)) },
                    )
                }
                if (rowCards.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OddCard(card: WheelCardView, wrong: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Canonical order (no shuffle): the four matching cards render identically,
    // so the odd one stands out.
    val emojis = card.counts.flatMap { (a, n) -> List(n) { a } }
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 100.dp)
            .clip(shape)
            .background(if (wrong) Theme.Red.copy(alpha = 0.12f) else Color.White)
            .border(width = if (wrong) 2.dp else 1.5.dp, color = if (wrong) Theme.Red else Theme.Ink.copy(alpha = 0.12f), shape = shape)
            .let { if (enabled && !wrong) it.clickable(onClick = onClick) else it }
            .padding(10.dp),
    ) {
        if (emojis.isEmpty()) {
            Text("—", color = Theme.Ink.copy(alpha = 0.3f), fontSize = 26.sp)
        } else {
            Text(emojis.joinToString(" "), fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
        }
    }
}

/* ----------------------------- Alphabet Lock ----------------------------- */

@Composable
fun AlphabetLockBoard(view: SeqView, busy: Boolean, onMove: (JSONObject) -> Unit) {
    // After a wrong flip, show the letter briefly, then flip everything back down.
    LaunchedEffect(view.wrong) {
        if (view.wrong) { delay(900); onMove(move("hide")) }
    }
    val locked = busy || view.wrong

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Flip the letters in ABC order",
            color = Theme.Ink, fontSize = 17.sp, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )

        // The ordered ladder: done letters filled, the next one outlined.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            view.order.forEachIndexed { i, l ->
                val done = i < view.progress
                val next = i == view.progress && !view.wrong
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (done) Theme.Green else if (next) Theme.Blue.copy(alpha = 0.15f) else Theme.Ink.copy(alpha = 0.06f))
                        .border(if (next) 2.dp else 0.dp, if (next) Theme.Blue else Color.Transparent, RoundedCornerShape(8.dp)),
                ) {
                    Text(l, color = if (done) Color.White else if (next) Theme.Blue else Theme.Ink.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // The 3×3 grid (built by hand so it lives happily inside a scroll).
        view.cards.chunked(3).forEach { rowCards ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowCards.forEach { c ->
                    val up = c.faceUp
                    val correct = up && !c.wrong
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(when { c.wrong -> Theme.Red.copy(alpha = 0.15f); up -> Color.White; else -> Theme.Blue })
                            .border(width = if (correct) 2.5.dp else 0.dp, color = if (correct) Theme.Green else Color.Transparent, shape = shape)
                            .let { if (!locked && !up) it.clickable { onMove(JSONObject().put("type", "flip").put("cardId", c.id)) } else it },
                    ) {
                        if (up) {
                            Text(c.letter, color = if (c.wrong) Theme.Red else Theme.Green, fontSize = 34.sp, fontWeight = FontWeight.Black)
                        } else {
                            Text("?", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        Text(
            "${view.progress}/${view.total} in order",
            color = Theme.Ink.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ----------------------------- Card Showdown ----------------------------- */

@Composable
fun ShowdownBoard(view: ShowdownView, players: List<CardPlayer>, busy: Boolean, onMove: (JSONObject) -> Unit) {
    val picked = remember(view.yourHand, view.committed, view.yourSelection) { mutableStateListOf<Int>() }
    val committed = view.yourSelection != null

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Scoreboard.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            players.forEach { p ->
                val out = view.eliminated.contains(p.learnerId)
                val stars = view.stars[p.learnerId] ?: 0
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (out) Theme.Ink.copy(alpha = 0.05f) else Color.White).padding(8.dp),
                ) {
                    Text("${p.avatar ?: "🙂"} ${p.name}${if (out) " ❌" else ""}", color = Theme.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        "⭐".repeat(stars) + "☆".repeat((view.starsToWin - stars).coerceAtLeast(0)) + "  🃏${view.handCounts[p.learnerId] ?: 0}",
                        color = Theme.Orange, fontSize = 11.sp,
                    )
                    if (!out && (view.committed[p.learnerId] == true)) {
                        Text("locked in", color = Theme.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        view.lastResult?.let { r ->
            Text(
                if (r.draw) "🤝 Draw — everyone played the same total."
                else "⭐ ${r.highIds.joinToString(" & ") { nm(players, it) }} won the round!",
                color = Theme.Ink.copy(alpha = 0.65f), fontSize = 13.sp,
            )
        }

        when {
            view.youEliminated -> Text("You're out — watching the rest. 👀", color = Theme.Ink.copy(alpha = 0.5f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            committed -> Column {
                Text("✅ Locked in: ${view.yourSelection!!.joinToString(" + ")}", color = Theme.Green, fontWeight = FontWeight.Bold)
                Text("Waiting for ${view.waitingOn} more player${if (view.waitingOn == 1) "" else "s"}…", color = Theme.Ink.copy(alpha = 0.45f), fontSize = 13.sp)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Secretly play one or two cards", color = Theme.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                HandRow(view.yourHand, picked.toSet(), enabled = !busy) { idx -> togglePick(picked, idx, max = 2) }
                val sum = picked.sumOf { view.yourHand[it] }
                KidButton(
                    title = if (picked.isEmpty()) "Play" else "Play ($sum)",
                    color = if (picked.isNotEmpty() && !busy) Theme.Pink else Theme.Ink.copy(alpha = 0.3f),
                    enabled = picked.isNotEmpty() && !busy,
                    onClick = { onMove(cardsMove("play", picked.map { view.yourHand[it] })); picked.clear() },
                )
            }
        }
    }
}

/* ----------------------------- Matching Colours ----------------------------- */

@Composable
fun MatchColoursBoard(view: MatchColoursView, players: List<CardPlayer>, busy: Boolean, onMove: (JSONObject) -> Unit) {
    val offset = remember(view.serverNow) { view.serverNow - System.currentTimeMillis() }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(100); tick++ }
    }
    // Reading `tick` as a remember key recomputes the effective clock ~10×/s,
    // driving the countdown and the shrinking timer bar locally.
    val eff = remember(tick) { System.currentTimeMillis() + offset }
    val answerMs = (view.deadlineAt - view.revealAt).coerceAtLeast(1)
    val preview = !view.resolved && eff < view.revealAt
    val result = view.resolved || eff >= view.deadlineAt
    val answering = !preview && !result

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (view.final) "🏁 Sudden-death round ${view.round}" else "Round ${view.round} / ${view.roundsTotal}",
            color = Theme.Ink.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // Scoreboard.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            players.forEach { p ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(8.dp),
                ) {
                    Text("${p.avatar ?: "🙂"} ${p.name}", color = Theme.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    val pts = view.points?.get(p.learnerId) ?: 0
                    Text(
                        "${view.scores[p.learnerId] ?: 0} pts" + if (result && pts > 0) "  +$pts" else "",
                        color = if (result && pts > 0) Theme.Green else Theme.Ink.copy(alpha = 0.6f), fontSize = 11.sp,
                    )
                }
            }
        }

        if (!view.inRound) {
            Text("👀 Watching the sudden-death round…", color = Theme.Ink.copy(alpha = 0.5f), fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        if (preview) {
            Text("Memorise the colours!", color = Theme.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            ColourMappingRow(view)
            val secs = ((view.revealAt - eff) / 1000.0).let { Math.ceil(it).toInt() }.coerceAtLeast(1)
            Text("$secs", color = Theme.Pink, fontSize = 48.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else if (answering) {
            val youAnswered = view.yourAnswer != null
            val frac = ((view.deadlineAt - eff).toFloat() / answerMs).coerceIn(0f, 1f)
            Text("Tap the colour for", color = Theme.Ink.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("${view.prompt}", color = Theme.Ink, fontSize = 56.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            // Timer bar.
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Theme.Ink.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(50)).background(Theme.Pink))
            }
            ColourMappingRow(view)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                view.colours.forEachIndexed { i, c ->
                    val chosen = view.yourAnswer == i
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp)).background(parseHex(c.hex))
                            .let { if (!busy && !youAnswered) it.clickable { onMove(JSONObject().put("type", "tap").put("colour", i)) } else it }
                            .padding(4.dp),
                    ) {
                        Text(
                            "${c.emoji}\n${c.label}",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = if (chosen) Modifier else if (youAnswered) Modifier else Modifier,
                        )
                    }
                }
            }
        } else {
            val correct = view.correctColour
            Text(
                if (view.yourAnswer != null && view.yourAnswer == correct) "✅ Correct!" else "⏱️ Round over",
                color = Theme.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            if (correct != null && correct in view.colours.indices) {
                Text("Answer: ${view.colours[correct].emoji} ${view.colours[correct].label}", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ColourMappingRow(view: MatchColoursView) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (num in 1..4) {
            val colourId = view.mapping.getOrNull(num - 1) ?: continue
            val c = view.colours.getOrNull(colourId) ?: continue
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp),
            ) {
                Text("$num", color = Theme.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(8.dp)).background(parseHex(c.hex)))
            }
        }
    }
}

/* ----------------------------- shared bits ----------------------------- */

@Composable
private fun TargetChip(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Theme.Ink.copy(alpha = 0.5f), fontSize = 12.sp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.15f)),
        ) {
            Text("$value", color = color, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

/** A wrapping row of hand cards (values), selectable. */
@Composable
private fun HandRow(hand: List<Int>, selectedIndices: Set<Int>, enabled: Boolean, onTap: (Int) -> Unit) {
    if (hand.isEmpty()) {
        Text("(no cards)", color = Theme.Ink.copy(alpha = 0.4f), fontSize = 13.sp)
        return
    }
    // Simple wrap using FlowRow-like manual chunking (6 per row), centred.
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        hand.indices.chunked(6).forEach { rowIdx ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowIdx.forEach { idx ->
                    PlayingCard(
                        value = hand[idx],
                        selected = selectedIndices.contains(idx),
                        enabled = enabled,
                        onClick = { onTap(idx) },
                    )
                }
            }
        }
    }
}

/**
 * A single elongated playing-card chip: a tall 3:4 card with corner pips and a
 * large centre value. Lifts and tints when selected.
 */
@Composable
private fun PlayingCard(value: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val fg = if (selected) Color.White else Theme.Ink
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(66.dp)
            .softShadow(shape)
            .clip(shape)
            .background(if (selected) Theme.Pink else Color.White)
            .border(width = 1.5.dp, color = if (selected) Theme.Pink else Theme.Ink.copy(alpha = 0.12f), shape = shape)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 5.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$value", color = fg, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

/* ----------------------------- selection helpers ----------------------------- */

private fun togglePick(list: androidx.compose.runtime.snapshots.SnapshotStateList<Int>, idx: Int, max: Int) {
    if (list.contains(idx)) list.remove(idx)
    else if (list.size < max) list.add(idx)
}

private fun nm(players: List<CardPlayer>, id: Int): String = players.firstOrNull { it.learnerId == id }?.name ?: "Player"
