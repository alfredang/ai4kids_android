package sg.com.tertiarycourses.ai4kids.cards

import org.json.JSONObject
import kotlin.random.Random

/**
 * On-device solo engines — Kotlin ports of the card games that offer a solo
 * mode (Memory Match, Tower Tumble, Number Hunt, Beat the Die, Make Ten,
 * Animal Count, Odd One Out, Alphabet Lock). These let a single player enjoy the
 * games fully offline, with no sign-in: only the online multiplayer modes talk
 * to the backend.
 *
 * Each engine produces the same [GameView] shape and accepts the same move
 * payloads as the server, so the existing boards render them unchanged.
 */

/** Illegal-move errors surface the message to the player (same as the server). */
private class MoveError(message: String) : Exception(message)

interface LocalEngine {
    fun view(): GameView
    fun move(m: JSONObject)
    val isOver: Boolean

    /** True when the game ended in a loss (e.g. ran out of time) — no best time. */
    val lost: Boolean get() = false
}

/** Wraps a solo engine and presents it to the UI as a one-player [CardState]. */
class LocalSession(val meta: CardGameMeta, pairs: Int) {
    private val engine: LocalEngine = when (meta.slug) {
        "memory-match" -> MemoryLocal(pairs)
        "tower-tumble" -> DiscardLocal()
        "number-hunt" -> MathLocal()
        "beat-the-die" -> BeatDieLocal()
        "make-ten" -> MakeTenLocal()
        "animal-count" -> WheelLocal()
        "odd-one-out" -> OddOneLocal()
        "alphabet-lock" -> SeqLocal()
        else -> throw IllegalArgumentException("No solo mode for ${meta.slug}")
    }
    private var finished = false

    fun apply(move: JSONObject) {
        if (finished) return
        engine.move(move)
        if (engine.isOver) {
            finished = true
            // A loss (e.g. timed out) doesn't count toward the best time.
            if (!engine.lost) CardApi.recordSoloBest(meta.activitySlug, System.currentTimeMillis() - startedAt)
        }
    }

    private val startedAt = System.currentTimeMillis()

    fun state(): CardState {
        // A solo win has the player as the sole winner; a loss has no winner
        // (the results screen reads an empty winners list as "out of time").
        val won = finished && !engine.lost
        return CardState(
            code = "SOLO",
            gameSlug = meta.slug,
            gameTitle = meta.title,
            mode = "solo",
            status = if (finished) "done" else "playing",
            hostId = SOLO_ID,
            you = SOLO_ID,
            players = listOf(CardPlayer(SOLO_ID, "You", null, isHost = true, place = if (won) 0 else null)),
            winners = if (won) listOf(SOLO_ID) else emptyList(),
            game = engine.view(),
            startedAt = null,
            finishedAt = null,
            bestMs = if (won) CardApi.soloBest(meta.activitySlug) else null,
        )
    }

    companion object {
        const val SOLO_ID = 1
    }
}

/* ----------------------------- helpers ----------------------------- */

/** 40-card deck of values: 1..10, four of each. */
private fun deckValues(): MutableList<Int> {
    val out = ArrayList<Int>(40)
    repeat(4) { for (v in 1..10) out.add(v) }
    out.shuffle()
    return out
}

private fun removeFromHand(hand: MutableList<Int>, cards: List<Int>): Boolean {
    val copy = hand.toMutableList()
    for (c in cards) {
        val i = copy.indexOf(c)
        if (i == -1) return false
        copy.removeAt(i)
    }
    hand.clear(); hand.addAll(copy)
    return true
}

private fun JSONObject.cardList(): List<Int> {
    val arr = optJSONArray("cards") ?: return emptyList()
    return (0 until arr.length()).map { arr.getInt(it) }
}

/* ----------------------------- Memory ----------------------------- */

private class MemoryLocal(pairsRequested: Int) : LocalEngine {
    private data class C(val id: Int, val concept: Int, val face: String, val label: String)

    private val concepts = listOf(
        "plant" to "🌱", "star" to "⭐", "rocket" to "🚀", "robot" to "🤖",
        "apple" to "🍎", "cat" to "🐱", "fish" to "🐟", "sun" to "☀️",
        "moon" to "🌙", "tree" to "🌳", "car" to "🚗", "ball" to "⚽",
        "cake" to "🍰", "dog" to "🐶", "frog" to "🐸", "boat" to "⛵",
    )

