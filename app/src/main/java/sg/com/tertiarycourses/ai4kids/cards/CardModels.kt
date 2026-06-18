package sg.com.tertiarycourses.ai4kids.cards

import org.json.JSONArray
import org.json.JSONObject

/* ----------------------------- JSON helpers ----------------------------- */

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key)

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key) || !has(key)) null else optLong(key)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key) || !has(key)) null else optInt(key)

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    return (0 until length()).map { getInt(it) }
}

private fun JSONObject?.toIntIntMap(): Map<Int, Int> {
    if (this == null) return emptyMap()
    val out = HashMap<Int, Int>()
    keys().forEach { k -> out[k.toInt()] = getInt(k) }
    return out
}

private fun JSONObject?.toIntBoolMap(): Map<Int, Boolean> {
    if (this == null) return emptyMap()
    val out = HashMap<Int, Boolean>()
    keys().forEach { k -> out[k.toInt()] = getBoolean(k) }
    return out
}

private fun JSONObject?.toIntListMap(): Map<Int, List<Int>> {
    if (this == null) return emptyMap()
    val out = HashMap<Int, List<Int>>()
    keys().forEach { k -> out[k.toInt()] = optJSONArray(k).toIntList() }
    return out
}

/* ----------------------------- Envelope ----------------------------- */

data class CardPlayer(
    val learnerId: Int,
    val name: String,
    val avatar: String?,
    val isHost: Boolean,
    val place: Int?,
)

data class CardState(
    val code: String,
    val gameSlug: String,
    val gameTitle: String,
    val mode: String,
    val status: String, // "lobby" | "playing" | "done"
    val hostId: Int,
    val you: Int,
    val players: List<CardPlayer>,
    val winners: List<Int>,
    val game: GameView?,
    val startedAt: String?,
    val finishedAt: String?,
    val bestMs: Long?,
) {
    companion object {
        fun parse(o: JSONObject): CardState {
            val players = o.optJSONArray("players").let { arr ->
                if (arr == null) emptyList() else (0 until arr.length()).map { i ->
                    val p = arr.getJSONObject(i)
                    CardPlayer(
                        learnerId = p.getInt("learnerId"),
                        name = p.optString("name", "Player"),
                        avatar = p.optStringOrNull("avatar"),
                        isHost = p.optBoolean("isHost", false),
                        place = p.optIntOrNull("place"),
                    )
                }
            }
            val gameObj = if (o.isNull("game")) null else o.optJSONObject("game")
            return CardState(
                code = o.optString("code"),
                gameSlug = o.optString("gameSlug"),
                gameTitle = o.optString("gameTitle"),
                mode = o.optString("mode"),
                status = o.optString("status"),
                hostId = o.optInt("hostId"),
                you = o.optInt("you"),
                players = players,
                winners = o.optJSONArray("winners").toIntList(),
                game = gameObj?.let { GameView.parse(it) },
                startedAt = o.optStringOrNull("startedAt"),
                finishedAt = o.optStringOrNull("finishedAt"),
                bestMs = o.optLongOrNull("bestMs"),
            )
        }
    }
}

/* ----------------------------- Game views ----------------------------- */

sealed interface GameView {
    val turnPlayerId: Int
    val yourTurn: Boolean

    companion object {
        fun parse(o: JSONObject): GameView = when (o.optString("kind")) {
            "memory" -> MemoryView.parse(o)
            "discard" -> DiscardView.parse(o)
            "math" -> MathView.parse(o)
            "beatdie" -> BeatDieView.parse(o)
            "showdown" -> ShowdownView.parse(o)
            "matchcolours" -> MatchColoursView.parse(o)
            "maketen" -> MakeTenView.parse(o)
            "wheel" -> WheelView.parse(o)
            "oddone" -> OddOneView.parse(o)
            "sequence" -> SeqView.parse(o)
            else -> UnknownView
        }
    }
}

object UnknownView : GameView {
    override val turnPlayerId = 0
    override val yourTurn = false
}

