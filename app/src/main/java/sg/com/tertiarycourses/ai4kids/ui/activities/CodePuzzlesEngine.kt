package sg.com.tertiarycourses.ai4kids.ui.activities

/**
 * Pure game logic for Code Puzzles — deliberately free of Compose/Android
 * imports so it is unit-testable with plain JUnit (see `CodePuzzlesEngineTest`).
 * [CodePuzzlesScreen] drives this model and renders it. The child sequences
 * arrow [Step]s (optionally wrapped in a repeat [Instr.Loop]) into a program,
 * which [simulate] walks across a [Level]'s grid.
 */

/** A single arrow move. [glyph] is used only for on-screen rendering. */
internal enum class Step(val glyph: String) { UP("↑"), DOWN("↓"), LEFT("←"), RIGHT("→") }

/** One puzzle: a square [size]×[size] grid with a [start], a [goal], blocked
 *  [walls], and a [maxMoves] budget. Coordinates are (x, y) with y pointing up. */
internal data class Level(
    val size: Int,
    val start: Pair<Int, Int>,
    val goal: Pair<Int, Int>,
    val walls: Set<Pair<Int, Int>>,
    /** Most steps the child may queue — keeps the plan short and forces them to
     *  think about an efficient route rather than spamming arrows. */
    val maxMoves: Int,
)

/** The built-in puzzles, in unlock order. */
internal val LEVELS = listOf(
    Level(4, 0 to 0, 3 to 0, emptySet(), maxMoves = 6),
    Level(4, 0 to 3, 3 to 0, setOf(2 to 2, 2 to 1), maxMoves = 10),
    Level(5, 0 to 0, 4 to 4, setOf(2 to 2, 3 to 2, 1 to 3), maxMoves = 14),
)

/** A program instruction: a single [Move], or a [Loop] that repeats a body. */
internal sealed interface Instr {
    data class Move(val step: Step) : Instr
    data class Loop(val body: List<Step>, val times: Int) : Instr
}

/** How many grid moves this program actually executes (loops expanded). */
internal fun List<Instr>.moveCount(): Int = sumOf {
    when (it) {
        is Instr.Move -> 1
        is Instr.Loop -> it.body.size * it.times
    }
}

/** Flatten to the raw move sequence the runner walks. */
internal fun List<Instr>.expand(): List<Step> = flatMap { instr ->
    when (instr) {
        is Instr.Move -> listOf(instr.step)
        is Instr.Loop -> (0 until instr.times).flatMap { instr.body }
    }
}

/** Apply one [dir] to [from], clamping to the grid and refusing to enter a wall
 *  (an illegal move leaves the robot where it was — matching the on-screen runner). */
internal fun Level.step(from: Pair<Int, Int>, dir: Step): Pair<Int, Int> {
    val (x, y) = from
    val (nx, ny) = when (dir) {
        Step.UP -> x to y + 1
        Step.DOWN -> x to y - 1
        Step.LEFT -> x - 1 to y
        Step.RIGHT -> x + 1 to y
    }
    return if (nx in 0 until size && ny in 0 until size && (nx to ny) !in walls) nx to ny else from
}

/** Walk [program] from [start] and return the robot's final cell. */
internal fun Level.simulate(program: List<Instr>): Pair<Int, Int> {
    var pos = start
    for (dir in program.expand()) pos = step(pos, dir)
    return pos
}

/** True when [program] lands the robot exactly on the [goal]. */
internal fun Level.solves(program: List<Instr>): Boolean = simulate(program) == goal