package sg.com.tertiarycourses.ai4kids.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import sg.com.tertiarycourses.ai4kids.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Free-tier image fallback for the AI Art Studio, via Cloudflare Workers AI
 * (Flux-1-schnell). Used when Gemini's image model has no quota. Credentials come
 * from `BuildConfig` (set in local.properties); if either is blank the fallback
 * is simply skipped and the caller degrades gracefully. Mirrors the website's
 * `gemini-image.ts` Cloudflare path.
 */
object CloudflareClient {

    private const val MODEL = "@cf/black-forest-labs/flux-1-schnell"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Debug-only diagnostic logging — a no-op in release builds. */
    private fun dlog(msg: String) { if (BuildConfig.DEBUG) android.util.Log.w("CloudflareClient", msg) }

    /** True when both the account id and Workers AI token are present. */
    fun isConfigured(): Boolean =
        BuildConfig.CLOUDFLARE_ACCOUNT_ID.isNotBlank() && BuildConfig.CLOUDFLARE_AI_TOKEN.isNotBlank()

    /**
     * Generate an image from an (already safety-checked) [fullPrompt]. Returns the
     * raw JPEG bytes, or null on missing credentials / any error.
     */
    suspend fun generateImage(fullPrompt: String): ByteArray? = withContext(Dispatchers.IO) {
        val account = BuildConfig.CLOUDFLARE_ACCOUNT_ID
        val token = BuildConfig.CLOUDFLARE_AI_TOKEN
        if (account.isBlank() || token.isBlank()) return@withContext null

        val body = JSONObject().put("prompt", fullPrompt).put("steps", 4)
        val request = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/accounts/$account/ai/run/$MODEL")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON))
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) { dlog("HTTP ${resp.code}: ${text.take(300)}"); return@use null }
                val b64 = JSONObject(text).optJSONObject("result")?.optString("image")
                if (b64.isNullOrEmpty()) null else Base64.decode(b64, Base64.DEFAULT)
            }
        }.getOrNull()
    }
}