data class MemoryCardView(
    val id: Int,
    val face: String, // "word" | "emoji"
    val label: String?,
    val matched: Boolean,
    val flipped: Boolean,
    val matchedBy: Int?,
)

data class MemoryView(
    val cards: List<MemoryCardView>,
    override val turnPlayerId: Int,
    override val yourTurn: Boolean,
    val mismatch: Boolean,
    val scores: Map<Int, Int>,
    val flips: Int,
    val pairsTotal: Int,
    val pairsFound: Int,
) : GameView {
    companion object {
        fun parse(o: JSONObject): MemoryView {
            val arr = o.optJSONArray("cards")
            val cards = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                MemoryCardView(
                    id = c.getInt("id"),
                    face = c.optString("face"),
                    label = c.optStringOrNull("label"),
                    matched = c.optBoolean("matched"),
                    flipped = c.optBoolean("flipped"),
                    matchedBy = c.optIntOrNull("matchedBy"),
                )
            }
            return MemoryView(
                cards = cards,
                turnPlayerId = o.optInt("turnPlayerId"),
                yourTurn = o.optBoolean("yourTurn"),
                mismatch = o.optBoolean("mismatch"),
                scores = o.optJSONObject("scores").toIntIntMap(),
                flips = o.optInt("flips"),
                pairsTotal = o.optInt("pairsTotal"),
                pairsFound = o.optInt("pairsFound"),
            )
        }
    }
}

data class DiscardView(
    val piles: List<Int>,
    val yourHand: List<Int>,
    val hands: Map<Int, Int>,
    override val turnPlayerId: Int,
    override val yourTurn: Boolean,
    val finished: List<Int>,
    val canPlay: Boolean,
) : GameView {
    companion object {
        fun parse(o: JSONObject) = DiscardView(
            piles = o.optJSONArray("piles").toIntList(),
            yourHand = o.optJSONArray("yourHand").toIntList(),
            hands = o.optJSONObject("hands").toIntIntMap(),
            turnPlayerId = o.optInt("turnPlayerId"),
            yourTurn = o.optBoolean("yourTurn"),
            finished = o.optJSONArray("finished").toIntList(),
            canPlay = o.optBoolean("canPlay"),
        )
    }
}

data class MathView(
    val target: Int,
    val discardTop: Int,
    val drawCount: Int,
    val yourHand: List<Int>,
    val hands: Map<Int, Int>,
    override val turnPlayerId: Int,
    override val yourTurn: Boolean,
    val finished: List<Int>,
) : GameView {
    companion object {
        fun parse(o: JSONObject) = MathView(
            target = o.optInt("target"),
            discardTop = o.optInt("discardTop"),
            drawCount = o.optInt("drawCount"),
            yourHand = o.optJSONArray("yourHand").toIntList(),
            hands = o.optJSONObject("hands").toIntIntMap(),
            turnPlayerId = o.optInt("turnPlayerId"),
            yourTurn = o.optBoolean("yourTurn"),
            finished = o.optJSONArray("finished").toIntList(),
        )
    }
}

data class BeatDieView(
    val die: Int?,
    val drawCount: Int,
    val yourHand: List<Int>,
    val hands: Map<Int, Int>,
    override val turnPlayerId: Int,
    override val yourTurn: Boolean,
    val finished: List<Int>,
    val canBeat: Boolean,
) : GameView {
    companion object {
        fun parse(o: JSONObject) = BeatDieView(
            die = o.optIntOrNull("die"),
            drawCount = o.optInt("drawCount"),
            yourHand = o.optJSONArray("yourHand").toIntList(),
            hands = o.optJSONObject("hands").toIntIntMap(),
            turnPlayerId = o.optInt("turnPlayerId"),
            yourTurn = o.optBoolean("yourTurn"),
            finished = o.optJSONArray("finished").toIntList(),
            canBeat = o.optBoolean("canBeat"),
        )
    }
}

