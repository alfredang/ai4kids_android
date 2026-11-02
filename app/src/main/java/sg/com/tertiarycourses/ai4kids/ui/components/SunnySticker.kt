package sg.com.tertiarycourses.ai4kids.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import sg.com.tertiarycourses.ai4kids.R

/** Original website artwork for decorative menu pictures. Adjacent text supplies the label. */
@Composable
fun SunnySticker(symbol: String, size: Int, modifier: Modifier = Modifier) {
    val artwork = when (symbol) {
        "📖", "✨📖✨" -> R.drawable.friend_story
        "🎨" -> R.drawable.friend_art
        "🤖" -> R.drawable.friend_science
        "🪄", "🅰️", "🅰" -> R.drawable.kid_wand
        "✍️", "✍" -> R.drawable.kid_book
        "▶" -> R.drawable.kid_play
        "🏆", "⭐", "⭐️", "🎉" -> R.drawable.kid_sparkles
        "🔓", "🔒" -> R.drawable.kid_shield
        "🏁" -> R.drawable.kid_check
        "🃏", "🎴", "🧠" -> R.drawable.kid_bag
        "🐾", "🐶", "🐱" -> R.drawable.kid_science
        else -> R.drawable.friend_maths
    }
    Image(painterResource(artwork), contentDescription = null, modifier = modifier.size(size.dp))
}
