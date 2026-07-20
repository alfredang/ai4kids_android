package sg.com.tertiarycourses.ai4kids.ui.activities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JUnit tests for the Story Builder engine — no Android, Compose, or
 * Robolectric. Everything here exercises real [StoryEngine] functions with real
 * inputs; nothing is stubbed or mocked.
 *
 * The beats are randomized, so structural claims are asserted across every
 * ingredient combination (or many samples) rather than against fixed prose.
 */
class StoryEngineTest {

    private val fox = Choice("🦊", "Fox")
    private val island = Choice("🏝️", "island")
    private val orb = Choice("🔮", "magic orb")
    private val brave = Choice("🦁", "brave")

    private fun sampleStory() = buildStory(fox, island, orb, brave)

    /** Walk the whole tale down one path, always taking A or always B. */
    private fun walk(story: Story, takeA: Boolean): List<Branch> {
        val chosen = mutableListOf<Branch>()
        var node: ForkNode = story
        while (true) {
            val next = (if (takeA) node.choiceA else node.choiceB) ?: break
            chosen += next
            node = next
        }
        return chosen
    }

    // --- an() ---------------------------------------------------------------

    @Test fun an_usesAnBeforeAVowel() {
        assertEquals("an", an("island"))
        assertEquals("an", an("orb"))
        assertEquals("an", an("Apple"))
    }

    @Test fun an_usesABeforeAConsonant() {
        assertEquals("a", an("castle"))
        assertEquals("a", an("forest"))
        assertEquals("a", an("snowy peak"))
    }

    @Test fun an_doesNotCrashOnEmpty() {
        assertEquals("a", an(""))
    }

    /** The bug that prompted porting `an()`: "lived near a island" reads wrong.
     *  Only the article is at stake — "a magic orb" is correct, since the article
     *  follows the ingredient's *name* ("magic orb"), not its noun. */
    @Test fun buildStory_neverWritesABeforeAVowelPlace() {
        repeat(200) {
            val prose = buildStory(fox, island, orb, brave).pre.joinToString(" ")
            assertFalse("found 'a island' in: $prose", prose.contains("a island"))
        }
    }

    /** …and the vowel case really is exercised: some openings do say "an island". */
    @Test fun buildStory_doesWriteAnBeforeAVowelPlace() {
        val sawAn = (1..200).any { buildStory(fox, island, orb, brave).pre.any { p -> p.contains("an island") } }
        assertTrue("no opening ever used 'an island'", sawAn)
    }

    // --- ingredients --------------------------------------------------------

    @Test fun everyIngredientRowOffersEightChoices() {
        assertEquals(8, HEROES.size)
        assertEquals(8, PLACES.size)
        assertEquals(8, OBJECTS.size)
        assertEquals(8, MOODS.size)
    }

    @Test fun ingredientsAreDistinct() {
        for (row in listOf(HEROES, PLACES, OBJECTS, MOODS)) {
            assertEquals(row.size, row.map { it.name }.toSet().size)
            assertEquals(row.size, row.map { it.emoji }.toSet().size)
        }
    }

    // --- story shape --------------------------------------------------------

    @Test fun buildStory_hasThreePreFencePagesAndTwoOpeningChoices() {
        val story = sampleStory()
        assertEquals(3, story.pre.size)
        assertTrue(story.problem.isNotBlank())
        assertEquals("Use the magic orb", story.choiceA.label)
        assertEquals("Call for friends", story.choiceB.label)
    }

    @Test fun buildStory_forksTwiceOnEveryPath() {
        for (takeA in listOf(true, false)) {
            for (thenA in listOf(true, false)) {
                val story = sampleStory()
                val first = if (takeA) story.choiceA else story.choiceB
                // Every first-act branch poses a follow-up fork...
                assertNotNull("first fork branch should pose a second fork", first.problem)
                assertNotNull(first.choiceA)
                assertNotNull(first.choiceB)
                // ...and the branch after it ends the tale rather than forking again.
                val second = (if (thenA) first.choiceA else first.choiceB)!!
                assertEquals(null, second.problem)
                assertEquals(null, second.choiceA)
                assertEquals(null, second.choiceB)
            }
        }
    }

    @Test fun buildStory_everyPathEndsWithTheEnd() {
        repeat(50) {
            val story = sampleStory()
            for (takeA in listOf(true, false)) {
                for (thenA in listOf(true, false)) {
                    val first = if (takeA) story.choiceA else story.choiceB
                    val second = (if (thenA) first.choiceA else first.choiceB)!!
                    assertTrue(
                        "last page should close the tale: ${second.pages.last()}",
                        second.pages.last().contains("The End!"),
                    )
                }
            }
        }
    }

