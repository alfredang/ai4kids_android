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
    // Brand palette — matches the AI Kids website (ai4kids.tertiarycourses.com.sg).
    // Mirrors the iOS values.
    val Purple = Color(0xFF80549F)
    val Pink = Color(0xFFB53C72)
    val Orange = Color(0xFFB07A35)
    val Yellow = Color(0xFFFFDC69)
    val Green = Color(0xFF38866C)
    val Blue = Color(0xFF438EAE)
    val Teal = Color(0xFF268081)
    val Red = Color(0xFFBF4D74)

    /** Deep ink — headings and primary text on cream. */
    val Ink = Color(0xFF41467D)
    /** Deepest ink, for the strongest contrast (button labels, borders). */
    val DeepInk = Color(0xFF343C79)
    /** Softer body ink for longer paragraphs. */
    val BodyInk = Color(0xFF655184)
    /** Plum — the website's primary button fill. */
    val Plum = Color(0xFF99499B)
    /** Soft website pink (decorative fills only; too light for text). */
    val Coral = Color(0xFFF89B9D)

    /** Warm cream page colour, card fill and secondary-button fill. */
    val Cream = Color(0xFFFFF8EE)
    val CardFill = Color(0xFFFFFEFA)
    val ButtonCream = Color(0xFFFFFDF7)
    /** Text-input border (website form fields). */
    val InputBorder = Color(0xFFC5B6DA)

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

    /** Website-style secondary button shape: slightly uneven corners (18/22/17/20). */
    val SecondaryButtonShape = RoundedCornerShape(topStart = 18.dp, topEnd = 22.dp, bottomEnd = 17.dp, bottomStart = 20.dp)
    /** Website form-field shape. */
    val InputShape = RoundedCornerShape(12.dp)

    /** The app-wide warm cream background gradient (website `#FFF8EE` → `#FFF3DC`). */
    val Background = Brush.linearGradient(
        colors = listOf(
            Cream,
            Color(0xFFFFF3DC),
        ),
    )
}

private val AppColorScheme = lightColorScheme(
    primary = Theme.Purple,
    secondary = Theme.Pink,
    background = Theme.Cream,
    surface = Theme.CardFill,
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