    private val cards: List<C>
    private val matched = HashSet<Int>()
    private val flipped = ArrayList<Int>()
    private var mismatch = false
    private var flips = 0

    init {
        val pairs = pairsRequested.coerceIn(4, concepts.size)
        val chosen = concepts.shuffled().take(pairs)
        val list = ArrayList<C>()
        var id = 0
        chosen.forEachIndexed { ci, c ->
            list.add(C(id++, ci, "word", c.first))
            list.add(C(id++, ci, "emoji", c.second))
        }
        cards = list.shuffled()
    }

    private fun totalPairs() = cards.size / 2
    override val isOver: Boolean get() = matched.size >= totalPairs() * 2

    override fun move(m: JSONObject) {
        when (m.optString("type")) {
            "next" -> {
                if (!mismatch) throw MoveError("Nothing to clear.")
                flipped.clear(); mismatch = false
            }
            "flip" -> {
                if (mismatch) { flipped.clear(); mismatch = false }
                val id = m.getInt("cardId")
                val card = cards.firstOrNull { it.id == id } ?: throw MoveError("No such card.")
                if (matched.contains(id)) throw MoveError("That pair is already found.")
                if (flipped.contains(id)) throw MoveError("That card is already up.")
                if (flipped.size >= 2) throw MoveError("Tap to clear first.")
                flipped.add(id); flips++
                if (flipped.size == 2) {
                    val a = cards.first { it.id == flipped[0] }
                    val b = cards.first { it.id == flipped[1] }
                    if (a.concept == b.concept) {
                        matched.add(a.id); matched.add(b.id); flipped.clear()
                    } else mismatch = true
                }
            }
            else -> throw MoveError("Unknown move.")
        }
    }

    override fun view(): GameView {
        val found = matched.size / 2
        val cv = cards.map { c ->
            val isMatched = matched.contains(c.id)
            val up = isMatched || flipped.contains(c.id)
            MemoryCardView(c.id, c.face, if (up) c.label else null, isMatched, flipped.contains(c.id), if (isMatched) LocalSession.SOLO_ID else null)
        }
        return MemoryView(cv, LocalSession.SOLO_ID, true, mismatch, mapOf(LocalSession.SOLO_ID to found), flips, totalPairs(), found)
    }
}

/* ----------------------------- Tower Tumble ----------------------------- */

private class DiscardLocal : LocalEngine {
    private val piles: IntArray
    private val hand: MutableList<Int>
    private var passStreak = 0
    private var done = false

    init {
        val deck = deckValues()
        piles = IntArray(4) { val v = deck[it]; if (v == 10) 0 else v }
        hand = deck.drop(4).sorted().toMutableList()
    }

    private fun canPlay(): Boolean = hand.any { c -> c == 10 || piles.any { c > it } }
    override val isOver: Boolean get() = done

    override fun move(m: JSONObject) {
        when (m.optString("type")) {
            "play" -> {
                val pile = m.getInt("pile")
                val card = m.getInt("card")
                if (pile < 0 || pile >= 4) throw MoveError("Pick a pile.")
                if (!hand.contains(card)) throw MoveError("You don't have that card.")
                val top = piles[pile]
                if (!(card == 10 || card > top)) throw MoveError("Play a card higher than $top, or a 10 to clear.")
                hand.remove(card)
                piles[pile] = if (card == 10) 0 else card
                passStreak = 0
                if (hand.isEmpty()) done = true
            }
            "pass" -> {
                if (canPlay()) throw MoveError("You have a move — play a card!")
                passStreak += 1
                if (passStreak >= 1) { for (i in piles.indices) piles[i] = 0; passStreak = 0 }
            }
            else -> throw MoveError("Unknown move.")
        }
    }

    override fun view(): GameView = DiscardView(
        piles = piles.toList(),
        yourHand = hand.toList(),
        hands = mapOf(LocalSession.SOLO_ID to hand.size),
        turnPlayerId = LocalSession.SOLO_ID,
        yourTurn = true,
        finished = if (done) listOf(LocalSession.SOLO_ID) else emptyList(),
        canPlay = canPlay(),
    )
}

