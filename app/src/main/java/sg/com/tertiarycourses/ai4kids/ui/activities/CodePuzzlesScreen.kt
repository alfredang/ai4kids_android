package sg.com.tertiarycourses.ai4kids.ui.activities

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.components.CelebrationView
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/** Bucket id under which the code-puzzle's cleared levels are persisted. */
private const val CODE_BUCKET = "code.levels"

/**
 * Code Puzzles — a tiny "sequence the steps" game that teaches algorithmic
 * thinking without real code execution. The child taps direction arrows to plan
 * a path that walks the robot 🤖 to the goal ⭐️ on a small grid, then runs it.
 * Android port of the iOS `CodePuzzlesView`.
 */
@Composable
fun CodePuzzlesScreen(onClose: () -> Unit) {
    val progress = LocalProgressStore.current
    val cleared = progress.clearedLevels(CODE_BUCKET)

    // A level is unlocked once the previous one has been cleared (the first is
    // always open). Cleared levels stay unlocked forever, persisted across exits.
    fun isUnlocked(index: Int) = index == 0 || (index - 1) in cleared

    // null = the level-select list is showing; otherwise the chosen level plays.
    var selectedLevel by remember { mutableStateOf<Int?>(null) }

    val current = selectedLevel
    if (current == null) {
        BackHandler(onBack = onClose)
        LevelSelect(
            cleared = cleared,
            isUnlocked = ::isUnlocked,
            stars = progress.stars(Activity.CODE),
            onClose = onClose,
            onPick = { selectedLevel = it },
        )
        return
    }

    LevelPlay(
        levelIndex = current,
        onExit = { selectedLevel = null },
        onCleared = { progress.markLevelCleared(CODE_BUCKET, current) },
        onNext = {
            val next = current + 1
            selectedLevel = if (next < LEVELS.size) next else null
        },
    )
}

/** The level picker: a grid of level chips, cleared ones starred and locked ones
 *  greyed out behind a padlock until the prior level is solved. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelSelect(
    cleared: Set<Int>,
    isUnlocked: (Int) -> Boolean,
    stars: Int,
    onClose: () -> Unit,
    onPick: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Theme.Background)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text("Code Puzzles", color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                StarBadge(count = stars)
            }
            Text(
                "Pick a level",
                color = Theme.Ink.copy(alpha = 0.75f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                LEVELS.indices.forEach { i ->
                    LevelChip(
                        number = i + 1,
                        cleared = i in cleared,
                        unlocked = isUnlocked(i),
                        onClick = { onPick(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelChip(number: Int, cleared: Boolean, unlocked: Boolean, onClick: () -> Unit) {
    val bg = when {
        cleared -> Theme.Green
        unlocked -> Theme.Blue
        else -> Theme.Ink.copy(alpha = 0.18f)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(84.dp)
            .then(if (unlocked) Modifier.softShadow(RoundedCornerShape(20.dp)) else Modifier)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(enabled = unlocked, onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!unlocked) {
                Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(28.dp))
            } else {
                Text("$number", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                if (cleared) {
                    Icon(Icons.Filled.Star, contentDescription = "Cleared", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** A single playable level. [onCleared] persists the win, [onNext] advances (or
 *  returns to the picker), and [onExit] backs out to the picker. */
