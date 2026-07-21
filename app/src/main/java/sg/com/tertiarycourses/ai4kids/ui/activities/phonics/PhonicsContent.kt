package sg.com.tertiarycourses.ai4kids.ui.activities.phonics

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * Phonics Quest — an offline, on-device phonics adventure for ages 4–6.
 *
 * Ideas adapted (not copied) from the PhonixQuest concept: a map of phonics
 * "worlds", bite-size mini-games, and gamified progression with stars and
 * unlocking. Everything runs locally — sounds are spoken with the device's
 * built-in TextToSpeech, so there's no network, no AI service, no accounts.
 */

/** The mini-game kinds a world can use. */
enum class PhonicsKind { POP, BUILD, RHYME, LISTEN, BLEND, DIGRAPH }

/** "Pop the Phoneme" round: which starting *sound* does this picture make?
 *  [options] and [answer] are phoneme slugs (clips in `res/raw`, played by
 *  [PhonemePlayback]) — keyed by sound, not letter, since the child decides by
 *  ear. [letter] is the grapheme that sound maps to in this word (e.g. Cat →
 *  'C' for the /k/ sound), used only for the Buddy hint. */
data class PopRound(
    val emoji: String,
    val word: String,
    val answer: String,
    val options: List<String>,
    val letter: Char,
)

/** "Build the Word" round: build the word from letter tiles by *sound*. As each
 *  correct letter lands, its phoneme clip plays (blending, not letter names);
 *  [sounds] has one phoneme slug per letter of [word], with "" marking a silent
 *  letter (e.g. the B in LAMB) — those play no sound, which teaches the silence. */
data class BuildRound(val emoji: String, val word: String, val sounds: List<String>) {
    init {
        require(sounds.size == word.length) { "sounds must have one entry per letter in \"$word\"" }
    }
}

/** "Rhyme Time" round: pick the option that rhymes with the target. */
data class RhymeRound(
    val emoji: String,
    val word: String,
    val options: List<Pair<String, String>>, // emoji to word
    val answer: Int,
)

/** "Listen & Find" round: hear the word, then tap the matching word among
 *  similar-sounding choices (no pictures — the child decides by listening). */
data class ListenRound(
    val word: String,
    val options: List<String>, // candidate words; [answer] is the spoken word
    val answer: Int,
)

/** "Sound Blender" round: hear each sound, blend them, then tap the matching
 *  picture — decoded purely by ear (no letters shown). [sounds] are phoneme
 *  slugs to blend; [options] are emoji→word choices with [answer] correct. */
data class BlendRound(
    val word: String,
    val sounds: List<String>,
    val options: List<Pair<String, String>>, // emoji to word
    val answer: Int,
)

/** "Buddy Sounds" round: hear a two-letter (digraph) sound, then pick the letter
 *  team that spells it — the child must map the sound to its spelling, so there's
 *  no read-the-word shortcut. [sound] is the digraph's phoneme slug; [teams] are
 *  candidate letter pairs (e.g. "sh"); [answer] is the correct index; the example
 *  emoji/word reinforce it on a win. */
data class DigraphRound(
    val sound: String,
    val teams: List<String>,
    val answer: Int,
    val exampleEmoji: String,
    val exampleWord: String,
)

/** One world on the adventure map. Only the list matching [kind] is populated. */
data class PhonicsStage(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val color: Color,
    val kind: PhonicsKind,
    val pop: List<PopRound> = emptyList(),
    val build: List<BuildRound> = emptyList(),
    val rhyme: List<RhymeRound> = emptyList(),
    val listen: List<ListenRound> = emptyList(),
    val blend: List<BlendRound> = emptyList(),
    val digraph: List<DigraphRound> = emptyList(),
) {
    val rounds: Int
        get() = when (kind) {
            PhonicsKind.POP -> pop.size
            PhonicsKind.BUILD -> build.size
            PhonicsKind.RHYME -> rhyme.size
            PhonicsKind.LISTEN -> listen.size
            PhonicsKind.BLEND -> blend.size
            PhonicsKind.DIGRAPH -> digraph.size
        }
}

