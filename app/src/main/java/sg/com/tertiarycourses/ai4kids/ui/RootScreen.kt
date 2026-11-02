package sg.com.tertiarycourses.ai4kids.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import sg.com.tertiarycourses.ai4kids.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import sg.com.tertiarycourses.ai4kids.ui.components.SunnyIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import sg.com.tertiarycourses.ai4kids.cards.BrainArcadeScreen
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.escape.EscapeLobbyScreen
import sg.com.tertiarycourses.ai4kids.gdx.EscapeActivity
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.activities.CodePuzzlesScreen
import sg.com.tertiarycourses.ai4kids.ui.activities.art.ArtStudioScreen
import sg.com.tertiarycourses.ai4kids.ui.activities.buddy.TalkingBuddyScreen
import sg.com.tertiarycourses.ai4kids.ui.activities.phonics.PhonicsScreen
import sg.com.tertiarycourses.ai4kids.ui.activities.StoryBuilderScreen
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * Home screen — a bright, friendly grid of the four learning activities plus a
 * running star total and a small Parents' Corner. Tapping a card opens that
 * activity full-screen. Android port of the iOS `RootView`.
 */
@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<Activity?>(null) }
    var showParents by remember { mutableStateOf(false) }
    var showArcade by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val progress = LocalProgressStore.current
    // The Escape Room runs as its own LibGDX Activity; it returns stars earned.
    val escapeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val stars = result.data?.getIntExtra(EscapeActivity.EXTRA_STARS, 0) ?: 0
            if (stars > 0) progress.award(stars, Activity.ESCAPE)
        }
    }
    // The Escape Room opens a lobby (solo or co-op) which then launches the game.
    fun launchEscape(code: String?, host: Boolean, level: Int) {
        val intent = Intent(context, EscapeActivity::class.java)
        intent.putExtra(EscapeActivity.EXTRA_LEVEL, level)
        if (code != null) {
            intent.putExtra(EscapeActivity.EXTRA_CODE, code)
            intent.putExtra(EscapeActivity.EXTRA_HOST, host)
        }
        escapeLauncher.launch(intent)
        selected = null
    }
    fun openActivity(activity: Activity) { selected = activity }

    // An open activity / arcade fully replaces the home grid (rather than drawing
    // on top of it), so touches can't fall through to the cards behind.
    when {
        selected != null -> when (selected) {
            Activity.PHONICS -> PhonicsScreen(onClose = { selected = null })
            Activity.STORY -> StoryBuilderScreen(onClose = { selected = null })
            Activity.CODE -> CodePuzzlesScreen(onClose = { selected = null })
            Activity.ESCAPE -> EscapeLobbyScreen(
                onClose = { selected = null },
                onPlay = { code, host, level -> launchEscape(code, host, level) },
            )
            Activity.BUDDY -> TalkingBuddyScreen(onClose = { selected = null })
            Activity.ART -> ArtStudioScreen(onClose = { selected = null })
            null -> Unit
        }
        showArcade -> BrainArcadeScreen(onClose = { showArcade = false })
        else -> Box(modifier = modifier) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Header(onParents = { showParents = true })
                }
                items(Activity.entries.toList(), key = { it.id }) { activity ->
                    ActivityCard(activity = activity) { openActivity(activity) }
                }
                item(key = "arcade") {
                    ArcadeCard(onOpen = { showArcade = true })
                }
            }
        }
    }

    // The Parents' Corner is a modal bottom sheet, fine to overlay the grid.
    if (showParents) {
        ParentsCornerSheet(onDismiss = { showParents = false })
    }
}

/** An activity-style tile that opens the online Brain Arcade card games. */
@Composable
private fun ArcadeCard(onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "arcadeScale",
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .heightIn(min = 230.dp)
            .kidCard()
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Theme.Green),
            ) {
                Icon(Icons.Filled.Psychology, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Theme.Green.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Online", color = Theme.Green, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("Brain Arcade", color = Theme.Purple, fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = Theme.DisplayFont)
        Text("Card games with friends", color = Theme.Ink.copy(alpha = 0.85f), fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text("10 games", color = Theme.Ink.copy(alpha = 0.85f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Header(onParents: () -> Unit) {
    val progress = LocalProgressStore.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Image(painterResource(R.drawable.kid_sunny_logo), contentDescription = null, modifier=Modifier.size(64.dp))
        Column(modifier=Modifier.weight(1f)) {
            Text(
                "AI Kids",
                color = Theme.Purple,
                fontSize = 34.sp,
                fontFamily = Theme.DisplayFont,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Little minds. Big ideas.",
                color = Theme.Ink.copy(alpha = 0.85f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        StarBadge(count = progress.totalStars)
        Spacer(Modifier.size(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .softShadow(CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onParents)
                .padding(14.dp),
        ) {
            Icon(Icons.Filled.Group, contentDescription = "Parents' Corner", tint = Theme.Ink, modifier = Modifier.size(22.dp))
        }
    }
}

/** A single tappable activity tile. */
@Composable
private fun ActivityCard(activity: Activity, onClick: () -> Unit) {
    val progress = LocalProgressStore.current
    val stars = progress.stars(activity)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardScale",
    )

    Row(
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(18.dp),
        modifier=Modifier.scale(scale).fillMaxWidth().heightIn(min=175.dp).kidCard()
            .clickable(interactionSource=interaction,indication=null,onClick=onClick).padding(20.dp)
    ) {
        Image(painterResource(when(activity.id) {"story"->R.drawable.friend_story;"art"->R.drawable.friend_art;"code"->R.drawable.friend_maths;"escape","buddy"->R.drawable.friend_science;else->R.drawable.kid_wand}),contentDescription=null,modifier=Modifier.size(104.dp))
        Column(verticalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.weight(1f)) {
            Text(activity.ageBand,color=activity.color,fontSize=17.sp,fontWeight=FontWeight.Bold)
            Text(activity.title,color=activity.color,fontSize=27.sp,fontWeight=FontWeight.Black,fontFamily=Theme.DisplayFont)
            Text(activity.subtitle,color=Theme.Ink,fontSize=18.sp,fontWeight=FontWeight.Medium)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Filled.Star,contentDescription=null,modifier=Modifier.size(25.dp))
                Text("$stars stars",color=Theme.Ink,fontSize=16.sp,fontWeight=FontWeight.Bold)
            }
        }
    }
}
