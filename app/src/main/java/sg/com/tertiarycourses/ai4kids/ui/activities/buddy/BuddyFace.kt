package sg.com.tertiarycourses.ai4kids.ui.activities.buddy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * The Talking Buddy — a friendly robot whose mouth opens while it speaks and who
 * blinks and bobs gently. A Compose-Canvas port of the website's SVG buddy
 * (`TalkingBuddy.tsx`), drawn in a 200×210 viewbox. Colours are derived from the
 * brand [Theme] palette (softened via `lerp`) rather than raw hex.
 *
 * @param mouthOpen 0 (closed) … 1 (wide) — drive from the speaking flag.
 * @param blink true squashes the eyes shut for a frame.
 */
@Composable
fun BuddyFace(mouthOpen: Float, blink: Boolean, modifier: Modifier = Modifier) {
    // Gentle idle bob so the buddy feels alive even when quiet.
    val bob = rememberInfiniteTransition(label = "buddyBob")
    val dy by bob.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "buddyBobY",
    )

    val body = lerp(Theme.Blue, Color.White, 0.60f)
    val bolt = lerp(Theme.Blue, Color.White, 0.35f)
    val stem = lerp(Theme.Purple, Color.White, 0.50f)
    val mouthColor = lerp(Theme.Pink, Theme.Ink, 0.55f)
    val tongue = lerp(Theme.Pink, Color.White, 0.30f)
    val cheek = Theme.Pink.copy(alpha = 0.35f)

    Canvas(modifier = modifier.graphicsLayer { translationY = dy }) {
        val s = size.width / 200f
        fun pt(x: Float, y: Float) = Offset(x * s, y * s)
        fun dot(x: Float, y: Float, r: Float, c: Color) = drawCircle(c, r * s, pt(x, y))
        fun oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color) =
            drawOval(c, topLeft = pt(cx - rx, cy - ry), size = Size(2 * rx * s, 2 * ry * s))
        fun rrect(x: Float, y: Float, w: Float, h: Float, rc: Float, c: Color) =
            drawRoundRect(c, topLeft = pt(x, y), size = Size(w * s, h * s), cornerRadius = CornerRadius(rc * s, rc * s))
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, c: Color) =
            drawLine(c, pt(x1, y1), pt(x2, y2), strokeWidth = w * s, cap = StrokeCap.Round)

        // Antennae with glowing tips
        line(72f, 40f, 64f, 16f, 5f, stem)
        dot(64f, 13f, 7f, Theme.Yellow)
        line(128f, 40f, 136f, 16f, 5f, stem)
        dot(136f, 13f, 7f, Theme.Pink)

        // Head + soft highlight
        rrect(24f, 36f, 152f, 150f, 48f, body)
        rrect(24f, 36f, 152f, 72f, 40f, Color.White.copy(alpha = 0.35f))

        // Side bolts / ears
        dot(22f, 112f, 10f, bolt)
        dot(178f, 112f, 10f, bolt)

        // Eyes — white base squashes shut on a blink
        val eyeRy = if (blink) 3f else 24f
        oval(76f, 100f, 22f, eyeRy, Color.White)
        oval(124f, 100f, 22f, eyeRy, Color.White)
        if (!blink) {
            dot(80f, 102f, 10f, Theme.Ink)
            dot(120f, 102f, 10f, Theme.Ink)
            dot(84f, 98f, 3.5f, Color.White)
            dot(124f, 98f, 3.5f, Color.White)
        }

        // Rosy cheeks
        dot(58f, 138f, 12f, cheek)
        dot(142f, 138f, 12f, cheek)

        // Mouth — a warm pill that opens when talking; a little tongue peeks out
        val m = mouthOpen.coerceIn(0f, 1f)
        rrect(80f, 140f - m * 9f, 40f, 10f + m * 34f, 14f, mouthColor)
        if (m > 0.3f) oval(100f, 150f + m * 14f, 10f, 3f + m * 6f, tongue)
    }
}