@Composable
private fun LevelPlay(
    levelIndex: Int,
    onExit: () -> Unit,
    onCleared: () -> Unit,
    onNext: () -> Unit,
) {
    BackHandler(onBack = onExit)
    val progress = LocalProgressStore.current
    val level = LEVELS[levelIndex]

    var program by remember { mutableStateOf<List<Instr>>(emptyList()) }

    // null = normal mode. Non-null = a loop is open; arrows drop into this buffer.
    var openLoop by remember { mutableStateOf<List<Step>?>(null) }
    var loopTimes by remember { mutableStateOf(2) }   // ×N for the open loop, 2..4

    var robot by remember { mutableStateOf(level.start) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Plan the robot's path to the star!") }
    var showCelebration by remember { mutableStateOf(false) }

    fun resetLevel() {
        program = emptyList()
        openLoop = null
        loopTimes = 2
        robot = level.start
        running = false
        message = "Plan the robot's path to the star!"
    }

    // Re-seed the robot whenever the level changes.
    LaunchedEffect(levelIndex) { resetLevel() }

    // Drive the robot along the planned program, one step every 0.4s.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        robot = level.start
        for (dir in program.expand()) {
            delay(400)
            robot = level.step(robot, dir) // clamps to the grid, refuses walls
        }
        delay(400)
        running = false
        if (robot == level.goal) {
            progress.award(2, Activity.CODE)
            onCleared()
            showCelebration = true
        } else {
            // Keep the plan so the child can debug it — tweak or Undo the last
            // step and Run again — rather than re-entering everything. Just snap
            // the robot back to the start ready for the next attempt.
            message = "Almost! Tweak your steps and Run again. 🔁"
            delay(400)
            robot = level.start
        }
    }

    fun wouldExceed() : Boolean {
        val open = openLoop
        val projected = if (open != null)
            program.moveCount() + (open.size + 1) * loopTimes
        else
            program.moveCount() + 1
        return projected > level.maxMoves
    }
    val onStep: (Step) -> Unit = { step ->
        if (!running) {
           if (wouldExceed()) {
                message = "You cannot add any more steps!"
           } else {
                val open = openLoop
                if (open != null) openLoop = open + step
                else program = program + Instr.Move(step)
           }
        }
    }
    // Close the loop: commit it (if it has a body), else cancel.
    val onDone = {
        if (!running) {
            val open = openLoop
            if (!open.isNullOrEmpty()) program = program + Instr.Loop(open, loopTimes)
            openLoop = null
        }
    }

    // Enter bracket mode: start an empty loop body.
    val onRepeat = { if (!running && openLoop == null) { openLoop = emptyList(); loopTimes = 2 } }

    // Set the ×N repeat count for the loop being built.
    val onTimes: (Int) -> Unit = { n -> if (!running && openLoop != null) loopTimes = n.coerceIn(2, 4) }

    val onUndo = {
        if (!running) {
            val open = openLoop
            when {
                open != null && open.isNotEmpty() -> openLoop = open.dropLast(1)
                open != null                      -> openLoop = null            // cancel empty loop
                program.isNotEmpty()              -> program = program.dropLast(1)
            }
        }
    }

    // Block Run while a loop is still open — force them to close it first.
    val onRun = {
        if (!running && openLoop == null && program.isNotEmpty()) running = true
        else if (openLoop != null) message = "Tap Done to finish your loop first."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // In landscape the screen is short, so put the grid beside the
            // controls (instead of stacked) and shrink the cells a touch. Both
            // layouts scroll as a safety net on very small screens.
            val landscape = maxWidth > maxHeight
            val cell = if (landscape) 46.dp else 60.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (landscape) 1000.dp else 720.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                // Top bar.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    CloseButton(onClick = onExit)
                    Text(
                        "Code Puzzles  •  Level ${levelIndex + 1}",
                        color = Theme.Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    StarBadge(count = progress.stars(Activity.CODE))
                }

                if (landscape) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Grid(level = level, robot = robot, side = cell)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            MessageText(message)
                            ProgramBar(program = program, openLoop = openLoop, loopTimes = loopTimes, maxMoves = level.maxMoves)
                            Controls(
                                running = running,
                                inLoop = openLoop != null,
                                loopTimes = loopTimes,
                                onStep = onStep,
                                onUndo = onUndo,
                                onRun = onRun,
                                onRepeat = onRepeat,
                                onDone = onDone,
                                onTimes = onTimes,
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        MessageText(message)
                        Grid(level = level, robot = robot, side = cell)
                        ProgramBar(program = program, openLoop = openLoop, loopTimes = loopTimes, maxMoves = level.maxMoves)
                        Controls(
                            running = running,
                            inLoop = openLoop != null,
                            loopTimes = loopTimes,
                            onStep = onStep,
                            onUndo = onUndo,
                            onRun = onRun,
                            onRepeat = onRepeat,
                            onDone = onDone,
                            onTimes = onTimes,
                        )
                    }
                }
            }
        }

        if (showCelebration) {
            CelebrationView(message = "Solved it! 🤖⭐️", onDismiss = {
                showCelebration = false
                onNext()
            })
        }
    }
}

