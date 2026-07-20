package sg.com.tertiarycourses.ai4kids.ui.activities.phonics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * Phonics Quest — an adventure map of phonics "worlds", each a quick mini-game
 * (Pop the Phoneme, Build the Word, Rhyme Time, Listen & Find, Sound Blender,
 * Buddy Sounds). Clearing a world unlocks the next and earns up to 3 stars.
 * Whole words are spoken on-device and isolated sounds play bundled clips, so the
 * quest runs fully offline; an optional Gemini "Buddy" adds hints when an API key
 * is configured.
 *
 * Mirrors the web app's `PhonicsQuest.tsx`, which is the source of truth for the
 * quest's behaviour. Two intentional divergences: the Buddy calls Gemini directly
 * on-device instead of the web's `/api/learn/phonics-buddy`, and the star tally
 * stays in local `SharedPreferences` (`PhonicsStore`) rather than syncing to the
 * server — the offline core collects nothing.
 */
@Composable
fun PhonicsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PhonicsStore(context) }
    // Whole words go through TTS, isolated sounds through the bundled clips; one
    // engine owns both so a clip can silence the voice before it sounds.
    val audio = rememberPhonicsAudio()
    var selected by remember { mutableStateOf<Int?>(null) }

    BackHandler { if (selected != null) selected = null else onClose() }

    if (selected == null) {
        AdventureMap(store = store, onPick = { selected = it }, onClose = onClose)
    } else {
        StageHost(index = selected!!, store = store, audio = audio, onBack = { selected = null })
    }
}

/* ----------------------------- Adventure map ----------------------------- */

@Composable
private fun AdventureMap(store: PhonicsStore, onPick: (Int) -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Theme.Background)) {
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        // A responsive grid: one column in portrait (the classic vertical path),
        // multiple in landscape so the worlds fit with little or no scrolling.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = if (landscape) 1120.dp else 640.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(if (landscape) 4.dp else 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        CloseButton(onClick = onClose)
                        Spacer(Modifier.weight(1f))
                        StarBadge(count = store.totalStars)
                    }
                    Text("Phonics Quest", color = Theme.Pink, fontSize = if (landscape) 26.sp else 34.sp, fontWeight = FontWeight.Black)
                    Text("Travel the worlds and master every sound!", color = Theme.Ink.copy(alpha = 0.65f), fontSize = if (landscape) 13.sp else 16.sp)
                }
            }
            itemsIndexed(PHONICS_STAGES) { i, stage ->
                StageNode(
                    stage = stage,
                    number = i + 1,
                    stars = store.stars(stage.id),
                    unlocked = store.isUnlocked(i),
                    onClick = { if (store.isUnlocked(i)) onPick(i) },
                )
            }
        }
    }
}

@Composable
private fun StageNode(stage: PhonicsStage, number: Int, stars: Int, unlocked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .kidCard()
            .clickable(enabled = unlocked, onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (unlocked) stage.color else Theme.Ink.copy(alpha = 0.15f)),
        ) {
            if (unlocked) Text(stage.emoji, fontSize = 32.sp)
            else Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "World $number · ${stage.title}",
                color = if (unlocked) Theme.Ink else Theme.Ink.copy(alpha = 0.4f),
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (unlocked) stage.subtitle else "Clear the world before to unlock",
                color = Theme.Ink.copy(alpha = 0.55f),
                fontSize = 14.sp,
            )
            if (unlocked) {
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    repeat(3) { s ->
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (s < stars) Theme.Yellow else Theme.Ink.copy(alpha = 0.15f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        if (unlocked) Text("▶", color = stage.color, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

/* ----------------------------- Stage host ----------------------------- */

/** Canned praise per star tier, spoken and shown on clearing a world. Hardcoded
 *  on purpose: a celebration must be instant, and an LLM round-trip (~1s, or far
 *  longer when the model is slow or rate-limited) is far too slow to cheer a child
 *  who has just finished. */
private val PRAISE = mapOf(
    3 to "Perfect! You cleared every round!",
    2 to "Great job! You're getting really good at this!",
    1 to "Well done! Keep practising and you'll be a superstar!",
)

@Composable
private fun StageHost(index: Int, store: PhonicsStore, audio: PhonicsAudio, onBack: () -> Unit) {
    val stage = PHONICS_STAGES[index]
    val globalProgress = LocalProgressStore.current

    var round by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(stage.rounds) }
    var earned by remember { mutableStateOf<Int?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    fun finish(mistakes: Int) {
        val stars = starsForMistakes(mistakes)
        val delta = store.record(stage.id, stars)
        if (delta > 0) globalProgress.award(delta, Activity.PHONICS)
        earned = stars
        PRAISE[stars]?.let(audio::speak)
    }

    Box(modifier = Modifier.fillMaxSize().background(Theme.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onBack)
                Spacer(Modifier.weight(1f))
                Text("${stage.emoji} ${stage.title}", color = Theme.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                StarBadge(count = store.stars(stage.id))
            }

            // Progress bar.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Round ${round + 1} of $total", color = Theme.Ink.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Theme.Ink.copy(alpha = 0.1f))) {
                    Box(
                        Modifier
                            .fillMaxWidth(((round + 1).toFloat() / total).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(stage.color),
                    )
                }
            }

            // Game (centred, scrollable). `key(attempt)` lets "Play again" restart.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                key(attempt) {
                    val onProgress: (Int, Int) -> Unit = { r, t -> round = r; total = t }
                    when (stage.kind) {
                        PhonicsKind.POP -> PopPhonemeGame(stage.pop, stage.color, audio, onProgress, ::finish)
                        PhonicsKind.BUILD -> BuildWordGame(stage.build, stage.color, audio, onProgress, ::finish)
                        PhonicsKind.RHYME -> RhymeGame(stage.rhyme, stage.color, audio, onProgress, ::finish)
                        PhonicsKind.LISTEN -> ListenFindGame(stage.listen, stage.color, audio, onProgress, ::finish)
                        PhonicsKind.BLEND -> BlendGame(stage.blend, stage.color, audio, onProgress, ::finish)
                        PhonicsKind.DIGRAPH -> DigraphGame(stage.digraph, stage.color, audio, onProgress, ::finish)
                    }
                }
            }
        }

        earned?.let { stars ->
            StageComplete(
                stage = stage,
                stars = stars,
                onAgain = {
                    earned = null; round = 0; attempt += 1
                },
                onMap = onBack,
            )
        }
    }
}

/* ----------------------------- Completion overlay ----------------------------- */

@Composable
private fun StageComplete(stage: PhonicsStage, stars: Int, onAgain: () -> Unit, onMap: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(28.dp)
                .softShadow(RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
        ) {
            Text("🎉", fontSize = 64.sp)
            Text("${stage.title} cleared!", color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Row {
                repeat(3) { s ->
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (s < stars) Theme.Yellow else Theme.Ink.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            PRAISE[stars]?.let {
                Text(
                    it,
                    color = Theme.Ink.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(stage.color.copy(alpha = 0.12f))
                        .padding(14.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KidButton(title = "Play again", color = Theme.Ink.copy(alpha = 0.5f), onClick = onAgain)
                KidButton(title = "Map", color = stage.color, onClick = onMap)
            }
        }
    }
}
