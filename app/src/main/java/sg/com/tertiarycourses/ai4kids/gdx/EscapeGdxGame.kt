package sg.com.tertiarycourses.ai4kids.gdx

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import sg.com.tertiarycourses.ai4kids.escape.CoopSession
import sg.com.tertiarycourses.ai4kids.escape.EscapeState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Robot Lab — a top-down LibGDX escape room, rebuilt from the Robot Lab in
 * alfredang/ai4kids (`src/lib/escape-rooms.ts`). Drive the explorer with a
 * floating joystick, walk up to each machine and solve its puzzle. The three
 * machines each light up a secret word on the Word Display; find all three in
 * the word search and the square where they cross gives the exit-door code.
 *
 * The four puzzle *interactions* are copied faithfully:
 *   • Control Panel  — count the robots and key in the number   (code)
 *   • Robot Helper   — tap the 3 "how machines learn" steps in order (order)
 *   • Symbol Decoder — read the symbol→letter key, decode GEAR   (cipher)
 *   • Word Display   — drag to find ROBOT / LEARN / GEAR; they cross at one cell
 *   • Exit Keypad    — key in the crossing square's Column + Row (= 45)
 * Emoji art from the source is drawn here as simple shape pictograms.
 *
 * [onFinish] is called with the stars earned (0 if the player just backs out).
 */
