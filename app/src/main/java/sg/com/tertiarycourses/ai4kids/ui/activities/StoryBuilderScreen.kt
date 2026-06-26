package sg.com.tertiarycourses.ai4kids.ui.activities

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import sg.com.tertiarycourses.ai4kids.ai.GeminiClient
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.components.CelebrationView
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

private data class Choice(val emoji: String, val name: String)

private val HEROES = listOf(
    Choice("🦊", "Fox"), Choice("🐉", "Dragon"),
    Choice("🤖", "Robot"), Choice("🦄", "Unicorn"),
)
private val PLACES = listOf(
    Choice("🏰", "castle"), Choice("🌋", "volcano"),
    Choice("🌌", "galaxy"), Choice("🏝️", "island"),
)
private val OBJECTS = listOf(
    Choice("🗝️", "golden key"), Choice("🔮", "magic orb"),
    Choice("🎈", "balloon"), Choice("📕", "spell book"),
)
// A mood/trait is threaded through the prose so the same hero can feel brave one
// time and silly the next — changing the whole tone of the story.
private val MOODS = listOf(
    Choice("🦁", "brave"), Choice("🤪", "silly"),
    Choice("😴", "sleepy"), Choice("🤔", "curious"),
)

/** One way the child can solve the mid-story problem: a labelled button plus the
 *  pages it leads to (a resolution beat + an ending). */
private data class Branch(val emoji: String, val label: String, val pages: List<String>)

/**
 * A branching story woven from the picks. [pre] are the pages read before the
 * fork (opening + discovery); the last page shown is [problem], where the child
 * chooses [left] or [right] to decide how the tale resolves. Every beat has
 * several phrasings chosen at random, so the same picks read differently each
 * time — that, plus the choice, is what keeps the builder from feeling repetitive.
 */
private data class Story(
    val pre: List<String>,
    val problem: String,
    val left: Branch,
    val right: Branch,
)

