package sg.com.tertiarycourses.ai4kids.ui.components
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import sg.com.tertiarycourses.ai4kids.R

/** Site-owned, full-colour artwork. Vector names are semantic inputs only. */
@Composable
fun SunnyIcon(imageVector: ImageVector, contentDescription:String?, modifier:Modifier=Modifier, tint:Color=Color.Unspecified) {
 val n=imageVector.name.lowercase()
 val resource=when {
  "close" in n || "delete" in n -> R.drawable.kid_close
  "star" in n || "celebrat" in n -> R.drawable.kid_sparkles
  "check" in n || "done" in n -> R.drawable.kid_check
  "arrowback" in n || "chevronleft" in n || "undo" in n -> R.drawable.kid_left
  "arrow" in n || "chevronright" in n || "refresh" in n -> R.drawable.kid_right
  "play" in n -> R.drawable.kid_play
  "pause" in n || "stop" in n -> R.drawable.kid_pause
  "book" in n || "stories" in n -> R.drawable.kid_book
  "brush" in n || "palette" in n || "photo" in n -> R.drawable.kid_art
  "group" in n || "person" in n -> R.drawable.kid_home
  "lock" in n || "shield" in n -> R.drawable.kid_shield
  "send" in n -> R.drawable.kid_send
  "mic" in n || "volume" in n || "chat" in n -> R.drawable.kid_chat
  "extension" in n || "search" in n -> R.drawable.kid_maths
  "face" in n -> R.drawable.kid_science
  "text" in n -> R.drawable.kid_wand
  "meetingroom" in n || "psychology" in n -> R.drawable.kid_bag
  else -> R.drawable.kid_sun
 }
 val angle=when { "arrowup" in n || "chevronup" in n -> -90f; "arrowdown" in n || "chevrondown" in n -> 90f; else -> 0f }
 Image(painterResource(resource),contentDescription,modifier=modifier.graphicsLayer(rotationZ=angle))
}