class EscapeGdxGame(
    /** Which escape-room map to play (index into [levels]). */
    private val levelIndex: Int = 0,
    /** When non-null, the game runs as a co-op session (shared solved set). */
    private val coop: CoopSession? = null,
    private val onFinish: (Int) -> Unit,
) : ApplicationAdapter() {

    private companion object {
        const val SPEED = 230f
        const val JOY_RADIUS = 72f
        const val CHAR_R = 22f

        // The Control Panel "count the robots" field: -1 = robot, else glyph kind.
        // Six robots hide among the other machines (matches the source clue row).
        val ROBOT_FIELD = listOf(2, -1, -1, 1, -1, 0, -1, 7, 4, -1, 2, -1)
    }

    // Puzzle design canvas. The height is fixed at 800 (so every puzzle's vertical
    // layout is orientation-independent); the width widens in landscape so the
    // overlay fills the screen instead of letterboxing into a portrait card. The
    // panel and content centre on W, so wider W just gives more room to spread.
    private var W = 480f
    private val H = 800f
    private val landscapePanel get() = W > 700f // the wide-canvas (landscape) layout
    // In landscape the panel is a centred wide card (capped) with the dim filling
    // the rest of the screen; in portrait it's the usual near-full-width card.
    private val PANEL_W get() = if (landscapePanel) minOf(W - 80f, 1040f) else W - 56f
    private val PANEL_X get() = if (landscapePanel) (W - PANEL_W) / 2f else 28f
    private val PANEL_Y = 130f
    private val PANEL_H = 540f

    private val cInk = Color(0.16f, 0.14f, 0.30f, 1f)
    // Interactive accent (keypad keys, tiles, badges, the joystick/action button):
    // follows the level theme so every widget feels at home in its world.
    private val cAccent get() = currentLevel.cardBtn
    private val confettiColors = arrayOf(
        Color(1f, 0.82f, 0.20f, 1f), Color(0.98f, 0.45f, 0.55f, 1f), Color(0.40f, 0.82f, 0.95f, 1f),
        Color(0.55f, 0.85f, 0.45f, 1f), Color(1f, 0.65f, 0.30f, 1f),
    )
    private val cGood = Color(0.20f, 0.78f, 0.45f, 1f)
    private val cSlot = Color(0.86f, 0.86f, 0.92f, 1f)
    private val cOpen = Color(0.93f, 0.93f, 0.97f, 1f)

    // Floor-texture overlay palette (subtle grooves/highlights painted over each
    // room's floor fill — the LibGDX counterpart to the web rooms' FLOOR_TEXTURE).
    private val texGroove = Color(0f, 0f, 0f, 0.20f)      // generic dark seam/joint
    private val texStone = Color(0.30f, 0.18f, 0.08f, 0.22f) // warm flagstone joint
    private val texWood = Color(0.20f, 0.10f, 0.02f, 0.24f)  // plank seam
    private val texHi = Color(0.90f, 0.92f, 1f, 0.10f)   // top-left tread highlight
    private val texLo = Color(0f, 0f, 0f, 0.18f)         // bottom-right tread shade
    private val texMottle = Color(0f, 0f, 0f, 0.05f)     // soft slab mottle
    private val texPanel = Color(0.46f, 0.50f, 0.92f, 0.16f)  // indigo hero-HQ lattice
    private val texPanelDot = Color(0.60f, 0.64f, 1f, 0.22f)  // lattice node
    private val texGrout = Color(0f, 0f, 0f, 0.12f)      // fine ceramic grout
    private val texBandLo = Color(0f, 0f, 0f, 0.05f)     // plank shade band (dark row)
    private val texBandHi = Color(1f, 1f, 1f, 0.03f)     // plank shade band (light row)
    // Grass floor: a green lawn wash + soft two-tone mottle, then tufts of blades.
    private val grassWash = Color(0.22f, 0.44f, 0.14f, 0.62f)
    private val grassMottleLo = Color(0.10f, 0.28f, 0.05f, 0.30f)
    private val grassMottleHi = Color(0.46f, 0.63f, 0.22f, 0.24f)
    private val grassCols = arrayOf(
        Color(0.18f, 0.34f, 0.06f, 0.7f), Color(0.28f, 0.47f, 0.08f, 0.7f), Color(0.50f, 0.66f, 0.20f, 0.65f),
    )

    // Distinct colours for the 8 shape pictograms (cipher symbols & decoy icons).
    private val glyphColors = arrayOf(
        Color(0.95f, 0.55f, 0.20f, 1f), // 0 circle
        Color(0.30f, 0.66f, 0.95f, 1f), // 1 square
        Color(0.38f, 0.80f, 0.42f, 1f), // 2 triangle
        Color(0.92f, 0.42f, 0.62f, 1f), // 3 diamond
        Color(0.66f, 0.48f, 0.92f, 1f), // 4 ring
        Color(0.95f, 0.80f, 0.22f, 1f), // 5 plus
        Color(0.50f, 0.60f, 0.72f, 1f), // 6 cross
        Color(0.95f, 0.45f, 0.35f, 1f), // 7 star
    )

    /** Which Word-Display word each machine lights up once its puzzle is solved. */
    private val wordSource = mapOf("ROBOT" to "panel", "LEARN" to "robot", "GEAR" to "decoder")
    private fun isWordRevealed(word: String) = wordSource[word]?.let { it in solved } ?: true

    /* --------------------------- puzzle base --------------------------- */

    private abstract inner class Puzzle {
        abstract val instruction: String
        open fun onOpen() {}
        abstract fun onDown(p: Vector2)
        open fun onDrag(p: Vector2) {}
        open fun onUp(p: Vector2) {}
        abstract fun draw()
        abstract val complete: Boolean
    }

    /* --- Control Panel & Exit Keypad: a number lock --------------------- */

    private inner class NumberLock(
        private val code: String,
        private val heading: String,
        private val prompt: String,
        /** Count field icons (null = plain keypad). -1 = robot, else glyph kind. */
        private val icons: List<Int>? = null,
        private val showClues: Boolean = false,
    ) : Puzzle() {
        override val instruction = if (icons != null) "Count, then key in the number" else "Key in the code"
        private val entered = StringBuilder()
        private var done = false
        private val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "<", "0", "OK")
        private val cols = 3
        private val btn = 60f
        private val gapx = 16f
        private val gapy = 12f
        private val topRowY = 386f

        override fun onOpen() { entered.setLength(0); done = false }

        // Landscape puts the keypad on the right; portrait centres it (computed
        // from the current width so it follows the orientation-aware canvas).
        private fun keyRect(i: Int): FloatArray {
            val r = i / cols; val c = i % cols
            val kw = cols * btn + (cols - 1) * gapx
            // Landscape: with a count field the keypad sits right (field on the left);
            // with no field it centres and drops a little to make room for the question.
            val kx0 = if (!landscapePanel) (W - kw) / 2f else if (icons != null) W / 2f + 30f else (W - kw) / 2f
            val ky0 = if (!landscapePanel) topRowY else if (icons != null) 470f else 420f
            return floatArrayOf(kx0 + c * (btn + gapx), ky0 - r * (btn + gapy), btn, btn)
        }

        // Landscape arranges the count field just left of centre; portrait above the keypad.
        private fun iconCenter(i: Int): Vector2 {
            val cellW = 78f
            if (landscapePanel) {
                val c = i % 3; val r = i / 3
                return Vector2(W / 2f - 230f + c * 80f, 460f - r * 56f)
            }
            val c = i % 4; val r = i / 4
            val startX = (W - 4 * cellW) / 2f + cellW / 2f
            return Vector2(startX + c * cellW, 590f - r * 40f)
        }

        override fun onDown(p: Vector2) {
            keys.indices.forEach { i ->
                if (inRect(p, keyRect(i))) when (val k = keys[i]) {
                    "<" -> if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
                    "OK" -> if (entered.toString() == code) done = true else { wrongFlash = 1f; entered.setLength(0) }
                    else -> if (entered.length < code.length) entered.append(k)
                }
            }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            // Display slot — sits right above the keypad so the keyed-in number reads
            // together with the keys (centred over them in landscape; above in portrait).
            val slotX = if (!landscapePanel) (W - 200f) / 2f else if (icons != null) W / 2f + 36f else W / 2f - 100f
            val slotY = if (!landscapePanel) 452f else if (icons != null) 540f else 490f
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            shapes.color = Color(0.10f, 0.12f, 0.14f, 1f)                 // LCD bezel
            shapes.rect(slotX, slotY, 200f, 46f)
            shapes.color = Color(0.07f, 0.12f, 0.09f, 1f)                 // dark LCD screen
            shapes.rect(slotX + 4f, slotY + 4f, 192f, 38f)
            keys.indices.forEach { i ->
                val r = keyRect(i)
                shapes.color = when (keys[i]) { "OK" -> cGood; "<" -> Color(0.9f, 0.5f, 0.3f, 1f); else -> cAccent }
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            shapes.end()

            batch.begin()
            // Count-field pictograms (anti-aliased, drawn through the batch).
            icons?.forEachIndexed { i, k ->
                val c = iconCenter(i)
                if (k < 0) drawRobot(c.x, c.y, 15f) else drawGlyph(k, c.x, c.y, 14f)
            }
            font.color = cInk
            centerText(heading, W / 2f, 652f, 1.2f)
            if (icons != null) {
                // With a count field filling the body, keep the (short) prompt as a
                // header tagline — wrapped so it never runs under the X button.
                val promptW = if (landscapePanel) PANEL_W - 160f else 2f * (backCenter.x - backR - 10f - W / 2f)
                wrapText(prompt, W / 2f, 634f, promptW, 0.74f)
            } else {
                // No count field: the prompt is a real question, centred in the body
                // right above the display + keypad (together, not split off to one side).
                wrapText(prompt, W / 2f, if (landscapePanel) 590f else 588f, if (landscapePanel) PANEL_W - 200f else W - 110f, 0.95f)
            }
            if (showClues) {
                val cl = collectedClues()
                if (cl.isNotEmpty()) { font.color = cGood; centerText(cl.joinToString("   "), W / 2f, 540f, 0.78f) }
            }
            font.color = Color(0.45f, 0.95f, 0.55f, 1f)                 // glowing LCD digits
            centerText(if (entered.isEmpty()) "_" else entered.toString(), slotX + 100f, slotY + 16f, 1.3f)
            keys.indices.forEach { i ->
                val r = keyRect(i)
                font.color = Color.WHITE
                centerText(keys[i], r[0] + btn / 2f, r[1] + btn / 2f + 6f, if (keys[i].length > 1) 0.7f else 1.1f)
            }
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Try again!", W / 2f, 168f, 1f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Robot Helper: put the learning steps in order ------------------ */

    private inner class Order(
        private val steps: List<String>,
        private val heading: String = "Robot Helper",
        private val subtitle: String = "Teach the robot to spot cats",
    ) : Puzzle() {
        override val instruction = "Tap the steps in order"
        private var display = steps.indices.toList()
        private val seq = ArrayList<Int>() // chosen step indices, in the order tapped
        private val full get() = seq.size == steps.size

        override fun onOpen() {
            seq.clear()
            do { display = steps.indices.shuffled() } while (display == steps.indices.toList())
        }

        // Three cards, vertically centred between the heading and the footer.
        // Landscape spreads them as wide full-width rows; portrait stacks narrow.
        private fun cardRect(i: Int): FloatArray =
            if (landscapePanel) floatArrayOf(PANEL_X + 28f, 480f - i * 145f, PANEL_W - 56f, 120f)
            else floatArrayOf(40f, 470f - i * 118f, 400f, 100f)

        // Picks just queue up — no per-tap judging. The order is only checked once
        // every card is chosen (green if right, a red flash if wrong, then retry).
        override fun onDown(p: Vector2) {
            val j = display.indices.firstOrNull { inRect(p, cardRect(it)) } ?: return
            if (full) seq.clear()        // a completed (wrong) attempt is showing → start over
            val step = display[j]
            if (step in seq) seq.clear() // tap an already-chosen card to undo and restart
            else {
                seq.add(step)
                if (full && seq != steps.indices.toList()) wrongFlash = 1f // all chosen, wrong
            }
        }

        override val complete: Boolean get() = seq == steps.indices.toList()

        override fun draw() {
            val land = landscapePanel
            val badgeDx = if (land) 56f else 36f
            val badgeR = if (land) 30f else 20f
            val textDx = if (land) 110f else 68f

            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            val solved = complete
            val wrongSet = full && !solved && wrongFlash > 0f // all chosen but wrong → flash red
            display.indices.forEach { j ->
                val r = cardRect(j)
                val sel = display[j] in seq
                shapes.color = when {
                    solved -> cGood                            // every card correct → green
                    wrongSet -> Color(0.93f, 0.42f, 0.42f, 1f) // full but wrong → red
                    sel -> Color(0.74f, 0.80f, 0.93f, 1f)      // chosen, awaiting the rest (neutral)
                    else -> cOpen
                }
                shapes.rect(r[0], r[1], r[2], r[3])
                shapes.color = if (sel) Color.WHITE else cAccent
                shapes.circle(r[0] + badgeDx, r[1] + r[3] / 2f, badgeR)
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText(heading, W / 2f, 652f, if (land) 1.5f else 1.3f)
            centerText(subtitle, W / 2f, 623f, if (land) 1.0f else 0.85f)
            display.indices.forEach { j ->
                val r = cardRect(j)
                val sel = display[j] in seq
                // A chosen card shows its pick order; unchosen shows "?".
                font.color = if (complete) cGood else if (sel) cAccent else Color.WHITE
                val badge = if (sel) (seq.indexOf(display[j]) + 1).toString() else "?"
                centerText(badge, r[0] + badgeDx, r[1] + r[3] / 2f, if (land) 1.6f else 1.3f)
                // Step text left-aligned beside the badge, vertically centred.
                font.color = cInk
                wrapLeft(steps[display[j]], r[0] + textDx, r[1] + r[3] / 2f, r[2] - textDx - 30f, if (land) 1.35f else 0.98f)
            }
            val footY = if (land) 158f else 188f
            if (full && !complete && wrongFlash > 0f) {
                font.color = Color(0.93f, 0.42f, 0.42f, 1f)
                centerText("Wrong order — try again!", W / 2f, footY, if (land) 1.1f else 0.95f)
            } else {
                font.color = cInk
                centerText(instruction, W / 2f, footY, if (land) 1.1f else 0.95f)
            }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Symbol Decoder: substitution cipher ---------------------------- */

    private inner class Cipher(
        private val legendLetters: List<Char>, // the letters shown in the key (must include every answer letter)
        private val answer: String,
    ) : Puzzle() {
        override val instruction = "Read the key, spell the word"
        private val typed = StringBuilder()
        private var done = false
        // The glyph↔letter pairing is reshuffled each open so the answer letters
        // never sit in order in the key — glyph-kind k decodes to legend[k].
        private var legend = legendLetters
        private var coded = listOf<Int>()
        private var tileLetters = legendLetters + '<'

        override fun onOpen() {
            typed.setLength(0); done = false
            legend = legendLetters.shuffled()
            coded = answer.map { legend.indexOf(it) }
            tileLetters = legend + '<'
        }

        // Landscape stacks the legend in a 2x4 block on the left; portrait is a 4x2 row.
        private fun legendRect(k: Int): FloatArray {
            if (landscapePanel) {
                val cw = 120f; val ch = 42f
                val c = k % 2; val r = k / 2
                return floatArrayOf(PANEL_X + 50f + c * cw, 540f - r * (ch + 10f), cw - 8f, ch)
            }
            val c = k % 4; val r = k / 4
            val cellW = 104f; val cellH = 40f
            val x0 = (W - 4 * cellW) / 2f
            return floatArrayOf(x0 + c * cellW, 566f - r * (cellH + 6f), cellW - 8f, cellH)
        }

        // Landscape stacks the letter tiles in a 3x3 block on the right.
        private fun tileRect(i: Int): FloatArray {
            if (landscapePanel) {
                val b = 60f; val gx = 14f; val gy = 14f
                val c = i % 3; val r = i / 3
                val x0 = PANEL_X + PANEL_W - (3 * b + 2 * gx) - 60f
                return floatArrayOf(x0 + c * (b + gx), 460f - r * (b + gy), b, b)
            }
            val cols = 5; val c = i % cols; val r = i / cols
            val b = 56f; val gx = 12f; val gy = 10f
            val x0 = (W - (cols * b + (cols - 1) * gx)) / 2f
            return floatArrayOf(x0 + c * (b + gx), 330f - r * (b + gy), b, b)
        }

        override fun onDown(p: Vector2) {
            tileLetters.indices.forEach { i ->
                if (inRect(p, tileRect(i))) {
                    val ch = tileLetters[i]
                    if (ch == '<') { if (typed.isNotEmpty()) typed.deleteCharAt(typed.length - 1) }
                    else if (typed.length < answer.length) {
                        typed.append(ch)
                        if (typed.length == answer.length) {
                            if (typed.toString() == answer) done = true else { wrongFlash = 1f; typed.setLength(0) }
                        }
                    }
                }
            }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            // Legend cells.
            legend.indices.forEach { k ->
                val r = legendRect(k)
                shapes.color = cOpen
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            // Answer slots — filled (neutral) as you type, green only once correct.
            for (i in answer.indices) {
                shapes.color = if (done) cGood else if (i < typed.length) Color(0.72f, 0.74f, 0.84f, 1f) else cSlot
                shapes.rect((W - answer.length * 50f) / 2f + i * 50f + 4f, 395f, 42f, 42f)
            }
            // Letter tiles.
            tileLetters.indices.forEach { i ->
                val r = tileRect(i)
                shapes.color = if (tileLetters[i] == '<') Color(0.9f, 0.5f, 0.3f, 1f) else cAccent
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            shapes.end()

            batch.begin()
            // Symbol pictograms — legend keys + the coded message (anti-aliased).
            legend.indices.forEach { k ->
                val r = legendRect(k)
                drawGlyph(k, r[0] + 20f, r[1] + r[3] / 2f, 12f)
            }
            coded.forEachIndexed { i, k ->
                drawGlyph(k, (W - coded.size * 56f) / 2f + i * 56f + 28f, 462f, 16f)
            }
            font.color = cInk
            centerText("Symbol Decoder", W / 2f, 652f, 1.2f)
            legend.indices.forEach { k ->
                val r = legendRect(k)
                font.color = cInk
                centerText("= ${legend[k]}", r[0] + r[2] - 24f, r[1] + r[3] / 2f, 1.0f)
            }
            font.color = cInk
            centerText("Secret word:", W / 2f, 498f, 0.9f)
            font.color = if (done) cGood else cInk
            typed.forEachIndexed { i, ch ->
                centerText(ch.toString(), (W - answer.length * 50f) / 2f + i * 50f + 25f, 416f, 1.3f)
            }
            tileLetters.indices.forEach { i ->
                val r = tileRect(i)
                font.color = Color.WHITE
                centerText(tileLetters[i].toString(), r[0] + r[2] / 2f, r[1] + r[3] / 2f, 1.25f)
            }
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Not quite!", W / 2f, 150f, 1.05f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Word Display: word search with a crossing cell ----------------- */

    private inner class WordSearch(
        private val words: List<String>,
        private val grid: Array<CharArray>,
        private val crossRow: Int,
        private val crossCol: Int,
    ) : Puzzle() {
        override val instruction = "Drag across a glowing word"
        private val n = grid.size
        // Landscape has the horizontal room for a bigger grid (the word list moves
        // to the right column); portrait keeps the compact centred layout.
        private val cell get() = if (landscapePanel) 56f else 44f
        private val x0 get() = if (landscapePanel) PANEL_X + 70f else (W - n * cell) / 2f
        private val top get() = 582f
        private val found = HashSet<String>()
        private val foundCells = HashSet<Int>()
        private var startCell = -1
        private var dragCell = -1

        /** The grid only powers on once every machine has lit up its word. */
        private val readable get() = words.all { isWordRevealed(it) }

        override fun onOpen() { startCell = -1; dragCell = -1 }

        private fun cellAt(p: Vector2): Int {
            val col = ((p.x - x0) / cell).toInt()
            val row = ((top - p.y) / cell).toInt()
            return if (row in 0 until n && col in 0 until n && p.x >= x0 && p.y <= top) row * n + col else -1
        }

        private fun cx(col: Int) = x0 + col * cell + cell / 2f
        private fun cy(row: Int) = top - row * cell - cell / 2f

        override fun onDown(p: Vector2) { startCell = cellAt(p); dragCell = startCell }
        override fun onDrag(p: Vector2) { if (startCell >= 0) cellAt(p).let { if (it >= 0) dragCell = it } }

        override fun onUp(p: Vector2) {
            val end = cellAt(p).let { if (it >= 0) it else dragCell }
            if (startCell < 0 || end < 0) { startCell = -1; return }
            val r0 = startCell / n; val c0 = startCell % n
            val r1 = end / n; val c1 = end % n
            val dr = r1 - r0; val dc = c1 - c0
            val steps = max(abs(dr), abs(dc))
            if ((dr == 0 || dc == 0 || abs(dr) == abs(dc)) && steps > 0) {
                val sr = sgn(dr); val sc = sgn(dc)
                val cells = (0..steps).map { (r0 + sr * it) * n + (c0 + sc * it) }
                val word = cells.joinToString("") { grid[it / n][it % n].toString() }
                val match = words.firstOrNull { it == word || it == word.reversed() }
                if (match != null && match !in found && readable) {
                    found.add(match); foundCells.addAll(cells)
                }
            }
            startCell = -1; dragCell = -1
        }

        override val complete: Boolean get() = found.size == words.size

        override fun draw() {
            val on = readable
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            for (row in 0 until n) for (col in 0 until n) {
                val idx = row * n + col
                shapes.color = when {
                    !on -> Color(0.40f, 0.40f, 0.46f, 1f)   // powered down — static
                    complete && row == crossRow && col == crossCol -> Color(1f, 0.82f, 0.25f, 1f)
                    idx in foundCells -> cGood
                    idx == startCell || idx == dragCell -> Color(1f, 0.85f, 0.3f, 1f)
                    else -> cOpen
                }
                shapes.rect(x0 + col * cell + 2f, top - row * cell - cell + 2f, cell - 4f, cell - 4f)
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText("Word Display", W / 2f, 652f, 1.1f) // centred in the panel header
            for (c in 0 until n) centerText((c + 1).toString(), cx(c), top + 16f, 0.7f)
            for (r in 0 until n) centerText((r + 1).toString(), x0 - 16f, cy(r) + 6f, 0.7f)
            val letterScale = if (landscapePanel) 1.05f else 0.85f
            for (row in 0 until n) for (col in 0 until n) {
                val idx = row * n + col
                // Letters are scrambled-out static until the display powers on.
                font.color = if (!on) Color(0.30f, 0.30f, 0.34f, 1f)
                    else if (idx in foundCells || (complete && row == crossRow && col == crossCol)) Color.WHITE else cInk
                centerText(if (on) grid[row][col].toString() else "?", cx(col), cy(row) + 6f, letterScale)
            }
            // Words to find: masked until their machine lights them up. In landscape
            // they stack in the right column beside the grid; in portrait, along the
            // bottom as before.
            fun wordText(w: String) = when { w in found -> "[$w]"; isWordRevealed(w) -> w; else -> "?".repeat(w.length) }
            if (landscapePanel) {
                val listLeft = x0 + n * cell + 40f
                val listRight = PANEL_X + PANEL_W - 40f
                val colCx = (listLeft + listRight) / 2f
                val colW = listRight - listLeft
                font.color = cInk
                centerText("Find these:", colCx, 540f, 1.0f)
                words.forEachIndexed { i, w ->
                    font.color = if (w in found) cGood else cInk
                    centerText(wordText(w), colCx, 492f - i * 48f, 1.1f)
                }
                val statusY = 478f - words.size * 48f
                when {
                    !on -> { font.color = Color(0.96f, 0.6f, 0.3f, 1f); wrapText("Power up the display — solve every machine first!", colCx, statusY, colW, 0.78f) }
                    complete -> { font.color = cGood; wrapText("They cross at Column ${crossCol + 1}, Row ${crossRow + 1}!", colCx, statusY, colW, 0.82f) }
                    else -> centerText(instruction, colCx, statusY, 0.8f)
                }
            } else {
                font.color = cInk
                centerText(words.joinToString("   ") { wordText(it) }, W / 2f, 196f, 0.8f)
                when {
                    !on -> { font.color = Color(0.96f, 0.6f, 0.3f, 1f); centerText("Power up the display — solve every machine first!", W / 2f, 166f, 0.72f) }
                    complete -> { font.color = cGood; centerText("They cross at Column ${crossCol + 1}, Row ${crossRow + 1}!", W / 2f, 166f, 0.78f) }
                    else -> centerText(instruction, W / 2f, 166f, 0.7f)
                }
            }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Note: a clue drop (never "solved", just closed) ---------------- */

    private inner class Note : Puzzle() {
        override val instruction = ""
        override fun onDown(p: Vector2) {}
        override val complete get() = false

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            // Dim the scene, then the clue card.
            shapes.color = Color(0f, 0f, 0f, 0.6f)
            shapes.rect(0f, 0f, W, H)
            shapes.color = Color(0.96f, 0.91f, 0.66f, 1f)
            shapes.rect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H)
            shapes.color = Color(0.78f, 0.68f, 0.38f, 1f)
            shapes.rect(PANEL_X, PANEL_Y + PANEL_H - 12f, PANEL_W, 12f)
            // Close (X) button.
            shapes.color = Color(0.92f, 0.40f, 0.40f, 1f)
            shapes.circle(backCenter.x, backCenter.y, backR)
            shapes.end()

            batch.begin()
            font.color = cInk
            // The clue card is shared by every level; draw the art that fits this one.
            when (currentLevel) {
                robotLab -> { centerText("Lab Note", W / 2f, 612f, 1.2f); drawLabSketch() }
                tower -> { centerText("Room Map", W / 2f, 612f, 1.2f); drawCastleMap() }
                else -> centerText("Note", W / 2f, 612f, 1.2f)
            }
            drawBackLabel()
            batch.end()
        }

        /** The kindness-castle map: a vertical tower of floors with each core (a
         *  coloured numbered disc on the Landing) and its matching Core Charger cell
         *  (the same number/colour), so the player knows which core to carry where.
         *  Floors, bottom (Foyer) → top (the Suit), mirror the level's 2x4 grid. */
        private fun drawCastleMap() {
            val cx = W / 2f
            val left = cx - 118f; val right = cx + 118f
            val bottom = 196f; val top = 576f
            val r1 = 291f; val r2 = 386f; val r3 = 481f // floor dividers, bottom→top
            val tan = Color(0.85f, 0.55f, 0.42f, 1f)
            val yellow = Color(0.98f, 0.80f, 0.25f, 1f) // fairness charger → core 1
            val blue = Color(0.34f, 0.62f, 0.96f, 1f)   // honesty charger  → core 2
            val green = Color(0.34f, 0.82f, 0.48f, 1f)  // kindness charger → core 3
            // Outline + floor dividers (straight rules).
            smooth.line(batch, left, bottom, right, bottom, 5f, tan)
            smooth.line(batch, left, top, right, top, 5f, tan)
            smooth.line(batch, left, bottom, left, top, 5f, tan)
            smooth.line(batch, right, bottom, right, top, 5f, tan)
            smooth.line(batch, left, r1, right, r1, 5f, tan)
            smooth.line(batch, left, r2, right, r2, 5f, tan)
            smooth.line(batch, left, r3, right, r3, 5f, tan)
            // Vertical splits in the two charger floors (honesty|fairness, suit|kindness).
            smooth.line(batch, cx, r1, cx, r2, 5f, tan)
            smooth.line(batch, cx, r3, cx, top, 5f, tan)
            // Charger-cell numbers, coloured like the room they stand for.
            fun digit(s: String, x: Float, y: Float, c: Color) { font.color = c; centerText(s, x, y, 1.5f) }
            digit("2", cx - 59f, (r1 + r2) / 2f, blue)    // honesty (left)
            digit("1", cx + 59f, (r1 + r2) / 2f, yellow)  // fairness (right)
            digit("3", cx + 59f, (r3 + top) / 2f, green)  // kindness (right)
            // The Suit goal sits top-left (the Attic): a green target ring.
            val suitX = cx - 59f; val suitY = (r3 + top) / 2f
            smooth.circle(batch, suitX, suitY, 15f, cGood)
            smooth.circle(batch, suitX, suitY, 9f, Color(0.96f, 0.91f, 0.66f, 1f)) // parchment hole
            // The three loose cores on the Landing, as numbered colour discs.
            val coreY = (r2 + r3) / 2f
            val cores = listOf(Triple("1", yellow, cx - 46f), Triple("2", blue, cx), Triple("3", green, cx + 46f))
            cores.forEach { (n, col, x) ->
                smooth.circle(batch, x, coreY, 17f, col)
                font.color = Color.WHITE; centerText(n, x, coreY, 1.0f)
            }
        }

        /** The hand-drawn clue: path "1" runs across, path "2" runs down, and they
         *  meet at a scrawled red X (where the words cross). The paths are straight
         *  horizontal/vertical rules; only the X keeps its crossed-stroke look. */
        private fun drawLabSketch() {
            val cx = W / 2f
            val crossX = cx + 58f
            val crossY = 360f
            val tan = Color(0.85f, 0.55f, 0.42f, 1f)
            val red = Color(0.80f, 0.22f, 0.18f, 1f)
            // "1 ——": the across stroke along the bottom (straight horizontal).
            smooth.line(batch, cx - 104f, crossY, crossX - 44f, crossY, 7f, tan)
            // "2 |": the down stroke (straight vertical), meeting the across line.
            smooth.line(batch, crossX, 506f, crossX, crossY + 28f, 7f, tan)
            // The red X where the two paths cross.
            smooth.line(batch, crossX - 24f, crossY - 24f, crossX + 24f, crossY + 24f, 8f, red)
            smooth.line(batch, crossX + 24f, crossY - 24f, crossX - 24f, crossY + 24f, 8f, red)
            // The "1" and "2" labels, with what each path counts off, in marker tan.
            font.color = tan
            centerText("1", cx - 128f, crossY, 1.7f)
            centerText("row", cx - 128f, crossY - 26f, 0.7f)
            centerText("2", crossX, 540f, 1.7f)
            centerText("column", crossX, 514f, 0.7f)
        }
    }

    /* --- Solar Panel: a multiple-choice question ----------------------- */

    private inner class Mcq(
        private val heading: String,
        private val question: String,
        /** [options][0] is the correct answer; choices are shuffled on open. */
        private val options: List<String>,
    ) : Puzzle() {
        override val instruction = "Tap the right answer"
        private var order = options.indices.toList()
        private var done = false

        override fun onOpen() { done = false; order = options.indices.shuffled() }

        // Landscape lays the choices out as a row; portrait stacks them.
        private fun optRect(i: Int): FloatArray {
            if (landscapePanel) {
                val gap = 28f
                val ow = (PANEL_W - 4 * gap) / 3f
                return floatArrayOf(PANEL_X + gap + i * (ow + gap), 300f, ow, 150f)
            }
            return floatArrayOf(56f, 452f - i * 96f, 368f, 78f)
        }

        override fun onDown(p: Vector2) {
            order.indices.forEach { j ->
                if (inRect(p, optRect(j))) {
                    if (order[j] == 0) done = true else wrongFlash = 1f
                }
            }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            order.indices.forEach { j ->
                val r = optRect(j)
                shapes.color = if (done && order[j] == 0) cGood else cOpen
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText(heading, W / 2f, 652f, 1.2f)
            wrapText(question, W / 2f, if (landscapePanel) 576f else 600f, if (landscapePanel) PANEL_W - 160f else 364f, 0.86f)
            order.indices.forEach { j ->
                val r = optRect(j)
                font.color = if (done && order[j] == 0) Color.WHITE else cInk
                centerText(options[order[j]], r[0] + r[2] / 2f, r[1] + r[3] / 2f + 6f, 0.92f)
            }
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Try again!", W / 2f, 168f, 1f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Power Circuit: rotate pipe tiles to connect power to the bulb -- */

    private inner class Circuit : Puzzle() {
        override val instruction = "Tap tiles to spin the pipes"
        // Connection bits: N=1, E=2, S=4, W=8 (grid row 0 = top).
        private val rowsN = 3
        private val colsN = 3
        // Bigger tiles in landscape, where there's room; portrait keeps 92f.
        private val cell get() = if (landscapePanel) 120f else 92f
        private val gap = 8f
        private val pitch get() = cell + gap
        // Computed from the current width so the grid stays centred when the
        // canvas widens in landscape.
        private val gridX0 get() = (W - (colsN * cell + (colsN - 1) * gap)) / 2f
        private val gridY0 = 472f
        private val srcR = 1; private val srcC = 0
        private val bulbR = 1; private val bulbC = 2

        private val solution = IntArray(rowsN * colsN)
        private val mask = IntArray(rowsN * colsN)

        init {
            // The intended winding path: power in the west of (1,0), up and across
            // the top row, then down into the bulb at the east of (1,2).
            solution[idx(1, 0)] = 8 or 1 // W + N
            solution[idx(0, 0)] = 4 or 2 // S + E
            solution[idx(0, 1)] = 8 or 2 // W + E (straight)
            solution[idx(0, 2)] = 8 or 4 // W + S
            solution[idx(1, 2)] = 1 or 2 // N + E
            // Decoys (off-path) get assorted pipe shapes.
            val decoyShapes = listOf(8 or 2, 1 or 4, 1 or 2, 4 or 8, 2 or 4, 1 or 8)
            solution[idx(1, 1)] = decoyShapes.random()
            solution[idx(2, 0)] = decoyShapes.random()
            solution[idx(2, 1)] = decoyShapes.random()
            solution[idx(2, 2)] = decoyShapes.random()
        }

        override fun onOpen() {
            do {
                for (i in mask.indices) mask[i] = rotate(solution[i], (1..3).random())
            } while (solved())
        }

        private fun idx(r: Int, c: Int) = r * colsN + c
        private fun rotateCW(m: Int) = ((m shl 1) or (m shr 3)) and 0xF
        private fun rotate(m: Int, times: Int): Int { var x = m; repeat(times) { x = rotateCW(x) }; return x }
        private fun opposite(b: Int) = when (b) { 1 -> 4; 4 -> 1; 2 -> 8; else -> 2 }
        private fun stepRC(r: Int, c: Int, b: Int) = when (b) {
            1 -> r - 1 to c; 4 -> r + 1 to c; 2 -> r to c + 1; else -> r to c - 1
        }

        /** Flood power from the west of the source; the bulb lights if reached. */
        private fun solved(): Boolean {
            val powered = BooleanArray(rowsN * colsN)
            val queue = ArrayDeque<Int>()
            if (mask[idx(srcR, srcC)] and 8 != 0) { powered[idx(srcR, srcC)] = true; queue.add(idx(srcR, srcC)) }
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst(); val r = cur / colsN; val c = cur % colsN
                for (b in intArrayOf(1, 2, 4, 8)) if (mask[cur] and b != 0) {
                    val (nr, nc) = stepRC(r, c, b)
                    if (nr in 0 until rowsN && nc in 0 until colsN) {
                        val ni = idx(nr, nc)
                        if (!powered[ni] && mask[ni] and opposite(b) != 0) { powered[ni] = true; queue.add(ni) }
                    }
                }
            }
            return powered[idx(bulbR, bulbC)] && mask[idx(bulbR, bulbC)] and 2 != 0
        }

        override val complete: Boolean get() = solved()

        private fun cellRect(r: Int, c: Int) = floatArrayOf(gridX0 + c * pitch, gridY0 - r * pitch, cell, cell)

        override fun onDown(p: Vector2) {
            for (r in 0 until rowsN) for (c in 0 until colsN) {
                if (inRect(p, cellRect(r, c))) { mask[idx(r, c)] = rotateCW(mask[idx(r, c)]); return }
            }
        }

        override fun draw() {
            val powered = poweredSet()
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            for (r in 0 until rowsN) for (c in 0 until colsN) {
                val rect = cellRect(r, c)
                shapes.color = cOpen
                shapes.rect(rect[0], rect[1], rect[2], rect[3])
            }
            shapes.end()

            batch.begin()
            // Pipes & nodes — anti-aliased, round-capped (SmoothDraw) — over the cells.
            // Stroke/node sizes scale with the tile so they look right at any size.
            val k = cell / 92f
            val pipeTh = 13f * k; val nodeR = 9f * k; val endR = 13f * k; val endOff = 16f * k
            for (r in 0 until rowsN) for (c in 0 until colsN) {
                val rect = cellRect(r, c)
                val cx = rect[0] + cell / 2f; val cy = rect[1] + cell / 2f
                val col = if (powered[idx(r, c)]) cGood else Color(0.62f, 0.64f, 0.72f, 1f)
                val m = mask[idx(r, c)]
                if (m and 1 != 0) smooth.line(batch, cx, cy, cx, cy + cell / 2f, pipeTh, col)
                if (m and 4 != 0) smooth.line(batch, cx, cy, cx, cy - cell / 2f, pipeTh, col)
                if (m and 2 != 0) smooth.line(batch, cx, cy, cx + cell / 2f, cy, pipeTh, col)
                if (m and 8 != 0) smooth.line(batch, cx, cy, cx - cell / 2f, cy, pipeTh, col)
                smooth.circle(batch, cx, cy, nodeR, col)
            }
            // Power inlet (left of source) and the bulb (right of the target).
            val s = cellRect(srcR, srcC); val b = cellRect(bulbR, bulbC)
            smooth.circle(batch, s[0] - endOff, s[1] + cell / 2f, endR, Color(0.95f, 0.80f, 0.22f, 1f))
            smooth.circle(batch, b[0] + cell + endOff, b[1] + cell / 2f, endR, if (complete) cGood else Color(0.62f, 0.64f, 0.72f, 1f))
            font.color = cInk
            centerText("Power Circuit", W / 2f, 652f, 1.2f)
            centerText("Spin the pipes to connect power to the bulb", W / 2f, 623f, 0.7f)
            font.color = Color(0.85f, 0.6f, 0.1f, 1f)
            centerText("PWR", s[0] - endOff, s[1] + cell / 2f - 26f * k, 0.62f)
            font.color = if (complete) cGood else cInk
            centerText("BULB", b[0] + cell + endOff, b[1] + cell / 2f - 26f * k, 0.62f)
            if (complete) { font.color = cGood; centerText("Power on!", W / 2f, 168f, 1f) }
            drawBackLabel()
            batch.end()
        }

        private fun poweredSet(): BooleanArray {
            val powered = BooleanArray(rowsN * colsN)
            val queue = ArrayDeque<Int>()
            if (mask[idx(srcR, srcC)] and 8 != 0) { powered[idx(srcR, srcC)] = true; queue.add(idx(srcR, srcC)) }
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst(); val r = cur / colsN; val c = cur % colsN
                for (bit in intArrayOf(1, 2, 4, 8)) if (mask[cur] and bit != 0) {
                    val (nr, nc) = stepRC(r, c, bit)
                    if (nr in 0 until rowsN && nc in 0 until colsN) {
                        val ni = idx(nr, nc)
                        if (!powered[ni] && mask[ni] and opposite(bit) != 0) { powered[ni] = true; queue.add(ni) }
                    }
                }
            }
            return powered
        }
    }

    /* --- Fairness Core: share the treats so everyone gets the same ----- */

    private inner class Fair(
        private val animalGlyphs: List<Int>, // pictogram stand-ins for the animals
        private val total: Int,
    ) : Puzzle() {
        override val instruction = "Give everyone the same"
        private val each = total / animalGlyphs.size
        private val give = IntArray(animalGlyphs.size)
        private var done = false

        override fun onOpen() { give.fill(0); done = false }
        private val used get() = give.sum()

        private fun colX(i: Int) = W / 2f + (i - (animalGlyphs.size - 1) / 2f) * 140f
        private fun plusRect(i: Int) = floatArrayOf(colX(i) - 30f, 360f, 60f, 52f)
        private fun minusRect(i: Int) = floatArrayOf(colX(i) - 30f, 296f, 60f, 52f)

        override fun onDown(p: Vector2) {
            animalGlyphs.indices.forEach { i ->
                if (inRect(p, plusRect(i)) && used < total) give[i]++
                else if (inRect(p, minusRect(i)) && give[i] > 0) give[i]--
            }
            done = used == total && give.all { it == each }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            animalGlyphs.indices.forEach { i ->
                val pr = plusRect(i); shapes.color = cGood; shapes.rect(pr[0], pr[1], pr[2], pr[3])
                val mr = minusRect(i); shapes.color = Color(0.9f, 0.5f, 0.3f, 1f); shapes.rect(mr[0], mr[1], mr[2], mr[3])
            }
            shapes.end()

            batch.begin()
            animalGlyphs.forEachIndexed { i, g -> drawAnimal(g, colX(i), 500f, 24f) }
            font.color = cInk
            centerText("Fairness Core Charger", W / 2f, 652f, 1.2f)
            centerText("Share the treats so everyone gets the same", W / 2f, 623f, 0.72f)
            animalGlyphs.indices.forEach { i ->
                font.color = cInk
                centerText("${give[i]}", colX(i), 452f, 1.4f)
                font.color = Color.WHITE
                centerText("+", colX(i), plusRect(i)[1] + 26f, 1.4f)
                centerText("-", colX(i), minusRect(i)[1] + 26f, 1.4f)
            }
            font.color = cInk
            centerText("Treats left: ${total - used}", W / 2f, 250f, 0.95f)
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Kindness Core: sort each sentence into Kind or Mean ----------- */

    private inner class Sort(
        private val items: List<Pair<String, Boolean>>, // text, true = kind
    ) : Puzzle() {
        override val instruction = "Sort kind from mean"
        private val cKind = Color(0.20f, 0.62f, 0.78f, 1f)
        private val cMean = Color(0.92f, 0.45f, 0.35f, 1f)
        private val choice = arrayOfNulls<Boolean>(items.size)
        // Vertical scroll: in landscape the larger rows don't all fit, so the list
        // scrolls inside a clipped window (the header/footer stay fixed).
        private var scroll = 0f
        private var lastY = 0f; private var startY = 0f; private var moved = false
        private val clipRect = Rectangle(); private val scissor = Rectangle()

        private val land get() = landscapePanel
        private val rowH get() = if (land) 86f else 60f
        private val pitch get() = if (land) 102f else 72f
        private val rowX get() = if (land) PANEL_X + 24f else 36f
        private val rowW get() = if (land) PANEL_W - 48f else W - 72f
        private val firstTop get() = 592f // top edge of row 0 at scroll 0
        private val btnW get() = if (land) 104f else 62f
        private val btnH get() = if (land) 60f else 44f
        private val winTop get() = if (land) 602f else 700f    // scroll window (clip band)
        private val winBottom get() = if (land) 150f else 120f
        private val maxScroll get() = (items.size * pitch - (winTop - winBottom)).coerceAtLeast(0f)

        override fun onOpen() { for (i in choice.indices) choice[i] = null; scroll = 0f; moved = false }

        // Row i's top edge slides with the scroll offset.
        private fun rowRect(i: Int): FloatArray { val t = firstTop - i * pitch + scroll; return floatArrayOf(rowX, t - rowH, rowW, rowH) }
        private fun kBtn(i: Int): FloatArray { val r = rowRect(i); return floatArrayOf(r[0] + r[2] - 2f * btnW - 28f, r[1] + (rowH - btnH) / 2f, btnW, btnH) }
        private fun mBtn(i: Int): FloatArray { val r = rowRect(i); return floatArrayOf(r[0] + r[2] - btnW - 16f, r[1] + (rowH - btnH) / 2f, btnW, btnH) }
        private fun rowVisible(r: FloatArray) = r[1] + rowH >= winBottom && r[1] <= winTop

        override fun onDown(p: Vector2) { startY = p.y; lastY = p.y; moved = false }
        override fun onDrag(p: Vector2) {
            if (maxScroll > 0f) scroll = (scroll + (p.y - lastY)).coerceIn(0f, maxScroll)
            lastY = p.y
            if (abs(p.y - startY) > 6f) moved = true
        }
        override fun onUp(p: Vector2) {
            if (moved) return // a scroll drag, not a tap
            items.indices.forEach { i ->
                if (inRect(p, kBtn(i))) choice[i] = true
                else if (inRect(p, mBtn(i))) choice[i] = false
            }
        }

        override val complete: Boolean get() = items.indices.all { choice[it] == items[it].second }

        override fun draw() {
            val clipping = maxScroll > 0f
            if (clipping) {
                clipRect.set(PANEL_X, winBottom, PANEL_W, winTop - winBottom)
                ScissorStack.calculateScissors(puzzleCam, batch.transformMatrix, clipRect, scissor)
            }
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            shapes.end()

            // Statement rows + Kind/Mean buttons, clipped to the scroll window.
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            val pushedShapes = clipping && ScissorStack.pushScissors(scissor)
            items.indices.forEach { i ->
                val r = rowRect(i)
                if (!rowVisible(r)) return@forEach
                shapes.color = cOpen; shapes.rect(r[0], r[1], r[2], r[3])
                val k = kBtn(i); shapes.color = if (choice[i] == true) cKind else Color(0.85f, 0.87f, 0.92f, 1f); shapes.rect(k[0], k[1], k[2], k[3])
                val m = mBtn(i); shapes.color = if (choice[i] == false) cMean else Color(0.85f, 0.87f, 0.92f, 1f); shapes.rect(m[0], m[1], m[2], m[3])
            }
            shapes.end()
            if (pushedShapes) ScissorStack.popScissors()

            val textScale = if (land) 0.92f else 0.68f
            val btnScale = if (land) 0.78f else 0.6f
            batch.begin()
            val pushedText = clipping && ScissorStack.pushScissors(scissor)
            items.forEachIndexed { i, item ->
                val r = rowRect(i)
                if (!rowVisible(r)) return@forEachIndexed
                font.color = cInk
                wrapLeft(item.first, r[0] + 18f, r[1] + rowH / 2f, r[2] - 2f * btnW - 64f, textScale)
                val k = kBtn(i); font.color = if (choice[i] == true) Color.WHITE else cInk
                centerText("Kind", k[0] + k[2] / 2f, k[1] + k[3] / 2f, btnScale)
                val m = mBtn(i); font.color = if (choice[i] == false) Color.WHITE else cInk
                centerText("Mean", m[0] + m[2] / 2f, m[1] + m[3] / 2f, btnScale)
            }
            batch.end()
            if (pushedText) ScissorStack.popScissors()

            // Scrollbar, only when the list overflows.
            if (clipping) {
                val win = winTop - winBottom
                val trackX = PANEL_X + PANEL_W - 16f
                val thumbH = (win * win / (items.size * pitch)).coerceAtLeast(28f)
                val thumbY = winTop - thumbH - (scroll / maxScroll) * (win - thumbH)
                shapes.begin(ShapeRenderer.ShapeType.Filled)
                shapes.color = Color(0.85f, 0.87f, 0.92f, 1f); shapes.rect(trackX, winBottom, 6f, win)
                shapes.color = Color(0.55f, 0.58f, 0.66f, 1f); shapes.rect(trackX, thumbY, 6f, thumbH)
                shapes.end()
            }

            batch.begin()
            font.color = cInk
            centerText("Kindness Core Charger", W / 2f, 652f, 1.2f)
            centerText("Tap Kind or Mean for each one", W / 2f, 623f, 0.72f)
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Honesty Core: an actual fog-of-war maze with honesty signposts ---
     * A faithful port of the web app's MazePuzzle (src/lib/maze-pool.ts): a real
     * 11x11 grid ('#' wall, '.' path, 'S' start, 'G' goal) walked one cell at a
     * time. Only a 3x3 area around the hero is ever lit (fog of war), and at the
     * forks a signpost frames the honest choice (the emoji arrows are reworded to
     * plain words since the BitmapFont is ASCII-only). Reach the goal to solve. */

    private class MazeSign(val r: Int, val c: Int, val text: String)
    private class MazeVariant(val grid: List<String>, val signs: List<MazeSign>)

    private inner class Maze(private val variants: List<MazeVariant>) : Puzzle() {
        override val instruction = "Walk the honest path to the heart"
        private lateinit var v: MazeVariant
        private var pr = 0; private var pc = 0   // hero row/col
        private var gr = 0; private var gc = 0   // goal row/col
        private val seen = HashSet<Int>()
        private var done = false

        private fun key(r: Int, c: Int) = r * 100 + c
        private val rows get() = v.grid.size
        private val cols get() = v.grid[0].length
        // Landscape puts the maze on the left with bigger cells; portrait stacks the
        // maze on top with smaller cells, leaving room for the D-pad below.
        private val cell get() = if (landscapePanel) 30f else 22f
        private val gx0 get() = if (landscapePanel) PANEL_X + 80f else (W - cols * cell) / 2f
        private val gy0 get() = if (landscapePanel) 235f else 360f   // grid bottom edge
        private fun cellX(c: Int) = gx0 + c * cell
        private fun cellY(r: Int) = gy0 + (rows - 1 - r) * cell      // grid row 0 = top

        // Virtual D-pad: a 4-way cross — right of the maze in landscape, below it in portrait.
        private val padR get() = if (landscapePanel) 30f else 26f
        private val padGap get() = if (landscapePanel) 62f else 50f
        private val padCx get() = if (landscapePanel) PANEL_X + PANEL_W - 150f else W / 2f
        private val padCy get() = if (landscapePanel) 400f else 205f
        private fun padBtn(cx: Float, cy: Float) = floatArrayOf(cx - padR, cy - padR, 2 * padR, 2 * padR)

        override fun onOpen() {
            v = variants.random()
            seen.clear(); done = false
            v.grid.forEachIndexed { r, row -> row.forEachIndexed { c, ch ->
                if (ch == 'S') { pr = r; pc = c }; if (ch == 'G') { gr = r; gc = c }
            } }
            reveal(pr, pc)
        }

        /** Light up the 3x3 area around the hero. */
        private fun reveal(r: Int, c: Int) {
            for (dr in -1..1) for (dc in -1..1) {
                val rr = r + dr; val cc = c + dc
                if (rr in 0 until rows && cc in 0 until cols) seen.add(key(rr, cc))
            }
        }

        private fun walkable(r: Int, c: Int) = r in 0 until rows && c in 0 until cols && v.grid[r][c] != '#'

        private fun move(dr: Int, dc: Int) {
            val rr = pr + dr; val cc = pc + dc
            if (!walkable(rr, cc)) return // a wall blocks the step
            pr = rr; pc = cc; reveal(pr, pc)
            if (pr == gr && pc == gc) done = true
        }

        override fun onDown(p: Vector2) {
            if (done) return
            // Row 0 is the top of the grid, so "up" decreases the row.
            when {
                inRect(p, padBtn(padCx, padCy + padGap)) -> move(-1, 0)
                inRect(p, padBtn(padCx, padCy - padGap)) -> move(1, 0)
                inRect(p, padBtn(padCx - padGap, padCy)) -> move(0, -1)
                inRect(p, padBtn(padCx + padGap, padCy)) -> move(0, 1)
            }
        }

        override val complete: Boolean get() = done

        /** A round D-pad button with a white chevron pointing [dir] (U/D/L/R). */
        private fun drawPadButton(cx: Float, cy: Float, dir: Char) {
            smooth.circle(batch, cx, cy, padR, cAccent)
            val a = padR * 0.42f; val t = padR * 0.18f; val w = Color.WHITE
            when (dir) {
                'U' -> { smooth.line(batch, cx - a, cy - a * 0.4f, cx, cy + a * 0.6f, t, w); smooth.line(batch, cx, cy + a * 0.6f, cx + a, cy - a * 0.4f, t, w) }
                'D' -> { smooth.line(batch, cx - a, cy + a * 0.4f, cx, cy - a * 0.6f, t, w); smooth.line(batch, cx, cy - a * 0.6f, cx + a, cy + a * 0.4f, t, w) }
                'L' -> { smooth.line(batch, cx + a * 0.4f, cy - a, cx - a * 0.6f, cy, t, w); smooth.line(batch, cx - a * 0.6f, cy, cx + a * 0.4f, cy + a, t, w) }
                else -> { smooth.line(batch, cx - a * 0.4f, cy - a, cx + a * 0.6f, cy, t, w); smooth.line(batch, cx + a * 0.6f, cy, cx - a * 0.4f, cy + a, t, w) }
            }
        }

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            for (r in 0 until rows) for (c in 0 until cols) {
                val lit = key(r, c) in seen
                shapes.color = when {
                    !lit -> Color(0.07f, 0.07f, 0.12f, 1f)
                    v.grid[r][c] == '#' -> Color(0.30f, 0.32f, 0.40f, 1f)
                    else -> cOpen
                }
                shapes.rect(cellX(c) + 1f, cellY(r) + 1f, cell - 2f, cell - 2f)
            }
            shapes.end()

            batch.begin()
            if (key(gr, gc) in seen) smooth.circle(batch, cellX(gc) + cell / 2f, cellY(gr) + cell / 2f, cell * 0.32f, cGood)
            smooth.circle(batch, cellX(pc) + cell / 2f, cellY(pr) + cell / 2f, cell * 0.34f, cAccent)
            smooth.circle(batch, cellX(pc) + cell / 2f, cellY(pr) + cell / 2f, cell * 0.15f, Color.WHITE)

            // Virtual D-pad — round buttons with white chevrons.
            drawPadButton(padCx, padCy + padGap, 'U')
            drawPadButton(padCx, padCy - padGap, 'D')
            drawPadButton(padCx - padGap, padCy, 'L')
            drawPadButton(padCx + padGap, padCy, 'R')

            font.color = cInk
            centerText("Honesty Core Charger", W / 2f, 652f, 1.2f)
            centerText("Use the arrows to reach the heart", W / 2f, 623f, 0.62f)
            val sign = v.signs.firstOrNull { it.r == pr && it.c == pc }
            // Landscape: centre the prompt in the open gap between the maze and the
            // D-pad (vertically aligned with the pad), not squeezed above it.
            val mazeRight = gx0 + cols * cell
            val dpadLeft = padCx - padGap - padR
            val sx = if (landscapePanel) (mazeRight + dpadLeft) / 2f else W / 2f
            val sy = if (landscapePanel) 420f else 336f
            val sw = if (landscapePanel) (dpadLeft - mazeRight - 24f).coerceIn(180f, 360f) else W - 64f
            when {
                done -> { font.color = cGood; wrapText("You charged the core the honest way!", sx, sy, sw, 0.64f) }
                sign != null -> { font.color = cAccent; wrapText(sign.text, sx, sy, sw, 0.58f) }
            }
            drawBackLabel()
            batch.end()
        }
    }

    // Honesty maze pool — ported from the web app's HONESTY_MAZES (signs reworded
    // to plain words). MazePuzzle picks one at random each time it's opened.
    private val honestyMazes = listOf(
        MazeVariant(
            listOf("###########", "#S....#...#", "#####.###.#", "#...#.....#", "#.#.#####.#", "#G#...#...#", "###.###.###", "#...#...#.#", "#.###.###.#", "#.........#", "###########"),
            listOf(
                MazeSign(3, 9, "You forgot your homework. Up: 'Pretend you lost it' (a lie). Down: 'Tell the teacher the truth'."),
                MazeSign(9, 5, "You knocked over a plant. Right: 'Blame the cat' (a lie). Left: 'Own up and help clean it'."),
                MazeSign(5, 3, "You found a lost pen. Right: 'Keep it secretly' (a lie). Up: 'Give it back honestly'."),
            ),
        ),
        MazeVariant(
            listOf("###########", "#S#.......#", "#.###.###.#", "#...#.#G..#", "###.#.#####", "#...#.....#", "#.###.###.#", "#.#...#...#", "#.#####.#.#", "#.......#.#", "###########"),
            listOf(
                MazeSign(7, 9, "You knocked over a plant. Down: 'Blame the cat' (a lie). Up: 'Own up and help clean it'."),
                MazeSign(5, 5, "You found a lost pen. Down: 'Keep it secretly' (a lie). Up: 'Give it back honestly'."),
                MazeSign(1, 5, "You broke a toy at a friend's house. Left: 'Hide it under the sofa' (a lie). Right: 'Tell them it was an accident'."),
            ),
        ),
        MazeVariant(
            listOf("###########", "#S#.....#G#", "#.#.###.#.#", "#.#...#...#", "#.#.#.#####", "#.#.#.....#", "#.#####.#.#", "#.....#.#.#", "#####.###.#", "#.........#", "###########"),
            listOf(
                MazeSign(9, 5, "You found a lost pen. Left: 'Keep it secretly' (a lie). Right: 'Give it back honestly'."),
                MazeSign(5, 7, "You broke a toy at a friend's house. Down: 'Hide it under the sofa' (a lie). Left: 'Tell them it was an accident'."),
                MazeSign(3, 3, "You scored extra points by mistake. Down: 'Keep the wrong score' (a lie). Up: 'Point out the error'."),
            ),
        ),
        MazeVariant(
            listOf("###########", "#S..#.....#", "###.#.###.#", "#G#.#...#.#", "#.#.#####.#", "#.#.......#", "#.#######.#", "#...#.....#", "#.###.#####", "#.........#", "###########"),
            listOf(
                MazeSign(5, 9, "You broke a toy at a friend's house. Up: 'Hide it under the sofa' (a lie). Down: 'Tell them it was an accident'."),
                MazeSign(9, 5, "You scored extra points by mistake. Right: 'Keep the wrong score' (a lie). Left: 'Point out the error'."),
                MazeSign(7, 1, "You forgot your homework. Right: 'Pretend you lost it' (a lie). Up: 'Tell the teacher the truth'."),
            ),
        ),
        MazeVariant(
            listOf("###########", "#S......#.#", "#######.#.#", "#G....#...#", "#####.###.#", "#.....#...#", "#.###.#.###", "#...#.#.#.#", "#.#.###.#.#", "#.#.......#", "###########"),
            listOf(
                MazeSign(9, 7, "You scored extra points by mistake. Right: 'Keep the wrong score' (a lie). Left: 'Point out the error'."),
                MazeSign(7, 1, "You forgot your homework. Down: 'Pretend you lost it' (a lie). Up: 'Tell the teacher the truth'."),
                MazeSign(5, 5, "You knocked over a plant. Down: 'Blame the cat' (a lie). Up: 'Own up and help clean it'."),
            ),
        ),
        MazeVariant(
            listOf("###########", "#S..#.....#", "###.###.#.#", "#G#.....#.#", "#.#######.#", "#...#.....#", "#.###.#####", "#...#...#.#", "#.#.###.#.#", "#.#.......#", "###########"),
            listOf(
                MazeSign(9, 7, "You forgot your homework. Right: 'Pretend you lost it' (a lie). Left: 'Tell the teacher the truth'."),
                MazeSign(7, 1, "You knocked over a plant. Down: 'Blame the cat' (a lie). Up: 'Own up and help clean it'."),
                MazeSign(5, 1, "You found a lost pen. Right: 'Keep it secretly' (a lie). Up: 'Give it back honestly'."),
            ),
        ),
    )

    /* --- The Suit: unscramble the three power words ------------------- */

    private inner class Unscramble(
        private val words: List<String>,        // solved in order
        private val heading: String = "The Hero Suit",
        private val hints: List<String> = emptyList(), // optional clue per word (parallel to [words])
    ) : Puzzle() {
        override val instruction = "Unscramble the words"
        private var wi = 0
        private val typed = StringBuilder()
        private var scrambled = charArrayOf()
        private val usedTiles = HashSet<Int>()
        private var done = false

        // Once the last word is solved `wi` runs one past the end; the puzzle stays
        // open for review, so rendering must clamp back to the final word.
        private val wiSafe get() = wi.coerceAtMost(words.size - 1)

        override fun onOpen() { wi = 0; done = false; loadWord() }

        private fun loadWord() {
            typed.setLength(0); usedTiles.clear()
            val w = words[wi]
            do { scrambled = w.toList().shuffled().toCharArray() } while (String(scrambled) == w && w.length > 1)
        }

        private fun slotRect(i: Int): FloatArray {
            val n = words[wiSafe].length
            return floatArrayOf((W - n * 56f) / 2f + i * 56f, 430f, 48f, 48f)
        }
        private fun tileRect(i: Int): FloatArray {
            val n = scrambled.size
            return floatArrayOf((W - n * 56f) / 2f + i * 56f, 320f, 48f, 48f)
        }

        override fun onDown(p: Vector2) {
            // tap a filled slot to clear the last letter
            if (typed.isNotEmpty() && inRect(p, slotRect(typed.length - 1))) {
                val last = typed.last(); typed.deleteCharAt(typed.length - 1)
                val idx = scrambled.indices.firstOrNull { it in usedTiles && scrambled[it] == last }
                if (idx != null) usedTiles.remove(idx)
                return
            }
            // Resolve the tapped tile *before* touching state — completing a word
            // calls loadWord(), which swaps `scrambled` for a different-length array;
            // iterating it while mutating would read past the new word's bounds.
            val tile = scrambled.indices.firstOrNull { it !in usedTiles && inRect(p, tileRect(it)) } ?: return
            if (typed.length >= words[wi].length) return
            typed.append(scrambled[tile]); usedTiles.add(tile)
            if (typed.length == words[wi].length) {
                if (typed.toString() == words[wi]) {
                    wi++; if (wi >= words.size) done = true else loadWord()
                } else { wrongFlash = 1f; typed.setLength(0); usedTiles.clear() }
            }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            for (i in words[wiSafe].indices) {
                val r = slotRect(i)
                shapes.color = if (done || i < typed.length) Color(0.72f, 0.74f, 0.84f, 1f) else cSlot
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            scrambled.indices.forEach { i ->
                if (i !in usedTiles) { val r = tileRect(i); shapes.color = cAccent; shapes.rect(r[0], r[1], r[2], r[3]) }
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText(heading, W / 2f, 652f, 1.2f)
            centerText("Word ${wiSafe + 1} of ${words.size}", W / 2f, 623f, 0.72f)
            val hint = hints.getOrNull(wiSafe)
            if (hint != null) { font.color = cAccent; wrapText("Clue: $hint", W / 2f, 572f, W - 90f, 1f) }
            else { font.color = cInk; centerText("Tap the letters in order", W / 2f, 560f, 0.78f) }
            typed.forEachIndexed { i, ch -> val r = slotRect(i); font.color = cInk; centerText(ch.toString(), r[0] + r[2] / 2f, r[1] + r[3] / 2f, 1.2f) }
            scrambled.indices.forEach { i ->
                if (i !in usedTiles) { val r = tileRect(i); font.color = Color.WHITE; centerText(scrambled[i].toString(), r[0] + r[2] / 2f, r[1] + r[3] / 2f, 1.2f) }
            }
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Not that word — try again!", W / 2f, 250f, 0.9f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- The Grand Hall: drag word-tiles into the acrostic crossword ---- */

    private inner class Crossword(
        private val rows: List<XRow>,
        private val secretCol: Int,
    ) : Puzzle() {
        override val instruction = "Drag each word into its numbered row"
        private val words = rows.map { it.word }
        private val wordInRow = IntArray(rows.size) { -1 } // word index placed in each row (-1 = empty)
        private val traySlot = words.indices.shuffled()
        private var drag = -1
        private val dragPos = Vector2()
        private val cell = 30f
        private val rowPitch = 38f
        private val gridCols = rows.maxOf { it.offset + it.word.length }
        private val gy0 = 506f

        private val gx0 get() = (W - gridCols * cell) / 2f
        private fun rowY(r: Int) = gy0 - r * rowPitch
        private fun cellRect(r: Int, col: Int) = floatArrayOf(gx0 + col * cell, rowY(r), cell, cell)
        // The word tray fits the panel: portrait keeps it inside the card edges
        // (instead of overflowing), landscape widens the boxes to fill the space.
        private val trayN = words.size
        private val trayGap get() = if (landscapePanel) 20f else 12f
        private val trayW get() = ((PANEL_W - 2f * (if (landscapePanel) 60f else 22f) - (trayN - 1) * trayGap) / trayN).coerceIn(80f, 220f)
        private val trayH get() = if (landscapePanel) 56f else 40f
        private fun trayRect(slot: Int): FloatArray {
            val total = trayN * trayW + (trayN - 1) * trayGap
            val x0 = PANEL_X + (PANEL_W - total) / 2f
            return floatArrayOf(x0 + slot * (trayW + trayGap), 250f, trayW, trayH)
        }
        private fun rowOfWord(w: Int): Int { for (r in rows.indices) if (wordInRow[r] == w) return r; return -1 }

        override fun onOpen() { for (r in wordInRow.indices) wordInRow[r] = -1; drag = -1 }

        override fun onDown(p: Vector2) {
            words.indices.forEach { w ->
                if (rowOfWord(w) < 0 && inRect(p, trayRect(traySlot.indexOf(w)))) { drag = w; dragPos.set(p); return }
            }
            rows.indices.forEach { r ->
                if (wordInRow[r] >= 0 && p.x >= gx0 && p.x <= gx0 + gridCols * cell && p.y >= rowY(r) && p.y <= rowY(r) + cell) {
                    drag = wordInRow[r]; wordInRow[r] = -1; dragPos.set(p); return
                }
            }
        }
        override fun onDrag(p: Vector2) { if (drag >= 0) dragPos.set(p) }
        override fun onUp(p: Vector2) {
            if (drag < 0) return
            var target = -1
            rows.indices.forEach { r -> if (p.y >= rowY(r) && p.y <= rowY(r) + cell) target = r }
            if (target >= 0 && wordInRow[target] < 0) wordInRow[target] = drag
            drag = -1
        }
        override val complete: Boolean get() = rows.indices.all { wordInRow[it] == it }

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            rows.forEachIndexed { r, row ->
                for (col in row.offset until row.offset + row.word.length) {
                    val rect = cellRect(r, col)
                    shapes.color = if (col == secretCol) Color(1f, 0.85f, 0.35f, 1f) else cOpen
                    shapes.rect(rect[0], rect[1], rect[2] - 2f, rect[3] - 2f)
                }
            }
            words.indices.forEach { w ->
                if (rowOfWord(w) < 0 && w != drag) { val t = trayRect(traySlot.indexOf(w)); shapes.color = cAccent; shapes.rect(t[0], t[1], t[2], t[3]) }
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText("Crossword", W / 2f, 652f, 1.2f)
            centerText("Drag each word into its numbered row", W / 2f, 623f, 0.66f)
            rows.forEachIndexed { r, row ->
                val nr = cellRect(r, row.offset)
                font.color = Color(0.4f, 0.4f, 0.5f, 1f); centerText("${row.num}", nr[0] + 7f, nr[1] + cell - 7f, 0.45f)
                val placed = wordInRow[r]
                if (placed >= 0) words[placed].forEachIndexed { i, ch ->
                    val cr = cellRect(r, row.offset + i); font.color = cInk; centerText(ch.toString(), cr[0] + cell / 2f, cr[1] + cell / 2f, 0.8f)
                }
            }
            val wordScale = if (landscapePanel) 0.95f else 0.62f
            words.indices.forEach { w ->
                if (rowOfWord(w) < 0 && w != drag) { val t = trayRect(traySlot.indexOf(w)); font.color = Color.WHITE; centerText(words[w], t[0] + t[2] / 2f, t[1] + t[3] / 2f, wordScale) }
            }
            if (drag >= 0) {
                smooth.rect(batch, dragPos.x - trayW / 2f, dragPos.y - trayH / 2f, trayW, trayH, cAccent)
                font.color = Color.WHITE; centerText(words[drag], dragPos.x, dragPos.y, wordScale)
            }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- The exit: tap the symbol for each letter of the secret word ---- */

    private inner class SymbolLock(
        private val word: String,
        private val decoys: Int = 3, // extra (non-answer) glyphs added to the palette
    ) : Puzzle() {
        override val instruction = "Tap the symbol for each letter"
        private val letters = word.toCharArray().toList().distinct() // letters needing a symbol, in word order
        private var glyphOf = mapOf<Char, Int>() // letter -> glyph kind, reshuffled each open
        private var target = listOf<Int>()
        private var palette = listOf<Int>()
        private val entered = ArrayList<Int>()
        private var done = false

        // Randomise the symbol↔letter pairing (and tile order) so the key can't be
        // memorised across attempts.
        private fun reshuffle() {
            val kinds = (0..7).shuffled()
            glyphOf = letters.mapIndexed { i, ch -> ch to kinds[i] }.toMap()
            target = word.map { glyphOf.getValue(it) }
            palette = (target.distinct() + kinds.drop(letters.size).take(decoys)).shuffled()
        }
        init { reshuffle() }

        override fun onOpen() { entered.clear(); done = false; reshuffle() }

        private fun slotRect(i: Int) = floatArrayOf((W - word.length * 54f) / 2f + i * 54f, 408f, 46f, 46f)
        private fun tileRect(i: Int): FloatArray {
            val cols = 4; val b = 58f; val gx = 12f
            val x0 = (W - (cols * b + (cols - 1) * gx)) / 2f
            return floatArrayOf(x0 + (i % cols) * (b + gx), 320f - (i / cols) * (b + 12f), b, b)
        }

        override fun onDown(p: Vector2) {
            if (entered.isNotEmpty() && inRect(p, slotRect(entered.size - 1))) { entered.removeAt(entered.size - 1); return }
            palette.indices.forEach { i ->
                if (inRect(p, tileRect(i)) && entered.size < word.length) {
                    entered.add(palette[i])
                    if (entered.size == word.length) { if (entered == target) done = true else { wrongFlash = 1f; entered.clear() } }
                }
            }
        }
        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            for (i in word.indices) { val r = slotRect(i); shapes.color = if (i < entered.size) Color(0.72f, 0.74f, 0.84f, 1f) else cSlot; shapes.rect(r[0], r[1], r[2], r[3]) }
            palette.indices.forEach { i -> val r = tileRect(i); shapes.color = cOpen; shapes.rect(r[0], r[1], r[2], r[3]) }
            shapes.end()

            batch.begin()
            palette.indices.forEach { i -> val r = tileRect(i); drawGlyph(palette[i], r[0] + r[2] / 2f, r[1] + r[3] / 2f, 16f) }
            entered.forEachIndexed { i, g -> val r = slotRect(i); drawGlyph(g, r[0] + r[2] / 2f, r[1] + r[3] / 2f, 14f) }
            font.color = cInk
            centerText("Symbol Lock", W / 2f, 652f, 1.2f)
            centerText("Spell the secret word in symbols", W / 2f, 623f, 0.7f)
            letters.forEachIndexed { k, ch ->
                val lx = (W - letters.size * 72f) / 2f + k * 72f + 36f
                font.color = cInk; centerText("$ch =", lx - 16f, 560f, 0.78f)
                drawGlyph(glyphOf.getValue(ch), lx + 16f, 558f, 12f)
            }
            font.color = cGood; centerText(word, W / 2f, 488f, 1.0f)
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Not quite!", W / 2f, 238f, 0.9f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* ------------------------------ world ------------------------------ */

    // A room's *content*, independent of where it sits on screen. The grid layout
    // (cell rects, machine positions, walls) is computed per orientation so the
    // same six rooms reflow between a 2x3 (portrait) and 3x2 (landscape) grid.
    /** A room placed on the level grid. [gx]/[gy] is its bottom-left grid unit;
     *  [gw]x[gh] its span (>1 = a conjoined room). Units no room covers are voids
     *  (solid), which lets maps be L-shaped / irregular. */
    private inner class GridRoom(
        val id: String, val title: String, val label: String?,
        val floor: Color, val nodeColor: Color = Color.WHITE,
        val gx: Int, val gy: Int, val gw: Int = 1, val gh: Int = 1,
        val puzzle: Puzzle? = null, val requires: String? = null, val clue: String? = null,
        /** Gate this station until *all* of these rooms are solved (e.g. the exit
         *  cipher needs every clue-bearing station first). */
        val requiresAll: List<String> = emptyList(),
        /** Override the level's floor texture for this room (else [EscapeLevel.floorKind]).
         *  One of: metal / concrete / stone / wood / grass / tile / panel. */
        val floorKind: String? = null,
    )
    private data class RoomCell(val title: String, val x: Float, val y: Float, val w: Float, val h: Float, val floor: Color)
    /** A crossword row: its clue [num], answer [word], and grid [offset] (start column). */
    private data class XRow(val num: Int, val word: String, val offset: Int)

    /** One escape-room map. [doors] are the connected room-id pairs — only those
     *  get an opening, so neighbours aren't all reachable. */
    private inner class EscapeLevel(
        val name: String, val gridCols: Int, val gridRows: Int,
        val rooms: List<GridRoom>, val doors: Set<Pair<String, String>>,
        val spawnRoom: String, val exitRoom: String, val clueRoom: String? = null,
        /** Core-carrying levels: the core ids (each matches a station id), the room
         *  the loose cores sit in, and the room with the suit they're delivered to. */
        val cores: List<String> = emptyList(),
        val coreRoom: String? = null,
        val suitRoom: String? = null,
        /** Direct-delivery levels (e.g. the Heritage Vault): each carried item sits in
         *  its own (solved) room and is placed straight into the suit/display — there
         *  is no separate charging step the way the Tower's cores have. */
        val directDeliver: Boolean = false,
        /** Recycling chain (green-lab): bottle ids scattered across [bottleHomeRooms]
         *  (parallel list), washed at the sink in [sinkRoom], then deposited at
         *  [depositRoom]'s recycling station. [bottleGateRoom]'s puzzle stays locked
         *  until every bottle has been recycled. */
        val bottles: List<String> = emptyList(),
        val bottleHomeRooms: List<String> = emptyList(),
        val sinkRoom: String? = null,
        val depositRoom: String? = null,
        val bottleGateRoom: String? = null,
        /** Themed backdrop behind the room floors (also shown in the wall gaps and
         *  letterbox margins) so each level reads in its own colour world. */
        val bg: Color = Color(0.12f, 0.13f, 0.22f, 1f),
        /** Every room floor is nudged toward this hue so the level's floors share a
         *  theme while each room keeps its own lightness. */
        val floorTint: Color = Color(0.33f, 0.34f, 0.44f, 1f),
        /** The level's default floor texture (a room may override via its own
         *  [GridRoom.floorKind]). Ported from the web escape rooms' `floorKind`:
         *  metal / concrete / stone / wood / grass / tile / panel. */
        val floorKind: String = "tile",
        /** Puzzle-panel chrome: [card] is the (light) surface dark text reads on,
         *  [cardBar] the header band + border, [cardBtn] the close button + emblem. */
        val card: Color = Color.WHITE,
        val cardBar: Color = Color(0.85f, 0.85f, 0.88f, 1f),
        val cardBtn: Color = Color(0.92f, 0.40f, 0.40f, 1f),
    )

    // A clue drop (parchment placeholder for now) that will explain the trick.
    private val clueNote = Note()

    private fun floorColor(i: Int) = listOf(
        Color(0.30f, 0.34f, 0.46f, 1f), Color(0.34f, 0.30f, 0.48f, 1f), Color(0.28f, 0.42f, 0.36f, 1f),
        Color(0.40f, 0.34f, 0.30f, 1f), Color(0.26f, 0.36f, 0.50f, 1f), Color(0.30f, 0.40f, 0.42f, 1f),
    )[((i % 6) + 6) % 6]

    // ---- Level 0: the Robot Lab — a 3x3 with a wide central hall, a tall word
    // display and the three machines branching off the hub (not a flat grid). ----
    private val robotLab = EscapeLevel(
        name = "Robot Lab", gridCols = 3, gridRows = 3,
        bg = Color(0.10f, 0.14f, 0.24f, 1f), // cool steel-blue tech lab
        floorTint = Color(0.24f, 0.34f, 0.50f, 1f), floorKind = "metal",
        card = Color(0.88f, 0.90f, 0.95f, 1f), cardBar = Color(0.70f, 0.77f, 0.88f, 1f), cardBtn = Color(0.38f, 0.55f, 0.80f, 1f), // brushed steel
        rooms = listOf(
            GridRoom("entrance", "Entrance", null, floorColor(0), gx = 0, gy = 0),
            GridRoom("panel", "Control Panel", "Control Panel", floorColor(4), Color(0.20f, 0.62f, 0.98f, 1f), gx = 1, gy = 0,
                puzzle = NumberLock("6", "Control Panel", "Count the robots", icons = ROBOT_FIELD), clue = "Display: ROBOT"),
            GridRoom("keypad", "Exit Chamber", "Exit Keypad", floorColor(5), cGood, gx = 2, gy = 0,
                puzzle = NumberLock("54", "Exit Keypad", "Enter the 2-digit door code", showClues = false), requires = "poster"),
            GridRoom("atrium", "Main Lab", null, floorColor(1), gx = 0, gy = 1, gw = 2),            // wide hub (2x1)
            GridRoom("poster", "Word Display", "Word Display", floorColor(2), cGood, gx = 2, gy = 1, gh = 2,  // tall (1x2)
                puzzle = WordSearch(
                    listOf("ROBOT", "LEARN", "GEAR"),
                    arrayOf("ZXQKVWYJ".toCharArray(), "GPDLHUFM".toCharArray(), "CEVEKXQZ".toCharArray(), "WYAAJPDH".toCharArray(),
                        "KVQROBOT".toCharArray(), "XZJNCWYF".toCharArray(), "MPUDKVQX".toCharArray(), "HFCZJWYP".toCharArray()),
                    4, 3,
                )),
            GridRoom("decoder", "Symbol Decoder", "Symbol Decoder", floorColor(3), Color(0.66f, 0.48f, 0.92f, 1f), gx = 0, gy = 2,
                puzzle = Cipher(listOf('G', 'E', 'A', 'R', 'S', 'T', 'N', 'O'), "GEAR"), clue = "Display: GEAR"),
            GridRoom("robot", "Robot Helper", "Robot Helper", floorColor(4), Color(1f, 0.58f, 0.20f, 1f), gx = 1, gy = 2,
                puzzle = Order(listOf("Show the robot lots of cat photos", "The robot spots the pattern", "The robot guesses 'cat!' on a new photo")), clue = "Display: LEARN"),
        ),
        doors = setOf(
            "entrance" to "panel", "entrance" to "atrium",
            "atrium" to "decoder", "atrium" to "robot", "atrium" to "poster",
            "panel" to "keypad", "poster" to "keypad",
        ),
        spawnRoom = "entrance", exitRoom = "keypad", clueRoom = "atrium",
    )

    // ---- The Vault: a Singapore-history "Heritage Vault". Each gallery hides an
    // artefact behind a history puzzle; recover all three and carry them into the
    // central Time Capsule to complete the national display and open the vault. ----
    private val vault = EscapeLevel(
        name = "History Vault", gridCols = 3, gridRows = 3,
        bg = Color(0.17f, 0.13f, 0.09f, 1f), // warm sepia heritage vault
        floorTint = Color(0.44f, 0.36f, 0.22f, 1f), floorKind = "stone",
        card = Color(0.96f, 0.92f, 0.82f, 1f), cardBar = Color(0.86f, 0.74f, 0.50f, 1f), cardBtn = Color(0.72f, 0.52f, 0.28f, 1f), // parchment + brass
        rooms = listOf(
            GridRoom("hall", "Heritage Hall", null, floorColor(0), gx = 0, gy = 0, gw = 3, gh = 1),  // wide entrance
            GridRoom("west", "Founding Gallery", "Founding Gallery", floorColor(1), Color(0.30f, 0.62f, 0.95f, 1f), gx = 0, gy = 1, gh = 2, // 1819 — holds the Treaty scroll
                puzzle = Mcq("Founding Gallery", "In which year did Raffles land and found modern Singapore?",
                    listOf("1819", "1942", "1965"))),
            GridRoom("core", "Time Capsule", "Time Capsule", floorColor(2), cGood, gx = 1, gy = 1),    // the display / exit
            GridRoom("east", "Independence Hall", "Independence Hall", floorColor(3), Color(0.95f, 0.34f, 0.34f, 1f), gx = 2, gy = 1, gh = 2, // 1965 — holds the National Flag
                puzzle = NumberLock("1965", "Independence Hall", "What year did Singapore become independent?")),
            GridRoom("top", "Lion City Room", "Lion City Room", floorColor(4), Color(0.95f, 0.80f, 0.22f, 1f), gx = 1, gy = 2, // holds the Merlion
                puzzle = Unscramble(listOf("SINGA", "PURA"), "Lion City Room",
                    listOf("In Malay, the word for 'lion'.", "In Malay, this means 'city' — put it after Singa for Singapore's old name."))),
        ),
        doors = setOf("hall" to "west", "hall" to "east", "west" to "core", "east" to "core", "core" to "top"),
        spawnRoom = "hall", exitRoom = "core",
        cores = listOf("west", "east", "top"), suitRoom = "core", directDeliver = true,
    )
    // The Tower hosts kindness-castle ("The Superhero Suit") with a core-carrying
    // twist: solve each Core station, then ferry its (unlabeled) core from the
    // Landing to that station to charge it, then bring the charged cores to the
    // Suit in the Attic to unlock the final unscramble. Station ids == core ids.
    private val tower = EscapeLevel(
        name = "Superhero Tower", gridCols = 2, gridRows = 4,
        bg = Color(0.15f, 0.12f, 0.24f, 1f), // twilight indigo castle
        floorTint = Color(0.38f, 0.30f, 0.50f, 1f), floorKind = "panel",
        card = Color(0.97f, 0.96f, 0.99f, 1f), cardBar = Color(0.78f, 0.72f, 0.93f, 1f), cardBtn = Color(0.86f, 0.32f, 0.42f, 1f), // comic page
        rooms = listOf(
            GridRoom("foyer", "Foyer", null, floorColor(0), gx = 0, gy = 0, gw = 2),          // wide
            GridRoom("honesty", "Core Charger", "Core Charger", floorColor(1), Color(0.30f, 0.60f, 0.95f, 1f), gx = 0, gy = 1,
                puzzle = Maze(honestyMazes)),
            GridRoom("fairness", "Core Charger", "Core Charger", floorColor(2), Color(0.98f, 0.80f, 0.25f, 1f), gx = 1, gy = 1,
                puzzle = Fair(listOf(0, 2, 4), 9)),
            GridRoom("landing", "Landing", null, floorColor(3), gx = 0, gy = 2, gw = 2),       // wide — cores + note
            GridRoom("attic", "The Suit", "The Suit", floorColor(4), cGood, gx = 0, gy = 3,
                puzzle = Unscramble(listOf("KIND", "TRUE", "FAIR"))),
            GridRoom("kindness", "Core Charger", "Core Charger", floorColor(5), Color(0.30f, 0.80f, 0.45f, 1f), gx = 1, gy = 3,
                puzzle = Sort(listOf(
                    "Want to play with us?" to true,
                    "You can't sit here!" to false,
                    "Great try — well done!" to true,
                    "Nobody likes you." to false,
                    "Here, let me help you up." to true,
                    "That's a dumb idea." to false,
                ))),
        ),
        doors = setOf("foyer" to "fairness", "fairness" to "honesty", "honesty" to "landing", "landing" to "kindness", "kindness" to "attic"),
        spawnRoom = "foyer", exitRoom = "attic", clueRoom = "landing",
        cores = listOf("fairness", "honesty", "kindness"),
        coreRoom = "landing", suitRoom = "attic",
    )
    // The Annex hosts the "Recycling Plant" (green-lab) puzzles, ported from
    // alfredang/ai4kids: a solar-power question, a recycling-order task and a
    // pipe-circuit, all feeding the POWER exit cipher.
    private val annex = EscapeLevel(
        name = "Green Workshop", gridCols = 3, gridRows = 3,                                   // L-shaped (2 voids)
        bg = Color(0.08f, 0.16f, 0.12f, 1f), // dark forest green eco-lab
        floorTint = Color(0.22f, 0.44f, 0.30f, 1f), floorKind = "concrete",
        card = Color(0.91f, 0.95f, 0.87f, 1f), cardBar = Color(0.72f, 0.85f, 0.66f, 1f), cardBtn = Color(0.34f, 0.66f, 0.42f, 1f), // recycled-green paper
        rooms = listOf(
            GridRoom("lobby", "Lobby", null, floorColor(0), gx = 0, gy = 0),
            // ids match the green-lab server stations: panel / bins / circuit.
            GridRoom("panel", "Solar Panel", "Solar Panel", floorColor(3), Color(0.95f, 0.80f, 0.22f, 1f), gx = 1, gy = 0, gw = 2,  // wide
                puzzle = Mcq("Solar Panel", "Which power comes from the sun and never runs out?",
                    listOf("Solar power", "Burning coal", "Plastic bags")),
                clue = "Sun = renewable"),
            GridRoom("bins", "Recycling Plant", "Recycling Plant", floorColor(2), cGood, gx = 0, gy = 1, gh = 2,                 // tall 1x2
                puzzle = Order(
                    listOf("Empty and rinse the bottle", "Drop it in the recycling bin", "It's made into something new!"),
                    "Recycling Plant", "Put the recycling steps in order"),
                clue = "Reuse, don't bin"),
            GridRoom("circuit", "Power Circuit", "Power Circuit", floorColor(4), Color(0.30f, 0.66f, 0.95f, 1f), gx = 1, gy = 1,
                puzzle = Circuit(), clue = "Power flows"),
            GridRoom("loft", "Exit Decoder", "Exit Decoder", floorColor(5), cGood, gx = 1, gy = 2,
                puzzle = Cipher(listOf('P', 'O', 'W', 'E', 'R', 'S', 'U', 'N'), "POWER"),
                requiresAll = listOf("panel", "bins", "circuit")),
            // grid units (2,1) and (2,2) are left void -> the map is an L.
        ),
        doors = setOf("lobby" to "panel", "lobby" to "bins", "bins" to "circuit", "circuit" to "loft"),
        spawnRoom = "lobby", exitRoom = "loft",
        // Find 3 bottles scattered in the lobby/panel/plant, wash each at the sink in
        // the Recycling Plant corner, then recycle them at the plant station to power
        // up the Power Circuit room.
        bottles = listOf("bottleA", "bottleB", "bottleC"),
        bottleHomeRooms = listOf("lobby", "panel", "bins"),
        sinkRoom = "bins", depositRoom = "bins", bottleGateRoom = "circuit",
    )
    // The Big Hall — "Lion City": a hub-and-spoke SG-culture room. Spawn in the
    // Grand Hall, solve the four themed rooms (each reveals a word), then drag the
    // words into the crossword — a secret word (LION) reads down. The exit asks you
    // to spell it back in symbols.
    private val bigHall = EscapeLevel(
        name = "Lion City Carnival", gridCols = 3, gridRows = 3,                                  // + shape (3 voids)
        bg = Color(0.18f, 0.10f, 0.12f, 1f), // festive deep maroon (Lion City)
        floorTint = Color(0.48f, 0.28f, 0.26f, 1f), floorKind = "tile",
        card = Color(0.98f, 0.95f, 0.87f, 1f), cardBar = Color(0.94f, 0.80f, 0.48f, 1f), cardBtn = Color(0.86f, 0.34f, 0.36f, 1f), // cream + gold trim
        rooms = listOf(
            GridRoom("hall", "Grand Hall", "Crossword", floorColor(2), Color(0.95f, 0.80f, 0.22f, 1f), gx = 1, gy = 1,  // central hub
                puzzle = Crossword(
                    listOf(XRow(1, "LAKSA", 5), XRow(2, "DIWALI", 4), XRow(3, "ORCHID", 5), XRow(4, "DURIAN", 0)),
                    secretCol = 5,
                ),
                requiresAll = listOf("food", "festival", "flower", "fruit")),
            GridRoom("food", "Hawker Stall", "Hawker Stall", floorColor(0), Color(0.95f, 0.55f, 0.20f, 1f), gx = 1, gy = 0,
                puzzle = Order(
                    listOf("Simmer the spicy coconut-milk broth", "Add the noodles, prawns and tofu puffs", "Top with cockles and serve hot"),
                    "Hawker Stall", "Put the laksa steps in order"),
                clue = "1 = LAKSA"),
            GridRoom("festival", "Little India", "Little India", floorColor(1), Color(0.92f, 0.42f, 0.62f, 1f), gx = 0, gy = 1,
                puzzle = Unscramble(listOf("DIWALI"), "Little India",
                    listOf("The Hindu festival of lights, with oil lamps and colourful rangoli.")),
                clue = "2 = DIWALI"),
            GridRoom("flower", "Gardens", "Gardens", floorColor(3), Color(0.66f, 0.48f, 0.92f, 1f), gx = 2, gy = 1,
                puzzle = Mcq("Gardens", "What is Singapore's national flower?", listOf("Orchid", "Rose", "Tulip")),
                clue = "3 = ORCHID", floorKind = "grass"),
            GridRoom("fruit", "Fruit Stall", "Fruit Stall", floorColor(4), Color(0.30f, 0.78f, 0.45f, 1f), gx = 1, gy = 2,
                puzzle = Cipher(listOf('D', 'U', 'R', 'I', 'A', 'N', 'O', 'S'), "DURIAN"),
                clue = "4 = DURIAN", floorKind = "wood"),
            GridRoom("exit", "Exit Panel", "Exit Panel", floorColor(5), cGood, gx = 2, gy = 2,
                puzzle = SymbolLock("LION"),
                requires = "hall"),
            // grid units (0,0) (0,2) (2,0) are left void -> a plus shape with an exit nub.
        ),
        doors = setOf(
            "hall" to "food", "hall" to "festival", "hall" to "flower", "hall" to "fruit", "flower" to "exit",
        ),
        spawnRoom = "hall", exitRoom = "exit",
    )

    // Ordered to match ESCAPE_COOP_SLUGS so each level co-op-routes to the server
    // room whose puzzles it actually contains:
    //   0 robotLab=robot-lab  1 tower=kindness-castle  2 annex=green-lab
    //   3 vault=sg-history     4 bigHall=sg-culture     (3-4 are still empty maps)
    private val levels = listOf(robotLab, tower, annex, vault, bigHall)
    private val currentLevel get() = levels[levelIndex.coerceIn(0, levels.size - 1)]
    private val rooms get() = currentLevel.rooms
    private val totalStations get() = rooms.count { it.puzzle != null }

    private val cluePos = Vector2()
    private val spawnPos = Vector2()
    private var exitRoomIndex = 0
    private var spawnRoomIndex = 0
    private var clueRoomIndex = -1

    // ---- core-carrying mechanic (kindness-castle) ----
    private val charged = HashSet<String>()    // cores sitting charged at their station
    private val delivered = HashSet<String>()  // cores delivered to the suit
    private var carrying: String? = null        // the core id currently carried
    private var carryingCharged = false
    private var coreRoomIndex = -1
    private var suitRoomIndex = -1
    private val suitPos = Vector2()
    private val coreHome = HashMap<String, Vector2>()
    private val coreDrop = HashMap<String, Vector2>() // where a core was set down (overrides its home/station)
    private val coreTmp = Vector2()
    // A charged core glows in its charger's identity colour (so the two match).
    private fun coreColor(id: String) = rooms.firstOrNull { it.id == id }?.nodeColor ?: Color(0.72f, 0.72f, 0.82f, 1f)

    // ---- recycling-bottle mechanic (green-lab) ----
    private val bottleWashed = HashSet<String>()    // washed (clean) but set down, not yet recycled
    private val bottleDeposited = HashSet<String>() // recycled at the plant
    private var carryingBottle: String? = null
    private var carriedClean = false                // is the carried bottle washed
    private var sinkRoomIndex = -1
    private var depositRoomIndex = -1
    private val sinkPos = Vector2()
    private val depositPos = Vector2() // the recycler — kept in its own corner, clear of the plant station
    private val bottleHome = HashMap<String, Vector2>() // scatter spot per bottle
    private val bottleDrop = HashMap<String, Vector2>() // where a bottle was set down
    private val bottleTmp = Vector2()
    private val bottlesDone get() = currentLevel.bottles.isNotEmpty() && bottleDeposited.size >= currentLevel.bottles.size

    private val wallColor get() = currentLevel.bg
    private val floorTmp = Color() // reused per-room floor colour (theme-tinted)

    // ---- layout (rebuilt on every orientation change) ----
    private var landscape = false
    private var worldW = 480f
    private var worldH = 800f
    private var areaX = 0f; private var areaY = 0f; private var areaW = 0f; private var areaH = 0f
    private var cells = listOf<RoomCell>()
    private var walls = listOf<FloatArray>()
    private var stationPos = listOf<Vector2>()
    private val exitDoor = Vector2()
    private val titlePos = Vector2()
    private val solvedPos = Vector2()
    private val cluesAnchor = Vector2()

    /** Lay out [currentLevel] for the current orientation. The level's grid fits
     *  the play area, so each map fills the screen in both portrait and landscape. */
    private fun loadLevel() {
        val level = currentLevel
        if (landscape) {
            worldW = 800f; worldH = 480f
            areaX = 22f; areaY = 32f; areaW = worldW - 44f; areaH = worldH - 92f
            titlePos.set(worldW / 2f, worldH - 18f)
            solvedPos.set(worldW / 2f, worldH - 42f)
            actionCenter.set(worldW - 58f, 58f)
            closeCenter.set(48f, worldH - 40f)
            cluesAnchor.set(worldW - 88f, worldH - 24f)
        } else {
            worldW = 480f; worldH = 800f
            areaX = 22f; areaY = 56f; areaW = worldW - 44f; areaH = 638f
            titlePos.set(worldW / 2f, 768f)
            solvedPos.set(worldW / 2f, 735f)
            actionCenter.set(410f, 90f)
            closeCenter.set(42f, 752f)
            cluesAnchor.set(worldW / 2f, 62f)
        }
        val cw = areaW / level.gridCols; val ch = areaH / level.gridRows
        cells = level.rooms.map { r ->
            RoomCell(r.title, areaX + r.gx * cw, areaY + r.gy * ch, r.gw * cw, r.gh * ch, r.floor)
        }
        stationPos = cells.map { Vector2(it.x + it.w / 2f, it.y + it.h / 2f + 8f) }
        spawnRoomIndex = level.rooms.indexOfFirst { it.id == level.spawnRoom }.coerceAtLeast(0)
        exitRoomIndex = level.rooms.indexOfFirst { it.id == level.exitRoom }.coerceAtLeast(0)
        clueRoomIndex = level.clueRoom?.let { id -> level.rooms.indexOfFirst { it.id == id } } ?: -1
        val sp = cells[spawnRoomIndex]; spawnPos.set(sp.x + sp.w / 2f, sp.y + sp.h / 2f)
        // Push the exit door to the room's edge so it's a deliberate walk from the
        // station (e.g. the player powers the suit at centre, then heads to the door).
        val ex = cells[exitRoomIndex]; exitDoor.set(ex.x + ex.w * 0.9f, ex.y + ex.h * 0.85f)
        if (clueRoomIndex >= 0) { val cc = cells[clueRoomIndex]; cluePos.set(cc.x + cc.w / 2f, cc.y + cc.h * 0.72f) }

        // Core-carrying setup: loose cores at one end of the core room, the note on
        // the far side, the suit at the suit room's station.
        coreRoomIndex = level.coreRoom?.let { id -> level.rooms.indexOfFirst { it.id == id } } ?: -1
        suitRoomIndex = level.suitRoom?.let { id -> level.rooms.indexOfFirst { it.id == id } } ?: -1
        coreHome.clear()
        if (level.directDeliver) {
            // Each artefact rests in its own gallery (off to one side of the machine).
            level.cores.forEach { id ->
                val si = level.rooms.indexOfFirst { it.id == id }
                if (si >= 0) { val rc = cells[si]; coreHome[id] = Vector2(rc.x + rc.w * 0.3f, rc.y + rc.h * 0.3f) }
            }
        } else if (coreRoomIndex >= 0) {
            val cc = cells[coreRoomIndex]
            level.cores.forEachIndexed { i, id -> coreHome[id] = Vector2(cc.x + cc.w * (0.16f + 0.15f * i), cc.y + cc.h * 0.42f) }
            if (clueRoomIndex == coreRoomIndex) cluePos.set(cc.x + cc.w * 0.84f, cc.y + cc.h * 0.55f) // note on the far side
        }
        if (suitRoomIndex >= 0) suitPos.set(stationPos[suitRoomIndex])

        // Recycling-bottle setup: the sink in a corner of its room, bottles scattered
        // across their home rooms (kept clear of the sink/station in the plant room).
        sinkRoomIndex = level.sinkRoom?.let { id -> level.rooms.indexOfFirst { it.id == id } } ?: -1
        depositRoomIndex = level.depositRoom?.let { id -> level.rooms.indexOfFirst { it.id == id } } ?: -1
        if (sinkRoomIndex >= 0) { val sc = cells[sinkRoomIndex]; sinkPos.set(sc.x + sc.w * 0.26f, sc.y + sc.h * 0.84f) }
        // The recycler sits in the opposite corner from the centre plant station so the two don't overlap.
        if (depositRoomIndex >= 0) { val dc = cells[depositRoomIndex]; depositPos.set(dc.x + dc.w * 0.76f, dc.y + dc.h * 0.82f) }
        bottleHome.clear()
        level.bottles.forEachIndexed { i, id ->
            val ri = level.rooms.indexOfFirst { it.id == (level.bottleHomeRooms.getOrNull(i) ?: level.spawnRoom) }
            if (ri >= 0) { val rc = cells[ri]; bottleHome[id] = Vector2(rc.x + rc.w * 0.5f, rc.y + rc.h * (if (ri == sinkRoomIndex) 0.2f else 0.45f)) }
        }

        walls = buildLevelWalls(level, cw, ch)
    }

    /** Walls from the level's rooms: solid along every room boundary and the outer
     *  border (and around voids), with a single doorway gap per connected pair. */
    private fun buildLevelWalls(level: EscapeLevel, cw: Float, ch: Float): List<FloatArray> {
        val cols = level.gridCols; val rows = level.gridRows
        val unit = Array(cols) { IntArray(rows) { -1 } }
        level.rooms.forEachIndexed { i, r ->
            for (gx in r.gx until r.gx + r.gw) for (gy in r.gy until r.gy + r.gh)
                if (gx in 0 until cols && gy in 0 until rows) unit[gx][gy] = i
        }
        fun connected(a: Int, b: Int): Boolean {
            if (a < 0 || b < 0) return false
            val ida = level.rooms[a].id; val idb = level.rooms[b].id
            return (ida to idb) in level.doors || (idb to ida) in level.doors
        }
        val t = 8f
        val out = ArrayList<FloatArray>()
        val pairEdges = HashMap<Long, ArrayList<FloatArray>>() // [x, y, len, vertical]
        fun key(a: Int, b: Int) = minOf(a, b).toLong() * 100000L + maxOf(a, b)

        // Vertical edges (between columns).
        for (col in 0..cols) for (row in 0 until rows) {
            val a = if (col - 1 in 0 until cols) unit[col - 1][row] else -1
            val b = if (col in 0 until cols) unit[col][row] else -1
            if (a == b) continue
            val x = areaX + col * cw; val y0 = areaY + row * ch
            if (a >= 0 && b >= 0 && connected(a, b)) {
                pairEdges.getOrPut(key(a, b)) { ArrayList() }.add(floatArrayOf(x, y0, ch, 1f))
            } else {
                out.add(floatArrayOf(x - t / 2f, y0, t, ch))
            }
        }
        // Horizontal edges (between rows).
        for (row in 0..rows) for (col in 0 until cols) {
            val a = if (row - 1 in 0 until rows) unit[col][row - 1] else -1
            val b = if (row in 0 until rows) unit[col][row] else -1
            if (a == b) continue
            val y = areaY + row * ch; val x0 = areaX + col * cw
            if (a >= 0 && b >= 0 && connected(a, b)) {
                pairEdges.getOrPut(key(a, b)) { ArrayList() }.add(floatArrayOf(x0, y, cw, 0f))
            } else {
                out.add(floatArrayOf(x0, y - t / 2f, cw, t))
            }
        }
        // Open the middle shared edge of each connected pair; wall the rest.
        for (edges in pairEdges.values) {
            edges.sortBy { it[0] + it[1] }
            val doorIdx = edges.size / 2
            edges.forEachIndexed { idx, e ->
                val x = e[0]; val y = e[1]; val len = e[2]; val vertical = e[3] > 0.5f
                if (idx == doorIdx) {
                    val seg = (len - minOf(len * 0.7f, 90f)) / 2f
                    if (seg > 0f) {
                        if (vertical) { out.add(floatArrayOf(x - t / 2f, y, t, seg)); out.add(floatArrayOf(x - t / 2f, y + len - seg, t, seg)) }
                        else { out.add(floatArrayOf(x, y - t / 2f, seg, t)); out.add(floatArrayOf(x + len - seg, y - t / 2f, seg, t)) }
                    }
                } else {
                    if (vertical) out.add(floatArrayOf(x - t / 2f, y, t, len))
                    else out.add(floatArrayOf(x, y - t / 2f, len, t))
                }
            }
        }
        return out
    }

    private fun currentRoomIndex(): Int {
        val i = cells.indexOfFirst { pos.x >= it.x && pos.x < it.x + it.w && pos.y >= it.y && pos.y < it.y + it.h }
        return if (i < 0) 0 else i
    }

    /** Index of an interactable (unsolved) machine in the room the player is in. */
    private fun activeMachine(): Int? {
        val i = currentRoomIndex()
        val rd = rooms[i]
        // Solved stations stay usable so the player can revisit/review them.
        if (rd.puzzle == null) return null
        val p = stationPos[i]
        return if (dst(pos.x, pos.y, p.x, p.y) < 80f) i else null
    }

    private fun nearExit() =
        currentRoomIndex() == exitRoomIndex && dst(pos.x, pos.y, exitDoor.x, exitDoor.y) < 46f

    private fun nearClue() =
        clueRoomIndex >= 0 && currentRoomIndex() == clueRoomIndex && dst(pos.x, pos.y, cluePos.x, cluePos.y) < 72f

    /* ---- core-carrying helpers ---- */
    private fun stationIndexOf(id: String) = rooms.indexOfFirst { it.id == id }
    private fun roomIndexAt(p: Vector2): Int {
        val i = cells.indexOfFirst { p.x >= it.x && p.x < it.x + it.w && p.y >= it.y && p.y < it.y + it.h }
        return if (i < 0) currentRoomIndex() else i
    }
    private fun coreRoomOf(id: String): Int = when {
        id == carrying -> currentRoomIndex()
        id in delivered -> suitRoomIndex
        id in coopCarriedCache -> -1 // a teammate is holding it elsewhere
        coopDropCache.containsKey(id) -> roomIndexOfId(coopDropCache[id]!!) // a teammate left it here
        coreDrop.containsKey(id) -> roomIndexAt(coreDrop[id]!!)
        id in charged -> stationIndexOf(id)
        currentLevel.directDeliver -> stationIndexOf(id) // the artefact's own gallery
        else -> coreRoomIndex
    }
    private fun corePos(id: String): Vector2 = when {
        id == carrying -> coreTmp.set(pos.x, pos.y + CHAR_R + 16f)
        id in delivered -> if (currentLevel.directDeliver) // on the Time Capsule's shelf (Vault) vs. at the suit (Tower)
            coreTmp.set(suitPos.x + (currentLevel.cores.indexOf(id) - 1) * 30f, suitPos.y + 24f)
        else coreTmp.set(suitPos.x + (currentLevel.cores.indexOf(id) - 1) * 34f, suitPos.y - 34f)
        coopDropCache.containsKey(id) -> roomIndexOfId(coopDropCache[id]!!).let { if (it >= 0) coreTmp.set(cells[it].x + cells[it].w / 2f, cells[it].y + cells[it].h / 2f) else coreTmp.set(0f, 0f) }
        coreDrop.containsKey(id) -> coreTmp.set(coreDrop[id]!!)
        id in charged -> stationIndexOf(id).let { if (it >= 0) coreTmp.set(stationPos[it].x, stationPos[it].y - 34f) else coreTmp.set(0f, 0f) }
        else -> coreHome[id]?.let { coreTmp.set(it) } ?: coreTmp.set(0f, 0f)
    }
    /** A core the player can pick up right now (loose in the core room, or a charged one at its station). */
    private fun pickableCore(): String? {
        if (carrying != null) return null
        val held = coopCarriedCache
        for (id in currentLevel.cores) {
            if (id in delivered || id in held) continue // already delivered, or a teammate has it
            if (currentLevel.directDeliver && id !in solved) continue // artefact stays sealed until its gallery is solved
            if (coreRoomOf(id) == currentRoomIndex() && dst(pos.x, pos.y, corePos(id).x, corePos(id).y) < 60f) return id
        }
        return null
    }
    private fun nearStation(id: String): Boolean {
        val i = stationIndexOf(id)
        return i >= 0 && currentRoomIndex() == i && dst(pos.x, pos.y, stationPos[i].x, stationPos[i].y) < 80f
    }
    private fun nearSuit(): Boolean =
        suitRoomIndex >= 0 && currentRoomIndex() == suitRoomIndex && dst(pos.x, pos.y, suitPos.x, suitPos.y) < 80f

    /* ---- recycling-bottle helpers ---- */
    private fun bottleRoomOf(id: String): Int = when {
        id == carryingBottle -> currentRoomIndex()
        id in bottleDeposited -> depositRoomIndex
        id in coopCarriedCache -> -1 // a teammate is holding it elsewhere
        coopDropCache.containsKey(id) -> roomIndexOfId(coopDropCache[id]!!) // a teammate left it here
        bottleDrop.containsKey(id) -> roomIndexAt(bottleDrop[id]!!)
        else -> bottleHome[id]?.let { roomIndexAt(it) } ?: -1
    }
    private fun bottlePos(id: String): Vector2 = when {
        id == carryingBottle -> bottleTmp.set(pos.x, pos.y + CHAR_R + 18f)
        id in bottleDeposited -> bottleTmp.set(depositPos.x + (currentLevel.bottles.indexOf(id) - 1) * 26f, depositPos.y - 26f)
        coopDropCache.containsKey(id) -> roomIndexOfId(coopDropCache[id]!!).let { if (it >= 0) bottleTmp.set(cells[it].x + cells[it].w / 2f, cells[it].y + cells[it].h / 2f) else bottleTmp.set(0f, 0f) }
        bottleDrop.containsKey(id) -> bottleTmp.set(bottleDrop[id]!!)
        else -> bottleHome[id]?.let { bottleTmp.set(it) } ?: bottleTmp.set(0f, 0f)
    }
    /** A bottle the player can pick up right now (loose in the room they're standing in). */
    private fun pickableBottle(): String? {
        if (carryingBottle != null) return null
        val held = coopCarriedCache
        for (id in currentLevel.bottles) {
            if (id in bottleDeposited || id in held) continue // already recycled, or a teammate has it
            if (bottleRoomOf(id) == currentRoomIndex() && dst(pos.x, pos.y, bottlePos(id).x, bottlePos(id).y) < 60f) return id
        }
        return null
    }
    private fun nearSink(): Boolean =
        sinkRoomIndex >= 0 && currentRoomIndex() == sinkRoomIndex && dst(pos.x, pos.y, sinkPos.x, sinkPos.y) < 72f
    private fun nearDeposit(): Boolean =
        depositRoomIndex >= 0 && currentRoomIndex() == depositRoomIndex && dst(pos.x, pos.y, depositPos.x, depositPos.y) < 70f

    private enum class Phase { PLAYING, PUZZLE, WON }

    // The walking scene uses an orientation-aware viewport; puzzle/win overlays use
    // a fixed 480x800 portrait viewport so their tuned layouts always fit (they
    // letterbox when the device is landscape).
    private lateinit var worldCam: OrthographicCamera
    private lateinit var worldViewport: FitViewport
    private lateinit var puzzleCam: OrthographicCamera
    private lateinit var puzzleViewport: FitViewport
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var smooth: SmoothDraw // anti-aliased circles/lines through the batch
    private val layout = GlyphLayout()
    private var winTime = 0f // seconds since the win screen opened (drives the typewriter)

    private val solved = HashSet<String>()
    private val pos = Vector2()
    private var phase = Phase.PLAYING

    private var joyActive = false
    private val joyOrigin = Vector2()
    private val joyKnob = Vector2()

    private var activePuzzle: Puzzle? = null
    private var activeStationId: String? = null
    private var wrongFlash = 0f
    private var flashMsg = ""
    private var prevTouched = false
    private var puzzleHadMistake = false
    // The open puzzle is solved and now in (locked) review mode until the X is tapped.
    private var puzzleSolved = false
    private var justSolved = false // true only on a fresh solve, so the banner doesn't replay on review re-open
    private var solvedTime = 0f // seconds the "Solved!" banner has been showing
    private val touch = Vector2()

    override fun create() {
        worldCam = OrthographicCamera()
        puzzleCam = OrthographicCamera()
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()
        enrichFont(font)
        smooth = SmoothDraw()
        font.setUseIntegerPositions(false)
        loadLevel()
        worldViewport = FitViewport(worldW, worldH, worldCam)
        puzzleViewport = FitViewport(W, H, puzzleCam)
        pos.set(spawnPos)
        Gdx.input.setCatchKey(com.badlogic.gdx.Input.Keys.BACK, false)
    }

    /**
     * Teach the default (ASCII-only) [BitmapFont] a handful of non-ASCII glyphs so
     * they stop rendering as the missing-glyph box (e.g. the em dash in "Power up
     * the display — ...").
     *
     * This mirrors how TextraTypist (github.com/tommyettinger/textratypist) models
     * a font — a BitmapFont plus extra glyphs mapped to texture regions — but is
     * reimplemented asset-free to keep the app's "no third-party SDKs, no bundled
     * assets" stance (the same reason [RichText] in GdxToolkit is a tiny hand-rolled
     * stand-in, not the library). Each added glyph *reuses an existing glyph's*
     * texture region, so no new atlas/texture is created:
     *   • em / en dash → the hyphen bar, widened (it's a solid shape, so the region
     *     stretches to a longer bar cleanly)
     *   • smart quotes → their straight ASCII equivalents
     */
    private fun enrichFont(f: BitmapFont) {
        val data = f.data
        // Map [codepoint] onto an existing glyph's pixels. [widen] stretches the
        // reused region horizontally (only sensible for solid bars like the dash);
        // the original side bearings are preserved in the advance width.
        fun map(codepoint: Int, from: Char, widen: Float = 1f) {
            val src = data.getGlyph(from) ?: return
            data.setGlyph(codepoint, BitmapFont.Glyph().apply {
                id = codepoint
                page = src.page
                srcX = src.srcX; srcY = src.srcY
                u = src.u; v = src.v; u2 = src.u2; v2 = src.v2
                xoffset = src.xoffset; yoffset = src.yoffset
                height = src.height
                width = (src.width * widen).roundToInt()
                xadvance = width + (src.xadvance - src.width) // keep the side bearing
            })
        }
        map(0x2014, '-', widen = 2.8f) // — em dash
        map(0x2013, '-', widen = 1.8f) // – en dash
        map(0x2018, '\'')              // ' left single quote
        map(0x2019, '\'')              // ' right single quote / curly apostrophe
        map(0x201C, '"')               // " left double quote
        map(0x201D, '"')               // " right double quote
    }

    /* ----------------------------- helpers ----------------------------- */

    /** Unproject through whichever viewport owns the current phase. */
    private fun unprojectTouch(): Vector2 {
        touch.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        return (if (phase == Phase.PLAYING) worldViewport else puzzleViewport).unproject(touch)
    }

    private val actionR = 46f
    private val closeR = 26f
    private val actionCenter = Vector2(410f, 90f) // repositioned per orientation in buildLayout
    private val closeCenter = Vector2(42f, 752f)
    private val backTmp = Vector2()
    // Puzzle close (X), top-right of the panel — follows the orientation-aware panel.
    private val backCenter: Vector2 get() = backTmp.set(PANEL_X + PANEL_W - 36f, PANEL_Y + PANEL_H - 32f)
    private val backR = 26f

    private fun dst(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by; return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /** True if a CHAR_R circle at (cx,cy) overlaps any wall — used for collision. */
    private fun blocked(cx: Float, cy: Float): Boolean {
        val r = CHAR_R - 2f
        for (w in walls) {
            val px = cx.coerceIn(w[0], w[0] + w[2])
            val py = cy.coerceIn(w[1], w[1] + w[3])
            val dx = cx - px; val dy = cy - py
            if (dx * dx + dy * dy < r * r) return true
        }
        return false
    }

    private fun within(p: Vector2, c: Vector2, r: Float) = dst(p.x, p.y, c.x, c.y) < r
    private fun inRect(p: Vector2, r: FloatArray) = p.x >= r[0] && p.x <= r[0] + r[2] && p.y >= r[1] && p.y <= r[1] + r[3]
    private fun sgn(x: Int) = if (x > 0) 1 else if (x < 0) -1 else 0

    private fun isLocked(i: Int): Boolean {
        val r = rooms[i]
        // The suit's final puzzle stays locked until every core has been delivered.
        if (currentLevel.suitRoom == r.id && delivered.size < currentLevel.cores.size) return true
        // The bottle-gated room (Power Circuit) stays locked until every bottle is recycled.
        if (currentLevel.bottleGateRoom == r.id && !bottlesDone) return true
        if (r.requires != null && r.requires !in solved) return true
        return r.requiresAll.any { it !in solved }
    }

    /** The first still-unsolved room this station is waiting on (for the lock message). */
    private fun lockedOn(i: Int): String? {
        val r = rooms[i]
        r.requires?.let { if (it !in solved) return it }
        return r.requiresAll.firstOrNull { it !in solved }
    }
    private fun labelOf(id: String) = rooms.firstOrNull { it.id == id }?.title ?: id
    private fun collectedClues() = rooms
        .filter { it.id in solved && it.clue != null }
        .map { it.clue!! }

    /** One of 8 simple shape pictograms (stand-ins for the source's emoji), drawn
     *  anti-aliased through [batch] (call inside a batch pass, not a shapes pass). */
    private fun drawGlyph(kind: Int, cx: Float, cy: Float, s: Float) {
        val col = glyphColors[kind % glyphColors.size]
        when (kind % 8) {
            0 -> smooth.circle(batch, cx, cy, s, col)
            1 -> smooth.rect(batch, cx - s, cy - s, 2 * s, 2 * s, col)
            2 -> smooth.triangle(batch, cx - s, cy - s, 2 * s, 2 * s, col)
            3 -> { smooth.triangle(batch, cx - s, cy, 2 * s, s, col); smooth.triangle(batch, cx - s, cy - s, 2 * s, s, col, pointDown = true) }
            4 -> { smooth.circle(batch, cx, cy, s, col); smooth.circle(batch, cx, cy, s * 0.5f, Color.WHITE) }
            5 -> { smooth.rect(batch, cx - s * 0.35f, cy - s, s * 0.7f, 2 * s, col); smooth.rect(batch, cx - s, cy - s * 0.35f, 2 * s, s * 0.7f, col) }
            6 -> { smooth.line(batch, cx - s, cy - s, cx + s, cy + s, s * 0.5f, col); smooth.line(batch, cx - s, cy + s, cx + s, cy - s, s * 0.5f, col) }
            7 -> { smooth.circle(batch, cx, cy, s * 0.5f, col); smooth.line(batch, cx, cy - s, cx, cy + s, s * 0.4f, col); smooth.line(batch, cx - s, cy, cx + s, cy, s * 0.4f, col) }
        }
    }

    /** A little robot pictogram for the counting field (anti-aliased, via [batch]). */
    private fun drawRobot(cx: Float, cy: Float, s: Float) {
        val teal = Color(0.30f, 0.78f, 0.78f, 1f)
        smooth.line(batch, cx, cy + s, cx, cy + s * 1.5f, 3f, teal)
        smooth.circle(batch, cx, cy + s * 1.5f, 3f, teal)
        smooth.rect(batch, cx - s, cy - s, 2 * s, 2 * s, teal)
        smooth.circle(batch, cx - s * 0.4f, cy + s * 0.1f, s * 0.22f, cInk)
        smooth.circle(batch, cx + s * 0.4f, cy + s * 0.1f, s * 0.22f, cInk)
    }

    private val animalColors = arrayOf(
        Color(0.96f, 0.66f, 0.30f, 1f), // cat — ginger
        Color(0.62f, 0.46f, 0.34f, 1f), // dog — brown
        Color(0.95f, 0.95f, 0.97f, 1f), // rabbit — white
        Color(0.52f, 0.38f, 0.28f, 1f), // bear — dark brown
        Color(0.42f, 0.78f, 0.46f, 1f), // frog — green
        Color(0.74f, 0.74f, 0.80f, 1f), // mouse — grey
    )

    /** A friendly animal-face pictogram (anti-aliased, via [batch]) — a recognisable
     *  stand-in for the source's emoji animals in the sharing puzzle. [s] is the face
     *  radius. Differentiated mostly by ear shape so each critter reads at a glance. */
    private fun drawAnimal(kind: Int, cx: Float, cy: Float, s: Float) {
        val body = animalColors[kind % animalColors.size]
        val ink = cInk
        fun face() = smooth.circle(batch, cx, cy, s, body)
        fun eyes(dy: Float = 0.12f) {
            smooth.circle(batch, cx - s * 0.34f, cy + s * dy, s * 0.12f, ink)
            smooth.circle(batch, cx + s * 0.34f, cy + s * dy, s * 0.12f, ink)
        }
        fun nose() = smooth.circle(batch, cx, cy - s * 0.2f, s * 0.13f, ink)
        when (kind % 6) {
            0 -> { // cat — pointy ears + whiskers
                smooth.triangle(batch, cx - s * 0.95f, cy + s * 0.3f, s * 0.7f, s * 0.85f, body)
                smooth.triangle(batch, cx + s * 0.25f, cy + s * 0.3f, s * 0.7f, s * 0.85f, body)
                face(); eyes(); nose()
                smooth.line(batch, cx - s * 0.25f, cy - s * 0.18f, cx - s * 0.9f, cy - s * 0.08f, 2f, ink)
                smooth.line(batch, cx + s * 0.25f, cy - s * 0.18f, cx + s * 0.9f, cy - s * 0.08f, 2f, ink)
            }
            1 -> { // dog — floppy round ears at the sides
                smooth.circle(batch, cx - s * 0.85f, cy + s * 0.15f, s * 0.42f, body)
                smooth.circle(batch, cx + s * 0.85f, cy + s * 0.15f, s * 0.42f, body)
                face(); eyes(); nose()
            }
            2 -> { // rabbit — tall upright ears
                smooth.line(batch, cx - s * 0.38f, cy + s * 0.5f, cx - s * 0.42f, cy + s * 1.7f, s * 0.34f, body)
                smooth.line(batch, cx + s * 0.38f, cy + s * 0.5f, cx + s * 0.42f, cy + s * 1.7f, s * 0.34f, body)
                face(); eyes(); nose()
            }
            3 -> { // bear — small round ears on top
                smooth.circle(batch, cx - s * 0.62f, cy + s * 0.7f, s * 0.32f, body)
                smooth.circle(batch, cx + s * 0.62f, cy + s * 0.7f, s * 0.32f, body)
                face(); eyes(); nose()
            }
            4 -> { // frog — eye bumps straddling the top, wide smile
                smooth.circle(batch, cx - s * 0.5f, cy + s * 0.7f, s * 0.34f, body)
                smooth.circle(batch, cx + s * 0.5f, cy + s * 0.7f, s * 0.34f, body)
                face()
                smooth.circle(batch, cx - s * 0.5f, cy + s * 0.78f, s * 0.14f, ink)
                smooth.circle(batch, cx + s * 0.5f, cy + s * 0.78f, s * 0.14f, ink)
                smooth.line(batch, cx - s * 0.45f, cy - s * 0.3f, cx + s * 0.45f, cy - s * 0.3f, 2.5f, ink)
            }
            else -> { // mouse — big round ears
                smooth.circle(batch, cx - s * 0.7f, cy + s * 0.6f, s * 0.5f, body)
                smooth.circle(batch, cx + s * 0.7f, cy + s * 0.6f, s * 0.5f, body)
                face(); eyes(); nose()
            }
        }
    }

    /** A little plastic-bottle pictogram (anti-aliased, via [batch]). Dirty bottles are
     *  a murky green-grey; a washed one is bright blue with a sparkle. [s] is the body radius. */
    private fun drawBottle(cx: Float, cy: Float, s: Float, clean: Boolean) {
        val body = if (clean) Color(0.42f, 0.74f, 0.96f, 1f) else Color(0.52f, 0.58f, 0.50f, 1f)
        smooth.rect(batch, cx - s * 0.5f, cy - s, s, s * 1.5f, body)                       // body
        smooth.circle(batch, cx, cy + s * 0.5f, s * 0.5f, body)                            // shoulder
        smooth.rect(batch, cx - s * 0.16f, cy + s * 0.5f, s * 0.32f, s * 0.6f, body)        // neck
        smooth.rect(batch, cx - s * 0.22f, cy + s * 1.0f, s * 0.44f, s * 0.22f, Color(0.30f, 0.40f, 0.30f, 1f)) // cap
        if (clean) smooth.circle(batch, cx + s * 0.16f, cy - s * 0.1f, s * 0.16f, Color(1f, 1f, 1f, 0.9f))      // sparkle
    }

    /* ----------------------- floor textures ---------------------------- */

    /** The floor texture kind for room [idx] (its own override, else the level's). */
    private fun floorKindOf(idx: Int): String = rooms[idx].floorKind ?: currentLevel.floorKind

    /** Paint a subtle repeating floor texture over a room's floor fill — the
     *  LibGDX counterpart to the web rooms' `FLOOR_TEXTURE`. Drawn in the shapes
     *  pass (so it sits under the walls, props, station and player), and only for
     *  the room in view (every other room is fogged over anyway). All strokes are
     *  clamped inside the cell so nothing bleeds into a neighbour. Expects [shapes]
     *  to be mid `ShapeType.Filled`. */
    private fun drawFloorTexture(c: RoomCell, kind: String) {
        when (kind) {
            "metal" -> floorMetal(c)                                  // tread plate + studs
            "concrete" -> { seamGrid(c, 116f, texGroove, 2f); floorMottle(c, 3) }
            "stone" -> { seamGrid(c, 100f, texStone, 2f); floorMottle(c, 3) }
            "tile" -> seamGrid(c, 30f, texGrout, 1f)                  // fine ceramic grout
            "wood" -> floorWood(c)                                    // plank seams
            "grass" -> floorGrass(c)                                  // scattered blades
            "panel" -> floorPanel(c)                                  // indigo tech lattice
        }
    }

    /** Continuous grid of thin seams at [pitch], clamped inside the cell. */
    private fun seamGrid(c: RoomCell, pitch: Float, color: Color, th: Float) {
        shapes.color = color
        var x = c.x + pitch
        while (x < c.x + c.w) { shapes.rect(x - th / 2f, c.y, th, c.h); x += pitch }
        var y = c.y + pitch
        while (y < c.y + c.h) { shapes.rect(c.x, y - th / 2f, c.w, th); y += pitch }
    }

    /** A few big, faint dark blobs — the poured-slab / flagstone mottle. */
    private fun floorMottle(c: RoomCell, n: Int) {
        shapes.color = texMottle
        var seed = (c.x.toInt() * 73856093) xor (c.y.toInt() * 19349663)
        fun rnd(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 16) and 0x7fff) / 32768f }
        repeat(n) { shapes.circle(c.x + rnd() * c.w, c.y + rnd() * c.h, 40f + rnd() * 40f) }
    }

    /** Metal tread plate: a plate grid plus an embossed diamond stud per plate. */
    private fun floorMetal(c: RoomCell) {
        val pitch = 40f
        seamGrid(c, pitch, Color(0f, 0f, 0f, 0.22f), 1.5f)
        val r = 5f
        var gy = c.y + pitch / 2f
        while (gy < c.y + c.h) {
            var gx = c.x + pitch / 2f
            while (gx < c.x + c.w) {
                if (gx - r > c.x && gx + r < c.x + c.w && gy - r > c.y && gy + r < c.y + c.h) {
                    shapes.color = texHi // top edges catch the light
                    shapes.rectLine(gx - r, gy, gx, gy + r, 1.2f)
                    shapes.rectLine(gx, gy + r, gx + r, gy, 1.2f)
                    shapes.color = texLo // bottom edges fall into shade
                    shapes.rectLine(gx + r, gy, gx, gy - r, 1.2f)
                    shapes.rectLine(gx, gy - r, gx - r, gy, 1.2f)
                }
                gx += pitch
            }
            gy += pitch
        }
    }

    /** Wooden planks: horizontal seams with a faint two-row shade banding. */
    private fun floorWood(c: RoomCell) {
        var y = c.y + 15f
        var band = 0
        while (y < c.y + c.h) {
            shapes.color = texWood
            shapes.rect(c.x, y - 0.6f, c.w, 1.2f)
            shapes.color = if (band % 2 == 0) texBandLo else texBandHi
            shapes.rect(c.x, y, c.w, minOf(15f, c.y + c.h - y))
            y += 15f; band++
        }
    }

    /** A grass lawn: a green wash over the (themed) floor fill, soft two-tone
     *  mottle for depth, then tufts of blades — seeded from the cell so the turf
     *  is stable frame to frame (never a grid, never boxes a prop). */
    private fun floorGrass(c: RoomCell) {
        var seed = (c.x.toInt() * 73856093) xor (c.y.toInt() * 19349663)
        fun rnd(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 16) and 0x7fff) / 32768f }
        // Green lawn base — the room's fill is themed maroon, so grass needs its own green.
        shapes.color = grassWash
        shapes.rect(c.x, c.y, c.w, c.h)
        shapes.color = grassMottleLo
        repeat(4) { shapes.circle(c.x + rnd() * c.w, c.y + rnd() * c.h, 30f + rnd() * 36f) }
        shapes.color = grassMottleHi
        repeat(3) { shapes.circle(c.x + rnd() * c.w, c.y + rnd() * c.h, 26f + rnd() * 30f) }
        // Tufts — three fanned blades from a shared base, so it reads as clumps of
        // grass rather than scattered dashes.
        val tufts = ((c.w * c.h) / 1500f).toInt().coerceIn(14, 60)
        repeat(tufts) {
            val bx = c.x + 8f + rnd() * (c.w - 16f)
            val by = c.y + 6f + rnd() * (c.h - 18f)
            for (k in -1..1) {
                val lean = k * 2.3f + (rnd() - 0.5f) * 1.6f
                val len = 6f + rnd() * 5f
                shapes.color = grassCols[(rnd() * grassCols.size).toInt().coerceIn(0, grassCols.size - 1)]
                shapes.rectLine(bx, by, bx + lean, by + len, 1.8f)
            }
        }
    }

    /** Hero-HQ tech floor: an indigo lattice (grid + glowing nodes at the joints). */
    private fun floorPanel(c: RoomCell) {
        val pitch = 34f
        seamGrid(c, pitch, texPanel, 1.2f)
        shapes.color = texPanelDot
        var gy = c.y + pitch
        while (gy < c.y + c.h - 2f) {
            var gx = c.x + pitch
            while (gx < c.x + c.w - 2f) { shapes.circle(gx, gy, 2.2f); gx += pitch }
            gy += pitch
        }
    }

    /* ------------------------ themed floor decor ----------------------- */

    /** Faded, non-interactive props themed to the room, set in two opposite
     *  corners of the room you're standing in (drawn in the batch pass). They read
     *  as background decals — clearly not something to tap. */
    private fun drawRoomDecor(c: RoomCell, title: String) {
        val x1 = c.x + 34f; val y1 = c.y + 32f                 // a near corner
        val x2 = c.x + c.w - 34f; val y2 = c.y + c.h - 42f     // the far corner
        when (title) {
            "Control Panel", "Main Lab", "Exit Chamber" -> { decorGear(x1, y1, 15f); decorGear(x2, y2, 11f) }
            "Word Display", "Symbol Decoder" -> { decorScreen(x1, y1, 17f); decorGear(x2, y2, 11f) }
            "Robot Helper" -> { decorRobot(x1, y1, 15f); decorGear(x2, y2, 11f) }
            "Solar Panel" -> { decorSolarPanel(x1, y1, 15f); decorPlant(x2, y2, 13f) }
            "Recycling Plant" -> { decorBin(x1, y1, 15f); decorPlant(x2, y2, 12f) }
            "Power Circuit" -> { decorBulb(x1, y1, 15f); decorBattery(x2, y2, 11f) }
            "Hawker Stall" -> { decorBowl(x1, y1, 16f); decorStool(x2, y2, 14f) }
            "Little India" -> { decorRangoli(x1, y1, 15f); decorDiya(x2, y2, 13f) }
            "Gardens" -> { decorPlant(x1, y1, 15f); decorPlant(x2, y2, 12f) }
            "Fruit Stall" -> { decorFruit(x1, y1, 14f, 3); decorFruit(x2, y2, 12f, 1) }
            "Grand Hall", "Lion City Room" -> { decorLantern(x1, y1, 15f); decorLantern(x2, y2, 11f) }
            "Founding Gallery" -> { decorScroll(x1, y1, 15f) }
            "Independence Hall" -> { decorFlag(x1, y1, 16f) }
            "Core Charger" -> { decorBattery(x1, y1, 15f) }
            "The Suit" -> { decorPodium(x1, y1, 15f) }
            else -> {}
        }
    }

    private fun decorGear(x: Float, y: Float, s: Float) {
        val c = Color(0.72f, 0.75f, 0.82f, 0.45f)
        // Eight teeth around the rim (unit directions, incl. diagonals) read as a cog.
        val r = s * 0.85f
        val u = 0.707f
        val dirs = arrayOf(
            0f to 1f, u to u, 1f to 0f, u to -u,
            0f to -1f, -u to -u, -1f to 0f, -u to u,
        )
        for ((dx, dy) in dirs) smooth.rect(batch, x + dx * r - s * 0.2f, y + dy * r - s * 0.2f, s * 0.4f, s * 0.4f, c)
        smooth.circle(batch, x, y, s * 0.66f, c)                                  // body
        smooth.circle(batch, x, y, s * 0.27f, Color(0.10f, 0.12f, 0.18f, 0.45f))  // hub hole
    }

    private fun decorScreen(x: Float, y: Float, s: Float) {
        smooth.rect(batch, x - s, y - s * 0.75f, 2f * s, 1.5f * s, Color(0.28f, 0.42f, 0.52f, 0.5f))
        smooth.rect(batch, x - s * 0.78f, y - s * 0.52f, 1.56f * s, 1.04f * s, Color(0.42f, 0.74f, 0.85f, 0.5f))
        smooth.line(batch, x - s * 0.5f, y, x + s * 0.5f, y, s * 0.18f, Color(0.92f, 0.97f, 1f, 0.55f))
    }

    private fun decorRobot(x: Float, y: Float, s: Float) {
        val c = Color(0.35f, 0.78f, 0.78f, 0.5f)
        smooth.line(batch, x, y + s, x, y + s * 1.5f, 3f, c); smooth.circle(batch, x, y + s * 1.5f, 3f, c)
        smooth.rect(batch, x - s, y - s, 2f * s, 2f * s, c)
        smooth.circle(batch, x - s * 0.4f, y + s * 0.1f, s * 0.2f, Color(0.10f, 0.12f, 0.18f, 0.55f))
        smooth.circle(batch, x + s * 0.4f, y + s * 0.1f, s * 0.2f, Color(0.10f, 0.12f, 0.18f, 0.55f))
    }

    private fun decorSolarPanel(x: Float, y: Float, s: Float) {
        val panel = Color(0.24f, 0.34f, 0.62f, 0.55f)
        val grid = Color(0.50f, 0.60f, 0.82f, 0.5f)
        val leg = Color(0.50f, 0.52f, 0.58f, 0.55f)
        smooth.rect(batch, x - s, y - s * 0.1f, 2f * s, 1.1f * s, panel)                 // PV panel
        smooth.line(batch, x - s * 0.33f, y, x - s * 0.33f, y + s, s * 0.08f, grid)
        smooth.line(batch, x + s * 0.33f, y, x + s * 0.33f, y + s, s * 0.08f, grid)
        smooth.line(batch, x - s, y + s * 0.45f, x + s, y + s * 0.45f, s * 0.08f, grid)
        smooth.line(batch, x - s * 0.5f, y - s * 0.1f, x - s * 0.5f, y - s, s * 0.16f, leg)
        smooth.line(batch, x + s * 0.5f, y - s * 0.1f, x + s * 0.5f, y - s, s * 0.16f, leg)
    }

    private fun decorBin(x: Float, y: Float, s: Float) {
        val body = Color(0.30f, 0.62f, 0.45f, 0.55f)
        smooth.rect(batch, x - s * 0.7f, y - s, 1.4f * s, 1.8f * s, body)                // bin
        smooth.rect(batch, x - s * 0.9f, y + s * 0.75f, 1.8f * s, s * 0.3f, body)         // lid
        smooth.line(batch, x, y + s * 0.75f, x, y + s * 1.1f, s * 0.16f, body)            // handle
        smooth.triangle(batch, x - s * 0.32f, y - s * 0.2f, s * 0.64f, s * 0.55f, Color(0.95f, 0.97f, 0.95f, 0.5f)) // recycle mark
    }

    private fun decorBulb(x: Float, y: Float, s: Float) {
        smooth.circle(batch, x, y + s * 0.2f, s * 0.72f, Color(0.96f, 0.82f, 0.35f, 0.55f))
        smooth.rect(batch, x - s * 0.3f, y - s * 0.95f, s * 0.6f, s * 0.5f, Color(0.62f, 0.62f, 0.66f, 0.55f))
    }

    private fun decorBowl(x: Float, y: Float, s: Float) {
        val bowl = Color(0.94f, 0.60f, 0.34f, 0.55f)
        val rim = Color(0.84f, 0.50f, 0.28f, 0.6f)
        val broth = Color(0.97f, 0.80f, 0.44f, 0.6f)
        val steam = Color(0.95f, 0.95f, 0.95f, 0.4f)
        smooth.circle(batch, x, y - s * 0.15f, s * 0.85f, bowl)                 // rounded bowl body
        smooth.rect(batch, x - s, y + s * 0.28f, 2f * s, s * 0.28f, rim)         // wide flat opening...
        smooth.circle(batch, x - s, y + s * 0.42f, s * 0.14f, rim)              // ...with rounded ends
        smooth.circle(batch, x + s, y + s * 0.42f, s * 0.14f, rim)
        smooth.rect(batch, x - s * 0.8f, y + s * 0.32f, 1.6f * s, s * 0.18f, broth) // soup peeking inside
        steamWisp(x - s * 0.3f, y + s * 0.6f, s, steam)                          // curvy rising steam
        steamWisp(x + s * 0.3f, y + s * 0.6f, s, steam)
    }

    /** A wavy, round-capped wisp of rising steam (so it isn't a straight line). */
    private fun steamWisp(sx: Float, sy: Float, s: Float, c: Color) {
        smooth.line(batch, sx, sy, sx - s * 0.2f, sy + s * 0.35f, 2.5f, c)
        smooth.line(batch, sx - s * 0.2f, sy + s * 0.35f, sx + s * 0.2f, sy + s * 0.7f, 2.5f, c)
        smooth.line(batch, sx + s * 0.2f, sy + s * 0.7f, sx, sy + s * 1.05f, 2.5f, c)
    }

    /** A round wooden hawker stool: a flat seat on three splayed legs. */
    private fun decorStool(x: Float, y: Float, s: Float) {
        val seat = Color(0.82f, 0.52f, 0.18f, 0.6f)   // warm wood top
        val leg = Color(0.55f, 0.30f, 0.10f, 0.6f)    // darker legs
        // Three legs splaying down from under the seat.
        smooth.line(batch, x - s * 0.5f, y + s * 0.2f, x - s * 0.72f, y - s * 0.9f, s * 0.16f, leg)
        smooth.line(batch, x + s * 0.5f, y + s * 0.2f, x + s * 0.72f, y - s * 0.9f, s * 0.16f, leg)
        smooth.line(batch, x, y + s * 0.2f, x, y - s * 0.95f, s * 0.16f, leg)
        // Seat — a rounded flat disc (a thick round-capped bar reads as an ellipse).
        smooth.line(batch, x - s * 0.85f, y + s * 0.32f, x + s * 0.85f, y + s * 0.32f, s * 0.5f, Color(0.62f, 0.36f, 0.12f, 0.6f)) // rim shadow
        smooth.line(batch, x - s * 0.85f, y + s * 0.42f, x + s * 0.85f, y + s * 0.42f, s * 0.42f, seat)                            // seat top
    }

    /** A diya — a clay oil lamp with a flame (Diwali / Little India). */
    private fun decorDiya(x: Float, y: Float, s: Float) {
        val clay = Color(0.72f, 0.30f, 0.10f, 0.65f)
        val clayRim = Color(0.55f, 0.20f, 0.07f, 0.65f)
        smooth.circle(batch, x, y + s * 0.15f, s * 0.95f, Color(1f, 0.88f, 0.50f, 0.22f))                    // warm glow
        smooth.line(batch, x - s * 0.7f, y - s * 0.2f, x + s * 0.7f, y - s * 0.2f, s * 0.44f, clay)          // clay bowl body
        smooth.line(batch, x - s * 0.82f, y + s * 0.02f, x + s * 0.82f, y + s * 0.02f, s * 0.16f, clayRim)   // bowl rim
        smooth.rect(batch, x - s * 0.05f, y + s * 0.05f, s * 0.1f, s * 0.18f, Color(0.30f, 0.15f, 0.05f, 0.7f)) // wick
        smooth.triangle(batch, x - s * 0.19f, y + s * 0.12f, s * 0.38f, s * 0.72f, Color(0.98f, 0.60f, 0.16f, 0.8f)) // outer flame
        smooth.triangle(batch, x - s * 0.1f, y + s * 0.2f, s * 0.2f, s * 0.46f, Color(1f, 0.9f, 0.45f, 0.85f))       // inner flame
    }

    /** A rangoli — a colourful symmetric floor pattern (Diwali). Flat ground detail:
     *  a ring of dots, a ring of petals and a bright centre bindu. */
    private fun decorRangoli(x: Float, y: Float, s: Float) {
        val cols = arrayOf(
            Color(0.96f, 0.45f, 0.71f, 0.7f), Color(0.98f, 0.75f, 0.20f, 0.7f),
            Color(0.22f, 0.74f, 0.95f, 0.7f), Color(0.66f, 0.33f, 0.93f, 0.7f),
        )
        val tau = 2f * Math.PI.toFloat()
        for (i in 0 until 12) {
            val a = i / 12f * tau
            smooth.circle(batch, x + cos(a) * s * 0.95f, y + sin(a) * s * 0.95f, s * 0.11f, cols[i % 4])   // outer dot ring
        }
        for (i in 0 until 8) {
            val a = i / 8f * tau
            smooth.circle(batch, x + cos(a) * s * 0.55f, y + sin(a) * s * 0.55f, s * 0.17f, cols[i % 4])   // inner petal ring
        }
        smooth.circle(batch, x, y, s * 0.28f, Color(0.98f, 0.80f, 0.13f, 0.75f))                           // centre
        smooth.circle(batch, x, y, s * 0.12f, Color(0.86f, 0.15f, 0.15f, 0.8f))                            // bindu
    }

    private fun decorPlant(x: Float, y: Float, s: Float) {
        val pot = Color(0.74f, 0.46f, 0.32f, 0.6f)
        val leafy = Color(0.36f, 0.72f, 0.44f, 0.58f)
        smooth.circle(batch, x, y + s * 0.5f, s * 0.5f, leafy)                            // foliage
        smooth.circle(batch, x - s * 0.45f, y + s * 0.3f, s * 0.4f, leafy)
        smooth.circle(batch, x + s * 0.45f, y + s * 0.3f, s * 0.4f, leafy)
        smooth.rect(batch, x - s * 0.55f, y - s, 1.1f * s, s * 0.85f, pot)                 // pot
        smooth.rect(batch, x - s * 0.68f, y - s * 0.2f, 1.36f * s, s * 0.22f, Color(0.66f, 0.40f, 0.28f, 0.6f)) // rim
    }

    /** A piece of fruit; [kind] picks the variety so a stall shows a mix. */
    private fun decorFruit(x: Float, y: Float, s: Float, kind: Int) {
        val stem = Color(0.45f, 0.34f, 0.22f, 0.6f)
        val leaf = Color(0.40f, 0.72f, 0.42f, 0.6f)
        when (kind % 4) {
            0 -> { // apple — two overlapping lobes, stem + leaf
                val red = Color(0.88f, 0.30f, 0.30f, 0.6f)
                smooth.circle(batch, x - s * 0.28f, y, s * 0.58f, red)
                smooth.circle(batch, x + s * 0.28f, y, s * 0.58f, red)
                smooth.line(batch, x, y + s * 0.5f, x, y + s * 0.9f, 2.5f, stem)
                smooth.circle(batch, x + s * 0.3f, y + s * 0.85f, s * 0.22f, leaf)
            }
            1 -> { // grapes — a purple cluster
                val g = Color(0.55f, 0.38f, 0.72f, 0.6f); val r = s * 0.32f
                smooth.circle(batch, x - s * 0.4f, y + s * 0.2f, r, g)
                smooth.circle(batch, x + s * 0.4f, y + s * 0.2f, r, g)
                smooth.circle(batch, x, y + s * 0.25f, r, g)
                smooth.circle(batch, x - s * 0.2f, y - s * 0.3f, r, g)
                smooth.circle(batch, x + s * 0.2f, y - s * 0.3f, r, g)
                smooth.circle(batch, x, y - s * 0.75f, r, g)
                smooth.line(batch, x, y + s * 0.45f, x, y + s * 0.8f, 2f, stem)
                smooth.circle(batch, x + s * 0.2f, y + s * 0.78f, s * 0.18f, leaf)
            }
            2 -> { // orange — round with a navel and a leaf
                smooth.circle(batch, x, y, s * 0.72f, Color(0.96f, 0.62f, 0.22f, 0.6f))
                smooth.circle(batch, x, y, s * 0.18f, Color(0.85f, 0.52f, 0.16f, 0.5f))
                smooth.circle(batch, x + s * 0.2f, y + s * 0.7f, s * 0.2f, leaf)
            }
            else -> { // durian — the stall's star: a khaki husk ringed with spikes + a stem
                val husk = Color(0.58f, 0.64f, 0.31f, 0.65f)
                val spike = Color(0.49f, 0.55f, 0.24f, 0.7f)
                val rr = s * 0.52f
                val sw = s * 0.34f; val sh = s * 0.52f
                val count = 11
                // Pyramid spikes radiating outward all around the husk (each triangle
                // rotated to aim its apex away from the centre — the durian's hallmark).
                for (i in 0 until count) {
                    val a = (i.toFloat() / count) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
                    val rimX = x + cos(a) * rr * 0.86f
                    val rimY = y + sin(a) * rr * 0.86f
                    smooth.triangle(
                        batch, rimX - sw / 2f, rimY, sw, sh, spike,
                        rotationDeg = a * 180f / Math.PI.toFloat() - 90f, originX = sw / 2f, originY = 0f,
                    )
                }
                smooth.circle(batch, x, y, rr, husk)                                    // husk body
                smooth.circle(batch, x, y, rr, Color(0.36f, 0.42f, 0.18f, 0.35f))       // rim shade
                smooth.circle(batch, x, y, rr * 0.82f, husk)
                // A few facet spikes across the front so the husk reads as segmented.
                smooth.triangle(batch, x - s * 0.22f, y - s * 0.02f, sw * 0.7f, sh * 0.7f, spike)
                smooth.triangle(batch, x + s * 0.06f, y - s * 0.12f, sw * 0.7f, sh * 0.7f, spike)
                smooth.line(batch, x, y + rr * 0.9f, x, y + rr * 1.45f, 2.5f, stem)      // short stem
            }
        }
    }

    private fun decorLantern(x: Float, y: Float, s: Float) {
        val red = Color(0.88f, 0.32f, 0.30f, 0.6f)
        val gold = Color(0.86f, 0.70f, 0.32f, 0.6f)
        smooth.rect(batch, x - s * 0.5f, y + s * 0.75f, s, s * 0.25f, gold)               // top cap
        smooth.circle(batch, x, y, s * 0.78f, red)                                        // body
        smooth.rect(batch, x - s * 0.7f, y - s * 0.3f, 1.4f * s, s * 0.6f, red)            // widen middle
        smooth.line(batch, x, y + s * 0.4f, x, y - s * 0.4f, s * 0.1f, gold)               // rib
        smooth.line(batch, x, y - s * 0.78f, x, y - s * 1.25f, s * 0.14f, gold)            // tassel
    }

    private fun decorScroll(x: Float, y: Float, s: Float) {
        smooth.rect(batch, x - s, y - s * 0.7f, 2f * s, 1.4f * s, Color(0.85f, 0.78f, 0.55f, 0.6f))
        smooth.circle(batch, x - s, y, s * 0.7f, Color(0.70f, 0.62f, 0.40f, 0.6f))
        smooth.circle(batch, x + s, y, s * 0.7f, Color(0.70f, 0.62f, 0.40f, 0.6f))
    }

    private fun decorFlag(x: Float, y: Float, s: Float) {
        smooth.line(batch, x - s, y - s, x - s, y + s * 1.3f, s * 0.16f, Color(0.70f, 0.70f, 0.75f, 0.55f))
        smooth.rect(batch, x - s, y + s * 0.3f, 1.6f * s, s, Color(0.90f, 0.35f, 0.35f, 0.6f))
    }

    private fun decorBattery(x: Float, y: Float, s: Float) {
        val c = Color(0.40f, 0.82f, 0.70f, 0.55f)
        val bolt = Color(0.98f, 0.86f, 0.30f, 0.85f)
        smooth.rect(batch, x - s * 0.7f, y - s, 1.4f * s, 2f * s, c)         // body
        smooth.rect(batch, x - s * 0.3f, y + s, 0.6f * s, s * 0.3f, c)       // terminal
        // A lightning bolt across the body so it never reads as a bottle.
        smooth.line(batch, x + s * 0.25f, y + s * 0.6f, x - s * 0.15f, y + s * 0.05f, s * 0.16f, bolt)
        smooth.line(batch, x - s * 0.15f, y + s * 0.05f, x + s * 0.15f, y - s * 0.05f, s * 0.16f, bolt)
        smooth.line(batch, x + s * 0.15f, y - s * 0.05f, x - s * 0.25f, y - s * 0.6f, s * 0.16f, bolt)
    }

    private fun decorPodium(x: Float, y: Float, s: Float) {
        val c = Color(0.60f, 0.56f, 0.70f, 0.55f)
        smooth.rect(batch, x - s * 0.8f, y - s, 1.6f * s, 1.5f * s, c)                     // column
        smooth.rect(batch, x - s, y + s * 0.4f, 2f * s, s * 0.4f, c)                       // top slab
        smooth.rect(batch, x - s, y - s, 2f * s, s * 0.3f, c)                              // base
        smooth.circle(batch, x, y + s * 0.85f, s * 0.32f, Color(0.96f, 0.85f, 0.42f, 0.6f)) // medal on top
    }

    /* ----------------------------- stations ---------------------------- */

    /** Draw the current room's puzzle station as a distinct themed *object* on the
     *  floor (a console, a solar array, a market crate, a flag, a gate…) — not a
     *  generic icon-on-a-disc. The body takes the room's identity colour (grey when
     *  locked), with a small lock / green-✓ badge for state and a chevron when usable. */
    private fun drawStation(title: String, cx: Float, cy: Float, identity: Color, locked: Boolean, done: Boolean, near: Boolean) {
        val c = if (locked) Color(0.5f, 0.5f, 0.56f, 1f) else identity
        val w = Color(1f, 1f, 1f, 0.95f)
        val dk = Color(0.12f, 0.14f, 0.22f, 0.85f)
        // Ground shadow.
        val sh = Color(0f, 0f, 0f, 0.18f)
        smooth.rect(batch, cx - 15f, cy - 23f, 30f, 7f, sh)
        smooth.circle(batch, cx - 15f, cy - 19.5f, 3.5f, sh); smooth.circle(batch, cx + 15f, cy - 19.5f, 3.5f, sh)
        when (title) {
            "Robot Helper" -> { // a standing robot
                smooth.line(batch, cx - 6f, cy - 16f, cx - 6f, cy - 22f, 3f, c)
                smooth.line(batch, cx + 6f, cy - 16f, cx + 6f, cy - 22f, 3f, c)
                smooth.rect(batch, cx - 11f, cy - 16f, 22f, 22f, c)
                smooth.line(batch, cx - 14f, cy - 4f, cx - 11f, cy - 4f, 3f, c)
                smooth.line(batch, cx + 11f, cy - 4f, cx + 14f, cy - 4f, 3f, c)
                smooth.rect(batch, cx - 9f, cy + 6f, 18f, 14f, c)
                smooth.circle(batch, cx - 4f, cy + 13f, 2.5f, dk); smooth.circle(batch, cx + 4f, cy + 13f, 2.5f, dk)
                smooth.line(batch, cx, cy + 20f, cx, cy + 25f, 2f, c); smooth.circle(batch, cx, cy + 25f, 2.5f, c)
            }
            "Solar Panel" -> { // a PV array on a post
                smooth.rect(batch, cx - 18f, cy + 2f, 36f, 18f, c)
                smooth.line(batch, cx - 6f, cy + 2f, cx - 6f, cy + 20f, 1.6f, w)
                smooth.line(batch, cx + 6f, cy + 2f, cx + 6f, cy + 20f, 1.6f, w)
                smooth.line(batch, cx - 18f, cy + 11f, cx + 18f, cy + 11f, 1.6f, w)
                smooth.rect(batch, cx - 2.5f, cy - 18f, 5f, 22f, c)
                smooth.rect(batch, cx - 7f, cy - 21f, 14f, 4f, c)
            }
            "Recycling Plant" -> { // a recycling bin
                smooth.rect(batch, cx - 12f, cy - 20f, 24f, 34f, c)
                smooth.rect(batch, cx - 14f, cy + 12f, 28f, 5f, c)
                smooth.line(batch, cx, cy + 16f, cx, cy + 21f, 2.5f, c)
                smooth.triangle(batch, cx - 5f, cy - 4f, 10f, 8f, w)
                smooth.triangle(batch, cx - 5f, cy + 4f, 10f, 8f, w, pointDown = true)
            }
            "Power Circuit" -> { // a power box with a bulb on top
                smooth.rect(batch, cx - 14f, cy - 20f, 28f, 26f, c)
                smooth.rect(batch, cx - 3f, cy + 6f, 6f, 5f, c)
                smooth.circle(batch, cx, cy + 16f, 8f, c)
                smooth.line(batch, cx - 3f, cy + 16f, cx + 3f, cy + 16f, 1.4f, w)
                smooth.circle(batch, cx - 7f, cy - 10f, 3.5f, w)
                smooth.line(batch, cx + 6f, cy - 4f, cx + 2f, cy - 11f, 2f, Color(0.98f, 0.86f, 0.3f, 0.9f))
                smooth.line(batch, cx + 2f, cy - 11f, cx + 8f, cy - 16f, 2f, Color(0.98f, 0.86f, 0.3f, 0.9f))
            }
            "The Suit" -> { // a superhero suit on a stand
                smooth.rect(batch, cx - 8f, cy - 22f, 16f, 4f, c)
                smooth.rect(batch, cx - 2f, cy - 18f, 4f, 8f, c)
                smooth.triangle(batch, cx - 12f, cy - 12f, 24f, 26f, c)
                smooth.circle(batch, cx, cy + 16f, 5f, c)
                smooth.triangle(batch, cx - 5f, cy - 2f, 10f, 8f, w)
                smooth.triangle(batch, cx - 5f, cy + 1f, 10f, 8f, w, pointDown = true)
            }
            "Founding Gallery" -> { // an artefact on a museum plinth
                smooth.rect(batch, cx - 12f, cy - 22f, 24f, 6f, c)
                smooth.rect(batch, cx - 7f, cy - 16f, 14f, 18f, c)
                smooth.rect(batch, cx - 13f, cy + 2f, 26f, 5f, c)
                smooth.rect(batch, cx - 7f, cy + 7f, 14f, 12f, w)
                smooth.circle(batch, cx - 7f, cy + 13f, 2.5f, c); smooth.circle(batch, cx + 7f, cy + 13f, 2.5f, c)
            }
            "Independence Hall" -> { // a flag on a stand
                smooth.rect(batch, cx - 10f, cy - 22f, 20f, 4f, c)
                smooth.line(batch, cx - 6f, cy - 18f, cx - 6f, cy + 22f, 2.5f, c)
                smooth.rect(batch, cx - 6f, cy + 10f, 18f, 12f, c)
            }
            "Lion City Room" -> { // a crown on a plinth
                smooth.rect(batch, cx - 11f, cy - 22f, 22f, 6f, c)
                smooth.rect(batch, cx - 7f, cy - 16f, 14f, 16f, c)
                smooth.rect(batch, cx - 12f, cy, 24f, 5f, c)
                smooth.rect(batch, cx - 9f, cy + 6f, 18f, 5f, w)
                smooth.triangle(batch, cx - 9f, cy + 10f, 7f, 7f, w)
                smooth.triangle(batch, cx - 3.5f, cy + 10f, 7f, 7f, w)
                smooth.triangle(batch, cx + 2f, cy + 10f, 7f, 7f, w)
            }
            "Grand Hall" -> { // a crossword board on an easel
                smooth.line(batch, cx - 12f, cy - 22f, cx - 6f, cy + 18f, 2.5f, c)
                smooth.line(batch, cx + 12f, cy - 22f, cx + 6f, cy + 18f, 2.5f, c)
                smooth.rect(batch, cx - 15f, cy - 6f, 30f, 26f, c)
                val g = 7f
                for ((dx, dy) in listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1))
                    smooth.rect(batch, cx + dx * g - 3f, cy + 7f + dy * g - 3f, 6f, 6f, w)
            }
            "Hawker Stall" -> { // a food stall with an awning + a bowl
                smooth.rect(batch, cx - 18f, cy - 22f, 36f, 16f, c)
                smooth.line(batch, cx - 16f, cy - 6f, cx - 16f, cy + 6f, 2f, c)
                smooth.line(batch, cx + 16f, cy - 6f, cx + 16f, cy + 6f, 2f, c)
                smooth.rect(batch, cx - 20f, cy + 6f, 40f, 5f, c)
                smooth.triangle(batch, cx - 20f, cy + 11f, 40f, 9f, c)
                smooth.circle(batch, cx, cy - 8f, 6f, w)
                smooth.rect(batch, cx - 7f, cy - 6f, 14f, 2.5f, w)
            }
            "Little India" -> { // a hanging lantern on a pole
                smooth.rect(batch, cx - 8f, cy - 22f, 16f, 4f, c)
                smooth.rect(batch, cx - 2f, cy - 18f, 4f, 26f, c)
                smooth.circle(batch, cx, cy + 12f, 7f, c)
                smooth.rect(batch, cx - 4f, cy + 18f, 8f, 3f, c)
                smooth.circle(batch, cx, cy + 12f, 3f, Color(1f, 0.85f, 0.4f, 0.9f))
            }
            "Gardens" -> { // a planter of flowers
                smooth.rect(batch, cx - 13f, cy - 22f, 26f, 12f, c)
                smooth.rect(batch, cx - 15f, cy - 12f, 30f, 4f, c)
                val stem = Color(0.34f, 0.6f, 0.4f, 0.85f)
                smooth.line(batch, cx - 7f, cy - 8f, cx - 7f, cy + 6f, 2f, stem)
                smooth.line(batch, cx, cy - 8f, cx, cy + 12f, 2f, stem)
                smooth.line(batch, cx + 7f, cy - 8f, cx + 7f, cy + 8f, 2f, stem)
                smooth.circle(batch, cx - 7f, cy + 8f, 4f, c)
                smooth.circle(batch, cx, cy + 14f, 4.5f, c)
                smooth.circle(batch, cx + 7f, cy + 10f, 4f, c)
                smooth.circle(batch, cx, cy + 14f, 2f, w)
            }
            "Fruit Stall" -> { // a crate of mixed fruit
                smooth.rect(batch, cx - 15f, cy - 20f, 30f, 16f, c)
                smooth.line(batch, cx - 15f, cy - 12f, cx + 15f, cy - 12f, 1.5f, dk)
                smooth.line(batch, cx, cy - 20f, cx, cy - 4f, 1.5f, dk)
                smooth.circle(batch, cx - 8f, cy - 1f, 5.5f, Color(0.88f, 0.30f, 0.30f, 0.92f))
                smooth.circle(batch, cx + 1f, cy + 1f, 5.5f, Color(0.96f, 0.62f, 0.22f, 0.92f))
                smooth.circle(batch, cx + 9f, cy - 1f, 5f, Color(0.55f, 0.40f, 0.72f, 0.92f))
            }
            "Word Display" -> { // a monitor on a stand
                smooth.rect(batch, cx - 18f, cy - 4f, 36f, 26f, c)
                smooth.rect(batch, cx - 14f, cy, 28f, 18f, dk)
                smooth.line(batch, cx - 9f, cy + 10f, cx + 9f, cy + 10f, 1.6f, w)
                smooth.line(batch, cx - 9f, cy + 5f, cx + 5f, cy + 5f, 1.6f, w)
                smooth.line(batch, cx - 9f, cy, cx + 2f, cy, 1.6f, w)
                smooth.rect(batch, cx - 4f, cy - 12f, 8f, 8f, c)
                smooth.rect(batch, cx - 10f, cy - 22f, 20f, 5f, c)
            }
            "Core Charger" -> { // a charging dock with a glowing socket
                smooth.rect(batch, cx - 13f, cy - 22f, 26f, 8f, c)
                smooth.rect(batch, cx - 4f, cy - 14f, 8f, 16f, c)
                smooth.rect(batch, cx - 11f, cy + 2f, 22f, 6f, c)
                smooth.circle(batch, cx, cy + 12f, 8f, Color(1f, 1f, 1f, 0.3f))
                smooth.circle(batch, cx, cy + 12f, 5f, c)
                smooth.line(batch, cx + 2.5f, cy + 15f, cx - 0.5f, cy + 12f, 1.6f, w)
                smooth.line(batch, cx - 0.5f, cy + 12f, cx + 1.5f, cy + 9f, 1.6f, w)
            }
            else -> { // lab console (Control Panel, Symbol/Exit Decoder, Exit Chamber, Exit Panel)
                smooth.line(batch, cx - 11f, cy - 16f, cx - 11f, cy - 22f, 3.5f, c)
                smooth.line(batch, cx + 11f, cy - 16f, cx + 11f, cy - 22f, 3.5f, c)
                smooth.rect(batch, cx - 16f, cy - 16f, 32f, 34f, c)
                smooth.rect(batch, cx - 12f, cy + 2f, 24f, 13f, dk)
                smooth.line(batch, cx - 8f, cy + 10f, cx + 8f, cy + 10f, 1.4f, w)
                smooth.line(batch, cx - 8f, cy + 5f, cx + 4f, cy + 5f, 1.4f, w)
                for (r in 0..1) for (col in 0..2) smooth.circle(batch, cx - 8f + col * 8f, cy - 6f - r * 7f, 2.3f, w)
            }
        }
        // State badge (top-right) + a chevron when the station is in reach.
        if (locked || done) {
            val bx = cx + 14f; val by = cy + 19f
            if (locked) {
                smooth.circle(batch, bx, by, 7f, Color(0.42f, 0.42f, 0.48f, 1f))
                smooth.rect(batch, bx - 3f, by - 3.5f, 6f, 4.5f, w)
                smooth.line(batch, bx - 2.4f, by + 1f, bx - 2.4f, by + 3.4f, 1.4f, w)
                smooth.line(batch, bx + 2.4f, by + 1f, bx + 2.4f, by + 3.4f, 1.4f, w)
                smooth.line(batch, bx - 2.4f, by + 3.4f, bx + 2.4f, by + 3.4f, 1.4f, w)
            } else {
                smooth.circle(batch, bx, by, 7f, cGood)
                smooth.line(batch, bx - 3f, by, bx - 0.5f, by - 2.5f, 2f, Color.WHITE)
                smooth.line(batch, bx - 0.5f, by - 2.5f, bx + 3.5f, by + 3f, 2f, Color.WHITE)
            }
        }
        if (near) {
            val y = cy + 34f
            smooth.line(batch, cx - 5f, y + 4f, cx, y - 2f, 2.5f, w)
            smooth.line(batch, cx + 5f, y + 4f, cx, y - 2f, 2.5f, w)
        }
    }

    /* ----------------------------- input ----------------------------- */

    private fun handleInput(dt: Float) {
        val touched = Gdx.input.isTouched()
        val justDown = touched && !prevTouched
        val justUp = !touched && prevTouched

        when (phase) {
            Phase.PLAYING -> {
                if (justDown) {
                    val p = unprojectTouch()
                    when {
                        within(p, closeCenter, closeR) -> { onFinish(0); return }
                        within(p, actionCenter, actionR) -> doAction()
                        else -> { joyActive = true; joyOrigin.set(p); joyKnob.set(p) }
                    }
                }
                if (joyActive && touched) {
                    val p = unprojectTouch()
                    val dir = Vector2(p).sub(joyOrigin)
                    if (dir.len() > JOY_RADIUS) dir.setLength(JOY_RADIUS)
                    joyKnob.set(joyOrigin).add(dir)
                    val v = Vector2(dir).scl(1f / JOY_RADIUS)
                    // Move each axis independently and stop at walls (so the player
                    // slides along them and passes cleanly through doorways).
                    val nx = (pos.x + v.x * SPEED * dt).coerceIn(areaX + CHAR_R, areaX + areaW - CHAR_R)
                    if (!blocked(nx, pos.y)) pos.x = nx
                    val ny = (pos.y + v.y * SPEED * dt).coerceIn(areaY + CHAR_R, areaY + areaH - CHAR_R)
                    if (!blocked(pos.x, ny)) pos.y = ny
                }
                if (!touched) joyActive = false
            }
            Phase.PUZZLE -> {
                val pz = activePuzzle
                // Once solved the puzzle stays open for review — only the X closes it.
                if (justDown) {
                    val p = unprojectTouch()
                    if (within(p, backCenter, backR)) closePuzzle() else if (!puzzleSolved) pz?.onDown(p)
                } else if (touched && !puzzleSolved) {
                    pz?.onDrag(unprojectTouch())
                }
                if (justUp && !puzzleSolved) pz?.onUp(unprojectTouch())
                if (wrongFlash > 0f) puzzleHadMistake = true
                if (pz != null && pz.complete && !puzzleSolved) {
                    puzzleSolved = true; justSolved = true; solvedTime = 0f // register the solve once, then let the player review
                    activeStationId?.let { id ->
                        solved.add(id)
                        // The exit-room lock (keypad / decoder) is normally a solo-only
                        // final gate — but on core-carry levels the exit *is* the suit's
                        // own puzzle, so share that solve too, letting the team reach
                        // "escaped" together instead of each player re-doing the delivery.
                        if (id != currentLevel.exitRoom || id == currentLevel.suitRoom) {
                            coop?.reportSolve(id, firstTry = !puzzleHadMistake)
                        }
                    }
                }
            }
            Phase.WON -> if (justDown) onFinish(starsForWin())
        }

        if (wrongFlash > 0f) wrongFlash -= dt
        prevTouched = touched
    }

    private fun doAction() {
        val lvl = currentLevel
        if (lvl.cores.isNotEmpty()) {
            val held = carrying
            if (held != null) {
                if (lvl.directDeliver) {
                    if (nearSuit()) {
                        delivered.add(held); myMilestones.add("dl:$held"); coreDrop.remove(held); carrying = null; flashMsg = "Artefact placed in the Time Capsule!"; wrongFlash = 0f; return
                    }
                } else {
                    if (!carryingCharged && nearStation(held) && held in solved) {
                        charged.add(held); myMilestones.add("ch:$held"); coreDrop.remove(held); carrying = null; flashMsg = "Core charged!"; wrongFlash = 0f; return
                    }
                    if (carryingCharged && nearSuit()) {
                        delivered.add(held); myMilestones.add("dl:$held"); coreDrop.remove(held); carrying = null; flashMsg = "Core powers the suit!"; wrongFlash = 0f; return
                    }
                }
                // Otherwise: set the item down right here so the player can swap it.
                coreDrop[held] = Vector2(pos.x, pos.y)
                myDrops[held] = rooms[currentRoomIndex()].id // share the room we left it in
                if (carryingCharged) charged.add(held)
                carrying = null
                return
            } else {
                val pick = pickableCore()
                if (pick != null) { carrying = pick; carryingCharged = pick in charged; charged.remove(pick); coreDrop.remove(pick); myDrops.remove(pick); return }
            }
        }
        if (lvl.bottles.isNotEmpty()) {
            val b = carryingBottle
            if (b != null) {
                if (!carriedClean && nearSink()) { carriedClean = true; flashMsg = "Bottle washed — now recycle it!"; wrongFlash = 0f; return }
                if (carriedClean && nearDeposit()) { bottleDeposited.add(b); myMilestones.add("rc:$b"); bottleDrop.remove(b); carryingBottle = null; flashMsg = "Bottle recycled!"; wrongFlash = 0f; return }
                // Otherwise set the bottle down right here.
                bottleDrop[b] = Vector2(pos.x, pos.y); myDrops[b] = rooms[currentRoomIndex()].id; if (carriedClean) bottleWashed.add(b); carryingBottle = null; return
            } else {
                val pick = pickableBottle()
                if (pick != null) { carryingBottle = pick; carriedClean = pick in bottleWashed; bottleWashed.remove(pick); bottleDrop.remove(pick); myDrops.remove(pick); return }
            }
        }
        val mi = activeMachine()
        if (mi != null) {
            val rd = rooms[mi]
            if (isLocked(mi)) {
                flashMsg = when {
                    lvl.suitRoom == rd.id && delivered.size < lvl.cores.size -> "Bring all the cores to power the suit"
                    lvl.bottleGateRoom == rd.id && !bottlesDone -> "Recycle all bottles first"
                    else -> "Locked — solve \"${labelOf(lockedOn(mi) ?: "")}\" first"
                }
                wrongFlash = 1.4f; return
            }
            // A solved station re-opens in review mode (no re-scramble); a fresh one resets.
            val alreadySolved = rd.id in solved
            activePuzzle = rd.puzzle; activeStationId = rd.id
            if (!alreadySolved) rd.puzzle!!.onOpen()
            puzzleSolved = alreadySolved; justSolved = false // a review re-open shows no banner
            wrongFlash = 0f; puzzleHadMistake = false; phase = Phase.PUZZLE; return
        }
        if (nearClue()) {
            activePuzzle = clueNote; activeStationId = null; puzzleSolved = false; justSolved = false; phase = Phase.PUZZLE; return
        }
        if (nearExit()) {
            if (exitUnlocked()) phase = Phase.WON
            else {
                flashMsg = if (currentLevel.directDeliver) "Place all the artefacts in the Time Capsule first"
                    else "Open the Exit Keypad first!"
                wrongFlash = 1.2f
            }
        }
    }

    /** The exit opens once every station is solved — and, on direct-delivery levels,
     *  every carried item has been placed at the suit/display. */
    private fun exitUnlocked() = solved.size >= totalStations &&
        (!currentLevel.directDeliver || delivered.size >= currentLevel.cores.size)

    private fun closePuzzle() {
        activePuzzle = null; activeStationId = null; phase = Phase.PLAYING; joyActive = false; wrongFlash = 0f; puzzleSolved = false; justSolved = false
    }

    private fun starsForWin() = if (totalStations == 0) 0 else 5

    /* ----------------------------- render ----------------------------- */

    override fun render() {
        val dt = min(Gdx.graphics.deltaTime, 1f / 30f)
        // Co-op: fold the team's shared solved set in, and report which room we're
        // in for presence. Teammates' solves unlock the same gates for everyone.
        coop?.let { c ->
            c.state?.let {
                solved.addAll(it.solved)
                // Fold teammates' carry milestones (charged/delivered/recycled) into our
                // shared item state so the whole team sees items move together.
                foldCoopMilestones(it)
                // If we both grabbed the same item in the same tick, the lower-priority
                // player yields it now so exactly one person holds it on every screen.
                resolveCarryConflicts(it)
                // Snapshot what teammates are carrying / where they've dropped items, so
                // both rendering and pickup agree on each item's whereabouts this frame.
                coopCarriedCache = mateCarried()
                coopDropCache = teammateDrops(it)
                // When the team's shared state flips to "escaped", everyone finishes
                // together — no teammate is left re-doing a local step (e.g. re-carrying
                // the kindness-castle cores) that someone else already completed.
                if (it.escaped && phase != Phase.WON) { phase = Phase.WON; winTime = 0f }
            }
            c.atStation = encodePresence()
        }
        handleInput(dt)
        winTime = if (phase == Phase.WON) winTime + dt else 0f
        solvedTime = if (justSolved) solvedTime + dt else 0f

        val bg = currentLevel.bg // letterbox margins get a darker shade of the level's theme
        Gdx.gl.glClearColor(bg.r * 0.55f, bg.g * 0.55f, bg.b * 0.55f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)

        when (phase) {
            Phase.PUZZLE -> { usePuzzleCam(); activePuzzle?.draw(); if (justSolved && solvedTime < 2.5f) drawSolvedBanner() }
            Phase.WON -> { usePuzzleCam(); drawWin() }
            else -> { useWorldCam(); drawScene(); drawHud() }
        }
    }

    private fun useWorldCam() {
        worldViewport.apply()
        shapes.projectionMatrix = worldCam.combined
        batch.projectionMatrix = worldCam.combined
    }

    private fun usePuzzleCam() {
        puzzleViewport.apply()
        shapes.projectionMatrix = puzzleCam.combined
        batch.projectionMatrix = puzzleCam.combined
    }

    // Distinct token colours for co-op teammates (keyed by learner id).
    private val mateColors = arrayOf(
        Color(0.36f, 0.78f, 0.98f, 1f), Color(0.96f, 0.55f, 0.80f, 1f),
        Color(0.55f, 0.85f, 0.45f, 1f), Color(0.98f, 0.62f, 0.30f, 1f),
        Color(0.70f, 0.60f, 0.95f, 1f),
    )
    private fun playerColor(id: Int) = mateColors[((id % mateColors.size) + mateColors.size) % mateColors.size]
    // Teammates are laid out in a row near the top of the room (we only know which
    // room they're in, not their exact position).
    private fun mateX(i: Int, n: Int, c: RoomCell) = c.x + c.w / 2f + (i - (n - 1) / 2f) * 46f
    private fun mateY(c: RoomCell) = c.y + c.h - 44f

    // ---- co-op presence codec --------------------------------------------
    // The server only shares solved-station ids plus one short presence string
    // ("atStation") per player, so we piggyback the carry mechanic on that string:
    //   "<roomId>|<carry>|<milestones>|<drops>"
    //     carry      = "c:<id>:<f>" (core, f=1 charged) | "b:<id>:<f>" (bottle,
    //                  f=1 washed) | "" empty-handed  — lets teammates see what you
    //                  are hauling, rendered on your avatar.
    //     milestones = cumulative, append-only tokens this player completed:
    //                  ch:<id> charged · dl:<id> delivered · rc:<id> recycled.
    //     drops      = "<id>@<roomId>" for each item this player has set down idle and
    //                  still owns — so a core dropped in another room shows up there for
    //                  everyone (at the room's centre; exact pixel isn't shared).
    // Milestones are monotonic, so a teammate folds them permanently into the shared
    // charged/delivered/recycled sets. Drop ownership transfers the moment someone else
    // picks the item up (we clear our own record when we see a teammate carrying it).
    private val myMilestones = LinkedHashSet<String>()
    private val myDrops = HashMap<String, String>() // itemId -> roomId we left it in
    // Per-frame snapshot of teammates' carry/drop state (so rendering and pickup agree).
    private var coopCarriedCache: Set<String> = emptySet()
    private var coopDropCache: Map<String, String> = emptyMap()
    private fun roomIndexOfId(id: String) = rooms.indexOfFirst { it.id == id }

    private fun encodePresence(): String {
        val room = rooms[currentRoomIndex()].id
        val carry = when {
            carrying != null -> "c:$carrying:${if (carryingCharged) 1 else 0}"
            carryingBottle != null -> "b:$carryingBottle:${if (carriedClean) 1 else 0}"
            else -> ""
        }
        val drops = myDrops.entries.joinToString(",") { "${it.key}@${it.value}" }
        return "$room|$carry|${myMilestones.joinToString(",")}|$drops"
    }

    private fun presenceRoom(s: String?) = (s ?: "").substringBefore('|')
    private fun presenceCarry(s: String?): String? =
        (s ?: "").split('|').getOrNull(1)?.takeIf { it.isNotEmpty() }
    private fun presenceMilestones(s: String?): List<String> =
        (s ?: "").split('|').getOrNull(2)?.takeIf { it.isNotEmpty() }?.split(',') ?: emptyList()
    private fun presenceDrops(s: String?): List<Pair<String, String>> =
        (s ?: "").split('|').getOrNull(3)?.takeIf { it.isNotEmpty() }?.split(',')
            ?.mapNotNull { it.split('@').takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } } ?: emptyList()

    /** Items a teammate is currently carrying — you can't pick these up (they're not
     *  loose). Empty in solo play. */
    private fun mateCarried(): Set<String> {
        val st = coop?.state ?: return emptySet()
        val out = HashSet<String>()
        st.players.forEach { p ->
            if (p.learnerId == st.you) return@forEach
            presenceCarry(p.atStation)?.split(':')?.getOrNull(1)?.let { out.add(it) }
        }
        return out
    }

    /** Resolve a simultaneous grab: pickups can't be made atomic over ~0.8s polling, so
     *  if two players end up holding the same item, the one with the higher learner id
     *  yields instantly. The tiebreak is a fixed, shared value, so both clients agree on
     *  the single keeper without any extra coordination. */
    private fun resolveCarryConflicts(state: EscapeState) {
        val mine = carrying ?: carryingBottle ?: return
        val outranked = state.players.any { p ->
            p.learnerId != state.you && p.learnerId < state.you &&
                presenceCarry(p.atStation)?.split(':')?.getOrNull(1) == mine
        }
        if (outranked) {
            if (carrying == mine) { carrying = null; carryingCharged = false }
            if (carryingBottle == mine) { carryingBottle = null; carriedClean = false }
            flashMsg = "Your teammate grabbed that one!"; wrongFlash = 0f
        }
    }

    /** Fold every teammate's charged/delivered/recycled milestones into our shared
     *  item sets so the whole team sees the same world (called each frame in co-op). */
    private fun foldCoopMilestones(state: EscapeState) {
        state.players.forEach { p ->
            if (p.learnerId == state.you) return@forEach
            presenceMilestones(p.atStation).forEach { tok ->
                val id = tok.substringAfter(':')
                when {
                    tok.startsWith("ch:") -> charged.add(id)
                    tok.startsWith("dl:") -> delivered.add(id)
                    tok.startsWith("rc:") -> bottleDeposited.add(id)
                }
            }
        }
        // A teammate now holding an item has taken ownership — drop our stale local copy
        // (the spot we left it) so it no longer lingers on our screen at the old place.
        mateCarried().forEach { id -> coreDrop.remove(id); bottleDrop.remove(id); myDrops.remove(id) }
    }

    /** Where teammates have left items, as id -> roomId (lowest learner id wins a tie).
     *  Items someone is carrying, or that are delivered, don't count as dropped. */
    private fun teammateDrops(state: EscapeState): Map<String, String> {
        val carried = mateCarried()
        val out = HashMap<String, String>()
        state.players.sortedBy { it.learnerId }.forEach { p ->
            if (p.learnerId == state.you) return@forEach
            presenceDrops(p.atStation).forEach { (id, room) ->
                if (id !in carried && id != carrying && id !in delivered && id !in out) out[id] = room
            }
        }
        return out
    }

    /** A distinct exit door per level, drawn in the shapes pass. An accent lights green
     *  once the exit is [open] (red/themed while still locked); a threshold bar appears
     *  when the player is [near]. */
    private fun drawExitDoor(open: Boolean, near: Boolean) {
        val ex = exitDoor.x; val ey = exitDoor.y
        val glow = if (open) Color(0.40f, 0.95f, 0.55f, 1f) else Color(0.85f, 0.30f, 0.30f, 1f)
        when (currentLevel) {
            robotLab -> { // sci-fi blast door: steel frame, two sliding panels, lit centre seam
                shapes.color = Color(0.16f, 0.18f, 0.24f, 1f); shapes.rect(ex - 22f, ey - 30f, 44f, 60f)
                shapes.color = glow; shapes.rect(ex - 19f, ey - 27f, 38f, 54f)        // lit doorway behind
                val inset = if (open) 10f else 1f
                shapes.color = Color(0.55f, 0.60f, 0.70f, 1f)
                shapes.rect(ex - 19f, ey - 27f, 18f - inset, 54f)                     // left panel
                shapes.rect(ex + 1f + inset, ey - 27f, 18f - inset, 54f)              // right panel
                shapes.color = Color(0.32f, 0.36f, 0.44f, 1f)                         // hazard stripe
                shapes.rect(ex - 19f, ey - 4f, 18f - inset, 8f); shapes.rect(ex + 1f + inset, ey - 4f, 18f - inset, 8f)
            }
            tower -> { // hero hatch: bold door, red band, gold star medallion
                shapes.color = Color(0.16f, 0.18f, 0.45f, 1f); shapes.rect(ex - 22f, ey - 30f, 44f, 60f)
                shapes.color = Color(0.30f, 0.34f, 0.62f, 1f); shapes.rect(ex - 18f, ey - 26f, 36f, 52f)
                shapes.color = Color(0.86f, 0.24f, 0.30f, 1f); shapes.rect(ex - 18f, ey - 6f, 36f, 11f) // red band
                val sc = if (open) glow else Color(0.98f, 0.80f, 0.20f, 1f); val r = 10f; val sy = ey + 13f
                shapes.color = sc                                                     // six-point star medallion
                shapes.triangle(ex - r, sy - r * 0.5f, ex + r, sy - r * 0.5f, ex, sy + r)
                shapes.triangle(ex - r, sy + r * 0.5f, ex + r, sy + r * 0.5f, ex, sy - r)
            }
            annex -> { // eco workshop: wooden door, round window, leaf accent
                shapes.color = Color(0.40f, 0.28f, 0.16f, 1f); shapes.rect(ex - 22f, ey - 30f, 44f, 60f)
                shapes.color = Color(0.34f, 0.52f, 0.30f, 1f); shapes.rect(ex - 18f, ey - 26f, 36f, 52f)
                shapes.color = Color(0.27f, 0.42f, 0.24f, 1f); shapes.rect(ex - 1f, ey - 26f, 2f, 52f)  // plank seam
                shapes.color = if (open) glow else Color(0.70f, 0.86f, 0.95f, 1f); shapes.circle(ex, ey + 12f, 8f) // window
                shapes.color = Color(0.55f, 0.78f, 0.40f, 1f); shapes.triangle(ex - 13f, ey - 12f, ex - 4f, ey - 5f, ex - 13f, ey + 2f) // leaf
            }
            vault -> { // bank-vault door: brass disc with a cross-handle wheel
                shapes.color = Color(0.34f, 0.28f, 0.16f, 1f); shapes.rect(ex - 24f, ey - 28f, 48f, 56f) // frame
                shapes.color = Color(0.62f, 0.50f, 0.24f, 1f); shapes.circle(ex, ey, 25f)                // brass disc
                shapes.color = Color(0.48f, 0.38f, 0.18f, 1f); shapes.circle(ex, ey, 17f)
                shapes.color = if (open) glow else Color(0.74f, 0.62f, 0.34f, 1f)
                shapes.rect(ex - 16f, ey - 3f, 32f, 6f); shapes.rect(ex - 3f, ey - 16f, 6f, 32f)         // cross handle
                shapes.color = Color(0.30f, 0.24f, 0.12f, 1f); shapes.circle(ex, ey, 4f)                 // hub
            }
            else -> { // Lion City Carnival: festive pagoda gate, gold pillars + roof
                shapes.color = Color(0.74f, 0.16f, 0.18f, 1f); shapes.rect(ex - 19f, ey - 28f, 38f, 50f) // red door
                shapes.color = Color(0.92f, 0.72f, 0.26f, 1f)                                            // gold pillars
                shapes.rect(ex - 24f, ey - 28f, 5f, 50f); shapes.rect(ex + 19f, ey - 28f, 5f, 50f)
                shapes.triangle(ex - 27f, ey + 22f, ex + 27f, ey + 22f, ex, ey + 34f)                    // pagoda roof
                shapes.color = if (open) glow else Color(0.95f, 0.85f, 0.40f, 1f); shapes.circle(ex, ey + 2f, 7f) // lantern
            }
        }
        if (near) { shapes.color = Color.WHITE; shapes.rect(ex - 22f, ey - 34f, 44f, 4f) } // step-here threshold
    }

    private fun drawScene() {
        val curIdx = currentRoomIndex()
        val mi = activeMachine()
        val nearEx = nearExit()
        val nearC = nearClue()
        val exitOpen = exitUnlocked()
        // Co-op teammates standing in this room (avatar tokens, drawn below).
        val mates = coop?.state?.let { st ->
            st.players.filter { it.learnerId != st.you && presenceRoom(it.atStation) == rooms[curIdx].id }
        } ?: emptyList()
        // Where each teammate's avatar is drawn (so we can hang a carried item on it).
        val matePos = mates.mapIndexed { i, m ->
            m.learnerId to Vector2(mateX(i, mates.size, cells[curIdx]), mateY(cells[curIdx]))
        }.toMap()
        // Items a teammate in this room is currently hauling: id -> (learnerId, flag).
        val mateCore = HashMap<String, Pair<Int, Boolean>>()
        val mateBottle = HashMap<String, Pair<Int, Boolean>>()
        mates.forEach { m ->
            val parts = presenceCarry(m.atStation)?.split(':') ?: return@forEach
            if (parts.size >= 3) {
                val pair = m.learnerId to (parts[2] == "1")
                if (parts[0] == "c") mateCore[parts[1]] = pair else if (parts[0] == "b") mateBottle[parts[1]] = pair
            }
        }
        // Items any teammate is carrying (in any room — hides our home copy), and the
        // rooms teammates have set items down in (drawn at that room's centre).
        val carriedAway = coopCarriedCache
        val drops = coopDropCache

        // Context action — carrying a core to charge/power takes priority over a machine.
        val held = carrying
        val actLabel = when {
            held != null && currentLevel.directDeliver && nearSuit() -> "PLACE"
            held != null && !currentLevel.directDeliver && !carryingCharged && nearStation(held) && held in solved -> "CHARGE"
            held != null && !currentLevel.directDeliver && carryingCharged && nearSuit() -> "POWER"
            held != null -> "DROP"
            currentLevel.cores.isNotEmpty() && pickableCore() != null -> "TAKE"
            carryingBottle != null && !carriedClean && nearSink() -> "WASH"
            carryingBottle != null && carriedClean && nearDeposit() -> "RECYCLE"
            carryingBottle != null -> "DROP"
            currentLevel.bottles.isNotEmpty() && pickableBottle() != null -> "TAKE"
            mi != null && isLocked(mi) -> "LOCKED"
            mi != null && rooms[mi].id in solved -> "VIEW"
            mi != null -> "USE"
            nearC -> "READ"
            nearEx -> if (exitOpen) "EXIT" else "LOCK"
            else -> ""
        }
        val canAct = actLabel.isNotEmpty()

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Backdrop + every room's floor + the walls.
        shapes.color = wallColor
        shapes.rect(0f, 0f, worldW, worldH)
        val tint = currentLevel.floorTint
        cells.forEach { c ->
            floorTmp.set(
                c.floor.r + (tint.r - c.floor.r) * 0.4f,
                c.floor.g + (tint.g - c.floor.g) * 0.4f,
                c.floor.b + (tint.b - c.floor.b) * 0.4f, 1f,
            )
            shapes.color = floorTmp
            shapes.rect(c.x, c.y, c.w, c.h)
        }
        // Floor texture for the room in view (others are fogged over below), painted
        // now so it sits under the walls, door, props, station and player.
        drawFloorTexture(cells[curIdx], floorKindOf(curIdx))
        shapes.color = wallColor
        walls.forEach { shapes.rect(it[0], it[1], it[2], it[3]) }

        // Exit door (sits in the Exit Chamber — the last room), themed per level.
        if (curIdx == exitRoomIndex) drawExitDoor(exitOpen, nearEx)

        // The Time Capsule (direct-delivery display): a stone pedestal with a display
        // shelf holding one socket per artefact. A socket lights in the artefact's
        // colour once it's placed; the pedestal glows green when the display is complete.
        if (currentLevel.directDeliver && curIdx == suitRoomIndex) {
            val full = delivered.size >= currentLevel.cores.size
            val cx = suitPos.x; val cy = suitPos.y
            val body = if (full) cGood else Color(0.50f, 0.52f, 0.60f, 1f)
            val dark = Color(0.30f, 0.32f, 0.40f, 1f)
            shapes.color = dark; shapes.rect(cx - 38f, cy - 42f, 76f, 14f)   // base slab
            shapes.color = body; shapes.rect(cx - 16f, cy - 30f, 32f, 34f)   // column
            shapes.color = dark; shapes.rect(cx - 42f, cy + 2f, 84f, 12f)    // display shelf
            currentLevel.cores.forEachIndexed { i, id ->                     // an artefact socket per core, on the shelf
                shapes.color = if (id in delivered) coreColor(id) else Color(0.24f, 0.25f, 0.31f, 1f)
                shapes.circle(cx + (i - 1) * 30f, cy + 24f, 9f)
            }
        }

        // The current room's puzzle station is drawn as a themed object in the batch
        // pass (anti-aliased) below — nothing for it here in the shapes pass.

        // Clue drop (dummy art placeholder) — a parchment note in the clue room.
        if (clueRoomIndex >= 0 && curIdx == clueRoomIndex) {
            shapes.color = Color(0.96f, 0.91f, 0.66f, 1f)
            shapes.rect(cluePos.x - 15f, cluePos.y - 18f, 30f, 36f)
            shapes.color = Color(0.78f, 0.68f, 0.38f, 1f)
            shapes.rect(cluePos.x - 15f, cluePos.y + 12f, 30f, 6f)
            if (nearC) { shapes.color = Color.WHITE; shapes.circle(cluePos.x, cluePos.y + 34f, 5f) }
        }

        // Fog of war — cover every room the player isn't standing in.
        shapes.color = Color(0.05f, 0.05f, 0.10f, 0.95f)
        cells.forEachIndexed { idx, c -> if (idx != curIdx) shapes.rect(c.x, c.y, c.w, c.h) }

        shapes.end()

        // Character tokens and the on-screen buttons are drawn through the batch
        // as anti-aliased circles (SmoothDraw), over the fog.
        batch.begin()
        // Themed, non-interactive floor props for the room you're standing in.
        drawRoomDecor(cells[curIdx], rooms[curIdx].title)
        // The current room's puzzle station, as a distinct themed object.
        if (rooms[curIdx].puzzle != null) {
            val r = rooms[curIdx]; val p = stationPos[curIdx]
            drawStation(r.title, p.x, p.y, r.nodeColor, isLocked(curIdx), r.id in solved, mi == curIdx)
        }
        // Co-op teammate avatars — drawn *before* the items so a core/bottle a teammate
        // is carrying (or one sitting in the same spot) stays on top and isn't covered.
        mates.forEachIndexed { i, m ->
            val mx = mateX(i, mates.size, cells[curIdx]); val my = mateY(cells[curIdx])
            smooth.circle(batch, mx, my, 16f, playerColor(m.learnerId))
            smooth.circle(batch, mx - 5f, my + 3f, 3f, cInk)
            smooth.circle(batch, mx + 5f, my + 3f, 3f, cInk)
        }
        // Cores (kindness-castle): glow when charged/delivered; only those in this
        // room — or the one being carried — are visible (everything else is fogged).
        currentLevel.cores.forEach { id ->
            val carrier = mateCore[id]
            val dropRoom = drops[id]
            // Draw a core at (cx,cy); grey until "live" (Tower cores when charged, Vault
            // artefacts once their gallery is solved).
            fun drawCore(cx: Float, cy: Float, liveFlag: Boolean) {
                if (liveFlag) smooth.circle(batch, cx, cy, 18f, Color(1f, 1f, 1f, 0.45f))
                smooth.circle(batch, cx, cy, 13f, if (liveFlag) coreColor(id) else Color(0.60f, 0.62f, 0.70f, 1f))
                smooth.circle(batch, cx, cy, 5f, Color(1f, 1f, 1f, 0.9f))
            }
            val liveShared = id in delivered || (if (currentLevel.directDeliver) id in solved else id in charged)
            when {
                // A teammate in this room is hauling this core — draw it on their avatar.
                carrier != null -> matePos[carrier.first]?.let { mp ->
                    drawCore(mp.x, mp.y + 24f, if (currentLevel.directDeliver) id in solved else carrier.second)
                }
                // Carried by a teammate elsewhere — hide our home copy (it's not here).
                id in carriedAway -> {}
                // A teammate left it in this room — show it at the room's centre.
                dropRoom != null -> if (dropRoom == rooms[curIdx].id) {
                    val c = cells[curIdx]; drawCore(c.x + c.w / 2f, c.y + c.h / 2f, liveShared)
                }
                id == carrying || coreRoomOf(id) == curIdx -> {
                    val cp = corePos(id)
                    drawCore(cp.x, cp.y, id in delivered || (if (currentLevel.directDeliver) id in solved
                        else id in charged || (id == carrying && carryingCharged)))
                }
            }
        }
        // Recycling chain (green-lab): the sink, the recycle bin, and the bottles.
        if (currentLevel.bottles.isNotEmpty()) {
            if (curIdx == sinkRoomIndex) { // a little wash basin with a tap
                smooth.rect(batch, sinkPos.x - 26f, sinkPos.y - 14f, 52f, 22f, Color(0.66f, 0.70f, 0.78f, 1f))
                smooth.rect(batch, sinkPos.x - 21f, sinkPos.y - 10f, 42f, 12f, Color(0.40f, 0.56f, 0.66f, 1f))
                smooth.line(batch, sinkPos.x + 16f, sinkPos.y + 8f, sinkPos.x + 16f, sinkPos.y + 26f, 5f, Color(0.56f, 0.59f, 0.67f, 1f))
                smooth.line(batch, sinkPos.x + 16f, sinkPos.y + 26f, sinkPos.x + 1f, sinkPos.y + 26f, 5f, Color(0.56f, 0.59f, 0.67f, 1f))
            }
            if (curIdx == depositRoomIndex) { // a recycle bin beside the plant station
                val bx = depositPos.x; val by = depositPos.y
                smooth.rect(batch, bx - 18f, by - 18f, 36f, 34f, if (bottlesDone) cGood else Color(0.30f, 0.60f, 0.42f, 1f))
                smooth.triangle(batch, bx - 11f, by + 16f, 22f, 11f, Color.WHITE)
            }
            currentLevel.bottles.forEach { id ->
                val carrier = mateBottle[id]
                val dropRoom = drops[id]
                when {
                    // A teammate in this room is hauling this bottle — draw it on them.
                    carrier != null -> matePos[carrier.first]?.let { mp ->
                        drawBottle(mp.x, mp.y + 24f, 12f, carrier.second)
                    }
                    // Carried by a teammate elsewhere — hide our home copy.
                    id in carriedAway -> {}
                    // A teammate left it in this room — show it at the room's centre.
                    dropRoom != null -> if (dropRoom == rooms[curIdx].id) {
                        val c = cells[curIdx]; drawBottle(c.x + c.w / 2f, c.y + c.h / 2f, 12f, id in bottleDeposited || id in bottleWashed)
                    }
                    id == carryingBottle || bottleRoomOf(id) == curIdx -> {
                        val bp = bottlePos(id)
                        val clean = id in bottleDeposited || id in bottleWashed || (id == carryingBottle && carriedClean)
                        drawBottle(bp.x, bp.y, 12f, clean)
                    }
                }
            }
        }
        // Player (drawn over the fog so it's never clipped at doorways).
        smooth.circle(batch, pos.x, pos.y, CHAR_R, Color(0.98f, 0.80f, 0.16f, 1f))
        smooth.circle(batch, pos.x - 7f, pos.y + 4f, 4f, cInk)
        smooth.circle(batch, pos.x + 7f, pos.y + 4f, 4f, cInk)
        // Floating joystick.
        if (joyActive) {
            smooth.circle(batch, joyOrigin.x, joyOrigin.y, JOY_RADIUS, Color(1f, 1f, 1f, 0.25f))
            smooth.circle(batch, joyKnob.x, joyKnob.y, 30f, cAccent)
        }
        // Action + close buttons.
        smooth.circle(batch, actionCenter.x, actionCenter.y, actionR, if (canAct) cAccent else Color(1f, 1f, 1f, 0.25f))
        smooth.circle(batch, closeCenter.x, closeCenter.y, closeR, Color.WHITE)

        font.color = Color.WHITE
        rooms[curIdx].label?.let { centerText(it, stationPos[curIdx].x, stationPos[curIdx].y - 40f, 0.9f) }
        if (currentLevel.bottles.isNotEmpty()) {
            if (curIdx == sinkRoomIndex) centerText("Sink", sinkPos.x, sinkPos.y - 26f, 0.7f)
            if (curIdx == depositRoomIndex) centerText("Recycle", depositPos.x, depositPos.y + 30f, 0.65f)
        }
        if (clueRoomIndex >= 0 && curIdx == clueRoomIndex) centerText("Lab Note", cluePos.x, cluePos.y - 30f, 0.75f)
        if (curIdx == exitRoomIndex) centerText(if (exitOpen) "EXIT" else "LOCKED", exitDoor.x, exitDoor.y - 40f, 0.75f)
        font.color = Color.WHITE
        centerText(actLabel, actionCenter.x, actionCenter.y, 0.85f)
        font.color = cInk
        centerText("X", closeCenter.x, closeCenter.y, 1.1f)
        // Co-op teammate name under each avatar token.
        font.color = Color(0.88f, 0.93f, 1f, 1f)
        mates.forEachIndexed { i, m ->
            centerText(m.name.substringBefore(' '), mateX(i, mates.size, cells[curIdx]), mateY(cells[curIdx]) - 24f, 0.6f)
        }
        batch.end()

        if (wrongFlash > 0f) {
            batch.begin(); font.color = Color(0.96f, 0.34f, 0.34f, 1f)
            centerText(flashMsg, worldW / 2f, areaY + 28f, 1.0f)
            batch.end()
        }
    }

    private fun drawDimAndPanel() {
        val lvl = currentLevel
        shapes.color = Color(0f, 0f, 0f, 0.6f)
        shapes.rect(0f, 0f, W, H)
        // Themed border frame, tinted surface, then a header band that every puzzle's
        // title (y=652) and subtitle (y=623) are standardised to sit on. The band's
        // bottom (y=614) clears all content (the highest is NumberLock's icons at ~605).
        shapes.color = lvl.cardBar
        shapes.rect(PANEL_X - 5f, PANEL_Y - 5f, PANEL_W + 10f, PANEL_H + 10f)
        shapes.color = lvl.card
        shapes.rect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H)
        shapes.color = lvl.cardBar
        shapes.rect(PANEL_X, PANEL_Y + PANEL_H - 61f, PANEL_W, 61f)
        shapes.color = lvl.cardBtn                                            // close button
        shapes.circle(backCenter.x, backCenter.y, backR)
        drawLevelEmblem(PANEL_X + 30f, PANEL_Y + PANEL_H - 22f)               // a small theme emblem in the header
    }

    /** A small per-level emblem drawn in the panel header (ShapeRenderer, in the
     *  level's accent colour) so you can tell the world at a glance. */
    private fun drawLevelEmblem(x: Float, y: Float) {
        val lvl = currentLevel
        shapes.color = lvl.cardBtn
        when (lvl) {
            robotLab -> { // a gear
                shapes.rect(x - 2f, y + 6f, 4f, 4f); shapes.rect(x - 2f, y - 10f, 4f, 4f)
                shapes.rect(x + 6f, y - 2f, 4f, 4f); shapes.rect(x - 10f, y - 2f, 4f, 4f)
                shapes.circle(x, y, 8f)
                shapes.color = lvl.cardBar; shapes.circle(x, y, 3.5f)
            }
            tower -> { // a hero star
                shapes.triangle(x - 9f, y - 3f, x + 9f, y - 3f, x, y + 9f)
                shapes.triangle(x - 9f, y + 3f, x + 9f, y + 3f, x, y - 9f)
            }
            annex -> { // a leaf
                shapes.triangle(x - 7f, y - 6f, x + 7f, y - 6f, x, y + 8f)
                shapes.rect(x - 1f, y - 10f, 2f, 5f)
            }
            vault -> { // a column
                shapes.rect(x - 7f, y + 6f, 14f, 3f); shapes.rect(x - 7f, y - 9f, 14f, 3f)
                shapes.rect(x - 5f, y - 6f, 3f, 12f); shapes.rect(x + 2f, y - 6f, 3f, 12f)
            }
            else -> { // a festive lantern (bigHall)
                shapes.circle(x, y, 7f)
                shapes.rect(x - 4f, y + 6f, 8f, 3f); shapes.rect(x - 1f, y - 11f, 2f, 4f)
            }
        }
    }

    private fun drawBackLabel() {
        font.color = Color.WHITE
        centerText("X", backCenter.x, backCenter.y, 1.25f)
    }

    /** Shown over a solved puzzle (review mode): the player taps X to continue.
     *  Centred on the panel so it never strands in the letterbox margin. */
    private fun drawSolvedBanner() {
        val bw = 420f; val bh = 56f
        val bx = (W - bw) / 2f
        val by = PANEL_Y + PANEL_H / 2f - bh / 2f
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Dark border so the banner stays distinct from green-lit options behind it.
        shapes.color = cInk
        shapes.rect(bx - 5f, by - 5f, bw + 10f, bh + 10f)
        shapes.color = cGood
        shapes.rect(bx, by, bw, bh)
        shapes.end()
        batch.begin()
        font.color = Color.WHITE
        centerText("Solved! Tap X to continue", W / 2f, by + bh / 2f, 1.05f)
        batch.end()
    }

    private fun drawWin() {
        val cardW = minOf(W - 100f, 560f) // a centred card, not a full-width banner in landscape
        val cardX = (W - cardW) / 2f
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.6f)
        shapes.rect(0f, 0f, W, H)
        shapes.color = cAccent
        shapes.rect(cardX, 280f, cardW, 240f)
        shapes.end()

        batch.begin()
        // Anti-aliased confetti drifting up the celebration card (SmoothDraw) — kept
        // within the card, not the full canvas, so it stays put in wide landscape.
        for (k in 0 until 14) {
            val seed = k * 127.1f
            val cx = cardX + 20f + (seed * 0.73f % (cardW - 40f))
            val cy = 280f + ((seed * 1.9f + winTime * 60f) % 240f)
            val r = 4f + (k % 3)
            smooth.circle(batch, cx, cy, r, confettiColors[k % confettiColors.size])
        }

        // Typewriter title, then the reward line with an inline star pictogram.
        val title = "You escaped!"
        val reveal = (winTime * 18f).toInt() // ~18 chars/sec
        RichText.draw(batch, font, layout, title, W / 2f, 466f, 1.8f, Color.WHITE, reveal)
        if (reveal >= title.length) {
            RichText.draw(
                batch, font, layout, "+5 [#FFD23B]stars[] :g7:", W / 2f, 408f, 1.2f, Color.WHITE,
                iconSize = 9f, icon = { _, x, y, s -> smooth.circle(batch, x, y, s, Color(0.95f, 0.80f, 0.22f, 1f)) },
            )
            font.color = Color(1f, 1f, 1f, 0.8f)
            centerText("Tap to go back", W / 2f, 344f, 0.95f)
        }
        batch.end()
    }

    private fun drawHud() {
        batch.begin()
        font.color = Color.WHITE
        centerText(cells[currentRoomIndex()].title, titlePos.x, titlePos.y, 1.2f)
        val coopState = coop?.state
        if (coopState != null) {
            centerText("Team ${coopState.solved.size}/${coopState.total} solved", solvedPos.x, solvedPos.y, 0.85f)
            if (coopState.players.size > 1) {
                val names = coopState.players.joinToString(", ") { it.name.substringBefore(' ') }
                font.color = Color(0.7f, 0.9f, 1f, 1f)
                centerText(names, solvedPos.x, solvedPos.y - 20f, 0.6f)
                font.color = Color.WHITE
            }
        } else {
            centerText("${solved.size}/$totalStations solved", solvedPos.x, solvedPos.y, 0.9f)
        }
        if (currentLevel.bottles.isNotEmpty()) {
            font.color = if (bottlesDone) Color(0.55f, 0.92f, 0.62f, 1f) else Color(0.72f, 0.86f, 1f, 1f)
            centerText("Bottles recycled: ${bottleDeposited.size}/${currentLevel.bottles.size}", solvedPos.x, solvedPos.y - 20f, 0.72f)
            font.color = Color.WHITE
        }
        val clues = collectedClues()
        if (clues.isNotEmpty()) {
            font.color = Color(1f, 0.9f, 0.55f, 1f)
            if (landscape) {
                // Top-right, growing downward — the side margin has the room.
                centerText("Clues:", cluesAnchor.x, cluesAnchor.y, 0.78f)
                clues.forEachIndexed { i, c -> centerText(c, cluesAnchor.x, cluesAnchor.y - 18f - i * 18f, 0.7f) }
            } else {
                // Portrait's bottom strip is shallow, so stack upward from a low
                // baseline (header on top) — keeps a 4th clue from falling off-screen.
                clues.forEachIndexed { i, c -> centerText(c, cluesAnchor.x, 10f + (clues.size - 1 - i) * 18f, 0.7f) }
                centerText("Clues:", cluesAnchor.x, 10f + clues.size * 18f, 0.78f)
            }
        }
        batch.end()
    }

    /** Global font-size multiplier for ALL escape-room text. Every per-element
     *  scale is multiplied by this, so bump it once to make everything bigger. */
    private val fontScale = 1.5f

    private fun centerText(text: String, cx: Float, cy: Float, scale: Float) {
        font.data.setScale(scale * fontScale)
        layout.setText(font, text)
        font.draw(batch, layout, cx - layout.width / 2f, cy + layout.height / 2f)
    }

    private fun wrapText(text: String, cx: Float, topY: Float, width: Float, scale: Float) {
        font.data.setScale(scale * fontScale)
        layout.setText(font, text, font.color, width, Align.center, true)
        font.draw(batch, layout, cx - width / 2f, topY)
    }

    /** Left-aligned wrapped text, vertically centred on [centerY]. */
    private fun wrapLeft(text: String, x: Float, centerY: Float, width: Float, scale: Float) {
        font.data.setScale(scale * fontScale)
        layout.setText(font, text, font.color, width, Align.left, true)
        font.draw(batch, layout, x, centerY + layout.height / 2f)
    }

    override fun resize(width: Int, height: Int) {
        // Reflow the room grid for the new orientation, keeping the player in the
        // same room (moved to its new centre).
        val keepRoom = currentRoomIndex()
        landscape = width > height
        // Widen the puzzle canvas to the screen aspect in landscape (height stays
        // 800), so the overlay fills the screen with no side letterboxing.
        W = if (landscape) (width.toFloat() / height * H).coerceIn(480f, 1700f) else 480f
        loadLevel()
        worldViewport.worldWidth = worldW
        worldViewport.worldHeight = worldH
        worldViewport.update(width, height, true)
        puzzleViewport.setWorldSize(W, H)
        puzzleViewport.update(width, height, true)
        val c = cells[keepRoom]
        pos.set(c.x + c.w / 2f, c.y + c.h / 2f)
    }

    override fun dispose() {
        shapes.dispose(); batch.dispose(); font.dispose(); smooth.dispose()
    }
}
