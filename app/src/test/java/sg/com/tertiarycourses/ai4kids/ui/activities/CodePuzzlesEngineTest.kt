package sg.com.tertiarycourses.ai4kids.ui.activities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JUnit tests for the Code Puzzles engine — no Android, Compose, or
 * Robolectric. Everything here exercises real [CodePuzzlesEngine] functions with
 * real inputs; nothing is stubbed or mocked.
 */
class CodePuzzlesEngineTest {

    private fun moves(vararg steps: Step): List<Instr> = steps.map { Instr.Move(it) }

    // --- expand() -----------------------------------------------------------

    @Test fun expand_keepsMovesInOrder() {
        assertEquals(
            listOf(Step.UP, Step.RIGHT, Step.DOWN),
            moves(Step.UP, Step.RIGHT, Step.DOWN).expand(),
        )
    }

    @Test fun expand_loopRepeatsBody() {
        val program = listOf(Instr.Loop(listOf(Step.UP, Step.RIGHT), times = 3))
        assertEquals(
            listOf(Step.UP, Step.RIGHT, Step.UP, Step.RIGHT, Step.UP, Step.RIGHT),
            program.expand(),
        )
    }

    @Test fun expand_emptyLoopContributesNothing() {
        val program = listOf(Instr.Move(Step.UP), Instr.Loop(emptyList(), times = 4))
        assertEquals(listOf(Step.UP), program.expand())
    }

    @Test fun expand_mixesMovesAndLoops() {
        val program = listOf(
            Instr.Move(Step.UP),
            Instr.Loop(listOf(Step.RIGHT), times = 2),
            Instr.Move(Step.DOWN),
        )
        assertEquals(listOf(Step.UP, Step.RIGHT, Step.RIGHT, Step.DOWN), program.expand())
    }

    // --- moveCount() --------------------------------------------------------

    @Test fun moveCount_matchesExpandedLength() {
        val program = listOf(
            Instr.Move(Step.UP),
            Instr.Loop(listOf(Step.RIGHT, Step.RIGHT), times = 3),
        )
        assertEquals(program.expand().size, program.moveCount())
        assertEquals(7, program.moveCount()) // 1 move + (2 * 3) looped
    }

    // --- step(): bounds & walls --------------------------------------------

    private val open4 = Level(4, 0 to 0, 3 to 0, emptySet(), maxMoves = 99)

    @Test fun step_movesWithinTheGrid() {
        assertEquals(1 to 0, open4.step(0 to 0, Step.RIGHT))
        assertEquals(0 to 1, open4.step(0 to 0, Step.UP))
    }

    @Test fun step_clampsAtEdges() {
        assertEquals(0 to 0, open4.step(0 to 0, Step.LEFT))  // off the left edge
        assertEquals(0 to 0, open4.step(0 to 0, Step.DOWN))  // off the bottom edge
        assertEquals(3 to 0, open4.step(3 to 0, Step.RIGHT)) // off the right edge
    }

    @Test fun step_refusesToEnterAWall() {
        val walled = Level(4, 0 to 0, 3 to 3, walls = setOf(1 to 0), maxMoves = 99)
        assertEquals(0 to 0, walled.step(0 to 0, Step.RIGHT)) // (1,0) is a wall -> stay put
    }

    // --- simulate() / solves() ---------------------------------------------

    @Test fun simulate_walksToTheFinalCell() {
        assertEquals(3 to 0, open4.simulate(moves(Step.RIGHT, Step.RIGHT, Step.RIGHT)))
    }

    @Test fun solves_trueOnlyWhenReachingTheGoal() {
        assertTrue(open4.solves(moves(Step.RIGHT, Step.RIGHT, Step.RIGHT)))
        assertFalse(open4.solves(moves(Step.RIGHT, Step.RIGHT)))
    }

    @Test fun simulate_expandsLoopsWhileWalking() {
        // A 3x RIGHT loop reaches the goal just like three individual moves.
        val looped = listOf(Instr.Loop(listOf(Step.RIGHT), times = 3))
        assertEquals(3 to 0, open4.simulate(looped))
        assertTrue(open4.solves(looped))
    }

    // --- every shipped level must be winnable within its move budget --------

    @Test fun everyLevelHasASolutionWithinMaxMoves() {
        for ((index, level) in LEVELS.withIndex()) {
            val solution = shortestSolution(level)
            assertNotNull("Level ${index + 1} has no solution at all", solution)
            assertTrue(
                "Level ${index + 1} needs ${solution!!.size} moves > budget ${level.maxMoves}",
                solution.size <= level.maxMoves,
            )
            // Sanity: the engine agrees the found path solves the level.
            assertTrue(level.solves(solution.map { Instr.Move(it) }))
        }
    }

    /** Shortest step sequence from start to goal via the engine's own [step],
     *  or null if the goal is unreachable. Test-only helper — it drives the real
     *  engine, it does not reimplement it. */
    private fun shortestSolution(level: Level): List<Step>? {
        val queue = ArrayDeque<Pair<Pair<Int, Int>, List<Step>>>()
        val seen = mutableSetOf(level.start)
        queue.add(level.start to emptyList())
        while (queue.isNotEmpty()) {
            val (pos, path) = queue.removeFirst()
            if (pos == level.goal) return path
            for (dir in Step.entries) {
                val next = level.step(pos, dir)
                if (next != pos && next !in seen) {
                    seen.add(next)
                    queue.add(next to path + dir)
                }
            }
        }
        return null
    }
}