data class ShowdownResult(
    val draw: Boolean,
    val highIds: List<Int>,
    val lowIds: List<Int>,
    val transferred: List<Int>,
    val eliminatedThisRound: List<Int>,
    val sums: Map<Int, Int>,
)

data class ShowdownView(
    val stars: Map<Int, Int>,
    val eliminated: List<Int>,
    val active: List<Int>,
    val handCounts: Map<Int, Int>,
    val yourHand: List<Int>,
    val youEliminated: Boolean,
    val yourSelection: List<Int>?,
    val committed: Map<Int, Boolean>,
    val waitingOn: Int,
    val lastResult: ShowdownResult?,
    val starsToWin: Int,
    override val turnPlayerId: Int,
    override val yourTurn: Boolean,
) : GameView {
    companion object {
        fun parse(o: JSONObject): ShowdownView {
            val lr = if (o.isNull("lastResult")) null else o.optJSONObject("lastResult")
            val result = lr?.let {
                ShowdownResult(
                    draw = it.optBoolean("draw"),
                    highIds = it.optJSONArray("highIds").toIntList(),
                    lowIds = it.optJSONArray("lowIds").toIntList(),
                    transferred = it.optJSONArray("transferred").toIntList(),
                    eliminatedThisRound = it.optJSONArray("eliminatedThisRound").toIntList(),
                    sums = it.optJSONObject("sums").toIntIntMap(),
                )
            }
            val sel = if (o.isNull("yourSelection")) null else o.optJSONArray("yourSelection").toIntList()
            return ShowdownView(
                stars = o.optJSONObject("stars").toIntIntMap(),
                eliminated = o.optJSONArray("eliminated").toIntList(),
                active = o.optJSONArray("active").toIntList(),
                handCounts = o.optJSONObject("handCounts").toIntIntMap(),
                yourHand = o.optJSONArray("yourHand").toIntList(),
                youEliminated = o.optBoolean("youEliminated"),
                yourSelection = sel,
                committed = o.optJSONObject("committed").toIntBoolMap(),
                waitingOn = o.optInt("waitingOn"),
                lastResult = result,
                starsToWin = o.optInt("starsToWin"),
                turnPlayerId = o.optInt("turnPlayerId"),
                yourTurn = o.optBoolean("yourTurn"),
            )
        }
    }
}

/** A single face-up card on the Make Ten board (stable [id] + its [value]). */
data class MakeTenCardView(val id: Int, val value: Int)

/**
 * Make Ten — a solo number-bonds game. The board is a set of face-up cards; the
 * player clears them two at a time by tapping pairs that sum to 10. [cleared] of
 * [goal] pairs are gone; the board is solvable to empty (every card has a partner).
 *
 * Each pair is one [round] with a shrinking [roundMs] time budget — the board
 * runs the countdown and POSTs a `timeout` move (a loss) if it hits zero.
 */
data class MakeTenView(
    val cards: List<MakeTenCardView>,
    val cleared: Int,
    val goal: Int,
    val round: Int,
    val roundMs: Long,
    override val turnPlayerId: Int,
    override val yourTurn: Boolean,
    val finished: List<Int>,
) : GameView {
    companion object {
        fun parse(o: JSONObject): MakeTenView {
            val arr = o.optJSONArray("cards")
            val cards = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                MakeTenCardView(c.getInt("id"), c.getInt("value"))
            }
            return MakeTenView(
                cards = cards,
                cleared = o.optInt("cleared"),
                goal = o.optInt("goal"),
                round = o.optInt("round"),
                roundMs = o.optLong("roundMs"),
                turnPlayerId = o.optInt("turnPlayerId"),
                yourTurn = o.optBoolean("yourTurn"),
                finished = o.optJSONArray("finished").toIntList(),
            )
        }
    }
}

/** One hand card in Critter Count: how many of each wheel animal it shows. */
data class WheelCardView(val id: Int, val counts: List<Pair<String, Int>>)