/** The seven worlds of Phonics Quest. */
val PHONICS_STAGES: List<PhonicsStage> = listOf(
    PhonicsStage(
        id = "letters-land",
        title = "Letters Land",
        subtitle = "Starting sounds",
        emoji = "🅰️",
        color = Theme.Pink,
        kind = PhonicsKind.POP,
        // Distractors are phonemes that clearly *sound* different from the
        // answer (a child picks by ear), e.g. Cat's /k/ vs /t/ and /s/ — not the
        // old C/K/T, since C and K are the same /k/ sound.
        pop = listOf(
            PopRound("🍎", "Apple", answer = "v_a_short", options = listOf("v_a_short", "c_b", "c_s"), letter = 'A'),
            PopRound("🐻", "Bear", answer = "c_b", options = listOf("c_b", "c_d", "c_m"), letter = 'B'),
            PopRound("🐱", "Cat", answer = "c_k", options = listOf("c_k", "c_t", "c_s"), letter = 'C'),
            PopRound("🐶", "Dog", answer = "c_d", options = listOf("c_d", "c_b", "c_p"), letter = 'D'),
            PopRound("🥚", "Egg", answer = "v_e_short", options = listOf("v_e_short", "v_a_short", "v_i_short"), letter = 'E'),
            PopRound("🌙", "Moon", answer = "c_m", options = listOf("c_m", "c_n", "c_w"), letter = 'M'),
        ),
    ),
    PhonicsStage(
        id = "blend-bridge",
        title = "Blend Bridge",
        subtitle = "Build short words",
        emoji = "🌉",
        color = Theme.Orange,
        kind = PhonicsKind.BUILD,
        // CVC words: one sound per letter, so tapping a tile sounds it out and a
        // full build blends into the word (/k/-/æ/-/t/ → "cat").
        build = listOf(
            BuildRound("🐱", "CAT", listOf("c_k", "v_a_short", "c_t")),
            BuildRound("🐶", "DOG", listOf("c_d", "v_o_short", "c_g")),
            BuildRound("☀️", "SUN", listOf("c_s", "v_u_short", "c_n")),
            BuildRound("🎩", "HAT", listOf("c_h", "v_a_short", "c_t")),
            BuildRound("🚌", "BUS", listOf("c_b", "v_u_short", "c_s")),
        ),
    ),
    PhonicsStage(
        id = "silent-letters",
        title = "Whisper Woods",
        subtitle = "Silent letters",
        emoji = "🤫",
        color = Theme.Purple,
        kind = PhonicsKind.BUILD,
        // "" marks a silent letter — it plays no sound, so the child hears which
        // letters are silent while building the word.
        build = listOf(
            BuildRound("🐑", "LAMB", listOf("c_l", "v_a_short", "c_m", "")),        // silent B
            BuildRound("🔪", "KNIFE", listOf("", "c_n", "d_ie", "c_f", "")),        // silent K, E
            BuildRound("👻", "GHOST", listOf("c_g", "", "d_oa", "c_s", "c_t")),     // silent H
            BuildRound("🏰", "CASTLE", listOf("c_k", "v_ar", "c_s", "", "c_l", "")),// silent T, E
            BuildRound("✍️", "WRITE", listOf("", "c_r", "d_ie", "c_t", "")),        // silent W, E
        ),
    ),
    PhonicsStage(
        id = "rhyme-road",
        title = "Rhyme Road",
        subtitle = "Words that rhyme",
        emoji = "🎵",
        color = Theme.Green,
        kind = PhonicsKind.RHYME,
        rhyme = listOf(
            RhymeRound("🐱", "Cat", listOf("🎩" to "Hat", "🐶" to "Dog", "☀️" to "Sun"), 0),
            RhymeRound("⭐", "Star", listOf("🚗" to "Car", "🌙" to "Moon", "🐟" to "Fish"), 0),
            RhymeRound("🌳", "Tree", listOf("🐝" to "Bee", "🐱" to "Cat", "☀️" to "Sun"), 0),
            RhymeRound("🐸", "Frog", listOf("🪵" to "Log", "🐱" to "Cat", "⭐" to "Star"), 0),
            RhymeRound("🐌", "Snail", listOf("🐳" to "Whale", "🐶" to "Dog", "🐦" to "Bird"), 0),
        ),
    ),
    PhonicsStage(
        id = "story-kingdom",
        title = "Story Kingdom",
        subtitle = "Listen & find",
        emoji = "👑",
        color = Theme.Blue,
        kind = PhonicsKind.LISTEN,
        listen = listOf(
            ListenRound("Sun", listOf("Sun", "Sock", "Sand"), 0),
            ListenRound("Dog", listOf("Dog", "Dot", "Duck"), 0),
            ListenRound("Tree", listOf("Tree", "Try", "Train"), 0),
            ListenRound("Cat", listOf("Cat", "Cap", "Cot"), 0),
            ListenRound("Bear", listOf("Bear", "Bee", "Boat"), 0),
        ),
    ),
    PhonicsStage(
        id = "sound-blender",
        title = "Sound Blender",
        subtitle = "Blend sounds into words",
        emoji = "🌀",
        color = Theme.Teal,
        kind = PhonicsKind.BLEND,
        // Hear /p/-/i/-/g/, blend it, tap the pig. Pure decoding of CVC words —
        // fresh words (only Dog carries over from Blend Bridge) so the child
        // decodes by ear rather than recalling the earlier build.
        blend = listOf(
            BlendRound("Pig", listOf("c_p", "v_i_short", "c_g"), listOf("🐷" to "Pig", "🐶" to "Dog", "🐔" to "Hen"), 0),
            BlendRound("Hen", listOf("c_h", "v_e_short", "c_n"), listOf("🐔" to "Hen", "🐷" to "Pig", "🐛" to "Bug"), 0),
            BlendRound("Bug", listOf("c_b", "v_u_short", "c_g"), listOf("🐛" to "Bug", "🐷" to "Pig", "🥤" to "Cup"), 0),
            BlendRound("Cup", listOf("c_k", "v_u_short", "c_p"), listOf("🥤" to "Cup", "🐛" to "Bug", "🐶" to "Dog"), 0),
            BlendRound("Dog", listOf("c_d", "v_o_short", "c_g"), listOf("🐶" to "Dog", "🐷" to "Pig", "🐔" to "Hen"), 0),
        ),
    ),
    PhonicsStage(
        id = "sound-buddies",
        title = "Sound Buddies",
        subtitle = "Two letters, one sound",
        emoji = "🤝",
        color = Theme.Purple,
        kind = PhonicsKind.DIGRAPH,
        // Hear a digraph SOUND, pick the two letters that spell it. The child has
        // to map the sound to its spelling, so there's no read-the-word shortcut.
        digraph = listOf(
            DigraphRound("c_sh", listOf("sh", "ch", "th"), 0, "🚢", "Ship"),
            DigraphRound("c_ch", listOf("ch", "sh", "th"), 0, "🍟", "Chip"),
            DigraphRound("c_th_unvoiced", listOf("th", "sh", "ng"), 0, "🛁", "Bath"),
            DigraphRound("c_ng", listOf("ng", "sh", "ch"), 0, "💍", "Ring"),
            DigraphRound("c_sh", listOf("sh", "th", "ch"), 0, "🐟", "Fish"),
        ),
    ),
)

