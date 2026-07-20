package sg.com.tertiarycourses.ai4kids.ai

import android.util.Base64
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
 * Primary image provider for the AI Art Studio: NVIDIA NIM FLUX.1-dev
 * (build.nvidia.com). Free tier, no billing required — which is why it replaced
 * Nano Banana (Gemini image gen, which needs billing) as the primary. The key
 * comes from `BuildConfig` (set in git-ignored local.properties); if it's blank
 * this provider is skipped and the caller falls back to Cloudflare Flux. Mirrors
 * the website's `kid-image.ts` NVIDIA path.
 */
object NvidiaClient {

    private const val ENDPOINT = "https://ai.api.nvidia.com/v1/genai/black-forest-labs/flux.1-dev"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Blank-frame threshold for a **1024²** result: below this, a decoded image is
     *  a solid/blank frame (a filtered or refused generation), not a real picture —
     *  a genuine one is tens of KB up. 20 KB leaves headroom for a legitimately
     *  simple cartoon while still catching the ~6 KB blank frames FLUX returns when
     *  its safety filter trips. Scaled by area for smaller (faster) sizes. */
    private const val MIN_REAL_IMAGE_BYTES_1024 = 20_000

    // Image generation is slow — a long read timeout, matching CloudflareClient.
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Debug-only diagnostic logging — a no-op in release builds. */
    private fun dlog(msg: String) { if (BuildConfig.DEBUG) android.util.Log.w("NvidiaClient", msg) }

    /** True when an API key is present. */
    fun isConfigured(): Boolean = BuildConfig.NVIDIA_API_KEY.isNotBlank()

    /**
     * Generate an image from an (already safety-checked) [fullPrompt] via FLUX.1-dev.
     * Returns the raw image bytes, or null on missing key / any error.
     *
     * [steps] and [size] trade quality for speed: FLUX.1-dev is a ~30-step model,
     * so the Art Studio "hero" picture uses the full defaults, while inline story
     * illustrations pass a smaller, fewer-step profile — each pass and each pixel is
     * time, and the free tier paints one image at a time. (FLUX.1-dev isn't the
     * distilled schnell model, so don't drop [steps] to single digits — the result
     * degrades; ~16 is the sensible floor.)
     *
     * The NVIDIA response shape is **unverified** (the web's `kid-image.ts` NVIDIA
     * path was never exercised either), so we don't trust a single field: we scan
     * the JSON for the first plausible base64 image string ([extractImageB64]) and
     * fall back to the raw body if it's already image bytes. On a debug build the
     * whole response is logged (see [dlog]) so the real shape can be confirmed.
     */
    suspend fun generateImage(fullPrompt: String, steps: Int = 30, size: Int = 1024, seed: Int? = null): ByteArray? = withContext(Dispatchers.IO) {
        val key = BuildConfig.NVIDIA_API_KEY
        if (key.isBlank()) return@withContext null

        val body = JSONObject()
            .put("prompt", fullPrompt)
            .put("mode", "base")
            .put("cfg_scale", 5)
            .put("width", size)
            .put("height", size)
            .put("steps", steps)
            // A fixed seed reproduces a matching look for the same prompt+anchor, so
            // a story's hero stays consistent page to page.
            .apply { if (seed != null) put("seed", seed) }

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val bytes = resp.body?.bytes()
                val text = bytes?.toString(Charsets.UTF_8).orEmpty()
                if (!resp.isSuccessful) {
                    dlog("HTTP ${resp.code}: ${text.take(500)}")
                    return@use null
                }
                // A 2xx can still be a poll/redirect envelope rather than an image
                // (NVIDIA returns 202 for large async results) — so never assume.
                dlog("HTTP ${resp.code}, ${bytes?.size ?: 0} body bytes, ct=${resp.header("Content-Type")}")

                val ct = resp.header("Content-Type").orEmpty()
                if (ct.startsWith("image/") && bytes != null) {
                    dlog("body is a raw ${ct} image")
                    return@use bytes
                }

                // Log the response metadata (every field except the base64 blobs),
                // so a blank/filtered result shows its reason — FLUX reports e.g.
                // finishReason: "CONTENT_FILTERED" here rather than erroring.
                dlog("meta: ${describeJson(text)}")

                val b64 = extractImageB64(text)
                if (b64 == null) return@use null
                val decoded = runCatching { Base64.decode(b64.trim(), Base64.DEFAULT) }.getOrNull()
                dlog("decoded ${decoded?.size ?: 0} bytes, magic=${magic(decoded)}")
                // A few-KB image is a blank frame (a filtered or refused generation),
                // not a picture — treat it as a miss so the caller falls back / shows
                // the kind "try again" notice. The threshold scales with area so a
                // smaller fast-profile image isn't wrongly judged blank.
                val minBytes = (MIN_REAL_IMAGE_BYTES_1024.toLong() * size * size / (1024L * 1024L))
                    .toInt().coerceAtLeast(8_000)
                if (decoded != null && decoded.size < minBytes) {
                    dlog("image only ${decoded.size} bytes (<$minBytes) — looks blank/filtered, discarding")
                    return@use null
                }
                decoded
            }
        }.getOrNull()
    }

    /**
     * Pull the image out of an unverified response. Handles the shapes NVIDIA /
     * FLUX-style endpoints are known to use — `artifacts[0].base64`, a top-level
     * `image`/`b64_json`, or `data[0].b64_json` — and strips a `data:` URL prefix
     * if one is present. Returns null if none match.
     */
    private fun extractImageB64(text: String): String? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val raw = root.optJSONArray("artifacts")?.optJSONObject(0)?.optString("base64")?.takeIf { it.isNotEmpty() }
            ?: root.optString("image").takeIf { it.isNotEmpty() }
            ?: root.optString("b64_json").takeIf { it.isNotEmpty() }
            ?: root.optJSONArray("data")?.optJSONObject(0)?.optString("b64_json")?.takeIf { it.isNotEmpty() }
            ?: return null
        // Drop a "data:image/png;base64," prefix if the field is a data URL.
        return raw.substringAfter("base64,", raw)
    }

    /** The response with every long base64 string replaced by `<b64:len>`, so the
     *  metadata (e.g. `finishReason`) is legible in a log without a multi-megabyte
     *  blob. Recurses through objects and arrays. */
    private fun describeJson(text: String): String {
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return "not JSON: ${text.take(200)}"
        return redact(root).toString().take(600)
    }

    private fun redact(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject().also { out ->
            value.keys().forEach { k -> out.put(k, redact(value.opt(k))) }
        }
        is JSONArray -> JSONArray().also { out ->
            for (i in 0 until value.length()) out.put(redact(value.opt(i)))
        }
        // Long strings are the base64 image payloads — replace with just the length.
        is String -> if (value.length > 120) "<b64:${value.length}>" else value
        else -> value
    }

    /** First two bytes as hex, to confirm a real image (JPEG=ffd8, PNG=8950). */
    private fun magic(b: ByteArray?): String =
        if (b == null || b.size < 2) "none" else "%02x%02x".format(b[0], b[1])
}
