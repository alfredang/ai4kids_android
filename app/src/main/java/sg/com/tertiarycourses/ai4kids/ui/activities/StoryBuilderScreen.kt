package sg.com.tertiarycourses.ai4kids.ui.activities

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import sg.com.tertiarycourses.ai4kids.ai.ArtEngine
import sg.com.tertiarycourses.ai4kids.ai.GeminiClient
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.ui.activities.buddy.rememberBuddyVoice
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.components.CelebrationView
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.IdeaChips
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * Story Builder — the reader UI. The story itself is woven by [StoryEngine], kept
 * separate so it stays unit-testable; this file only renders it and adapts the
 * optional Gemini call. Mirrors the web app's `/learn/storytelling` page, which
 * splits the same way (`src/lib/story-builder/templates.ts` + `page.tsx`) and is
 * the source of truth for the story's shape and behaviour.
 *
 * Intentional divergences from the web: Android writes with Gemini on-device
 * rather than through `/api/learn/story-builder` (which uses Claude), the star
 * tally stays local, and the web's server-side extras — per-page AI
 * illustrations, read-aloud, and saving to a "My Stories" gallery — are not
 * ported, since Story Builder is part of the offline core.
 */

/** Never trust the model's page counts — cap them, matching the web's sanitizer. */
private const val MAX_PAGES_PER_BRANCH = 4

/** Stars for finishing a story, either way in. */
private const val STORY_STARS = 3

/** Range a story's image seed is drawn from — pinned per story so its hero stays
 *  visually consistent across scenes/pages. Matches the web's `Math.random()*1e6`. */
private val STORY_SEED_RANGE = 0 until 1_000_000

/** A small "read to me" pill — on-device narration of a page/scene. When [active]
 *  (e.g. auto-read on) it takes the accent fill so its state reads at a glance. */
@Composable
private fun ReadAloudPill(label: String, active: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Theme.Green.copy(alpha = 0.9f) else Theme.Orange.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            "🔊 $label",
            color = if (active) Color.White else Theme.Orange,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/** The two ways in. Mirrors the web page's Build / Write split. */
private enum class StoryMode { BUILD, WRITE }

/**
 * Story Builder — one activity, two ways to make a story:
 *  • Build — pick a hero/place/item/mood and read a branching, illustrated tale.
 *  • Write — type your own idea and get a 3-scene story.
 *
 * Mirrors the web page's mode picker.
 */
@Composable
fun StoryBuilderScreen(onClose: () -> Unit) {
    var mode by remember { mutableStateOf<StoryMode?>(null) }
    // Back steps out to the picker first, so a child can switch modes without
    // leaving the activity.
    BackHandler { if (mode != null) mode = null else onClose() }

    when (mode) {
        null -> StoryStartScreen(onPick = { mode = it }, onClose = onClose)
        StoryMode.BUILD -> BuildMode(onBack = { mode = null })
        StoryMode.WRITE -> WriteMode(onBack = { mode = null })
    }
}

@Composable
private fun StoryStartScreen(onPick: (StoryMode) -> Unit, onClose: () -> Unit) {
    val progress = LocalProgressStore.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .align(Alignment.TopCenter)
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text("Story Time", color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                StarBadge(count = progress.stars(Activity.STORY))
            }
            Text("📖", fontSize = 64.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                "How do you want to make your story?",
                color = Theme.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            ModeCard(
                emoji = "🪄",
                title = "Build a story",
                blurb = "Pick a hero, place, and magic item — then choose how the tale ends.",
                color = Theme.Purple,
                onClick = { onPick(StoryMode.BUILD) },
            )
            ModeCard(
                emoji = "✍️",
                title = "Write your own",
                blurb = "Type any idea and watch it turn into a story.",
                color = Theme.Orange,
                onClick = { onPick(StoryMode.WRITE) },
            )
        }
    }
}