private fun buildStory(h: Choice, p: Choice, o: Choice, m: Choice): Story {
    val opening = listOf(
        "Once upon a time, a ${m.name} ${h.name} ${h.emoji} lived near a ${p.name} ${p.emoji}.",
        "Long ago, in a faraway ${p.name} ${p.emoji}, there lived a ${m.name} little ${h.name} ${h.emoji}.",
        "Every morning, a ${m.name} ${h.name} ${h.emoji} woke up right beside a ${p.name} ${p.emoji}.",
        "In a cozy corner of the ${p.name} ${p.emoji}, a ${m.name} ${h.name} ${h.emoji} was just waking up.",
        "There once was a ${m.name} ${h.name} ${h.emoji} who loved the ${p.name} ${p.emoji} more than anywhere else.",
        "Far past the clouds, a ${m.name} ${h.name} ${h.emoji} made a home by a ${p.name} ${p.emoji}.",
    ).random()
    val discovery = listOf(
        "One sunny day, the ${h.name} found a ${o.name} ${o.emoji} hidden in the tall grass!",
        "While exploring the ${p.name}, the ${h.name} ${h.emoji} spotted a ${o.name} ${o.emoji}!",
        "Then, with a twinkle, a ${o.name} ${o.emoji} appeared right in front of the ${h.name}!",
        "As the ${h.name} ${h.emoji} skipped along, a shiny ${o.name} ${o.emoji} caught the light!",
        "Tucked under an old tree, the ${h.name} ${h.emoji} discovered a ${o.name} ${o.emoji}.",
        "What's this? The ${h.name} ${h.emoji} had never seen a ${o.name} ${o.emoji} quite like it before.",
    ).random()
    val journey = listOf(
        "The ${m.name} ${h.name} ${h.emoji} tucked the ${o.name} ${o.emoji} away and set off deep into the ${p.name} ${p.emoji}.",
        "Step by step, the ${h.name} ${h.emoji} wandered further into the ${p.name} ${p.emoji}, the ${o.name} ${o.emoji} glowing softly.",
        "Full of wonder, the ${h.name} ${h.emoji} explored every winding corner of the ${p.name} ${p.emoji}.",
        "Holding the ${o.name} ${o.emoji} close, the ${h.name} ${h.emoji} marched bravely on through the ${p.name} ${p.emoji}.",
        "The ${o.name} ${o.emoji} seemed to point the way, so the ${h.name} ${h.emoji} followed it across the ${p.name} ${p.emoji}.",
        "Humming a happy tune, the ${m.name} ${h.name} ${h.emoji} skipped deeper into the ${p.name} ${p.emoji}.",
    ).random()
    val trouble = listOf(
        "But then — uh oh! A grumpy troll stomped across the ${p.name} ${p.emoji} and blocked the way.",
        "Suddenly a big storm cloud rolled over the ${p.name} ${p.emoji}, and everything went dark.",
        "Just then, a tiny lost cub began to cry at the edge of the ${p.name} ${p.emoji}.",
        "Oh no! A wobbly old bridge over the ${p.name} ${p.emoji} began to creak and sway.",
        "All at once, a thick fog rolled across the ${p.name} ${p.emoji} and hid the path.",
        "Then a sleepy giant snored so loudly that the whole ${p.name} ${p.emoji} shook!",
    ).random()
    val problem = "$trouble\nWhat should the ${m.name} ${h.name} ${h.emoji} do?"

    // A cheery beat after the problem is solved, then the closing line.
    val celebration = listOf(
        "Everyone cheered for the ${h.name} ${h.emoji}! The ${p.name} ${p.emoji} sparkled brighter than ever. ✨",
        "What a day! The ${h.name} ${h.emoji} laughed and danced with all the new friends. 🎶",
        "The ${o.name} ${o.emoji} hummed a happy tune, and the whole ${p.name} ${p.emoji} joined in. 🎵",
        "Hooray! The ${h.name} ${h.emoji} jumped for joy as the ${p.name} ${p.emoji} filled with giggles. 😄",
        "Confetti swirled through the ${p.name} ${p.emoji} as everyone thanked the ${h.name} ${h.emoji}. 🎊",
        "The ${o.name} ${o.emoji} glittered happily, and the ${p.name} ${p.emoji} felt warm and bright. 🌟",
    )
    val ending = listOf(
        "With a happy heart, the ${m.name} ${h.name} ${h.emoji} shared the magic with every friend. The End! 🎉",
        "And so the ${h.name} ${h.emoji} and all the friends celebrated together. The End! 🎉",
        "From that day on, the ${p.name} ${p.emoji} was the happiest place of all. The End! 🎉",
        "And the ${m.name} ${h.name} ${h.emoji} went home with the best story to tell. The End! 🎉",
        "Tucked in that night, the ${h.name} ${h.emoji} smiled, dreaming of new adventures. The End! 🌙",
        "Forever after, the ${h.name} ${h.emoji} and the ${p.name} ${p.emoji} were the best of friends. The End! 🎉",
    )

    // Branch A — be clever and use the magic item.
    val clever = Branch(
        emoji = o.emoji,
        label = "Use the ${o.name}",
        pages = listOf(
            listOf(
                "The ${h.name} ${h.emoji} held up the ${o.name} ${o.emoji}. With a bright flash of magic, the trouble melted away! ✨",
                "Quick as a wink, the ${h.name} ${h.emoji} waved the ${o.name} ${o.emoji} — and poof! the problem was gone. ✨",
                "The clever ${h.name} ${h.emoji} pointed the ${o.name} ${o.emoji} just right, and everything turned out perfectly! ✨",
            ).random(),
            celebration.random(),
            ending.random(),
        ),
    )
    // Branch B — be kind and call friends for help.
    val friends = Branch(
        emoji = "🤝",
        label = "Call for friends",
        pages = listOf(
            listOf(
                "The ${h.name} ${h.emoji} called out for help. Friends came running, and together they fixed everything in no time! 🤝",
                "The ${h.name} ${h.emoji} whistled, and kind friends arrived to lend a hand. Together, they sorted it out! 🤝",
                "With a big friendly shout, the ${h.name} ${h.emoji} gathered everyone, and as a team they made it all okay! 🤝",
            ).random(),
            celebration.random(),
            ending.random(),
        ),
    )

    return Story(pre = listOf(opening, discovery, journey), problem = problem, left = clever, right = friends)
}

/**
 * Ask Gemini for a richer, freshly-written branching story from the picks, in the
 * same shape [Story] uses. Returns null on any failure (no key, no network, bad
 * JSON) so the caller falls back to the on-device [buildStory] templates.
 */