/* ----------------------------- Number Hunt ----------------------------- */

private class MathLocal : LocalEngine {
    private val hand: MutableList<Int>
    private var target: Int
    private var draw: MutableList<Int>
    private var discardTop: Int
    private val discardPile: MutableList<Int>
    private var done = false
    private var lowWater: Int
    private var sinceProgress = 0
    private var dead = false

    init {
        val deck = deckValues()
        hand = ArrayList()
        var k = 0
        repeat(5) { hand.add(deck[k++]) }
        discardTop = deck[k++]
        discardPile = mutableListOf(discardTop)
        draw = deck.drop(k).toMutableList()
        target = 2 + Random.nextInt(8)
        hand.sort()
        lowWater = 5
    }

    private fun makesTarget(cards: List<Int>, t: Int): Boolean = when (cards.size) {
        1 -> cards[0] == t
        2 -> cards[0] + cards[1] == t || kotlin.math.abs(cards[0] - cards[1]) == t
        else -> false
    }

    private fun canDiscard(h: List<Int>, t: Int): Boolean {
        if (h.contains(t)) return true
        for (i in h.indices) for (j in i + 1 until h.size) if (makesTarget(listOf(h[i], h[j]), t)) return true
        return false
    }

    private fun achievableForPlayer(): List<Int> = (2..9).filter { canDiscard(hand, it) }

    private fun relief() {
        if (done) return
        val total = hand.size
        if (total < lowWater) { lowWater = total; sinceProgress = 0; return }
        sinceProgress += 1
        if (sinceProgress < 4) return
        val forNext = achievableForPlayer()
        val fresh = forNext.filter { it != target }
        when {
            fresh.isNotEmpty() -> target = fresh.random()
            forNext.isNotEmpty() -> target = forNext[0]
            else -> dead = true
        }
        sinceProgress = 0
    }

    override val isOver: Boolean get() = dead || done

    override fun move(m: JSONObject) {
        when (m.optString("type")) {
            "discard" -> {
                val cards = m.cardList()
                if (cards.size < 1 || cards.size > 2) throw MoveError("Pick one or two cards.")
                if (!makesTarget(cards, target)) throw MoveError("Those don't make $target. Try one card = $target, or two that add/subtract to it.")
                if (!removeFromHand(hand, cards)) throw MoveError("You don't have those cards.")
                discardPile.addAll(cards)
                discardTop = cards.last()
                if (hand.isEmpty()) done = true
                relief()
            }
            "draw" -> {
                if (draw.isEmpty()) {
                    val top = if (discardPile.isNotEmpty()) discardPile.removeAt(discardPile.size - 1) else null
                    draw = discardPile.toMutableList().also { it.shuffle() }
                    discardPile.clear()
                    if (top != null) { discardPile.add(top); discardTop = top }
                }
                if (draw.isNotEmpty()) { hand.add(draw.removeAt(0)); hand.sort() }
                relief()
            }
            else -> throw MoveError("Unknown move.")
        }
    }

    override fun view(): GameView = MathView(
        target = target,
        discardTop = discardTop,
        drawCount = draw.size,
        yourHand = hand.toList(),
        hands = mapOf(LocalSession.SOLO_ID to hand.size),
        turnPlayerId = LocalSession.SOLO_ID,
        yourTurn = true,
        finished = if (done) listOf(LocalSession.SOLO_ID) else emptyList(),
    )
}

/* ----------------------------- Make Ten ----------------------------- */

private class MakeTenLocal : LocalEngine {
    private data class C(val id: Int, val value: Int)

    // Number bonds that sum to 10. Building the deck only from these pairs
    // guarantees the board is always clearable to empty (no dead ends), since
    // each value keeps its complement balanced.
    private val bonds = listOf(1 to 9, 2 to 8, 3 to 7, 4 to 6, 5 to 5)
    private val goal = 12 // pairs to clear (24 cards)
    private val cards = ArrayList<C>()
    private var done = false
    private var timedOut = false

    // Per-round countdown that tightens as the board clears: 8s on the first
    // pair, shrinking 0.4s each round down to a 3s floor. The board enforces it
    // in real time and reports a "timeout" when the clock runs out.
    private val startMs = 8_000L
    private val stepMs = 400L
    private val floorMs = 3_000L