    @Test fun buildStory_secondForkOffersShareAndExplore() {
        val story = sampleStory()
        assertEquals("Share the magic", story.choiceA.choiceA?.label)
        assertEquals("Go exploring", story.choiceA.choiceB?.label)
    }

    /** Four distinct endings is the whole point of the second fork. */
    @Test fun buildStory_hasFourReachablePaths() {
        val story = sampleStory()
        val leaves = listOf(story.choiceA, story.choiceB).flatMap { first ->
            listOf(first.choiceA!!, first.choiceB!!)
        }
        assertEquals(4, leaves.size)
        assertTrue(leaves.all { it.pages.isNotEmpty() })
    }

    // --- buildTimeline ------------------------------------------------------

    @Test fun buildTimeline_beforeAnyChoiceStopsAtTheFirstFork() {
        val story = sampleStory()
        val t = buildTimeline(story, emptyList())
        assertEquals(story.pre + story.problem, t.pages)
        // The fork is the problem page — the last one revealed so far.
        assertEquals(listOf(3), t.forks)
        assertEquals(t.pages.size - 1, t.forks[0])
    }

    @Test fun buildTimeline_growsAsEachForkIsAnswered() {
        val story = sampleStory()
        val first = story.choiceA
        val second = first.choiceA!!

        val afterFirst = buildTimeline(story, listOf(first))
        // pre + problem + branch pages + the branch's own follow-up problem.
        assertEquals(3 + 1 + first.pages.size + 1, afterFirst.pages.size)
        assertEquals(2, afterFirst.forks.size)
        // The second fork sits on the last revealed page.
        assertEquals(afterFirst.pages.size - 1, afterFirst.forks[1])

        val afterSecond = buildTimeline(story, listOf(first, second))
        assertEquals(afterFirst.pages.size + second.pages.size, afterSecond.pages.size)
        // The final branch poses no fork, so no third entry appears.
        assertEquals(2, afterSecond.forks.size)
        assertTrue(afterSecond.pages.last().contains("The End!"))
    }

    @Test fun buildTimeline_forkIndexAlwaysPointsAtAProblemPage() {
        val story = sampleStory()
        val first = story.choiceA
        val t = buildTimeline(story, listOf(first))
        assertEquals(story.problem, t.pages[t.forks[0]])
        assertEquals(first.problem, t.pages[t.forks[1]])
    }

    /** Rewinding a decision must land the child back on the fork they answered —
     *  this is what "try the other path" relies on. */
    @Test fun buildTimeline_rewindingLandsOnTheForkJustAnswered() {
        val story = sampleStory()
        val chosen = walk(story, takeA = true)
        assertEquals(2, chosen.size)

        val rewoundOnce = chosen.dropLast(1)
        val t1 = buildTimeline(story, rewoundOnce)
        assertEquals(chosen[0].problem, t1.pages[t1.forks[rewoundOnce.size]])

        val rewoundTwice = emptyList<Branch>()
        val t2 = buildTimeline(story, rewoundTwice)
        assertEquals(story.problem, t2.pages[t2.forks[rewoundTwice.size]])
    }

    @Test fun buildTimeline_pathsDivergeBetweenBranches() {
        val story = sampleStory()
        val a = buildTimeline(story, walk(story, takeA = true)).pages
        val b = buildTimeline(story, walk(story, takeA = false)).pages
        assertFalse("A and B should not read identically", a == b)
    }

    // --- remainingFrom ------------------------------------------------------

    @Test fun remainingFrom_isZeroWhenThereIsNoPendingFork() {
        assertEquals(0, remainingFrom(null))
        val leaf = Branch("🎁", "Share the magic", listOf("one", "two"))
        assertEquals(0, remainingFrom(leaf))
    }

    @Test fun remainingFrom_countsTheBranchPagesPlusItsNestedFork() {
        val story = sampleStory()
        // Down choiceA: its own pages, then 1 problem page, then the sub-branch's.
        val expected = story.choiceA.pages.size + 1 + story.choiceA.choiceA!!.pages.size
        assertEquals(expected, remainingFrom(story))
    }

    @Test fun remainingFrom_handlesASingleForkStory() {
        // An AI story whose second fork was dropped is still valid — see the
        // sanitizer note in StoryEngine.
        val single = Story(
            pre = listOf("a", "b", "c"),
            problem = "trouble?",
            choiceA = Branch("✨", "Use it", listOf("solved", "yay", "The End!")),
            choiceB = Branch("🤝", "Call friends", listOf("solved", "yay", "The End!")),
        )
        assertEquals(3, remainingFrom(single))
        val t = buildTimeline(single, listOf(single.choiceA))
        assertEquals(listOf(3), t.forks) // no second fork appears
        assertEquals(7, t.pages.size)
    }
}
