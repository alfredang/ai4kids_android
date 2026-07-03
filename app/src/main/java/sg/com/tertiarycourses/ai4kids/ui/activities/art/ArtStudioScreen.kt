package sg.com.tertiarycourses.ai4kids.ui.activities.art

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sg.com.tertiarycourses.ai4kids.ai.ArtEngine
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/** Stars awarded for painting a picture (the jigsaw adds its own on top). */
private const val ART_STARS = 3

/**
 * The AI Art Studio: describe a picture, pick a style, and the AI paints it
 * (Gemini "Nano Banana", Cloudflare Flux fallback). The result can be turned into
 * a jigsaw. Android port of the website's `/learn/art`.
 */
@Composable
fun ArtStudioScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val accent = Activity.ART.color

    if (!ArtEngine.isConfigured()) {
        NotConfigured(onClose, accent)
        return
    }

    val progress = LocalProgressStore.current
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(ArtEngine.STYLE_CHIPS.first().key) }
    var loading by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var notice by remember { mutableStateOf("") }
    var scored by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }

    fun generate() {
        if (prompt.isBlank() || loading) return
        loading = true; notice = ""; bitmap = null; scored = false; playing = false
        scope.launch {
            when (val res = ArtEngine.generate(prompt.trim(), style)) {
                is ArtEngine.Result.Success -> {
                    bitmap = res.bitmap
                    progress.award(ART_STARS, Activity.ART)
                    scored = true
                }
                is ArtEngine.Result.Blocked -> notice = res.message
                is ArtEngine.Result.Unavailable -> notice = res.message
            }
            loading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                StarBadge(count = progress.totalStars)
            }

            // Prompt + styles.
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().kidCard().padding(20.dp),
            ) {
                Text("🎨 AI Art Studio", color = Theme.Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Describe a picture and the AI will paint it for you!",
                    color = Theme.Ink.copy(alpha = 0.6f), fontSize = 16.sp,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it.take(200) },
                    placeholder = { Text("I want a picture of…") },
                    minLines = 2,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Theme.Ink.copy(alpha = 0.15f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowChips(ArtEngine.IDEAS) { prompt = it }
                Text("Pick a style", color = Theme.Ink.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                StyleChips(selected = style, accent = accent) { style = it }
                KidButton(
                    title = if (loading) "Painting… 🖌️" else "Make my picture! 🪄",
                    icon = Icons.Filled.AutoAwesome,
                    color = accent,
                    enabled = prompt.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { generate() },
                )
                if (notice.isNotEmpty()) {
                    Text(notice, color = Theme.Orange, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Result.
            val bmp = bitmap
            if (bmp != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().kidCard().padding(18.dp),
                ) {
                    if (scored) {
                        Text(
                            "+$ART_STARS ⭐ earned! 🎉",
                            color = Theme.Green, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Theme.Green.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    if (!playing) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = prompt,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                        )
                        KidButton(
                            title = "Play a puzzle 🧩",
                            color = Theme.Purple,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { playing = true },
                        )
                    } else {
                        JigsawBoard(image = bmp.asImageBitmap(), onSolved = { stars -> progress.award(stars, Activity.ART) })
                        Text(
                            "← Back to my picture",
                            color = Theme.Ink.copy(alpha = 0.5f), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clickable { playing = false }.padding(4.dp),
                        )
                    }
                    KidButton(
                        title = "Make another! 🎨",
                        color = Theme.Blue,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { bitmap = null; scored = false; playing = false; prompt = "" },
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleChips(selected: String, accent: Color, onPick: (String) -> Unit) {
    // Two rows so five chips fit comfortably on a small phone.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ArtEngine.STYLE_CHIPS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { chip ->
                    val on = chip.key == selected
                    Text(
                        "${chip.emoji} ${chip.label}",
                        color = if (on) Color.White else Theme.Ink.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) accent else Color.White)
                            .then(if (on) Modifier else Modifier.softShadow(RoundedCornerShape(16.dp)))
                            .clickable { onPick(chip.key) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowChips(items: List<String>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { idea ->
                    Text(
                        "✨ $idea",
                        color = Theme.Ink.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Theme.Orange.copy(alpha = 0.10f))
                            .clickable { onPick(idea) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NotConfigured(onClose: () -> Unit, accent: Color) {
    Box(
        modifier = Modifier.fillMaxSize().background(Theme.Background).systemBarsPadding().padding(18.dp),
    ) {
        CloseButton(onClick = onClose)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().kidCard().padding(28.dp),
        ) {
            Text("🎨", fontSize = 72.sp)
            Text("Art Studio needs a grown-up", color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(
                "Ask a grown-up to add an AI key so you can paint with AI!",
                color = Theme.Ink.copy(alpha = 0.6f), fontSize = 17.sp, textAlign = TextAlign.Center,
            )
            KidButton(title = "Okay", color = accent, onClick = onClose)
        }
    }
}