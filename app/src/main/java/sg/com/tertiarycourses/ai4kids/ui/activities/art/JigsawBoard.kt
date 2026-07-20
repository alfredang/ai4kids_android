package sg.com.tertiarycourses.ai4kids.ui.activities.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

private data class Diff(val name: String, val grid: Int, val stars: Int)

private val DIFFS = listOf(
    Diff("Easy", 3, 2),
    Diff("Medium", 4, 3),
    Diff("Hard", 5, 5),
)

private fun shuffledPieces(n: Int): List<Int> = (0 until n).shuffled()

/**
 * A tap-to-place jigsaw of the generated artwork — pick a piece from the tray,
 * tap a board slot to drop it (swapping any piece already there). Compose port of
 * the website's `JigsawBoard.tsx`. Calls [onSolved] with the star reward once the
 * board matches the picture.
 */
@Composable
fun JigsawBoard(image: ImageBitmap, onSolved: (Int) -> Unit) {
    var diff by remember { mutableStateOf(DIFFS[0]) }
    var board by remember { mutableStateOf(List<Int?>(diff.grid * diff.grid) { null }) }
    var tray by remember { mutableStateOf(shuffledPieces(diff.grid * diff.grid)) }
    // sel: Pair(fromTray, position)
    var sel by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var peek by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }

    fun newGame(d: Diff) {
        diff = d
        board = List(d.grid * d.grid) { null }
        tray = shuffledPieces(d.grid * d.grid)
        sel = null; peek = false; solved = false
    }

    fun tapTray(i: Int) {
        if (solved) return
        sel = if (sel?.first == true && sel?.second == i) null else true to i
    }

    fun tapSlot(d: Int) {
        if (solved) return
        val s = sel
        if (s == null) {
            if (board[d] != null) sel = false to d // pick up a placed piece
            return
        }
        val nextBoard = board.toMutableList()
        val nextTray = tray.toMutableList()
        val moving = if (s.first) nextTray[s.second] else nextBoard[s.second]!!
        val displaced = nextBoard[d]
        nextBoard[d] = moving
        if (s.first) {
            nextTray.removeAt(s.second)
            if (displaced != null) nextTray.add(displaced)
        } else {
            nextBoard[s.second] = displaced
        }
        board = nextBoard
        tray = nextTray
        sel = null
        if (nextTray.isEmpty() && nextBoard.indices.all { nextBoard[it] == it }) {
            solved = true
            onSolved(diff.stars)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        // Difficulty + peek.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DIFFS.forEach { d ->
                Chip("${d.name} (${d.grid}×${d.grid})", selected = diff.grid == d.grid, color = Theme.Orange) { newGame(d) }
            }
            Text(
                if (peek) "Hide 👀" else "Peek 👀",
                color = Theme.Blue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Theme.Blue.copy(alpha = 0.12f))
                    .clickable { peek = !peek }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // Board.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .softShadow(RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Theme.Orange.copy(alpha = 0.20f))
                .padding(3.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                for (r in 0 until diff.grid) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        for (c in 0 until diff.grid) {
                            val slot = r * diff.grid + c
                            val home = board[slot]
                            val picked = sel?.first == false && sel?.second == slot
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(if (home == null) Modifier.background(Color.White.copy(alpha = 0.5f)) else Modifier)
                                    .then(if (picked) Modifier.border(3.dp, Theme.Pink, RoundedCornerShape(6.dp)) else Modifier)
                                    .clickable { tapSlot(slot) },
                            ) {
                                if (home != null) PieceTile(image, home, diff.grid, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            if (peek) {
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = null,
                    alpha = 0.3f,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)),
                )
            }
        }

        // Tray — chunked into rows so up to 25 pieces wrap.
        if (tray.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().kidCardTray().padding(12.dp),
            ) {
                tray.chunked(5).forEachIndexed { rowIdx, rowPieces ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowPieces.forEachIndexed { colIdx, home ->
                            val pos = rowIdx * 5 + colIdx
                            val picked = sel?.first == true && sel?.second == pos
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(if (picked) Modifier.border(3.dp, Theme.Pink, RoundedCornerShape(8.dp)) else Modifier)
                                    .clickable { tapTray(pos) },
                            ) { PieceTile(image, home, diff.grid, Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
        }

        if (solved) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Theme.Green.copy(alpha = 0.18f))
                    .padding(16.dp),
            ) {
                Text("You solved it! +${diff.stars} ⭐", color = Theme.Green, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Chip("Play again 🔁", selected = false, color = Theme.Blue) { newGame(diff) }
            }
        }
    }
}

/** One jigsaw piece — the [home]-th tile of the image cropped from a [grid]×[grid] slice. */
@Composable
private fun PieceTile(image: ImageBitmap, home: Int, grid: Int, modifier: Modifier) {
    val col = home % grid
    val row = home / grid
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val pieceW = image.width / grid
        val pieceH = image.height / grid
        drawImage(
            image = image,
            srcOffset = IntOffset(col * pieceW, row * pieceH),
            srcSize = IntSize(pieceW, pieceH),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        label,
        color = if (selected) Color.White else Theme.Ink.copy(alpha = 0.6f),
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            // softShadow BEFORE clip/background — the order `kidCardTray` below
            // encodes. Reversed, the shadow's layer wraps only the label and paints
            // a hard-edged white box over the rounded fill.
            .then(if (selected) Modifier else Modifier.softShadow(shape))
            .clip(shape)
            .background(if (selected) color else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun Modifier.kidCardTray(): Modifier = this
    .softShadow(RoundedCornerShape(18.dp))
    .clip(RoundedCornerShape(18.dp))
    .background(Color.White)