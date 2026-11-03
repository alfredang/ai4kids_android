package sg.com.tertiarycourses.ai4kids.ui.workshops

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import sg.com.tertiarycourses.ai4kids.R
import sg.com.tertiarycourses.ai4kids.ai.KidSafety
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/** A row of tappable choices shown before the first message. When
 *  [customLabel] is set, an extra chip lets the child type their own choice. */
data class ChipGroup(val label: String, val options: List<String>, val customLabel: String? = null)

/**
 * The four **AI Workshops** (mirroring the website's workshops), powered by
 * DeepSeek and unlocked by the parent login. Each defines its art, the
 * quick-start chips that let younger kids tap instead of type, how those picks
 * become the first message, follow-up chips, and its part of the system prompt.
 * Mirrors the iOS `Workshop` enum.
 */
enum class Workshop(
    val id: String,
    val title: String,
    val tagline: String,
    val ages: String,
    @DrawableRes val art: Int,
    val color: Color,
    /** Choices offered before the first message (all must be picked). */
    val setup: List<ChipGroup>,
    /** Builds the first message from the picks (one per [setup] group). */
    val firstMessage: (List<String>) -> String,
    /** Quick replies once the conversation has started. */
    val followUps: List<String>,
    /** Workshop-specific instructions appended to [KidSafety.SYSTEM_PREFIX]. */
    private val prompt: String,
) {
    STORY(
        id = "story",
        title = "Storybook Studio",
        tagline = "Dream up a Merlion adventure. Make it your own.",
        ages = "Ages 7–12",
        art = R.drawable.friend_story,
        color = Theme.Orange,
        setup = listOf(
            ChipGroup("Pick a hero", listOf("a tiny Merlion", "a brave little otter", "a curious robot"), customLabel = "My own hero"),
            ChipGroup("Pick a place", listOf("Gardens by the Bay", "a hawker centre", "Sentosa beach", "outer space")),
            ChipGroup("Pick a twist", listOf("a lost map", "a sudden rainstorm", "a talking durian", "a magic kite")),
        ),
        firstMessage = { (hero, place, twist) ->
            "My hero is $hero. The story happens at $place. The twist is $twist. Please give me 3 story sparks."
        },
        followUps = listOf("A", "B", "C", "What happens next?", "Make it funnier"),
        prompt = "Workshop: Storybook Studio. The child picks a hero, a place and a twist. " +
            "First, offer exactly 3 one-line story sparks labelled A, B and C, and ask them to pick one. " +
            "When they pick, write a 5-part story of about 150 words with a short title, then ask one " +
            "\"What happens next?\" question. If they answer, continue the story in the same gentle style. " +
            "If the child types their own hero, use it if it is kind and safe.",
    ),
    MAKER(
        id = "maker",
        title = "Mini Maker Studio",
        tagline = "Make a poster and a mini comic.",
        ages = "Ages 7–12",
        art = R.drawable.friend_art,
        color = Theme.Pink,
        setup = listOf(
            ChipGroup("Pick a topic", listOf("Save water", "Be kind", "Recycle more", "Eat healthy", "Keep our parks clean")),
        ),
        firstMessage = { (topic) -> "My poster topic is: $topic. Please make my poster plan." },
        followUps = listOf("Now make the mini comic", "A different slogan", "New topic idea"),
        prompt = "Workshop: Mini Maker Studio. The child gives a topic. Reply with a poster plan using " +
            "these labels: TITLE (6 words or fewer), SLOGAN, PICTURE IDEAS (3 simple things to draw), " +
            "COLOURS (suggested colours). Then write a 4-panel mini comic script labelled Panel 1 to " +
            "Panel 4, each with a picture description and one short speech bubble. Keep it positive " +
            "and easy to draw.",
    ),
    MATHS(
        id = "maths",
        title = "Maths Detectives",
        tagline = "Solve number mysteries, one clue at a time.",
        ages = "Ages 8–12",
        art = R.drawable.friend_maths,
        color = Theme.Blue,
        setup = listOf(
            ChipGroup("Pick your level", listOf("P1–P2", "P3–P4", "P5–P6")),
        ),
        firstMessage = { (level) -> "I'm in $level. Please give me a maths mystery!" },
        followUps = listOf("Give me a hint", "Show me a bar model", "New case"),
        prompt = "Workshop: Maths Detectives. Present a short Singapore-themed maths mystery (for example " +
            "at a hawker centre, MRT station or school fair) suited to the child's level (P1–P2, P3–P4 or " +
            "P5–P6, following the Singapore primary maths syllabus), with clues. Never give the answer " +
            "outright. When the child answers, check it kindly. If wrong, give one hint at a time. Suggest " +
            "a bar model when helpful, drawn simply with text. When solved, celebrate and offer a new case.",
    ),
    SCIENCE(
        id = "science",
        title = "Science Explorers",
        tagline = "Ask big \"why?\" questions. Try tiny experiments.",
        ages = "Ages 8–12",
        art = R.drawable.friend_science,
        color = Theme.Teal,
        setup = listOf(
            ChipGroup(
                "Pick a question",
                listOf("Why is the sky blue?", "How do plants drink?", "Why do leaves change colour?", "Why does ice float?"),
            ),
        ),
        firstMessage = { (question) -> question },
        followUps = listOf("Tell me more", "Another experiment", "Why does that happen?"),
        prompt = "Workshop: Science Explorers. The child asks a why/how question. Explain in 100 words or " +
            "fewer using an everyday analogy. Then give one safe at-home mini experiment using common " +
            "household items, starting with \"Ask a grown-up to help:\". Never suggest heat, fire, sharp " +
            "tools, chemicals or electricity. End with one follow-up question to keep them curious.",
    );

    /** Full system prompt: the shared safety prefix plus this workshop's part. */
    val systemPrompt: String get() = KidSafety.SYSTEM_PREFIX + "\n\n" + prompt
}