/** Stars from mistakes: 0 → 3 stars, 1–2 → 2 stars, else 1 star. */
fun starsForMistakes(mistakes: Int): Int = when {
    mistakes == 0 -> 3
    mistakes <= 2 -> 2
    else -> 1
}

/**
 * Per-stage progress (best stars 0–3) persisted to SharedPreferences and exposed
 * as Compose state. A stage unlocks once the previous one is cleared (≥1 star).
 *
 * Local-only by design: the web keeps this per learner on the server, but the
 * Android offline core has no accounts and collects nothing.
 */
class PhonicsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ai4kids.phonics", Context.MODE_PRIVATE)
    private var stars by mutableStateOf<Map<String, Int>>(emptyMap())

    init {
        stars = PHONICS_STAGES.associate { it.id to prefs.getInt("stage.${it.id}", 0) }
            .filterValues { it > 0 }
    }

    fun stars(stageId: String): Int = stars[stageId] ?: 0

    val totalStars: Int get() = stars.values.sum()

    /** True if the stage at [index] is playable (first stage, or previous cleared). */
    fun isUnlocked(index: Int): Boolean {
        if (index <= 0) return true
        val prev = PHONICS_STAGES[index - 1]
        return stars(prev.id) >= 1
    }

    /** Record a stage result; returns the star *improvement* (0 if not a new best). */
    fun record(stageId: String, earned: Int): Int {
        val old = stars(stageId)
        if (earned <= old) return 0
        stars = stars.toMutableMap().apply { put(stageId, earned) }
        prefs.edit().putInt("stage.$stageId", earned).apply()
        return earned - old
    }
}
