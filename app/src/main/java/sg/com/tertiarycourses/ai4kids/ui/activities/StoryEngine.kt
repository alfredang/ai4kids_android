package sg.com.tertiarycourses.ai4kids.ui.activities

/**
 * Pure story logic for the Story Builder — deliberately free of Compose/Android
 * imports so it is unit-testable with plain JUnit (see `StoryEngineTest`).
 * [StoryBuilderScreen] drives this model and renders it.
 *
 * The child picks a hero, place, magic item and mood; [buildStory] weaves a short
 * branching tale from randomized beats, so the same picks read differently each
 * time. Mirrors the web app's `src/lib/story-builder/templates.ts`, which is the
 * source of truth for the ingredients and the story shape.
 *
 * The tale forks TWICE — first how to solve the trouble, then what to do with the
 * magic afterwards — so there are four possible endings and a replay reads
 * differently. The second fork is OPTIONAL on [Branch]: an AI story that omits or
 * malforms it still plays as a shorter single-fork tale instead of being thrown
 * away.
 */

internal data class Choice(val emoji: String, val name: String)

internal val HEROES = listOf(
    Choice("🦊", "Fox"), Choice("🐉", "Dragon"),
    Choice("🤖", "Robot"), Choice("🦄", "Unicorn"),
    Choice("🐼", "Panda"), Choice("🦉", "Owl"),
    Choice("🐢", "Turtle"), Choice("🐱", "Kitten"),
)
internal val PLACES = listOf(
    Choice("🏰", "castle"), Choice("🌋", "volcano"),
    Choice("🌌", "galaxy"), Choice("🏝️", "island"),
    Choice("🌲", "forest"), Choice("💧", "waterfall"),
    Choice("🏔️", "snowy peak"), Choice("🪸", "coral reef"),
)
internal val OBJECTS = listOf(
    Choice("🗝️", "golden key"), Choice("🔮", "magic orb"),
    Choice("🎈", "balloon"), Choice("📕", "spell book"),
    Choice("🏮", "lantern"), Choice("🧭", "compass"),
    Choice("🪶", "feather"), Choice("🎶", "music box"),
)
// A mood/trait is threaded through the prose so the same hero can feel brave one
// time and silly the next — changing the whole tone of the story.
internal val MOODS = listOf(
    Choice("🦁", "brave"), Choice("🤪", "silly"),
    Choice("😴", "sleepy"), Choice("🤔", "curious"),
    Choice("💖", "kind"), Choice("🧠", "clever"),
    Choice("😄", "cheerful"), Choice("🙈", "shy"),
)

/** "a" vs "an" — PLACES has `island`, so a naive "a $place" reads wrong. */
internal fun an(word: String): String =
    if (word.firstOrNull()?.lowercaseChar() in listOf('a', 'e', 'i', 'o', 'u')) "an" else "a"

/** A node that can pose a fork — the story root, or any branch with a follow-up. */
internal interface ForkNode {
    val problem: String?
    val choiceA: Branch?
    val choiceB: Branch?
}

/** One way the child can solve a problem. [pages] are read straight after the
 *  pick. [problem] + [choiceA]/[choiceB], when present, pose a follow-up fork —
 *  they're optional so a story with only one fork is still valid and playable. */
internal data class Branch(
    val emoji: String,
    val label: String,
    val pages: List<String>,
    override val problem: String? = null,
    override val choiceA: Branch? = null,
    override val choiceB: Branch? = null,
) : ForkNode

/**
 * A branching story woven from the picks. [pre] are the pages read before the
 * first fork; [problem] is that fork, where the child picks [choiceA] or
 * [choiceB]. Every beat has several phrasings chosen at random, so the same picks
 * read differently each time — that, plus the choices, is what keeps the builder
 * from feeling repetitive.
 */
internal data class Story(
    val pre: List<String>,
    override val problem: String,
    override val choiceA: Branch,
    override val choiceB: Branch,
) : ForkNode