    private val cleared get() = goal - cards.size / 2
    private val round get() = cleared + 1 // 1-based round currently in play
    private fun budgetFor(r: Int): Long = (startMs - (r - 1) * stepMs).coerceAtLeast(floorMs)

    init {
        var id = 0
        repeat(goal) {
            val (a, b) = bonds.random()
            cards.add(C(id++, a)); cards.add(C(id++, b))
        }
        cards.shuffle()
    }

    override val isOver: Boolean get() = done || timedOut
    override val lost: Boolean get() = timedOut

    override fun move(m: JSONObject) {
        when (m.optString("type")) {
            "clear" -> {
                val ids = m.cardList()
                if (ids.size != 2 || ids[0] == ids[1]) throw MoveError("Pick two different cards.")
                val a = cards.firstOrNull { it.id == ids[0] } ?: throw MoveError("No such card.")
                val b = cards.firstOrNull { it.id == ids[1] } ?: throw MoveError("No such card.")
                if (a.value + b.value != 10) throw MoveError("${a.value} + ${b.value} = ${a.value + b.value}, not 10.")
                cards.remove(a); cards.remove(b)
                if (cards.isEmpty()) done = true
            }
            "timeout" -> timedOut = true
            else -> throw MoveError("Unknown move.")
        }
    }

    override fun view(): GameView = MakeTenView(
        cards = cards.map { MakeTenCardView(it.id, it.value) },
        cleared = cleared,
        goal = goal,
        round = round,
        roundMs = budgetFor(round),
        turnPlayerId = LocalSession.SOLO_ID,
        yourTurn = true,
        finished = if (done) listOf(LocalSession.SOLO_ID) else emptyList(),
    )
}

/* ----------------------------- Critter Count ----------------------------- */

private class WheelLocal : LocalEngine {
    private data class Card(val id: Int, val counts: Map<String, Int>)

    // Every card always shows all five animals (the full pool); only the *wheel*
    // — the set of animals that can still be the target — shrinks each round.
    private val pool = listOf("🐶", "🐱", "🐰", "🦊", "🐻")
    private val wheel = pool.toMutableList()
    private val roundsTotal = 5
    private val subroundsTotal = 3
    private val baseMs = 6_000L      // sub-round 1 budget
    private val subStepMs = 1_500L   // shrink per sub-round
    private val floorMs = 1_500L

    private var round = 1
    private var subround = 1
    private var done = false
    private var failed = false

    private lateinit var targetAnimal: String
    private var targetCount = 0
    private var cards: List<Card> = emptyList()
    private val wrongPicks = LinkedHashSet<Int>()
    private var nextId = 0

    private val lastRound get() = wheel.size <= 1

    init { spin(); deal() }

    /** Pick the round's target animal (the lone one on the last round) + number. */
    private fun spin() {
        targetAnimal = wheel.random()
        targetCount = Random.nextInt(0, 3) // 0..2
    }

    /** Fresh hand of 5; exactly one card carries the target count (unique answer). */
    private fun deal() {
        wrongPicks.clear()
        val answer = Random.nextInt(5)
        cards = (0 until 5).map { i ->
            val counts = HashMap<String, Int>()
            for (a in pool) {
                counts[a] = when {
                    a != targetAnimal -> Random.nextInt(0, 3)
                    i == answer -> targetCount
                    else -> (0..2).filter { it != targetCount }.random()
                }
            }
            Card(nextId++, counts)
        }
    }

    private fun roundMs(): Long = (baseMs - (subround - 1) * subStepMs).coerceAtLeast(floorMs)

    override val isOver: Boolean get() = done || failed
    override val lost: Boolean get() = failed

    override fun move(m: JSONObject) {
        when (m.optString("type")) {
            "pick" -> {
                val id = m.optInt("cardId", -1)
                val card = cards.firstOrNull { it.id == id } ?: throw MoveError("No such card.")
                if (wrongPicks.contains(id)) throw MoveError("Already tried that one.")
                if ((card.counts[targetAnimal] ?: 0) == targetCount) advance()
                else {
                    wrongPicks.add(id)
                    if (wrongPicks.size >= 2) failed = true // only one slip per sub-round
                }
            }
            "timeout" -> failed = true
            else -> throw MoveError("Unknown move.")
        }
    }