private suspend fun generateStoryWithGemini(h: Choice, p: Choice, o: Choice, m: Choice): Story? {
    val prompt = """
        Write a short, gentle, G-rated adventure story for a child aged 7 to 9.
        Ingredients to use:
        - Hero: ${h.name} ${h.emoji}
        - Place: ${p.name} ${p.emoji}
        - Magic item: ${o.name} ${o.emoji}
        - Tone/mood: ${m.name}

        The story branches: the child reads a few pages, hits a friendly problem,
        then picks one of two ways to solve it. Return ONLY JSON of exactly this shape:
        {
          "pre": ["page", "page", "page"],
          "problem": "one page that introduces a friendly obstacle and ends with the question: What should the ${h.name} do?",
          "choiceA": { "emoji": "${o.emoji}", "label": "Use the ${o.name}", "pages": ["solve it with the ${o.name}'s magic", "a happy celebration", "a warm ending that says The End!"] },
          "choiceB": { "emoji": "🤝", "label": "Call for friends", "pages": ["solve it by asking friends for help", "a happy celebration", "a warm ending that says The End!"] }
        }
        Rules: each page is 1 to 2 short sentences. Keep it positive, kind, and
        age-appropriate — no violence, scariness, or romance. Weave the emojis into
        the sentences. "pre" must have exactly 3 pages; each "pages" exactly 3.
    """.trimIndent()

    val raw = GeminiClient.generateJson(prompt) ?: return null
    // responseMimeType is JSON, but be lenient in case the model adds stray text.
    val jsonText = raw.substring(raw.indexOf('{').coerceAtLeast(0), raw.lastIndexOf('}') + 1)

    return runCatching {
        val root = JSONObject(jsonText)
        fun strings(arr: org.json.JSONArray) = List(arr.length()) { arr.getString(it).trim() }
        fun branch(key: String): Branch {
            val b = root.getJSONObject(key)
            return Branch(
                emoji = b.optString("emoji").ifBlank { "✨" },
                label = b.optString("label").ifBlank { "Keep going" },
                pages = strings(b.getJSONArray("pages")),
            )
        }
        val pre = strings(root.getJSONArray("pre"))
        val problem = root.getString("problem").trim()
        val left = branch("choiceA")
        val right = branch("choiceB")
        require(pre.isNotEmpty() && problem.isNotEmpty() && left.pages.isNotEmpty() && right.pages.isNotEmpty())
        Story(pre = pre, problem = problem, left = left, right = right)
    }.getOrNull()
}

/**
 * Story Builder — the child picks a hero, place, magic item, and mood; the app
 * weaves a short, branching illustrated story and reads it back as tappable pages.
 * When a Gemini key is configured it writes a fresh tale online (with the on-device
 * templates as the fallback); with no key it runs fully offline. Android port of
 * the iOS `StoryBuilderView`.
 */
@Composable
fun StoryBuilderScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val progress = LocalProgressStore.current
    val scope = rememberCoroutineScope()

    var hero by remember { mutableStateOf<Choice?>(null) }
    var place by remember { mutableStateOf<Choice?>(null) }
    var obj by remember { mutableStateOf<Choice?>(null) }
    var mood by remember { mutableStateOf<Choice?>(null) }
    var story by remember { mutableStateOf<Story?>(null) }
    // Pages revealed so far. Starts as the pre-fork pages + the problem page; once
    // the child picks a branch, that branch's pages are appended.
    var pages by remember { mutableStateOf<List<String>>(emptyList()) }
    var pageIndex by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }

    val ready = hero != null && place != null && obj != null && mood != null
    // The problem page is the fork: when we're on it and haven't chosen yet, the
    // reader offers the two branch buttons instead of "Next".
    val atChoice = story != null && !picked && pageIndex == story!!.pre.size

    fun reset() {
        showCelebration = false
        story = null
        pages = emptyList()
        hero = null; place = null; obj = null; mood = null
        pageIndex = 0
        picked = false
    }

    fun showStory(s: Story) {
        story = s
        pages = s.pre + s.problem
        pageIndex = 0
        picked = false
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

    fun choose(left: Boolean) {
        val s = story ?: return
        val branch = if (left) s.left else s.right
        pages = s.pre + s.problem + branch.pages
        picked = true
        pageIndex += 1 // advance from the problem page onto the chosen resolution
    }

    fun nextPage() {
        if (pageIndex < pages.size - 1) {
            pageIndex += 1
        } else {
            progress.award(3, Activity.STORY)
            showCelebration = true
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
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text("Story Builder", color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
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
                    page = pages[pageIndex], pageIndex = pageIndex,
                    // Before the fork, estimate the length from one branch; after the
                    // pick, use the real page list (branches may differ in length).
                    pageCount = if (picked) pages.size else story!!.pre.size + 1 + story!!.left.pages.size,
                    choice = if (atChoice) story!!.left to story!!.right else null,
                    onChoose = { choose(it) },
                    onNext = { nextPage() },
                )
            }
        }

        if (showCelebration) {
            CelebrationView(message = "What a story! ⭐️⭐️⭐️", onDismiss = { reset() })
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

@Composable
private fun ChoiceRow(
    title: String,
    items: List<Choice>,
    selection: Choice?,
    onSelect: (Choice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                ChoiceTile(item, isOn = selection == item, onSelect = { onSelect(item) }, modifier = Modifier.weight(1f))
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
    /** When non-null, this page is the fork: offer the two branches instead of Next. */
    choice: Pair<Branch, Branch>?,
    onChoose: (Boolean) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text("${hero.emoji}${place.emoji}${obj.emoji}", fontSize = 72.sp)
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
                    title = if (pageIndex == pageCount - 1) "The End!" else "Next",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    color = Theme.Orange,
                    onClick = onNext,
                )
            }
        }
    }
}
