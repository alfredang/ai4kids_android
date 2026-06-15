package sg.com.tertiarycourses.ai4kids.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/** Standard soft shadow used across cards and buttons. */
fun Modifier.softShadow(shape: androidx.compose.ui.graphics.Shape) = this.shadow(
    elevation = Theme.SoftShadowElevation,
    shape = shape,
    ambientColor = Color.Black.copy(alpha = 0.18f),
    spotColor = Color.Black.copy(alpha = 0.18f),
)

/** Wraps content in the standard rounded white "play card" surface. */
fun Modifier.kidCard(cornerRadius: androidx.compose.ui.unit.Dp = Theme.CardCornerRadius): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .softShadow(shape)
        .clip(shape)
        .background(Color.White)
}

/** A chunky, tappable primary button styled for small hands. */
@Composable
fun KidButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Theme.Purple,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "kidButtonScale",
    )
    val shape = RoundedCornerShape(22.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .scale(scale)
            .softShadow(shape)
            .clip(shape)
            .background(color)
            .clickableNoRipple(interaction, enabled, onClick)
            .padding(horizontal = 28.dp, vertical = 16.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

private fun Modifier.clickableNoRipple(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interaction,
    indication = null,
    enabled = enabled,
    onClick = onClick,
)

/** A small pill showing a star count. */
@Composable
fun StarBadge(count: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .softShadow(CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = Theme.Yellow, modifier = Modifier.size(22.dp))
        Text("$count", color = Theme.Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Standard rounded "close / back to home" button used by every activity. */
@Composable
fun CloseButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .softShadow(CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickableNoRipple(interaction, true, onClick)
            .padding(14.dp),
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "Close",
            tint = Theme.Ink,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Celebratory burst of emoji "confetti" shown when a kid finishes a round.
 * Tapping anywhere dismisses it via [onDismiss].
 */
@Composable
fun CelebrationView(message: String, onDismiss: () -> Unit) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }

    val pieces = listOf("⭐️", "🎉", "🌟", "🎈", "✨", "🏆", "🥳")
    val cardScale by animateFloatAsState(
        targetValue = if (animate) 1f else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "celebrateScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickableNoRipple(remember { MutableInteractionSource() }, true, onDismiss),
    ) {
        // Falling confetti pieces.
        for (i in 0 until 24) {
            val fall by animateFloatAsState(
                targetValue = if (animate) 420f else -420f,
                animationSpec = tween(durationMillis = 1600, delayMillis = i * 30),
                label = "confetti$i",
            )
            val alpha by animateFloatAsState(
                targetValue = if (animate) 0f else 1f,
                animationSpec = tween(durationMillis = 1600, delayMillis = i * 30),
                label = "confettiAlpha$i",
            )
            Text(
                text = pieces[i % pieces.size],
                fontSize = 40.sp,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = ((i * 53) % 320 - 160).toFloat()
                        translationY = fall
                        this.alpha = alpha
                    },
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .scale(cardScale)
                .softShadow(Theme.BigShape)
                .clip(Theme.BigShape)
                .background(Theme.Purple)
                .padding(40.dp),
        ) {
            Text("🎉", fontSize = 90.sp)
            Text(
                text = message,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Convenience helper to use an emoji [String] as content; kept for parity with
 *  the iOS components that lean on system imagery. */
@Composable
fun EmojiBadge(emoji: String, size: Int, modifier: Modifier = Modifier) {
    Text(emoji, fontSize = size.sp, modifier = modifier)
}