@Composable
private fun MessageText(message: String) {
    Text(
        message,
        color = Theme.Ink.copy(alpha = 0.75f),
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Grid(level: Level, robot: Pair<Int, Int>, side: Dp = 60.dp) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .softShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(10.dp),
    ) {
        for (y in (level.size - 1) downTo 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (x in 0 until level.size) {
                    val isRobot = robot == (x to y)
                    val isGoal = level.goal == (x to y)
                    val isWall = (x to y) in level.walls
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(side)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isWall) Theme.Ink.copy(alpha = 0.8f) else Theme.Blue.copy(alpha = 0.12f)),
                    ) {
                        val glyph = (side.value * 0.56f).sp
                        if (isGoal) Text("⭐️", fontSize = glyph)
                        if (isRobot) Text("🤖", fontSize = glyph)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramBar(program: List<Instr>, openLoop: List<Step>?, loopTimes: Int, maxMoves: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .softShadow(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // A step counter of executed moves (loops expanded) so the child sees
        // how many moves they've used and the cap for this level.
        Text(
            "Steps ${program.moveCount()} / $maxMoves",
            color = Theme.Ink.copy(alpha = 0.55f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (program.isEmpty() && openLoop == null) {
            Text("Your steps appear here", color = Theme.Ink.copy(alpha = 0.4f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        } else {
            // FlowRow wraps onto extra lines so every queued move stays visible,
            // however long the plan gets.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                program.forEach { instr ->
                    when (instr) {
                        is Instr.Move -> MoveTile(instr.step)
                        is Instr.Loop -> LoopTile(instr.body, instr.times, Theme.Purple)
                    }
                }
                // The loop currently being built, highlighted in orange as
                // "in progress" so it's clear where new arrows are landing.
                if (openLoop != null) LoopTile(openLoop, loopTimes, Theme.Orange)
            }
        }
    }
}

/** A single move as a blue rounded glyph tile. */
@Composable
private fun MoveTile(step: Step) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Theme.Blue),
    ) {
        Text(step.glyph, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** A loop rendered as a bracketed container: its body moves in a row, tinted with
 *  [color], plus a ×N badge. An empty body (a loop still being built) shows a dash. */
@Composable
private fun LoopTile(body: List<Step>, times: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (body.isEmpty()) {
            Text("…", color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        } else {
            body.forEach { MoveTile(it) }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("×$times", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun Controls(
    running: Boolean,
    inLoop: Boolean,
    loopTimes: Int,
    onStep: (Step) -> Unit,
    onUndo: () -> Unit,
    onRun: () -> Unit,
    onRepeat: () -> Unit,
    onDone: () -> Unit,
    onTimes: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Step.entries.forEach { step ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(66.dp)
                        .softShadow(CircleShape)
                        .clip(CircleShape)
                        .background(Theme.Blue)
                        .clickable(enabled = !running) { onStep(step) },
                ) {
                    Text(step.glyph, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        // While a loop is open, offer ×2/×3/×4 chips for its repeat count.
        if (inLoop) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(2, 3, 4).forEach { n ->
                    val selected = n == loopTimes
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (selected) Theme.Orange else Theme.Ink.copy(alpha = 0.15f))
                            .clickable(enabled = !running) { onTimes(n) },
                    ) {
                        Text(
                            "×$n",
                            color = if (selected) Color.White else Theme.Ink,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (inLoop) {
                KidButton(title = "Done", icon = Icons.Filled.Check, color = Theme.Green, onClick = onDone)
            } else {
                KidButton(title = "Repeat", color = Theme.Purple, onClick = onRepeat)
            }
            KidButton(title = "Undo", icon = Icons.AutoMirrored.Filled.Undo, color = Theme.Ink.copy(alpha = 0.5f), onClick = onUndo)
        }
        KidButton(title = "Run", icon = Icons.Filled.PlayArrow, color = Theme.Green, onClick = onRun)
    }
}
