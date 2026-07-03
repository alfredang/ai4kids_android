package sg.com.tertiarycourses.ai4kids.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Orchestrates the AI Art Studio: safety-gate the child's idea, then generate a
 * kid-safe image (Gemini "Nano Banana" first, Cloudflare Flux as the free-tier
 * fallback). A Kotlin port of the website's `/api/learn/art` route + `gemini-image.ts`.
 * Keys never touch the child — they live in BuildConfig (see the two clients).
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
    fun isConfigured(): Boolean = GeminiClient.isConfigured() || CloudflareClient.isConfigured()

    private fun buildPrompt(prompt: String, styleKey: String): String {
        val styleHint = STYLES[styleKey] ?: STYLES.getValue("cartoon")
        return "A $styleHint picture of: $prompt. " +
            "Child-friendly, wholesome, cheerful, no text or words in the image, " +
            "nothing scary, violent or unsafe. Suitable for young children."
    }

    /**
     * Safety-check the [prompt], then paint it in [styleKey]. Falls back from
     * Gemini to Cloudflare, and fails safe (uses the raw prompt) if the classifier
     * is unavailable — the prompt template still enforces wholesome output.
     */
    suspend fun generate(prompt: String, styleKey: String): Result {
        val check = GeminiClient.classifyDrawing(prompt)
        if (check != null && !check.first) {
            return Result.Blocked(
                "Let's try a different idea! How about something fun like a friendly dragon or a space cat? 🚀",
            )
        }
        val cleaned = check?.second?.takeIf { it.isNotBlank() } ?: prompt
        val full = buildPrompt(cleaned, styleKey)

        val bytes = GeminiClient.generateImage(full) ?: CloudflareClient.generateImage(full)
            ?: return Result.Unavailable("Our art robot is taking a nap 😴 — please try again in a moment!")

        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            ?: return Result.Unavailable("Our art robot smudged that one — please try again! 🖌️")

        return Result.Success(bitmap)
    }
}
