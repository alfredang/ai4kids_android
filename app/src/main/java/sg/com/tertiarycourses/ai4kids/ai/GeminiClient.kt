package sg.com.tertiarycourses.ai4kids.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import sg.com.tertiarycourses.ai4kids.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Tiny client for Google's Gemini API, used to power the Phonics "Buddy" — short,
 * kid-friendly hints and praise. The API key comes from `BuildConfig` (set via
 * local.properties), never hard-coded. If no key is configured or the call
 * fails, callers fall back gracefully and the games still work offline.
 */
object GeminiClient {

    /** Default text model — capable enough for stories and the safety classifier. */
    private const val MODEL = "gemini-2.5-flash"
    /** Smaller, cheaper, higher free-quota model — used for low-stakes chat (Buddy, Phonics). */
    const val FLASH_LITE = "gemini-2.5-flash-lite"

    private fun endpointFor(model: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** True when an API key is present, so the UI can show the AI affordances. */
    fun isConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /**
     * Generate a short reply for [prompt]. Returns the trimmed text, or null on
     * any failure (no key, no network, bad response) so callers degrade safely.
     */
    suspend fun generate(prompt: String, maxTokens: Int = 120, model: String = MODEL): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) return@withContext null

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("maxOutputTokens", maxTokens)
                    .put("temperature", 0.9)
                    // Disable "thinking" (default-on for 2.5-flash) so these short
                    // replies aren't truncated by thinking eating the token budget.
                    .put("thinkingConfig", JSONObject().put("thinkingBudget", 0)),
            )

        val request = Request.Builder()
            .url(endpointFor(model))
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(JSON))
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                dlog("HTTP ${resp.code} (${text.length} bytes)")
                if (!resp.isSuccessful) { dlog("body: ${text.take(400)}"); return@use null }
                JSONObject(text)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.onFailure { dlog("call threw: ${it.message}", it) }.getOrNull()
    }

    /**
     * Generate a structured JSON reply for [prompt] (e.g. a multi-page Story
     * Builder tale). Forces `application/json` output, disables "thinking" so the
     * token budget goes to the story, and applies strict kid-safety filters.
     * Returns the raw JSON text, or null on any failure so callers fall back.
     */
    suspend fun generateJson(prompt: String, maxTokens: Int = 1200, model: String = MODEL): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) return@withContext null

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("maxOutputTokens", maxTokens)
                    .put("temperature", 1.0)
                    .put("responseMimeType", "application/json")
                    .put("thinkingConfig", JSONObject().put("thinkingBudget", 0)),
            )
            .put(
                "safetySettings",
                JSONArray()
                    .put(safety("HARM_CATEGORY_HARASSMENT"))
                    .put(safety("HARM_CATEGORY_HATE_SPEECH"))
                    .put(safety("HARM_CATEGORY_SEXUALLY_EXPLICIT"))
                    .put(safety("HARM_CATEGORY_DANGEROUS_CONTENT")),
            )

        val request = Request.Builder()
            .url(endpointFor(model))
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(JSON))
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                dlog("HTTP ${resp.code} (${text.length} bytes)")
                if (!resp.isSuccessful) { dlog("body: ${text.take(400)}"); return@use null }
                JSONObject(text)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.onFailure { dlog("call threw: ${it.message}", it) }.getOrNull()
    }

    /**
     * Kid-safe chat reply for the Talking Buddy. Sends a [system] instruction plus
     * the recent [conversation] (already formatted as "Child: … / Buddy: …"), with
     * strict safety filters and "thinking" disabled so the short reply isn't cut off.
     * Returns the trimmed text, or null on any failure so the caller can fall back.
     */
    suspend fun generateReply(
        system: String,
        conversation: String,
        maxTokens: Int = 256,
        model: String = MODEL,
    ): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) return@withContext null

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", conversation))),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("maxOutputTokens", maxTokens)
                    .put("temperature", 0.9)
                    .put("thinkingConfig", JSONObject().put("thinkingBudget", 0)),
            )
            .put(
                "safetySettings",
                JSONArray()
                    .put(safety("HARM_CATEGORY_HARASSMENT"))
                    .put(safety("HARM_CATEGORY_HATE_SPEECH"))
                    .put(safety("HARM_CATEGORY_SEXUALLY_EXPLICIT"))
                    .put(safety("HARM_CATEGORY_DANGEROUS_CONTENT")),
            )

        val request = Request.Builder()
            .url(endpointFor(model))
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(JSON))
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                dlog("HTTP ${resp.code} (${text.length} bytes)")
                if (!resp.isSuccessful) { dlog("body: ${text.take(400)}"); return@use null }
                JSONObject(text)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.onFailure { dlog("call threw: ${it.message}", it) }.getOrNull()
    }

    /**
     * Shared plumbing for the kid-safety classifiers below. [ask] must instruct the
     * model to return `{"safe": boolean, "cleanedPrompt": string}`; each caller
     * words its own question, since "is this a wholesome *drawing*" and "…*story*"
     * are judged differently. Returns (safe, cleanedPrompt), or null if the
     * classifier is unavailable (callers then fail safe by using the raw prompt).
     */
    private suspend fun classifySafe(ask: String, prompt: String): Pair<Boolean, String>? {
        val raw = generateJson(ask, maxTokens = 200) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            json.getBoolean("safe") to json.optString("cleanedPrompt").trim().ifEmpty { prompt }
        }.onFailure { dlog("call threw: ${it.message}", it) }.getOrNull()
    }

    /** Kid-safety classifier: is a child's drawing idea wholesome? */
    suspend fun classifyDrawing(prompt: String): Pair<Boolean, String>? = classifySafe(
        """
            A child wants to make a picture of: "$prompt".
            Decide if this is wholesome and appropriate for a young child's drawing.
            Return ONLY JSON: {"safe": boolean, "cleanedPrompt": "a tidied, kid-friendly version of the idea"}.
            Mark unsafe anything violent, scary, sexual, hateful, or otherwise not for kids.
        """.trimIndent(),
        prompt,
    )

    /**
     * Kid-safety classifier: is a child's story idea wholesome?
     *
     * Story Builder's "Write your own" is the only place a child's *free text*
     * leaves the device for a story (Build mode sends only the ingredient picks),
     * so it is gated before anything is generated from it. The web route has no
     * equivalent gate — it leans on the model's own safety — so this is Android
     * being deliberately stricter.
     */
    suspend fun classifyStoryIdea(prompt: String): Pair<Boolean, String>? = classifySafe(
        """
            A child wants a story about: "$prompt".
            Decide if this is wholesome and appropriate for a young child's story.
            Return ONLY JSON: {"safe": boolean, "cleanedPrompt": "a tidied, kid-friendly version of the idea"}.
            Mark unsafe anything violent, scary, sexual, hateful, or otherwise not for kids.
        """.trimIndent(),
        prompt,
    )

    private fun safety(category: String): JSONObject =
        JSONObject().put("category", category).put("threshold", "BLOCK_LOW_AND_ABOVE")

    /** Debug-only diagnostic logging — compiled to a no-op in release builds so
     *  API error bodies never reach a shipped device's logcat. */
    private fun dlog(msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) android.util.Log.w("GeminiClient", msg, t)
    }
}
