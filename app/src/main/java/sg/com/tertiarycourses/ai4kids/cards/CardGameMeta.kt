package sg.com.tertiarycourses.ai4kids.cards

import androidx.compose.ui.graphics.Color
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/** Multiplayer modes a card game supports. Mirrors the web `CardGameMode`. */
enum class CardMode(val slug: String, val label: String) {
    SOLO("solo", "Solo"),
    COOP("coop", "Co-op"),
    VERSUS("versus", "Versus");

    companion object {
        fun fromSlug(s: String): CardMode = entries.first { it.slug == s }
    }
}

/**
 * Catalogue metadata for the six "Brain Arcade" card games — a Kotlin port of
 * the web `meta.ts`. Drives the hub list and the per-game lobby copy. No game
 * logic lives here (the backend is authoritative); this only labels things.
 */
data class CardGameMeta(
    val slug: String,
    val activitySlug: String,
    val title: String,
    val emoji: String,
    val tagline: String,
    val blurb: String,
    val accent: Color,
    val modes: List<CardMode>,
    val minPlayers: Int,
    val maxPlayers: Int,
    val how: List<String>,
)

val CARD_GAMES: List<CardGameMeta> = listOf(
    CardGameMeta(
        slug = "memory-match",
        activitySlug = "cards-memory-match",
        title = "Memory Match",
        emoji = "🧠",
        tagline = "Flip and find the pairs",
        blurb = "Flip the cards two at a time and match each word with its picture. Play solo, team up, or race a friend.",
        accent = Theme.Purple,
        modes = listOf(CardMode.SOLO, CardMode.COOP, CardMode.VERSUS),
        minPlayers = 2,
        maxPlayers = 4,
        how = listOf(
            "On your turn, flip two cards.",
            "Match a word with its matching picture (like “plant” and 🌱).",
            "A match lets you go again; a miss passes the turn.",
            "Co-op: clear the whole board as a team. Versus: most pairs wins!",
        ),
    ),
    CardGameMeta(
        slug = "tower-tumble",
        activitySlug = "cards-tower-tumble",
        title = "Tower Tumble",
        emoji = "🃏",
        tagline = "Climb the piles, empty your hand",
        blurb = "Stack cards higher and higher on four piles. Play a 10 to topple a tower! First to run out of cards wins.",
        accent = Theme.Pink,
        modes = listOf(CardMode.SOLO, CardMode.VERSUS),
        minPlayers = 2,
        maxPlayers = 4,
        how = listOf(
            "Place a card HIGHER than the top of any pile.",
            "Play a 10 to clear a pile — then anyone can start it fresh.",
            "Stuck with no move? You pass.",
            "First player to empty their hand wins. Solo: beat the clock!",
        ),
    ),
    CardGameMeta(
        slug = "number-hunt",
        activitySlug = "cards-number-hunt",
        title = "Number Hunt",
        emoji = "🔢",
        tagline = "Make the target number",
        blurb = "Hunt for cards that hit the target — one card that equals it, or two that add or subtract to it. Empty your hand to win!",
        accent = Theme.Blue,
        modes = listOf(CardMode.SOLO, CardMode.VERSUS),
        minPlayers = 2,
        maxPlayers = 4,
        how = listOf(
            "Discard ONE card that equals the target number.",
            "Or discard TWO cards that add up to — or subtract to — the target.",
            "Can't discard? Draw a card and pass.",
            "First to empty their hand wins. Solo: race the clock!",
        ),
    ),
    CardGameMeta(
        slug = "beat-the-die",
        activitySlug = "cards-beat-the-die",
        title = "Beat the Die",
        emoji = "🎲",
        tagline = "Roll, then beat it",
        blurb = "Roll the dice, then throw down one or two cards that add up to at least the roll. Can't beat it? Draw. First to empty their hand wins!",
        accent = Theme.Green,
        modes = listOf(CardMode.SOLO, CardMode.VERSUS),
        minPlayers = 2,
        maxPlayers = 4,
        how = listOf(
            "Roll the 6-sided die at the start of your turn.",
            "Discard ONE or TWO cards that add up to at least the roll.",
            "Can't beat the die? Draw a card instead.",
            "First to empty their hand wins. Solo: race the clock!",
        ),
    ),
    CardGameMeta(
        slug = "card-showdown",
        activitySlug = "cards-card-showdown",
        title = "Card Showdown",
        emoji = "⭐",
        tagline = "Clash for victory stars",
        blurb = "Everyone plays cards in secret, then reveals at once. Highest total wins a star — but hands those cards to the lowest player! First to 3 stars, or last standing, wins.",
        accent = Theme.Orange,
        modes = listOf(CardMode.VERSUS),
        minPlayers = 3,
        maxPlayers = 4,
        how = listOf(
            "Everyone secretly plays ONE or TWO cards at the same time.",
            "Highest total wins a ⭐ — and gives those cards to the lowest player, who discards what they played. Everyone else keeps theirs.",
            "Tie for lowest? They ALL collect the winning cards. Tie for highest? They each win a ⭐, and the lowest collects every card they played.",
            "Run out of cards and you're out. First to 3 ⭐ — or last standing — wins!",
        ),
    ),
    CardGameMeta(
        slug = "matching-colours",
        activitySlug = "cards-matching-colours",
        title = "Matching Colours",
        emoji = "🌈",
        tagline = "Quick! Tap the right colour",
        blurb = "Each round the four colours get new numbers. A number is called — race to tap the matching colour the fastest! Most points after 10 rounds wins.",
        accent = Theme.Teal,
        modes = listOf(CardMode.VERSUS),
        minPlayers = 2,
        maxPlayers = 4,
        how = listOf(
            "You hold four colour cards: 🟥 🟦 🟩 🟨.",
            "Each round the colours are tied to the numbers 1–4 — memorise them in the countdown!",
            "A number is called. Tap the colour it matches within 5 seconds.",
            "Fastest correct tap scores most. Most points after 10 rounds wins!",
        ),
    ),
)

fun cardGameBySlug(slug: String): CardGameMeta? = CARD_GAMES.firstOrNull { it.slug == slug }