private fun celebrations(h: Choice, p: Choice, o: Choice): List<String> = listOf(
    "Everyone cheered for the ${h.name} ${h.emoji}! The ${p.name} ${p.emoji} sparkled brighter than ever. ✨",
    "What a day! The ${h.name} ${h.emoji} laughed and danced with all the new friends. 🎶",
    "The ${o.name} ${o.emoji} hummed a happy tune, and the whole ${p.name} ${p.emoji} joined in. 🎵",
    "Hooray! The ${h.name} ${h.emoji} jumped for joy as the ${p.name} ${p.emoji} filled with giggles. 😄",
    "Confetti swirled through the ${p.name} ${p.emoji} as everyone thanked the ${h.name} ${h.emoji}. 🎊",
    "The ${o.name} ${o.emoji} glittered happily, and the ${p.name} ${p.emoji} felt warm and bright. 🌟",
    "Every creature in the ${p.name} ${p.emoji} clapped and cheered for the little ${h.name} ${h.emoji}. 👏",
    "The ${h.name} ${h.emoji} took a bow, and the ${p.name} ${p.emoji} rang with happy laughter. 🥳",
)

private fun endings(h: Choice, p: Choice, m: Choice): List<String> = listOf(
    "With a happy heart, the ${m.name} ${h.name} ${h.emoji} shared the magic with every friend. The End! 🎉",
    "And so the ${h.name} ${h.emoji} and all the friends celebrated together. The End! 🎉",
    "From that day on, the ${p.name} ${p.emoji} was the happiest place of all. The End! 🎉",
    "And the ${m.name} ${h.name} ${h.emoji} went home with the best story to tell. The End! 🎉",
    "Tucked in that night, the ${h.name} ${h.emoji} smiled, dreaming of new adventures. The End! 🌙",
    "Forever after, the ${h.name} ${h.emoji} and the ${p.name} ${p.emoji} were the best of friends. The End! 🎉",
    "The stars came out over the ${p.name} ${p.emoji}, and the ${h.name} ${h.emoji} yawned a happy yawn. The End! 🌙",
    "And every story after that one started right here, in the ${p.name} ${p.emoji}. The End! 📖",
)

/**
 * Graft on the second fork: after the trouble is solved, what to do with the
 * magic. A different *kind* of decision than the first, so it doesn't read as a
 * repeat.
 *
 * Grafted rather than asked of the model, matching the web: measured there, a
 * nested prompt costs ~58s (median) vs ~22s for the first act alone, which is far
 * too long a wait for a child. The AI writes the first act; this supplies the
 * second — same ingredients, so it reads consistently — for four endings at no
 * latency cost.
 */
internal fun Branch.withSecondFork(h: Choice, p: Choice, o: Choice, m: Choice): Branch {
    val twist = listOf(
        "Just then, the ${o.name} ${o.emoji} began to glow — one last sparkle of magic was left inside!",
        "On the way home, the ${h.name} ${h.emoji} spotted a tiny door tucked into the ${p.name} ${p.emoji}.",
        "Then the ${o.name} ${o.emoji} gave a soft hum, as if it had one more secret to share.",
        "As the sun dipped low, the ${p.name} ${p.emoji} filled with a warm golden glow.",
        "Just then, a friendly breeze carried a faraway giggle across the ${p.name} ${p.emoji}.",
        "Suddenly the ${o.name} ${o.emoji} felt warm, and a little map shimmered across the ${p.name} ${p.emoji}.",
    ).random()
    val celebration = celebrations(h, p, o)
    val ending = endings(h, p, m)

    return copy(
        problem = "$twist\nWhat should the ${h.name} ${h.emoji} do now?",
        // Branch A — be generous with the magic.
        choiceA = Branch(
            emoji = "🎁",
            label = "Share the magic",
            pages = listOf(
                listOf(
                    "\"This belongs to all of us!\" said the ${h.name} ${h.emoji}, passing the ${o.name} ${o.emoji} around the ${p.name} ${p.emoji}. 💛",
                    "The ${m.name} ${h.name} ${h.emoji} shared the last of the magic, and every friend got a little sparkle of their own. 💛",
                    "One by one, the ${h.name} ${h.emoji} gave everyone a turn with the ${o.name} ${o.emoji}. 💛",
                ).random(),
                celebration.random(),
                ending.random(),
            ),
        ),
        // Branch B — keep the adventure going.
        choiceB = Branch(
            emoji = "🗺️",
            label = "Go exploring",
            pages = listOf(
                listOf(
                    "The ${h.name} ${h.emoji} held the ${o.name} ${o.emoji} high and set off to see what else the ${p.name} ${p.emoji} was hiding! 🗺️",
                    "Off went the ${m.name} ${h.name} ${h.emoji}, following the glow to a secret corner of the ${p.name} ${p.emoji}. 🗺️",
                    "\"Let's find out!\" cheered the ${h.name} ${h.emoji}, and the whole ${p.name} ${p.emoji} came along. 🗺️",
                ).random(),
                celebration.random(),
                ending.random(),
            ),
        ),
    )
}