    private fun advance() {
        if (subround < subroundsTotal) {
            subround++; deal() // same animal/number, fresh cards, tighter timer
            return
        }
        if (round >= roundsTotal) { done = true; return }
        wheel.remove(targetAnimal) // retire the cleared animal from the wheel only
        round++; subround = 1
        spin(); deal()
    }

    override fun view(): GameView = WheelView(
        wheel = wheel.toList(),
        targetAnimal = targetAnimal,
        targetCount = targetCount,
        cards = cards.map { c -> WheelCardView(c.id, pool.map { it to (c.counts[it] ?: 0) }) },
        round = round,
        roundsTotal = roundsTotal,
        subround = subround,
        subroundsTotal = subroundsTotal,
        roundMs = roundMs(),
        lastRound = lastRound,
        wrongPicks = wrongPicks.toList(),
        turnPlayerId = LocalSession.SOLO_ID,
        yourTurn = true,
        finished = if (done) listOf(LocalSession.SOLO_ID) else emptyList(),
    )
}

/* ----------------------------- Odd One Out ----------------------------- */

private class OddOneLocal : LocalEngine {
    private data class Card(val id: Int, val counts: Map<String, Int>)

    private val pool = listOf("🐶", "🐱", "🐰", "🦊", "🐻")
    private val roundsTotal = 5
    private val subroundsTotal = 3
    private val baseMs = 6_000L
    private val subStepMs = 1_500L
    private val floorMs = 1_500L

    private var round = 1
    private var subround = 1
    private var done = false
    private var failed = false

    private var cards: List<Card> = emptyList()
    private var oddId = -1
    private val wrongPicks = LinkedHashSet<Int>()
    private var nextId = 0

    init { deal() }

    /**
     * Build four identical cards plus one "odd" card. Difficulty climbs with the
     * round: more animal types and more emoji, so the single swapped animal on
     * the odd card gets harder to spot.
     */
    private fun deal() {
        wrongPicks.clear()
        val variety = (round + 1).coerceIn(2, pool.size)   // animal types in play
        val total = (round + 2).coerceIn(3, 8)             // emoji per card
        val types = pool.take(variety)

        val base = HashMap<String, Int>()
        types.forEach { base[it] = 0 }
        repeat(total) { val t = types.random(); base[t] = base[t]!! + 1 }

        // The odd card swaps one emoji for a different animal (same total count).
        val odd = HashMap(base)
        val from = types.filter { (odd[it] ?: 0) > 0 }.random()
        val to = types.filter { it != from }.random()
        odd[from] = odd[from]!! - 1
        odd[to] = (odd[to] ?: 0) + 1

        val oddIdx = Random.nextInt(6)
        cards = (0 until 6).map { i ->
            Card(nextId++, HashMap(if (i == oddIdx) odd else base))
        }
        oddId = cards[oddIdx].id
    }

    private fun roundMs(): Long = (baseMs - (subround - 1) * subStepMs).coerceAtLeast(floorMs)

    override val isOver: Boolean get() = done || failed
    override val lost: Boolean get() = failed

    override fun move(m: JSONObject) {
        when (m.optString("type")) {
            "pick" -> {
                val id = m.optInt("cardId", -1)
                if (cards.none { it.id == id }) throw MoveError("No such card.")
                if (wrongPicks.contains(id)) throw MoveError("Already tried that one.")
                if (id == oddId) advance()
                else {
                    wrongPicks.add(id)
                    if (wrongPicks.size >= 2) failed = true // only one slip per sub-round
                }
            }
            "timeout" -> failed = true
            else -> throw MoveError("Unknown move.")
        }
    }

    private fun advance() {
        if (subround < subroundsTotal) { subround++; deal(); return }
        if (round >= roundsTotal) { done = true; return }
        round++; subround = 1; deal()
    }

    override fun view(): GameView = OddOneView(
        cards = cards.map { c -> WheelCardView(c.id, pool.map { it to (c.counts[it] ?: 0) }) },
        round = round,
        roundsTotal = roundsTotal,
        subround = subround,
        subroundsTotal = subroundsTotal,
        roundMs = roundMs(),
        wrongPicks = wrongPicks.toList(),
        turnPlayerId = LocalSession.SOLO_ID,
        yourTurn = true,
        finished = if (done) listOf(LocalSession.SOLO_ID) else emptyList(),
    )
}

