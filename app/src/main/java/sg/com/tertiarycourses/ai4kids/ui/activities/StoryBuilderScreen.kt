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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Choice("🎈", "flying balloon"), Choice("📕", "spell book"),
)

/**
 * Story Builder — the child picks a hero, a place, and a magical object; the app
 * weaves a short illustrated story from those choices and reads back as tappable
 * pages. Fully on-device with templated text — no network calls. Android port of
 * the iOS `StoryBuilderView`.
 */
@Composable
fun StoryBuilderScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val progress = LocalProgressStore.current

    var hero by remember { mutableStateOf<Choice?>(null) }
    var place by remember { mutableStateOf<Choice?>(null) }
    var obj by remember { mutableStateOf<Choice?>(null) }
    var pages by remember { mutableStateOf<List<String>>(emptyList()) }
    var pageIndex by remember { mutableStateOf(0) }
    var showCelebration by remember { mutableStateOf(false) }

    val ready = hero != null && place != null && obj != null

    fun reset() {
        showCelebration = false
        pages = emptyList()
        hero = null; place = null; obj = null
        pageIndex = 0
    }

    fun buildStory() {
        val h = hero!!; val p = place!!; val o = obj!!
        pages = listOf(
            "Once upon a time, a brave ${h.name} ${h.emoji} lived near a ${p.name} ${p.emoji}.",
            "One sunny day, the ${h.name} found a ${o.name} ${o.emoji} hidden in the grass!",
            "The ${o.name} began to glow, and the whole ${p.name} lit up with magic ✨.",
            "With a happy heart, the ${h.name} ${h.emoji} shared the magic with every friend. The End! 🎉",
        )
        pageIndex = 0
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

            if (pages.isEmpty()) {
                PickerStage(
                    modifier = Modifier.weight(1f),
                    hero = hero, place = place, obj = obj, ready = ready,
                    onHero = { hero = it }, onPlace = { place = it }, onObject = { obj = it },
                    onBuild = { if (ready) buildStory() },
                )
            } else {
                ReaderStage(
                    modifier = Modifier.weight(1f),
                    hero = hero!!, place = place!!, obj = obj!!,
                    page = pages[pageIndex], pageIndex = pageIndex, pageCount = pages.size,
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
private fun PickerStage(
    modifier: Modifier = Modifier,
    hero: Choice?, place: Choice?, obj: Choice?, ready: Boolean,
    onHero: (Choice) -> Unit, onPlace: (Choice) -> Unit, onObject: (Choice) -> Unit,
    onBuild: () -> Unit,
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
        KidButton(
            title = "Make my story!",
            icon = Icons.Filled.AutoFixHigh,
            color = if (ready) Theme.Orange else Theme.Ink.copy(alpha = 0.25f),
            enabled = ready,
            onClick = onBuild,
            modifier = Modifier.padding(top = 8.dp),
        )
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
