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
        slug = "make-ten",
        activitySlug = "cards-make-ten",
        title = "Make Ten",
        emoji = "🔟",
        tagline = "Pair up cards that add to 10",
        blurb = "Tap two cards that add up to exactly 10 to clear them. Sweep the whole board to win — how fast can you do it?",
        accent = Theme.Teal,
        modes = listOf(CardMode.SOLO),
        minPlayers = 1,
        maxPlayers = 1,
        how = listOf(
            "Look for two cards that add up to 10 (like 6 and 4).",
            "Tap the first card, then its partner — they clear away.",
            "Every card has a partner, so there's always a move.",
            "Clear the whole board to win. Solo: race the clock!",
        ),
    ),
    CardGameMeta(
        slug = "animal-count",
        activitySlug = "cards-animal-count",
        title = "Animal Count",
        emoji = "🐾",
        tagline = "Spot the right animal count",
        blurb = "An animal lights up and a number is called. Race the timer to tap the card with exactly that many — before the clock runs out!",
        accent = Theme.Orange,
        modes = listOf(CardMode.SOLO),
        minPlayers = 1,
        maxPlayers = 1,
        how = listOf(
            "An animal lights up, and a number (0–2) is called.",
            "Tap the card with exactly that many of that animal.",
            "Three speed-rounds per animal — fresh cards, less time each.",
            "One slip is okay; a second wrong tap or the timer ends the game. Clear all 5 animals to win!",
        ),
    ),
    CardGameMeta(
        slug = "odd-one-out",
        activitySlug = "cards-odd-one-out",
        title = "Odd One Out",
        emoji = "🔍",
        tagline = "Spot the card that's different",
        blurb = "Four cards match — one doesn't. Find the odd card out before the timer runs out!",
        accent = Theme.Purple,
        modes = listOf(CardMode.SOLO),
        minPlayers = 1,
        maxPlayers = 1,
        how = listOf(
            "Four cards show the same animals — one is different.",
            "Tap the card that doesn't match the others.",
            "Three speed-rounds each get trickier, with less time.",
            "One slip is okay; a second wrong tap or the timer ends the game. Clear all the rounds to win!",
        ),
    ),
    CardGameMeta(
        slug = "alphabet-lock",
        activitySlug = "cards-alphabet-lock",
        title = "Alphabet Lock",
        emoji = "🔤",
        tagline = "Flip the letters in ABC order",
        blurb = "Nine letters hide in a grid. Flip them in alphabetical order — but one wrong flip turns them all back over! Remember where they are to crack the lock.",
        accent = Theme.Blue,
        modes = listOf(CardMode.SOLO),
        minPlayers = 1,
        maxPlayers = 1,
        how = listOf(
            "Nine letters hide in the grid, face down.",
            "Flip them in ABC order — the smallest letter first.",
            "Flip a wrong letter and they all turn back over!",
            "Remember the spots and flip all nine in order to win.",
        ),
    ),
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
