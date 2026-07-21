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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
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
            // Guarantee a comfortable tap target even for short labels.
            .defaultMinSize(minHeight = 56.dp)
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
            // Wrap a long label (e.g. a "Painting picture 2 of 3…" progress state)
            // onto a second line rather than hard-clipping it at the button edge.
            // Short labels are unaffected — they still sit on one line.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
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
        Text("$count", color = Theme.Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
    }
}

/** Standard rounded "close / back to home" button used by every activity. The
 *  tappable area (56dp) is larger than the visible circle (44dp) so small hands
 *  can hit it easily without the button itself looking oversized. */
@Composable
fun CloseButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickableNoRipple(interaction, true, onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .softShadow(CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = Theme.Ink,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Celebratory burst of emoji "confetti" shown when a kid finishes a round.
 * Tapping anywhere dismisses it via [onDismiss].
 *
 * [actions] adds optional buttons inside the card — e.g. the Story Builder's "try
 * the other path", which needs a way out of the celebration other than dismissing
 * it. Their own taps are consumed, so pressing one doesn't also dismiss.
 */
@Composable
fun CelebrationView(
    message: String,
    onDismiss: () -> Unit,
    actions: @Composable (ColumnScope.() -> Unit)? = null,
) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }

    // Split a clean headline from any trailing emoji/stars (anything from the symbol
    // ranges upward) so the text reads cleanly and the emoji get their own row.
    val trimmed = message.trim()
    val cut = trimmed.indexOfLast { it.code < 0x2190 && it != ' ' } + 1
    val title = trimmed.substring(0, cut).trim()
    val decor = trimmed.substring(cut).trim()

    val cardScale by animateFloatAsState(
        targetValue = if (animate) 1f else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "celebrateScale",
    )

    val confettiColors = listOf(
        Theme.Orange, Theme.Purple, Theme.Teal, Theme.Green,
        Color(0xFFFFC83D), Color(0xFFFF6F91), Color(0xFF4DA3FF),
    )

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickableNoRipple(remember { MutableInteractionSource() }, true, onDismiss),
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        // Falling confetti — small bright shapes spread across the whole width, each
        // drifting, spinning and fading as it falls. Reads cleaner than emoji confetti.
        for (i in 0 until 30) {
            val dur = 1800 + (i % 5) * 140
            val delay = (i * 43) % 700
            val fall by animateFloatAsState(
                targetValue = if (animate) hPx / 2f + 80f else -hPx / 2f - 80f,
                animationSpec = tween(durationMillis = dur, delayMillis = delay),
                label = "confetti$i",
            )
            val spin by animateFloatAsState(
                targetValue = if (animate) (160 + (i * 37) % 220).toFloat() else 0f,
                animationSpec = tween(durationMillis = dur, delayMillis = delay),
                label = "confettiSpin$i",
            )
            val alpha by animateFloatAsState(
                targetValue = if (animate) 0f else 1f,
                animationSpec = tween(durationMillis = dur, delayMillis = delay),
                label = "confettiAlpha$i",
            )
            val xFrac = (i * 0.1379f) % 1f          // spread across the full width
            val drift = ((i % 3) - 1) * 26f         // gentle sideways lean
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 14.dp)
                    .graphicsLayer {
                        translationX = (xFrac - 0.5f) * wPx + drift * (fall / hPx)
                        translationY = fall
                        rotationZ = spin
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(3.dp))
                    .background(confettiColors[i % confettiColors.size]),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .scale(cardScale)
                .softShadow(Theme.BigShape)
                .clip(Theme.BigShape)
                .background(Theme.Purple)
                .padding(horizontal = 44.dp, vertical = 36.dp),
        ) {
            Text("🎉", fontSize = 80.sp)
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
            }
            if (decor.isNotBlank()) {
                Text(text = decor, fontSize = 40.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center)
            }
            if (actions != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) { actions() }
            }
        }
    }
}

/**
 * A two-column grid of tappable "idea" suggestions — the ✨ prompts offered under
 * a free-text field so a child who can't think of anything still has a way in.
 * Used by the Art Studio and the Story Builder's "Write your own".
 *
 * Chips in a row share the taller one's height, so a one-line idea beside a
 * two-line one reads as a matched pair rather than a ragged step.
 */
@Composable
fun IdeaChips(
    items: List<String>,
    tint: Color = Theme.Orange,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                row.forEach { idea ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(tint.copy(alpha = 0.10f))
                            .clickable { onPick(idea) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "✨ $idea",
                            color = Theme.Ink.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Convenience helper to use an emoji [String] as content; kept for parity with
 *  the iOS components that lean on system imagery. */
@Composable
fun EmojiBadge(emoji: String, size: Int, modifier: Modifier = Modifier) {
    Text(emoji, fontSize = size.sp, modifier = modifier)
}
