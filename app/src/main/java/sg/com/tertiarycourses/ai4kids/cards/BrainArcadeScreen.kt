package sg.com.tertiarycourses.ai4kids.cards

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * The online Brain Arcade. Gates on sign-in, then shows the six card games.
 * Picking one opens [CardGameScreen]. Android port of the web `CardGamesHub`.
 */
@Composable
fun BrainArcadeScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var selected by remember { mutableStateOf<CardGameMeta?>(null) }
    var loggedIn by remember { mutableStateOf(CardApi.isLoggedIn()) }
    var showLogin by remember { mutableStateOf(false) }

    if (showLogin) {
        LoginScreen(onClose = { showLogin = false }, onLoggedIn = { showLogin = false; loggedIn = true })
        return
    }

    selected?.let { game ->
        CardGameScreen(game = game, loggedIn = loggedIn, onClose = { selected = null })
        return
    }

    // Signed-out players see only the solo-capable games; signing in unlocks the
    // rest plus host/join multiplayer.
    val games = if (loggedIn) CARD_GAMES else CARD_GAMES.filter { it.modes.contains(CardMode.SOLO) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        CloseButton(onClick = onClose)
                        Spacer(Modifier.weight(1f))
                        if (loggedIn) {
                            TextButton(onClick = {
                                CardApi.logout()
                                loggedIn = false
                            }) { Text("Sign out", color = Theme.Ink.copy(alpha = 0.55f)) }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    Text("🕹️ Brain Arcade", color = Theme.Ink, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (loggedIn) "Quick card games — play solo, team up, or race your friends with a room code."
                        else "Quick card games you can play right now, on your own.",
                        color = Theme.Ink.copy(alpha = 0.65f),
                        fontSize = 16.sp,
                    )
                    if (!loggedIn) {
                        Spacer(Modifier.size(12.dp))
                        LoginPrompt(onLogin = { showLogin = true })
                    }
                }
            }
            items(games, key = { it.slug }) { game ->
                GameCard(game = game, onClick = { selected = game })
            }
        }
    }
}

/** Banner inviting signed-out players to log in for more games + multiplayer. */
@Composable
private fun LoginPrompt(onLogin: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Theme.Purple.copy(alpha = 0.12f))
            .clickable(onClick = onLogin)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text("🔓", fontSize = 26.sp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Log in for more games", color = Theme.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Unlock all 6 games and play with friends using a room code.", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        Text("Log in ▶", color = Theme.Purple, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun GameCard(game: CardGameMeta, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 190.dp)
            .kidCard()
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(game.accent.copy(alpha = 0.15f)),
            ) {
                Text(game.emoji, fontSize = 30.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .softShadow(RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    game.modes.joinToString(" · ") { it.label },
                    color = Theme.Ink.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(game.title, color = Theme.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(game.blurb, color = Theme.Ink.copy(alpha = 0.6f), fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(game.accent)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text("Play ▶", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}
