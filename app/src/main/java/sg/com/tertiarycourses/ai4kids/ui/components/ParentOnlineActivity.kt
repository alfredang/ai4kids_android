package sg.com.tertiarycourses.ai4kids.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.com.tertiarycourses.ai4kids.cards.ParentalGate
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

enum class FamilyOnlineActivity(val title: String, val path: String, val picture: String) {
    ART("AI Art Studio", "/learn/art", "🎨"),
    BUDDY("Talking Buddy", "/learn/buddy", "🤖"),
    STORY("Write your own story", "/learn/storytelling", "📖"),
}

/** Fixed first-party destinations; no account details are put in the browser URL. */
@Composable
fun ParentOnlineActivity(activity: FamilyOnlineActivity, onClose: () -> Unit) {
    val context = LocalContext.current
    var showGate by remember { mutableStateOf(false) }
    var browserUnavailable by remember { mutableStateOf(false) }
    BackHandler { if (showGate) showGate = false else onClose() }
    if (showGate) {
        ParentalGate(
            headerTitle = activity.title,
            cardTitle = "A grown-up, please",
            message = "This opens the AI Kids family website in your browser. A parent signs in and chooses their child's profile before playing online. The website's privacy policy applies.",
            confirmLabel = "I'm a grown-up — open website",
            onClose = { showGate = false },
            onConsent = {
                showGate = false
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ai4kids.tertiarycourses.com.sg" + activity.path)))
                } catch (_: ActivityNotFoundException) {
                    browserUnavailable = true
                }
            },
        )
        return
    }
    Box(Modifier.fillMaxSize().background(Theme.Background).systemBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) { CloseButton(onClick = onClose) }
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.widthIn(max = 660.dp).fillMaxWidth().kidCard().padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SunnySticker(activity.picture, 130)
                Text(activity.title, fontFamily = Theme.DisplayFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Theme.Purple, textAlign = TextAlign.Center)
                Text("Create and explore together with your grown-up.", fontSize = 21.sp, color = Theme.Ink, textAlign = TextAlign.Center)
                KidButton(title = "Play online with a parent", onClick = { showGate = true })
                Text("Opens the family website in your browser. Parent sign-in is required.", fontSize = 17.sp, color = Theme.Ink, textAlign = TextAlign.Center)
                if (browserUnavailable) Text("Ask your grown-up to open ai4kids.tertiarycourses.com.sg in a browser.", fontSize = 18.sp, color = Theme.Purple, textAlign = TextAlign.Center)
            }
        }
    }
}