@Composable
private fun ModeCard(
    emoji: String,
    title: String,
    blurb: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .kidCard()
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(color.copy(alpha = 0.15f)),
        ) {
            Text(emoji, fontSize = 32.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(title, color = Theme.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(blurb, color = Theme.Ink.copy(alpha = 0.6f), fontSize = 15.sp)
        }
        Text("▶", color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * Ask Gemini for the story's FIRST ACT only, then graft the offline engine's
 * second fork onto each branch — giving four endings at no extra wait (see
 * [withSecondFork]). Returns null on any failure (no key, no network, bad JSON)
 * so the caller falls back to the on-device [buildStory] templates.
 */
private suspend fun generateStoryWithGemini(h: Choice, p: Choice, o: Choice, m: Choice): Story? {
    val prompt = """
        Write the first half of a short, gentle, G-rated adventure story for a child aged 7 to 9.
        Ingredients to use:
        - Hero: ${h.name} ${h.emoji}
        - Place: ${p.name} ${p.emoji}
        - Magic item: ${o.name} ${o.emoji}
        - Tone/mood: ${m.name}

        The child reads three pages, meets a friendly problem, then picks one of two
        ways to solve it. Return ONLY JSON of exactly this shape:
        {
          "pre": ["page", "page", "page"],
          "problem": "a friendly obstacle, ending with the question: What should the ${h.name} do?",
          "choiceA": { "emoji": "${o.emoji}", "label": "Use the ${o.name}", "pages": ["solve it using the ${o.name}"] },
          "choiceB": { "emoji": "🤝", "label": "Call for friends", "pages": ["solve it by asking friends for help"] }
        }
        Rules: each page is 1 to 2 short sentences. Keep it positive, kind, and
        age-appropriate — no violence, scariness, or romance. Weave the emojis into
        the sentences. "pre" must have exactly 3 pages. Do NOT end the story — the
        adventure continues after the pick, so no "The End". Do NOT give the hero a
        personal name; always call it "the ${h.name}", so the rest of the tale matches.
    """.trimIndent()

    val raw = GeminiClient.generateJson(prompt) ?: return null
    // responseMimeType is JSON, but be lenient in case the model adds stray text.
    val jsonText = raw.substring(raw.indexOf('{').coerceAtLeast(0), raw.lastIndexOf('}') + 1)

    return runCatching {
        val root = JSONObject(jsonText)
        fun pages(arr: JSONArray) = List(arr.length()) { arr.getString(it).trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_PAGES_PER_BRANCH)
        fun branch(key: String, fallbackEmoji: String, fallbackLabel: String): Branch {
            val b = root.getJSONObject(key)
            return Branch(
                emoji = b.optString("emoji").ifBlank { fallbackEmoji },
                label = b.optString("label").ifBlank { fallbackLabel },
                pages = pages(b.getJSONArray("pages")),
            )
        }
        val pre = pages(root.getJSONArray("pre"))
        val problem = root.getString("problem").trim()
        val a = branch("choiceA", o.emoji, "Use the ${o.name}")
        val b = branch("choiceB", "🤝", "Call for friends")
        require(pre.isNotEmpty() && problem.isNotEmpty() && a.pages.isNotEmpty() && b.pages.isNotEmpty())
        // Each branch gets its own second fork, so A and B get different twists.
        Story(
            pre = pre,
            problem = problem,
            choiceA = a.withSecondFork(h, p, o, m),
            choiceB = b.withSecondFork(h, p, o, m),
        )
    }.getOrNull()
}

/* ============================ Write your own ============================ */

/** One scene of a free-text story: the prose, the emoji that illustrate it, and a
 *  short visual description used to paint the scene. Mirrors the web's `SceneStory`
 *  scenes; the [emojis] stay as the always-present fallback shown while the picture
 *  loads and if it can't be painted (the web's own graceful-degradation path). */
private data class Scene(val text: String, val emojis: String, val imagePrompt: String)

private data class SceneStory(val title: String, val scenes: List<Scene>)

/** Story ideas offered under the field, from the web page's `IDEAS`. */
private val WRITE_IDEAS = listOf(
    "a dragon who is afraid of fire",
    "a robot learning to paint",
    "a cat astronaut on the moon",
    "a magical treehouse",
)

/** Matches the web route's `z.string().min(1).max(300)`. */
private const val MAX_IDEA_CHARS = 300

/** The web asks for exactly 3 scenes; never trust the model to obey. */
private const val MAX_SCENES = 3

/** The outcome of a write request — mirrors `ArtEngine.Result`, the app's
 *  established shape for a gated AI generation. */
private sealed interface WriteResult {
    data class Success(val story: SceneStory) : WriteResult
    /** The idea was blocked by the safety gate — show [message], keep it kind. */
    data class Blocked(val message: String) : WriteResult
    /** Nothing could write it right now — show [message]. */
    data class Unavailable(val message: String) : WriteResult
}

/**
 * Safety-gate the child's idea, then ask Gemini for a 3-scene story.
 *
 * A Kotlin port of the web's `/api/learn/storytelling` route, with one deliberate
 * difference: the idea goes through [GeminiClient.classifyStoryIdea] first (the
 * web route has no gate). The scenes are illustrated afterwards, on the device,
 * by painting each scene's [Scene.imagePrompt] — see [WriteMode].
 */
private suspend fun writeStoryWithGemini(idea: String): WriteResult {
    val check = GeminiClient.classifyStoryIdea(idea)
    if (check != null && !check.first) {
        return WriteResult.Blocked(
            "Let's try a different idea! How about a dragon who is afraid of fire, or a robot who paints? 🐉",
        )
    }
    // Fail safe if the classifier is unavailable — the prompt below still asks for
    // wholesome output, exactly as ArtEngine does.
    val cleaned = check?.second?.takeIf { it.isNotBlank() } ?: idea

    val prompt = """
        Write a short, wholesome 3-scene illustrated story for kids about: "$cleaned".
        Return ONLY JSON of the form:
        {"title": string, "scenes": [{"text": "2-3 simple sentences", "emojis": "2-4 emojis that illustrate the scene", "imagePrompt": "a short visual description of the scene for an illustrator — characters, setting, colours; no text in the image"}, ... exactly 3 scenes]}
        Keep language simple, positive, and age-appropriate. No scary or unsafe content.
    """.trimIndent()

    val raw = GeminiClient.generateJson(prompt)
        ?: return WriteResult.Unavailable("Our story robot is taking a nap 😴 — please try again in a moment!")
    val jsonText = raw.substring(raw.indexOf('{').coerceAtLeast(0), raw.lastIndexOf('}') + 1)

    val story = runCatching {
        val root = JSONObject(jsonText)
        val arr = root.getJSONArray("scenes")
        val scenes = List(arr.length()) { i ->
            val s = arr.getJSONObject(i)
            val text = s.getString("text").trim()
            Scene(
                text = text,
                emojis = s.optString("emojis").trim().ifEmpty { "✨" },
                // Fall back to the prose as the visual prompt if the model omits it.
                imagePrompt = s.optString("imagePrompt").trim().ifEmpty { text },
            )
        }.filter { it.text.isNotEmpty() }.take(MAX_SCENES)
        require(scenes.isNotEmpty())
        SceneStory(title = root.optString("title").trim().ifEmpty { "My Story" }, scenes = scenes)
    }.getOrNull()
        ?: return WriteResult.Unavailable("Our story robot got its words in a twist — please try again! ✏️")

    return WriteResult.Success(story)
}

/**
 * "Write your own" — the child types any idea and Gemini turns it into a 3-scene
 * story, illustrated with emoji.
 *
 * Privacy: this is the only part of Story Builder that sends a child's **free
 * text** off-device (Build mode sends only ingredient picks), so the idea is
 * safety-gated first and the mode is hidden entirely behind an "ask a grown-up"
 * state when no key is configured. Unlike the web it saves nothing — there is no
 * account and no gallery; the star tally stays local.
 */
@Composable
private fun WriteMode(onBack: () -> Unit) {
    if (!GeminiClient.isConfigured()) {
        WriteNotConfigured(onBack)
        return
    }

    val progress = LocalProgressStore.current
    val scope = rememberCoroutineScope()
    // On-device narration (offline, no key) — reused from the Talking Buddy so the
    // stories read aloud without a server TTS call.
    val voice = rememberBuddyVoice()

    var idea by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var story by remember { mutableStateOf<SceneStory?>(null) }
    var notice by remember { mutableStateOf("") }
    // Award once per story, so re-reading the same tale can't farm stars.
    var scored by remember { mutableStateOf(false) }
    // Painted scene illustrations, keyed by scene index; a null value means that
    // scene couldn't be painted, so its emoji stands in. Populated *before* the
    // story is revealed, so the child sees a complete, illustrated storybook rather
    // than pages filling in one by one.
    var sceneImages by remember { mutableStateOf<Map<Int, Bitmap?>>(emptyMap()) }
    // While painting: (done, total) for the "Painting 2 of 3…" progress; null once
    // the story is revealed or while still writing the text.
    var paintProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Whether an image provider is set — with none, scenes stay emoji (no wait).
    val canIllustrate = remember { ArtEngine.isConfigured() }

    fun write() {
        if (idea.isBlank() || loading) return
        loading = true
        notice = ""
        story = null
        scored = false
        sceneImages = emptyMap()
        paintProgress = null
        scope.launch {
            when (val result = writeStoryWithGemini(idea.trim())) {
                is WriteResult.Success -> {
                    val s = result.story
                    // Paint every scene BEFORE revealing, one at a time (the free
                    // image tier serves a single request at a time). Only then set
                    // `story`, so the whole illustrated tale appears at once. With no
                    // image provider, skip straight to the emoji storybook.
                    if (canIllustrate) {
                        // One seed for the whole story, so its hero looks the same in
                        // every scene rather than redrawn afresh each time.
                        val seed = STORY_SEED_RANGE.random()
                        val imgs = mutableMapOf<Int, Bitmap?>()
                        s.scenes.forEachIndexed { i, scene ->
                            paintProgress = i to s.scenes.size
                            imgs[i] = ArtEngine.paint(scene.imagePrompt, "watercolor", fast = true, seed = seed)
                        }
                        sceneImages = imgs
                    }
                    paintProgress = null
                    story = s
                    if (!scored) {
                        scored = true
                        progress.award(STORY_STARS, Activity.STORY)
                    }
                }
                is WriteResult.Blocked -> notice = result.message
                is WriteResult.Unavailable -> notice = result.message
            }
            loading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .align(Alignment.TopCenter)
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onBack)
                Spacer(Modifier.weight(1f))
                Text("Write your own", color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                StarBadge(count = progress.stars(Activity.STORY))
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().kidCard().padding(18.dp),
            ) {
                Text("What is your story about?", color = Theme.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                OutlinedTextField(
                    value = idea,
                    onValueChange = { idea = it.take(MAX_IDEA_CHARS) },
                    placeholder = { Text("My story is about…") },
                    minLines = 2,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Theme.Orange,
                        unfocusedBorderColor = Theme.Ink.copy(alpha = 0.15f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                IdeaChips(WRITE_IDEAS) { idea = it }
                val paint = paintProgress
                KidButton(
                    title = when {
                        paint != null -> "Painting ${paint.first + 1} of ${paint.second}… 🎨"
                        loading -> "Writing your story… ✨"
                        else -> "Make my story! 🪄"
                    },
                    icon = Icons.Filled.AutoFixHigh,
                    color = Theme.Orange,
                    enabled = idea.isNotBlank() && !loading,
                    onClick = { write() },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (notice.isNotEmpty()) {
                    Text(notice, color = Theme.Orange, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            story?.let { s ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().kidCard().padding(18.dp),
                ) {
                    if (scored) {
                        Text(
                            "+$STORY_STARS ⭐ earned! 🎉",
                            color = Theme.Green, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Theme.Green.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Text(s.title, color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    s.scenes.forEachIndexed { i, scene ->
                        // Every scene's picture is already painted by the time the
                        // story is revealed; a null means it couldn't be painted, so
                        // the emoji stands in.
                        val painted = sceneImages[i]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Theme.Orange.copy(alpha = 0.08f))
                                .padding(16.dp),
                        ) {
                            if (painted != null) {
                                Image(
                                    bitmap = painted.asImageBitmap(),
                                    contentDescription = scene.text,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                                )
                            } else {
                                Text(scene.emojis, fontSize = 44.sp)
                            }
                            Text(
                                scene.text,
                                color = Theme.Ink,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            ReadAloudPill("Read to me") { voice.speak(scene.text) }
                        }
                    }
                    KidButton(
                        title = "Write another! ✏️",
                        color = Theme.Purple,
                        onClick = { story = null; idea = ""; scored = false; sceneImages = emptyMap() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Write mode is pure AI — with no key there's nothing to fall back to, so it asks
 *  for a grown-up rather than faking a story. Build mode stays fully playable. */
@Composable
private fun WriteNotConfigured(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background)
            .padding(28.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().kidCard().padding(28.dp),
        ) {
            Text("✍️", fontSize = 72.sp)
            Text(
                "Writing your own needs a grown-up",
                color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            )
            Text(
                "Ask a grown-up to add an AI key — or go build a story instead!",
                color = Theme.Ink.copy(alpha = 0.6f), fontSize = 17.sp, textAlign = TextAlign.Center,
            )
            KidButton(title = "Okay", color = Theme.Orange, onClick = onBack)
        }
    }
}

/**
 * "Build a story" — the child picks a hero, place, magic item, and mood; the app
 * weaves a short, twice-branching story and reads it back as tappable pages.
 * When a Gemini key is configured it writes a fresh tale online (with the
 * on-device templates as the fallback); with no key it runs fully offline.
 *
 * Privacy: the story itself is woven from only the four *picks* — never free text
 * — which is why it needs no safety gate and works with no key at all. When an
 * image key IS configured, each page is *illustrated*, which sends that page's
 * prose (template- or Gemini-written, still never the child's own words) to the
 * NVIDIA/Cloudflare image providers; with no key the pages stay emoji-only and
 * the mode remains fully offline. Contrast [WriteMode], which sends the child's
 * typed idea.
 */
@Composable
private fun BuildMode(onBack: () -> Unit) {
    val progress = LocalProgressStore.current
    val scope = rememberCoroutineScope()
    // On-device narration (offline, no key), reused from the Talking Buddy.
    val voice = rememberBuddyVoice()
    var autoRead by remember { mutableStateOf(false) }

    var hero by remember { mutableStateOf<Choice?>(null) }
    var place by remember { mutableStateOf<Choice?>(null) }
    var obj by remember { mutableStateOf<Choice?>(null) }
    var mood by remember { mutableStateOf<Choice?>(null) }
    var story by remember { mutableStateOf<Story?>(null) }
    var pageIndex by remember { mutableStateOf(0) }
    // The branches picked so far — one per resolved fork. Replaces a `picked`
    // boolean, which couldn't express a path once the story forks twice.
    var chosen by remember { mutableStateOf<List<Branch>>(emptyList()) }
    var generating by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    // Award the finish once, even if the child replays to see the other ending.
    var scored by remember { mutableStateOf(false) }
    var awarded by remember { mutableStateOf(false) }
    // Page illustrations, cached by page *text* (not index) so a page reused across
    // a fork or a rewind is painted once. Only populated when an image provider is
    // configured; otherwise pages stay emoji-only and nothing leaves the device.
    var pageImages by remember { mutableStateOf<Map<String, Bitmap?>>(emptyMap()) }
    val illustrate = remember { ArtEngine.isConfigured() }
    // Pinned once per story so the hero looks the same on every page.
    var storySeed by remember { mutableStateOf(0) }

    val ready = hero != null && place != null && obj != null && mood != null

    val timeline = remember(story, chosen) { story?.let { buildTimeline(it, chosen) } }
    val pages = timeline?.pages ?: emptyList()
    // The node whose fork is unanswered: the root until a pick, then the last branch.
    val pendingNode: ForkNode? = story?.let { if (chosen.isEmpty()) it else chosen.last() }
    val optionA = pendingNode?.choiceA
    val optionB = pendingNode?.choiceB
    val atChoice = timeline != null &&
        pageIndex == timeline.forks.getOrNull(chosen.size) &&
        optionA != null && optionB != null
    val pageCount = if (timeline == null) 0 else pages.size + remainingFrom(if (atChoice) pendingNode else null)

    fun reset() {
        showCelebration = false
        story = null
        chosen = emptyList()
        hero = null; place = null; obj = null; mood = null
        pageIndex = 0
        scored = false
        pageImages = emptyMap()
    }

    fun showStory(s: Story) {
        story = s
        chosen = emptyList()
        pageIndex = 0
        scored = false
        pageImages = emptyMap()
        storySeed = STORY_SEED_RANGE.random()
    }

    // Build the tale from the picks. When a Gemini key is configured we ask it for
    // a freshly-written story (showing a brief "writing…" state); otherwise — or on
    // any failure — we fall back to the on-device templates instantly.
    fun makeStory() {
        val h = hero!!; val p = place!!; val o = obj!!; val m = mood!!
        if (!GeminiClient.isConfigured()) {
            showStory(buildStory(h, p, o, m))
            return
        }
        generating = true
        scope.launch {
            val s = generateStoryWithGemini(h, p, o, m) ?: buildStory(h, p, o, m)
            generating = false
            showStory(s)
        }
    }

    // "Surprise me" — roll a random pick for every row and build straight away.
    fun surprise() {
        hero = HEROES.random(); place = PLACES.random()
        obj = OBJECTS.random(); mood = MOODS.random()
        makeStory()
    }

    fun choose(useA: Boolean) {
        val branch = (if (useA) optionA else optionB) ?: return
        chosen = chosen + branch // pages re-derive; the next page is the branch's first
        pageIndex += 1
    }

    fun nextPage() {
        if (pageIndex < pages.size - 1) {
            pageIndex += 1
        } else {
            voice.stop() // don't read on over the celebration
            val first = !scored
            if (first) {
                scored = true
                progress.award(3, Activity.STORY)
            }
            awarded = first
            showCelebration = true
        }
    }

    // Rewind all the way to the FIRST fork so the child can retake every decision
    // — from there they can reach any of the four endings. (Dropping just the last
    // choice only ever flipped the second fork.) The story is already built, so
    // it's instant.
    fun replayFork() {
        val s = story ?: return
        if (chosen.isEmpty()) return
        showCelebration = false
        chosen = emptyList()
        pageIndex = buildTimeline(s, emptyList()).forks[0]
    }

    // Illustrate the page being read, then prefetch what comes next — one request at
    // a time (the free image tier serves one), so a page is ready when the child taps
    // through. Cached by text, so a rewound or re-forked page is instant. The hero
    // anchor + a pinned seed keep it the same character page to page. At a fork we
    // prefetch BOTH branches' first pages, so either choice lands on a ready picture.
    LaunchedEffect(pageIndex, pages, illustrate) {
        if (!illustrate || pages.isEmpty()) return@LaunchedEffect
        val anchor = "a ${mood!!.name} ${hero!!.name} ${hero!!.emoji}"
        suspend fun ensure(text: String?) {
            if (text == null || text in pageImages) return
            pageImages = pageImages + (text to ArtEngine.paint(text, "watercolor", fast = true, seed = storySeed, characterAnchor = anchor))
        }
        ensure(pages.getOrNull(pageIndex))
        if (atChoice) {
            ensure(optionA?.pages?.firstOrNull())
            ensure(optionB?.pages?.firstOrNull())
        } else {
            ensure(pages.getOrNull(pageIndex + 1))
        }
    }

    // Auto-narrate each page as the child arrives on it (once its picture is ready,
    // so the reading matches what's on screen), while "Auto-read" is on.
    val currentReady = pages.getOrNull(pageIndex)?.let { !illustrate || it in pageImages } ?: false
    LaunchedEffect(pageIndex, autoRead, currentReady, showCelebration) {
        if (autoRead && !showCelebration && currentReady) {
            pages.getOrNull(pageIndex)?.let { voice.speak(it) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .align(Alignment.TopCenter)
                .padding(28.dp),
        ) {
            // Top bar.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onBack)
                Spacer(Modifier.weight(1f))
                Text("Build a story", color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                StarBadge(count = progress.stars(Activity.STORY))
            }

            when {
                generating -> WritingStage(modifier = Modifier.weight(1f))
                pages.isEmpty() -> PickerStage(
                    modifier = Modifier.weight(1f),
                    hero = hero, place = place, obj = obj, mood = mood, ready = ready,
                    onHero = { hero = it }, onPlace = { place = it }, onObject = { obj = it }, onMood = { mood = it },
                    onBuild = { if (ready) makeStory() },
                    onSurprise = { surprise() },
                )
                else -> ReaderStage(
                    modifier = Modifier.weight(1f),
                    hero = hero!!, place = place!!, obj = obj!!,
                    page = pages[pageIndex], pageIndex = pageIndex, pageCount = pageCount,
                    isLastPage = pageIndex == pages.size - 1,
                    image = pageImages[pages[pageIndex]],
                    imageLoading = illustrate && pages[pageIndex] !in pageImages,
                    autoRead = autoRead,
                    onReadAloud = { voice.speak(pages[pageIndex]) },
                    onToggleAutoRead = {
                        autoRead = !autoRead
                        if (autoRead) voice.speak(pages[pageIndex]) else voice.stop()
                    },
                    choice = if (atChoice) optionA!! to optionB!! else null,
                    onChoose = { choose(it) },
                    onNext = { nextPage() },
                )
            }
        }

        if (showCelebration) {
            CelebrationView(
                message = if (awarded) "What a story! ⭐️⭐️⭐️" else "Another great ending! 🌈",
                onDismiss = { reset() },
            ) {
                // The tale forks, so there's always another ending to find.
                if (chosen.isNotEmpty()) {
                    KidButton(
                        title = "🔀  Try the other path",
                        color = Theme.Teal,
                        onClick = { replayFork() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                KidButton(
                    title = "🔁  Build another",
                    color = Theme.Orange,
                    onClick = { reset() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WritingStage(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("✨📖✨", fontSize = 56.sp)
        Text(
            "Writing your story…",
            color = Theme.Ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
        )
        CircularProgressIndicator(color = Theme.Orange)
    }
}

@Composable
private fun PickerStage(
    modifier: Modifier = Modifier,
    hero: Choice?, place: Choice?, obj: Choice?, mood: Choice?, ready: Boolean,
    onHero: (Choice) -> Unit, onPlace: (Choice) -> Unit, onObject: (Choice) -> Unit, onMood: (Choice) -> Unit,
    onBuild: () -> Unit,
    onSurprise: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        ChoiceRow("Pick your hero", HEROES, hero, onHero)
        ChoiceRow("Pick a place", PLACES, place, onPlace)
        ChoiceRow("Pick a magic item", OBJECTS, obj, onObject)
        ChoiceRow("Pick a mood", MOODS, mood, onMood)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            KidButton(
                title = "Make my story!",
                icon = Icons.Filled.AutoFixHigh,
                color = if (ready) Theme.Orange else Theme.Ink.copy(alpha = 0.25f),
                enabled = ready,
                onClick = onBuild,
                modifier = Modifier.fillMaxWidth(),
            )
            KidButton(
                title = "Surprise me!",
                icon = Icons.Filled.Casino,
                color = Theme.Purple,
                onClick = onSurprise,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Tiles per row in the picker. Eight choices side by side would be too cramped
 *  to tap, so they wrap onto a grid — chunked by hand because a LazyVerticalGrid
 *  can't measure inside the picker's vertical scroll. */
private const val PICKER_COLUMNS = 4

@Composable
private fun ChoiceRow(
    title: String,
    items: List<Choice>,
    selection: Choice?,
    onSelect: (Choice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
        items.chunked(PICKER_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    ChoiceTile(item, isOn = selection == item, onSelect = { onSelect(item) }, modifier = Modifier.weight(1f))
                }
                // Keep a short last row's tiles the same width as a full row's.
                repeat(PICKER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChoiceTile(
    item: Choice,
    isOn: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(110.dp)
            .softShadow(shape)
            .clip(shape)
            .background(if (isOn) Theme.Orange.copy(alpha = 0.22f) else Color.White)
            .border(width = if (isOn) 4.dp else 0.dp, color = if (isOn) Theme.Orange else Color.Transparent, shape = shape)
            .clickable(onClick = onSelect)
            .padding(4.dp),
    ) {
        Text(item.emoji, fontSize = 44.sp)
        Text(
            item.name,
            color = Theme.Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReaderStage(
    hero: Choice, place: Choice, obj: Choice,
    page: String, pageIndex: Int, pageCount: Int,
    isLastPage: Boolean,
    /** The painted illustration for this page, or null (emoji header stands in). */
    image: Bitmap?,
    /** True while this page's picture is still being painted. */
    imageLoading: Boolean,
    autoRead: Boolean,
    onReadAloud: () -> Unit,
    onToggleAutoRead: () -> Unit,
    /** When non-null, this page is a fork: offer the two branches instead of Next. */
    choice: Pair<Branch, Branch>?,
    onChoose: (Boolean) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hold the whole page until its picture is ready, so the child reads a fully
    // illustrated page rather than watching it fill in. Prefetching the next page
    // means this usually only waits on the very first page.
    if (imageLoading) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            modifier = modifier.fillMaxWidth(),
        ) {
            Text("${hero.emoji}${place.emoji}${obj.emoji}", fontSize = 64.sp)
            CircularProgressIndicator(color = Theme.Orange)
            Text(
                "Painting your page… 🎨",
                color = Theme.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black,
            )
        }
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = page,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(22.dp)),
            )
        } else {
            // Emoji header stands in when no image provider is set (or paint failed).
            Text("${hero.emoji}${place.emoji}${obj.emoji}", fontSize = 72.sp)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .kidCard()
                .padding(28.dp),
        ) {
            Text(
                page,
                color = Theme.Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReadAloudPill("Read to me", onClick = onReadAloud)
            ReadAloudPill(if (autoRead) "Auto-read on" else "Auto-read", active = autoRead, onClick = onToggleAutoRead)
        }
        if (choice != null) {
            val (left, right) = choice
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                KidButton(
                    title = "${left.emoji}  ${left.label}",
                    color = Theme.Purple,
                    onClick = { onChoose(true) },
                    modifier = Modifier.fillMaxWidth(),
                )
                KidButton(
                    title = "${right.emoji}  ${right.label}",
                    color = Theme.Teal,
                    onClick = { onChoose(false) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Page ${pageIndex + 1} of $pageCount",
                    color = Theme.Ink.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                KidButton(
                    title = if (isLastPage) "The End!" else "Next",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    color = Theme.Orange,
                    onClick = onNext,
                )
            }
        }
    }
}
