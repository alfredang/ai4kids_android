package sg.com.tertiarycourses.ai4kids.cards

import org.json.JSONObject
import kotlin.random.Random

/**
 * On-device solo engines — Kotlin ports of the four card games that offer a
 * solo mode (Memory Match, Tower Tumble, Number Hunt, Beat the Die). These let a
 * single player enjoy the games fully offline, with no sign-in: only the
 * online multiplayer modes talk to the backend.
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
}

/** Wraps a solo engine and presents it to the UI as a one-player [CardState]. */
class LocalSession(val meta: CardGameMeta, pairs: Int) {
    private val engine: LocalEngine = when (meta.slug) {
        "memory-match" -> MemoryLocal(pairs)
        "tower-tumble" -> DiscardLocal()
        "number-hunt" -> MathLocal()
        "beat-the-die" -> BeatDieLocal()
        else -> throw IllegalArgumentException("No solo mode for ${meta.slug}")
    }
    private var finished = false

    fun apply(move: JSONObject) {
        if (finished) return
        engine.move(move)
        if (engine.isOver) {
            finished = true
            CardApi.recordSoloBest(meta.activitySlug, System.currentTimeMillis() - startedAt)
        }
    }

    private val startedAt = System.currentTimeMillis()

    fun state(): CardState = CardState(
        code = "SOLO",
        gameSlug = meta.slug,
        gameTitle = meta.title,
        mode = "solo",
        status = if (finished) "done" else "playing",
        hostId = SOLO_ID,
        you = SOLO_ID,
        players = listOf(CardPlayer(SOLO_ID, "You", null, isHost = true, place = if (finished) 0 else null)),
        winners = if (finished) listOf(SOLO_ID) else emptyList(),
        game = engine.view(),
        startedAt = null,
        finishedAt = null,
        bestMs = if (finished) CardApi.soloBest(meta.activitySlug) else null,
    )

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