/* ----------------------------- Alphabet Lock ----------------------------- */

private class SeqLocal : LocalEngine {
    private data class Card(val id: Int, val letter: String)

    private val total = 9
    private val order: List<String> // the nine letters in alphabetical order
    private val cards: List<Card>   // same letters shuffled into grid slots
    private val revealed = LinkedHashSet<Int>() // correctly-flipped prefix
    private var wrongCard: Int? = null
    private var done = false

    init {
        val start = Random.nextInt(0, 26 - total + 1) // first letter, A..R
        order = (0 until total).map { ('A' + start + it).toString() }
        cards = order.shuffled().mapIndexed { i, l -> Card(i, l) }
    }

    private val progress get() = revealed.size
    override val isOver: Boolean get() = done

    override fun move(m: JSONObject) {
        when (m.optString("type")) {
            "flip" -> {
                if (wrongCard != null) return // locked while a wrong card is shown
                val id = m.optInt("cardId", -1)
                val card = cards.firstOrNull { it.id == id } ?: return
                if (revealed.contains(id)) return
                if (card.letter == order[progress]) {
                    revealed.add(id)
                    if (revealed.size == total) done = true
                } else {
                    wrongCard = id // shown briefly; a "hide" then flips everything down
                }
            }
            "hide" -> { revealed.clear(); wrongCard = null }
            else -> throw MoveError("Unknown move.")
        }
    }

    override fun view(): GameView = SeqView(
        cards = cards.map { c ->
            SeqCardView(c.id, c.letter, faceUp = revealed.contains(c.id) || c.id == wrongCard, wrong = c.id == wrongCard)
        },
        order = order,
        progress = progress,
        total = total,
        wrong = wrongCard != null,
        turnPlayerId = LocalSession.SOLO_ID,
        yourTurn = true,
        finished = if (done) listOf(LocalSession.SOLO_ID) else emptyList(),
    )
}

/* ----------------------------- Beat the Die ----------------------------- */

private class BeatDieLocal : LocalEngine {
    private val hand: MutableList<Int>
    private val draw: MutableList<Int>
    private var die: Int? = null
    private var done = false

    init {
        hand = ArrayList()
        for (v in 1..4) repeat(3) { hand.add(v) }
        val pool = ArrayList<Int>()
        for (v in 1..4) repeat(10) { pool.add(v) }
        pool.shuffle()
        draw = pool
    }

    private fun canBeat(h: List<Int>, d: Int): Boolean {
        if (h.any { it >= d }) return true
        if (h.size < 2) return false
        val top2 = h.sortedDescending().take(2)
        return top2[0] + top2[1] >= d
    }

    override val isOver: Boolean get() = done

    override fun move(m: JSONObject) {
        when (m.optString("type")) {
            "roll" -> {
                if (die != null) throw MoveError("You already rolled — now discard or draw.")
                die = 1 + Random.nextInt(6)
            }
            "discard" -> {
                val d = die ?: throw MoveError("Roll the die first.")
                val cards = m.cardList()
                if (cards.size < 1 || cards.size > 2) throw MoveError("Pick one or two cards.")
                val sum = cards.sum()
                if (sum < d) throw MoveError("That only makes $sum — you need to beat $d.")
                if (!removeFromHand(hand, cards)) throw MoveError("You don't have those cards.")
                if (hand.isEmpty()) done = true
                die = null
            }
            "draw" -> {
                val d = die ?: throw MoveError("Roll the die first.")
                if (canBeat(hand, d)) throw MoveError("You can beat the die — discard instead!")
                if (draw.isNotEmpty()) { hand.add(draw.removeAt(0)); hand.sort() }
                die = null
            }
            else -> throw MoveError("Unknown move.")
        }
    }

    override fun view(): GameView = BeatDieView(
        die = die,
        drawCount = draw.size,
        yourHand = hand.toList(),
        hands = mapOf(LocalSession.SOLO_ID to hand.size),
        turnPlayerId = LocalSession.SOLO_ID,
        yourTurn = true,
        finished = if (done) listOf(LocalSession.SOLO_ID) else emptyList(),
        canBeat = die?.let { canBeat(hand, it) } ?: true,
    )
}