/**
 * Animal Count — a solo spot-the-count game. The [wheel] spins to a
 * [targetAnimal]; a [targetCount] (0–2) is called. The player taps the one card
 * in their hand that shows exactly that many of the target animal.
 *
 * Each animal is played over [subroundsTotal] timed sub-rounds ([roundMs]
 * shrinks each one, fresh [cards] each time); clearing all sub-rounds removes
 * the animal from the wheel and advances the [round]. [wrongPicks] are the cards
 * already tapped wrongly this sub-round (one slip allowed). The board owns the
 * spin animation + countdown and POSTs a `timeout` move (a loss) at zero.
 */
data class WheelView(
    val wheel: List<String>,
    val targetAnimal: String,
    val targetCount: Int,
    val cards: List<WheelCardView>,
    val round: Int,
    val roundsTotal: Int,
    val subround: Int,
    val subroundsTotal: Int,
    val roundMs: Long,
    val lastRound: Boolean,
    val wrongPicks: List<Int>,
    override val turnPlayerId: Int,
    override val yourTurn: Boolean,
    val finished: List<Int>,
) : GameView {
    companion object {
        fun parse(o: JSONObject): WheelView {
            val wheel = o.optJSONArray("wheel")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            val arr = o.optJSONArray("cards")
            val cards = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                val co = c.optJSONObject("counts")
                WheelCardView(c.getInt("id"), wheel.map { it to (co?.optInt(it) ?: 0) })
            }
            return WheelView(
                wheel = wheel,
                targetAnimal = o.optString("targetAnimal"),
                targetCount = o.optInt("targetCount"),
                cards = cards,
                round = o.optInt("round"),
                roundsTotal = o.optInt("roundsTotal"),
                subround = o.optInt("subround"),
                subroundsTotal = o.optInt("subroundsTotal"),
                roundMs = o.optLong("roundMs"),
                lastRound = o.optBoolean("lastRound"),
                wrongPicks = o.optJSONArray("wrongPicks").toIntList(),
                turnPlayerId = o.optInt("turnPlayerId"),
                yourTurn = o.optBoolean("yourTurn"),
                finished = o.optJSONArray("finished").toIntList(),
            )
        }
    }
}

/**
 * Odd One Out — a solo spot-the-difference game. Four of the five [cards] share
 * the same animals (in the same order); one differs. The player taps the odd
 * card before the [roundMs] timer runs out. Difficulty climbs each [round]
 * (more animals, subtler differences); each round has [subroundsTotal] timed
 * sub-rounds with fresh cards and a shrinking timer. [wrongPicks] are cards
 * already tapped wrongly this sub-round (one slip allowed). The board owns the
 * countdown and POSTs a `timeout` move (a loss) at zero.
 */
data class OddOneView(
    val cards: List<WheelCardView>,
    val round: Int,
    val roundsTotal: Int,
    val subround: Int,
    val subroundsTotal: Int,
    val roundMs: Long,
    val wrongPicks: List<Int>,
    override val turnPlayerId: Int,
    override val yourTurn: Boolean,
    val finished: List<Int>,
) : GameView {
    companion object {
        fun parse(o: JSONObject): OddOneView {
            val animals = o.optJSONArray("animals")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            val arr = o.optJSONArray("cards")
            val cards = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                val co = c.optJSONObject("counts")
                WheelCardView(c.getInt("id"), animals.map { it to (co?.optInt(it) ?: 0) })
            }
            return OddOneView(
                cards = cards,
                round = o.optInt("round"),
                roundsTotal = o.optInt("roundsTotal"),
                subround = o.optInt("subround"),
                subroundsTotal = o.optInt("subroundsTotal"),
                roundMs = o.optLong("roundMs"),
                wrongPicks = o.optJSONArray("wrongPicks").toIntList(),
                turnPlayerId = o.optInt("turnPlayerId"),
                yourTurn = o.optBoolean("yourTurn"),
                finished = o.optJSONArray("finished").toIntList(),
            )
        }
    }
}