internal fun buildStory(h: Choice, p: Choice, o: Choice, m: Choice): Story {
    val opening = listOf(
        "Once upon a time, ${an(m.name)} ${m.name} ${h.name} ${h.emoji} lived near ${an(p.name)} ${p.name} ${p.emoji}.",
        "Long ago, in a faraway ${p.name} ${p.emoji}, there lived a ${m.name} little ${h.name} ${h.emoji}.",
        "Every morning, ${an(m.name)} ${m.name} ${h.name} ${h.emoji} woke up right beside ${an(p.name)} ${p.name} ${p.emoji}.",
        "In a cozy corner of the ${p.name} ${p.emoji}, ${an(m.name)} ${m.name} ${h.name} ${h.emoji} was just waking up.",
        "There once was ${an(m.name)} ${m.name} ${h.name} ${h.emoji} who loved the ${p.name} ${p.emoji} more than anywhere else.",
        "Far past the clouds, ${an(m.name)} ${m.name} ${h.name} ${h.emoji} made a home by ${an(p.name)} ${p.name} ${p.emoji}.",
        "Where the sun rose over ${an(p.name)} ${p.name} ${p.emoji}, ${an(m.name)} ${m.name} ${h.name} ${h.emoji} was humming a little tune.",
        "Nobody in the ${p.name} ${p.emoji} was quite as ${m.name} as one small ${h.name} ${h.emoji}.",
    ).random()
    val discovery = listOf(
        "One sunny day, the ${h.name} found ${an(o.name)} ${o.name} ${o.emoji} hidden in the tall grass!",
        "While exploring the ${p.name}, the ${h.name} ${h.emoji} spotted ${an(o.name)} ${o.name} ${o.emoji}!",
        "Then, with a twinkle, ${an(o.name)} ${o.name} ${o.emoji} appeared right in front of the ${h.name}!",
        "As the ${h.name} ${h.emoji} skipped along, a shiny ${o.name} ${o.emoji} caught the light!",
        "Tucked under an old tree, the ${h.name} ${h.emoji} discovered ${an(o.name)} ${o.name} ${o.emoji}.",
        "What's this? The ${h.name} ${h.emoji} had never seen ${an(o.name)} ${o.name} ${o.emoji} quite like it before.",
        "Something sparkled in the shadows — ${an(o.name)} ${o.name} ${o.emoji}, waiting to be found!",
        "Right there, half-buried in the ${p.name} ${p.emoji}, lay ${an(o.name)} ${o.name} ${o.emoji}.",
    ).random()
    val journey = listOf(
        "The ${m.name} ${h.name} ${h.emoji} tucked the ${o.name} ${o.emoji} away and set off deep into the ${p.name} ${p.emoji}.",
        "Step by step, the ${h.name} ${h.emoji} wandered further into the ${p.name} ${p.emoji}, the ${o.name} ${o.emoji} glowing softly.",
        "Full of wonder, the ${h.name} ${h.emoji} explored every winding corner of the ${p.name} ${p.emoji}.",
        "Holding the ${o.name} ${o.emoji} close, the ${h.name} ${h.emoji} marched bravely on through the ${p.name} ${p.emoji}.",
        "The ${o.name} ${o.emoji} seemed to point the way, so the ${h.name} ${h.emoji} followed it across the ${p.name} ${p.emoji}.",
        "Humming a happy tune, the ${m.name} ${h.name} ${h.emoji} skipped deeper into the ${p.name} ${p.emoji}.",
        "With the ${o.name} ${o.emoji} safe in hand, the ${h.name} ${h.emoji} tiptoed where nobody had been before.",
        "The ${p.name} ${p.emoji} stretched out wide, and the ${m.name} ${h.name} ${h.emoji} could not wait to see it all.",
    ).random()
    val trouble = listOf(
        "But then — uh oh! A grumpy troll stomped across the ${p.name} ${p.emoji} and blocked the way.",
        "Suddenly a big storm cloud rolled over the ${p.name} ${p.emoji}, and everything went dark.",
        "Just then, a tiny lost cub began to cry at the edge of the ${p.name} ${p.emoji}.",
        "Oh no! A wobbly old bridge over the ${p.name} ${p.emoji} began to creak and sway.",
        "All at once, a thick fog rolled across the ${p.name} ${p.emoji} and hid the path.",
        "Then a sleepy giant snored so loudly that the whole ${p.name} ${p.emoji} shook!",
        "Uh oh — a tangle of vines had grown right across the ${p.name} ${p.emoji} overnight.",
        "Just then, a little bird flapped down, too tired to fly home across the ${p.name} ${p.emoji}.",
    ).random()
    val problem = "$trouble\nWhat should the ${m.name} ${h.name} ${h.emoji} do?"

    return Story(
        pre = listOf(opening, discovery, journey),
        problem = problem,
        // Branch A — be clever and use the magic item.
        choiceA = Branch(
            emoji = o.emoji,
            label = "Use the ${o.name}",
            pages = listOf(
                listOf(
                    "The ${h.name} ${h.emoji} held up the ${o.name} ${o.emoji}. With a bright flash of magic, the trouble melted away! ✨",
                    "Quick as a wink, the ${h.name} ${h.emoji} waved the ${o.name} ${o.emoji} — and poof! the problem was gone. ✨",
                    "The clever ${h.name} ${h.emoji} pointed the ${o.name} ${o.emoji} just right, and everything turned out perfectly! ✨",
                    "One gentle tap of the ${o.name} ${o.emoji}, and the ${p.name} ${p.emoji} was safe and sound again. ✨",
                ).random(),
            ),
        ).withSecondFork(h, p, o, m),
        // Branch B — be kind and call friends for help.
        choiceB = Branch(
            emoji = "🤝",
            label = "Call for friends",
            pages = listOf(
                listOf(
                    "The ${h.name} ${h.emoji} called out for help. Friends came running, and together they fixed everything in no time! 🤝",
                    "The ${h.name} ${h.emoji} whistled, and kind friends arrived to lend a hand. Together, they sorted it out! 🤝",
                    "With a big friendly shout, the ${h.name} ${h.emoji} gathered everyone, and as a team they made it all okay! 🤝",
                    "\"Together!\" cheered the ${h.name} ${h.emoji} — and every friend in the ${p.name} ${p.emoji} pitched in. 🤝",
                ).random(),
            ),
        ).withSecondFork(h, p, o, m),
    )
}

