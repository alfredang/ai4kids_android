package sg.com.tertiarycourses.ai4kids.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * One of the learning activities offered on the home screen. The activities are
 * offline-first and need no login to play; the [ESCAPE] room additionally has an
 * optional online co-op mode (see `escape/`) that requires a learner sign-in.
 * This is the Android port of the iOS `Activity` enum.
 */
enum class Activity(
    val id: String,
    /** Display title shown on the home card. */
    val title: String,
    /** One-line, kid-readable description. */
    val subtitle: String,
    /** Card accent color. */
    val color: Color,
    /** Recommended age band (shown as a small tag). */
    val ageBand: String,
    /** Material icon shown on the card. */
    val icon: ImageVector,
) {
    PHONICS(
        id = "phonics",
        title = "Phonics Quest",
        subtitle = "Explore the sound worlds",
        color = Theme.Pink,
        ageBand = "Ages 4–6",
        icon = Icons.Filled.TextFields,
    ),
    STORY(
        id = "story",
        title = "Story Builder",
        subtitle = "Make your own story",
        color = Theme.Orange,
        ageBand = "Ages 7–9",
        icon = Icons.Filled.AutoStories,
    ),
    CODE(
        id = "code",
        title = "Code Puzzles",
        subtitle = "Solve coding puzzles",
        color = Theme.Blue,
        ageBand = "Ages 7–12",
        icon = Icons.Filled.Extension,
    ),
    ESCAPE(
        id = "escape",
        title = "Escape Room",
        subtitle = "Walk, solve & escape!",
        color = Theme.Teal,
        ageBand = "Ages 7–12",
        icon = Icons.Filled.MeetingRoom,
    ),

    // The two AI activities need a Gemini (and optional Cloudflare) key — they
    // degrade to a friendly "ask a grown-up" state when none is configured.
    BUDDY(
        id = "buddy",
        title = "Talking Buddy",
        subtitle = "Chat with your AI pal",
        color = Theme.Blue,
        ageBand = "Ages 5–10",
        icon = Icons.Filled.Face,
    ),
    ART(
        id = "art",
        title = "Art Studio",
        subtitle = "Paint with AI, then puzzle it",
        color = Theme.Orange,
        ageBand = "Ages 5–12",
        icon = Icons.Filled.Brush,
    );

    companion object {
        fun fromId(raw: String?): Activity? = entries.firstOrNull { it.id == raw }
    }
}
