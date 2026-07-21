package sg.com.tertiarycourses.ai4kids.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Orchestrates the AI Art Studio: safety-gate the child's idea, then generate a
 * kid-safe image. The provider chain is ordered fastest-first (Cloudflare's
 * 4-step schnell, then NVIDIA FLUX.1-dev as the higher-fidelity fallback) — these
 * illustrate a kids' story page by page, so speed beats fidelity. A Kotlin port of
 * the website's `/api/learn/art` route + `kid-image.ts`. Keys never touch the
 * child — they live in BuildConfig (see the clients).
 */
object ArtEngine {

    /** Allowlisted art styles: key → the phrase templated into the model prompt. */
    val STYLES: Map<String, String> = linkedMapOf(
        "cartoon" to "fun bright cartoon style",
        "watercolor" to "soft watercolour painting style",
        "pixel" to "colourful retro pixel-art style",
        "crayon" to "playful child's crayon-drawing style",
        "scifi" to "friendly colourful sci-fi illustration style",
    )

    /** A pickable style chip for the UI. */
    data class StyleChip(val key: String, val label: String, val emoji: String)

    val STYLE_CHIPS = listOf(
        StyleChip("cartoon", "Cartoon", "✏️"),
        StyleChip("watercolor", "Watercolour", "🎨"),
        StyleChip("pixel", "Pixel", "👾"),
        StyleChip("crayon", "Crayon", "🖍️"),
        StyleChip("scifi", "Sci-Fi", "🚀"),
    )

    val IDEAS = listOf(
        "a friendly dragon having a picnic",
        "a cat astronaut on the moon",
        "a robot painting a rainbow",
        "a magical underwater castle",
    )

    /** The outcome of a generation request. */
    sealed interface Result {
        data class Success(val bitmap: Bitmap) : Result
        /** The idea was blocked by the safety gate — show [message], keep it kind. */
        data class Blocked(val message: String) : Result
        /** No provider could paint it right now — show [message]. */
        data class Unavailable(val message: String) : Result
    }

    /** True when at least one image provider is configured. */
    fun isConfigured(): Boolean = NvidiaClient.isConfigured() || CloudflareClient.isConfigured()

    /** Emoji (astral pictographs) + variation selectors / ZWJ. Stripped from every
     *  prompt: the story prose and hero anchor carry emoji (🦊🗝️✨) that contradict
     *  the no-text rule below, and the schnell model tends to render them as literal
     *  glyphs. */
    private val EMOJI = Regex("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]|[\\uFE0F\\u200D]")
    private fun stripEmoji(s: String): String = EMOJI.replace(s, "").replace(Regex("\\s+"), " ").trim()

    private fun buildPrompt(prompt: String, styleKey: String, characterAnchor: String? = null): String {
        val styleHint = STYLES[styleKey] ?: STYLES.getValue("cartoon")
        // When a story illustrates page after page, anchor the recurring hero so it
        // reads as the SAME character each time. Pure txt2img (no reference image)
        // can't guarantee identity, so lean on the two levers it has: an identical
        // anchor clause placed FIRST — Flux weights the leading tokens most, and the
        // varying scene would otherwise dominate — plus the same seed across pages.
        val anchor = characterAnchor?.let { stripEmoji(it) }
        val consistency = if (!anchor.isNullOrEmpty()) {
            "The main character is always $anchor — drawn in the exact same way in every picture: same colours, same design, same friendly face. "
        } else {
            ""
        }
        // Positive framing ONLY. Naming the banned concepts — even to forbid them
        // ("nothing scary, violent or unsafe") — trips FLUX.1-dev's keyword safety
        // filter, which then returns a blank frame (finishReason CONTENT_FILTERED).
        // The child's idea is already vetted by classifyDrawing, so the wrapper just
        // steers toward a wholesome look without ever naming what to avoid.
        return consistency +
            "A $styleHint picture of: ${stripEmoji(prompt)}. " +
            "Child-friendly, wholesome, cheerful, gentle and sweet, with no text or words in the image. " +
            "Suitable for young children."
    }

    /** NVIDIA FLUX.1-dev settings for an inline story illustration. NVIDIA is now
     *  the *fallback* (Cloudflare schnell paints first when configured), so this
     *  favours quality over raw speed: full 1024² and 20 steps — fewer than the
     *  Art Studio hero's 30, but enough to avoid the soft, smeared look 16 steps at
     *  768² produced. */
    private const val FAST_STEPS = 20
    private const val FAST_SIZE = 1024

    /**
     * Paint an **already-safe** [prompt] in [styleKey] — the provider chain
     * (NVIDIA → Cloudflare) plus decode, with no safety gate. Returns null if no
     * provider could paint it, or the result was a filtered/blank frame (see
     * `NvidiaClient`'s size guard).
     *
     * [fast] trades quality for speed (smaller, fewer FLUX steps) — used for Story
     * Builder's many small inline illustrations, where the wait matters more than
     * fidelity. Art Studio leaves it off: its picture is the centrepiece a child
     * turns into a jigsaw, so it gets the full-quality defaults.
     *
     * [seed], when set, pins the generation so the same prompt + character anchor
     * reproduces a matching look — how a story keeps one hero consistent across all
     * its pages. Passed to whichever provider paints it. [characterAnchor], when
     * set, is the recurring hero woven into the prompt so it stays the same page to
     * page (see [buildPrompt]).
     *
     * Used for Story Builder scene/page illustrations, where the idea was already
     * gated by `classifyStoryIdea` upstream; [generate] adds the gate for Art
     * Studio, where the child's raw prompt hasn't been checked yet.
     */
    suspend fun paint(prompt: String, styleKey: String, fast: Boolean = false, seed: Int? = null, characterAnchor: String? = null): Bitmap? {
        val full = buildPrompt(prompt, styleKey, characterAnchor)
        // Fast path first: Cloudflare's 4-step schnell answers in a few seconds. Only
        // fall back to the slower NVIDIA FLUX.1-dev when it's unconfigured or errors.
        val bytes = CloudflareClient.generateImage(full, seed = seed)
            ?: (if (fast) NvidiaClient.generateImage(full, steps = FAST_STEPS, size = FAST_SIZE, seed = seed)
            else NvidiaClient.generateImage(full, seed = seed))
            ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    /**
     * Safety-check the [prompt], then [paint] it in [styleKey]. Fails safe (uses
     * the raw prompt) if the classifier is unavailable — the prompt template still
     * enforces wholesome output.
     */
    suspend fun generate(prompt: String, styleKey: String): Result {
        val check = GeminiClient.classifyDrawing(prompt)
        if (check != null && !check.first) {
            return Result.Blocked(
                "Let's try a different idea! How about something fun like a friendly dragon or a space cat? 🚀",
            )
        }
        val cleaned = check?.second?.takeIf { it.isNotBlank() } ?: prompt
        val bitmap = paint(cleaned, styleKey)
            ?: return Result.Unavailable("Our art robot is taking a nap 😴 — please try again in a moment!")
        return Result.Success(bitmap)
    }
}
