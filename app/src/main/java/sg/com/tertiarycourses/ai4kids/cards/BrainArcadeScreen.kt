package sg.com.tertiarycourses.ai4kids.cards

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * The online Brain Arcade. Gates on sign-in, then shows the card games.
 * Picking one opens [CardGameScreen]. Android port of the web `CardGamesHub`.
 */
@Composable
fun BrainArcadeScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var selected by remember { mutableStateOf<CardGameMeta?>(null) }
    var loggedIn by remember { mutableStateOf(CardApi.isLoggedIn()) }
    var showGate by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }

    if (showGate) {
        // Parental gate immediately before sign-in — the only point the app
        // sends anything to a server (a username + password for the kid account).
        ParentalGate(
            headerTitle = "Grown-ups only",
            cardTitle = "Parental consent to sign in",
            message = "Signing in to Brain Arcade uses an ai4kids kid account so children can " +
                "play card games with friends online. It stores only a username — no real name, " +
                "email, phone number, or location — and there are no ads. Solo and offline games " +
                "never need an account.\n\n" +
                "A parent or guardian: please confirm by solving the problem below.",
            confirmLabel = "I'm a grown-up — continue",
            onClose = { showGate = false },
            onConsent = { showGate = false; showLogin = true },
        )
        return
    }

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

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        LazyVerticalGrid(
            // Smaller min cell in landscape packs more columns (fewer rows to scroll).
            columns = GridCells.Adaptive(minSize = if (landscape) 240.dp else 280.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(if (landscape) 14.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(if (landscape) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (landscape) 12.dp else 16.dp),
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
                    Spacer(Modifier.size(if (landscape) 2.dp else 8.dp))
                    Text("🕹️ Brain Arcade", color = Theme.Ink, fontSize = if (landscape) 26.sp else 34.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (loggedIn) "Quick card games — play solo, team up, or race your friends with a room code."
                        else "Quick card games you can play right now, on your own.",
                        color = Theme.Ink.copy(alpha = 0.65f),
                        fontSize = if (landscape) 13.sp else 16.sp,
                    )
                    if (!loggedIn) {
                        Spacer(Modifier.size(if (landscape) 8.dp else 12.dp))
                        LoginPrompt(onLogin = { showGate = true })
                    }
                }
            }
            items(games, key = { it.slug }) { game ->
                GameCard(game = game, compact = landscape, onClick = { selected = game })
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
            Text("Unlock every game and play with friends using a room code.", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        Text("Log in ▶", color = Theme.Purple, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun GameCard(game: CardGameMeta, onClick: () -> Unit, compact: Boolean = false) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 150.dp else 190.dp)
            .kidCard()
            .clickable(onClick = onClick)
            .padding(if (compact) 14.dp else 20.dp),
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
