package sg.com.tertiarycourses.ai4kids.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Icon
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
import sg.com.tertiarycourses.ai4kids.cards.BrainArcadeScreen
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.activities.CodePuzzlesScreen
import sg.com.tertiarycourses.ai4kids.ui.activities.PhonicsScreen
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

    Box(modifier = modifier) {
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
                ActivityCard(activity = activity) { selected = activity }
            }
            item(key = "arcade") {
                ArcadeCard(onOpen = { showArcade = true })
            }
        }
    }

    selected?.let { activity ->
        when (activity) {
            Activity.PHONICS -> PhonicsScreen(onClose = { selected = null })
            Activity.STORY -> StoryBuilderScreen(onClose = { selected = null })
            Activity.CODE -> CodePuzzlesScreen(onClose = { selected = null })
        }
    }

    if (showArcade) {
        BrainArcadeScreen(onClose = { showArcade = false })
    }

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
                Text("Online", color = Theme.Green, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("Brain Arcade", color = Theme.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("Card games with friends", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text("🃏 6 games", color = Theme.Ink.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
        Column {
            Text(
                "AI4Kids",
                color = Theme.Purple,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Play. Learn. Create.",
                color = Theme.Ink.copy(alpha = 0.7f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
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

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .heightIn(min = 230.dp)
            .kidCard()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(activity.color),
            ) {
                Icon(activity.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(activity.color.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(activity.ageBand, color = activity.color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(activity.title, color = Theme.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(activity.subtitle, color = Theme.Ink.copy(alpha = 0.6f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Theme.Yellow, modifier = Modifier.size(18.dp))
            Text("$stars stars", color = Theme.Ink.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