/** The pages read so far along the path the child has chosen, plus the page index
 *  of each fork. `forks[k]` is answered by `chosen[k]`, so the first unanswered
 *  fork is `forks[chosen.size]` — that's where the child is deciding now. */
internal data class Timeline(val pages: List<String>, val forks: List<Int>)

/** Flatten the story along the path chosen so far. The tale forks up to twice, so
 *  the page list grows as the child decides. */
internal fun buildTimeline(story: Story, chosen: List<Branch>): Timeline {
    val pages = mutableListOf<String>()
    pages += story.pre
    pages += story.problem
    val forks = mutableListOf(story.pre.size)
    for (b in chosen) {
        pages += b.pages
        if (b.problem != null && b.choiceA != null && b.choiceB != null) {
            pages += b.problem
            forks += pages.size - 1
        }
    }
    return Timeline(pages, forks)
}

/** Pages still to come if the child keeps picking A — used only to show a total.
 *  Both branches are the same length in generated stories; an AI story may differ
 *  slightly, so this is an estimate (as the single-fork version was too). */
internal fun remainingFrom(node: ForkNode?): Int {
    val b = node?.choiceA ?: return 0
    val nested = if (b.problem != null && b.choiceA != null && b.choiceB != null) 1 + remainingFrom(b) else 0
    return b.pages.size + nested
}
