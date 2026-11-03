package sg.com.tertiarycourses.ai4kids.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import sg.com.tertiarycourses.ai4kids.ui.components.SunnyIcon as Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.data.ParentAuth
import sg.com.tertiarycourses.ai4kids.ui.components.KidSecondaryButton
import sg.com.tertiarycourses.ai4kids.ui.workshops.ParentTask
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * Parents' Corner — explains the privacy stance, manages the AI Workshops
 * parent login (status, Lock now, Replace key, Change PIN, Remove key & turn
 * off AI) and offers a progress reset.
 * Android port of the iOS `ParentsCornerView`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ParentsCornerSheet(onDismiss: () -> Unit, onParentTask: (ParentTask) -> Unit) {
    val progress = LocalProgressStore.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmRemoveAi by remember { mutableStateOf(false) }
    val aiConfigured = ParentAuth.isConfigured
    val aiUnlocked = ParentAuth.unlockedUntil > 0 && ParentAuth.isUnlocked()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Theme.CardFill) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Parents' Corner", color = Theme.Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)

            SectionTitle("About AI4Kids")
            InfoRow(Icons.Filled.WifiOff, "Learning activities & solo card games play fully offline — no login needed")
            InfoRow(Icons.Filled.Block, "No ads, ever")
            InfoRow(Icons.Filled.ChildCare, "Designed for ages 4–16")

            SectionTitle("Privacy")
            Text(
                "The offline play activities work without an account and collect nothing. The optional " +
                    "AI Workshops are off until a parent sets a PIN and adds their own DeepSeek API key. " +
                    "When on, only the text your child types in a workshop is sent to DeepSeek to generate " +
                    "a reply. We don't run ads or analytics, and we never see your key or your child's " +
                    "workshop messages. Online card games and co-op rooms are separate and need a parent sign-in.",
                color = Theme.BodyInk,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )

            SectionTitle("AI Workshops")
            InfoRow(
                Icons.Filled.Lock,
                when {
                    !aiConfigured -> "Off — not set up. Set a parent PIN and add your own DeepSeek key to turn them on."
                    ParentAuth.keyNeedsReplacing -> "On — but DeepSeek rejected the saved key. Replace it to continue."
                    aiUnlocked -> "On · Unlocked (locks when the app closes, after 30 minutes, or when you tap Lock now)"
                    else -> "On · Locked — your PIN opens them"
                },
            )
            InfoRow(Icons.Filled.Wifi, "Only what your child types in a workshop goes to DeepSeek (api.deepseek.com). Chats are never saved.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!aiConfigured) {
                    KidSecondaryButton("Set up AI Workshops", fontSize = 16.sp) { onParentTask(ParentTask.UNLOCK) }
                } else {
                    if (aiUnlocked) KidSecondaryButton("Lock now", fontSize = 16.sp) { ParentAuth.lock() }
                    KidSecondaryButton("Replace key", fontSize = 16.sp) { onParentTask(ParentTask.REPLACE_KEY) }
                    KidSecondaryButton("Change PIN", fontSize = 16.sp) { onParentTask(ParentTask.CHANGE_PIN) }
                }
            }
            if (aiConfigured) {
                TextButton(onClick = { confirmRemoveAi = true }) {
                    Text("Remove key & turn off AI", color = Theme.Red, fontWeight = FontWeight.Bold)
                }
            }

            SectionTitle("Brain Arcade (online play)")
            InfoRow(Icons.Filled.Groups, "Optional multiplayer card games — play with friends using a room code")
            InfoRow(Icons.Filled.Lock, "A parent signs in and chooses their own child's profile for online multiplayer; solo play never needs an account")
            InfoRow(Icons.Filled.Wifi, "Online games connect to ai4kids only to sync play between friends")

            SectionTitle("Create with AI together")
            InfoRow(Icons.Filled.Lock, "AI art, Talking Buddy and writing your own story open the family website after a grown-up check")
            InfoRow(Icons.Filled.Wifi, "The website needs parent sign-in and internet access; its privacy policy applies")

            SectionTitle("Progress")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Total stars earned", color = Theme.Ink, fontSize = 17.sp)
                Text(
                    "${progress.totalStars}",
                    color = Theme.Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Theme.Red, modifier = Modifier.size(22.dp))
                TextButton(onClick = { confirmReset = true }) {
                    Text("Reset all progress", color = Theme.Red, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (confirmRemoveAi) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAi = false },
            title = { Text("Turn off AI Workshops?") },
            text = { Text("This deletes the parent PIN, your DeepSeek key and your consent from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    ParentAuth.wipe()
                    confirmRemoveAi = false
                }) { Text("Remove & turn off", color = Theme.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveAi = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = { Text("This clears every star earned. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    progress.resetAll()
                    confirmReset = false
                }) { Text("Reset", color = Theme.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Theme.Ink.copy(alpha = 0.5f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(icon, contentDescription = null, tint = Theme.Purple, modifier = Modifier.size(24.dp))
        Text(text, color = Theme.Ink, fontSize = 16.sp)
    }
}
