package sg.com.tertiarycourses.ai4kids.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
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
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * Parents' Corner — explains the no-data-collection stance and offers a reset.
 * Android port of the iOS `ParentsCornerView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentsCornerSheet(onDismiss: () -> Unit) {
    val progress = LocalProgressStore.current
    var confirmReset by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Parents' Corner", color = Theme.Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)

            SectionTitle("About AI4Kids")
            InfoRow(Icons.Filled.WifiOff, "Learning activities & solo card games play fully offline — no login needed")
            InfoRow(Icons.Filled.Block, "No ads, ever")
            InfoRow(Icons.Filled.ChildCare, "Designed for ages 4–16")

            SectionTitle("Brain Arcade (online play)")
            InfoRow(Icons.Filled.Groups, "Optional multiplayer card games — play with friends using a room code")
            InfoRow(Icons.Filled.Lock, "Online multiplayer requires signing in with an ai4kids kid account; solo play never does")
            InfoRow(Icons.Filled.Wifi, "Online games connect to ai4kids only to sync play between friends")

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