/** One card in Alphabet Lock: its [letter], whether it's [faceUp], and whether
 *  it's the [wrong] card being shown briefly before everything flips back down. */
data class SeqCardView(val id: Int, val letter: String, val faceUp: Boolean, val wrong: Boolean)

/**
 * Alphabet Lock — a solo memory game. Nine consecutive letters hide in a 3×3
 * grid ([cards]); the player flips them in alphabetical [order]. A correct flip
 * stays up ([progress] climbs); a [wrong] flip shows the letter, then a `hide`
 * move flips every card back down (reset to the start). Flip all [total] in
 * order to win. The board owns the brief reveal-then-hide on a wrong flip.
 */
data class SeqView(
    val cards: List<SeqCardView>,
    val order: List<String>,
    val progress: Int,
    val total: Int,
    val wrong: Boolean,
    override val turnPlayerId: Int,
    override val yourTurn: Boolean,
    val finished: List<Int>,
) : GameView {
    companion object {
        fun parse(o: JSONObject): SeqView {
            val arr = o.optJSONArray("cards")
            val cards = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                SeqCardView(c.getInt("id"), c.optString("letter"), c.optBoolean("faceUp"), c.optBoolean("wrong"))
            }
            val order = o.optJSONArray("order")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            return SeqView(
                cards = cards,
                order = order,
                progress = o.optInt("progress"),
                total = o.optInt("total"),
                wrong = o.optBoolean("wrong"),
                turnPlayerId = o.optInt("turnPlayerId"),
                yourTurn = o.optBoolean("yourTurn"),
                finished = o.optJSONArray("finished").toIntList(),
            )
        }
    }
}

data class ColourSpec(val key: String, val label: String, val hex: String, val emoji: String)

data class MatchColoursView(
    val colours: List<ColourSpec>,
    val round: Int,
    val roundsTotal: Int,
    val final: Boolean,
    val pool: List<Int>,
    val mapping: List<Int>,
    val prompt: Int,
    val startAt: Long,
    val revealAt: Long,
    val deadlineAt: Long,
    val resolved: Boolean,
    val correctColour: Int?,
    val scores: Map<Int, Int>,
    val points: Map<Int, Int>?,
    val yourPoints: Int?,
    val answered: Map<Int, Boolean>,
    val yourAnswer: Int?,
    val inRound: Boolean,
    val serverNow: Long,
    val winner: Int?,
    override val turnPlayerId: Int,
    override val yourTurn: Boolean,
) : GameView {
    companion object {
        fun parse(o: JSONObject): MatchColoursView {
            val arr = o.optJSONArray("colours")
            val colours = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                ColourSpec(c.optString("key"), c.optString("label"), c.optString("hex"), c.optString("emoji"))
            }
            val pts = if (o.isNull("points")) null else o.optJSONObject("points").toIntIntMap()
            return MatchColoursView(
                colours = colours,
                round = o.optInt("round"),
                roundsTotal = o.optInt("roundsTotal"),
                final = o.optBoolean("final"),
                pool = o.optJSONArray("pool").toIntList(),
                mapping = o.optJSONArray("mapping").toIntList(),
                prompt = o.optInt("prompt"),
                startAt = o.optLong("startAt"),
                revealAt = o.optLong("revealAt"),
                deadlineAt = o.optLong("deadlineAt"),
                resolved = o.optBoolean("resolved"),
                correctColour = o.optIntOrNull("correctColour"),
                scores = o.optJSONObject("scores").toIntIntMap(),
                points = pts,
                yourPoints = o.optIntOrNull("yourPoints"),
                answered = o.optJSONObject("answered").toIntBoolMap(),
                yourAnswer = o.optIntOrNull("yourAnswer"),
                inRound = o.optBoolean("inRound"),
                serverNow = o.optLong("serverNow"),
                winner = o.optIntOrNull("winner"),
                turnPlayerId = o.optInt("turnPlayerId"),
                yourTurn = o.optBoolean("yourTurn"),
            )
        }
    }
}
