package sg.com.tertiarycourses.ai4kids.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import sg.com.tertiarycourses.ai4kids.R

/**
 * Shared, kid-friendly visual styling tokens — the Android counterpart of the
 * iOS app's `Theme`. Bright, rounded, high-contrast colors tuned for young
 * learners (ages 4–16).
 */
object Theme {
    // Brand palette (AI4Kids — playful, saturated). Mirrors the iOS values.
    val Purple = Color(0xFF8653A4)
    val Pink = Color(0xFFB53C72)
    val Orange = Color(0xFFB07A35)
    val Yellow = Color(0xFFFFDA70)
    val Green = Color(0xFF38866C)
    val Blue = Color(0xFF438EAE)
    val Teal = Color(0xFF31877D)
    val Red = Color(0xFFBF4D74)

    val Ink = Color(0xFF505D87)

    val BodyFont = FontFamily(Font(R.font.aikidsbody_regular, FontWeight.Normal), Font(R.font.aikidsbody_bold, FontWeight.Bold))
    val DisplayFont = FontFamily(Font(R.font.aikidsdisplay_bold, FontWeight.Bold))

    val CardCornerRadius = 28.dp
    val BigCornerRadius = 36.dp

    val CardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 20.dp, bottomEnd = 32.dp, bottomStart = 24.dp)
    val BigShape = RoundedCornerShape(BigCornerRadius)

    /** Soft, friendly drop shadow color/elevation used on cards and buttons. */
    val SoftShadowElevation = 4.dp

    /** Text shadow used for headings drawn over colored surfaces. */
    val SoftTextShadow = Shadow(
        color = Color.Black.copy(alpha = 0.18f),
        offset = Offset(0f, 4f),
        blurRadius = 8f,
    )

    /** The app-wide warm background gradient. */
    val Background = Brush.linearGradient(
        colors = listOf(
            Color(0xFFEDF7FF),
            Color(0xFFF4EFFF),
        ),
    )
}

private val AppColorScheme = lightColorScheme(
    primary = Theme.Purple,
    secondary = Theme.Pink,
    background = Color(0xFFEDF7FF),
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Theme.Ink,
    onSurface = Theme.Ink,
)

@Composable
fun AI4KidsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography().let { t -> t.copy(
            displayLarge=t.displayLarge.copy(fontFamily=Theme.DisplayFont),displayMedium=t.displayMedium.copy(fontFamily=Theme.DisplayFont),displaySmall=t.displaySmall.copy(fontFamily=Theme.DisplayFont),
            headlineLarge=t.headlineLarge.copy(fontFamily=Theme.DisplayFont),headlineMedium=t.headlineMedium.copy(fontFamily=Theme.DisplayFont),headlineSmall=t.headlineSmall.copy(fontFamily=Theme.DisplayFont),
            titleLarge=t.titleLarge.copy(fontFamily=Theme.DisplayFont),titleMedium=t.titleMedium.copy(fontFamily=Theme.BodyFont),titleSmall=t.titleSmall.copy(fontFamily=Theme.BodyFont),
            bodyLarge=t.bodyLarge.copy(fontFamily=Theme.BodyFont,fontSize=19.sp),bodyMedium=t.bodyMedium.copy(fontFamily=Theme.BodyFont,fontSize=17.sp),bodySmall=t.bodySmall.copy(fontFamily=Theme.BodyFont,fontSize=16.sp),
            labelLarge=t.labelLarge.copy(fontFamily=Theme.BodyFont),labelMedium=t.labelMedium.copy(fontFamily=Theme.BodyFont),labelSmall=t.labelSmall.copy(fontFamily=Theme.BodyFont)
        ) },
        content = content,
    )
}
