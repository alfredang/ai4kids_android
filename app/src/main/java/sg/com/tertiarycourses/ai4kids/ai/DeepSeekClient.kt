package sg.com.tertiarycourses.ai4kids.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Tiny client for the DeepSeek chat API that powers the **AI Workshops**.
 *
 * There is **no developer key**: every call uses the parent's own DeepSeek API
 * key, entered behind the parent PIN and kept in the Keystore-encrypted
 * `ParentAuth` store. Only the workshop's system prompt and the child's typed
 * turns (already screened by [KidSafety]) are sent. History is in-memory only.
 * Blocking OkHttp calls run on [Dispatchers.IO]. Mirrors the iOS `DeepSeekClient`.
 */
object DeepSeekClient {

    private const val BASE = "https://api.deepseek.com"
    const val MODEL = "deepseek-flash"
    /** Most recent turns sent with each request. */
    const val MAX_HISTORY_TURNS = 10

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    /** One chat turn. [fromKid] = the child's message; otherwise Sunny's reply. */
    data class Turn(val fromKid: Boolean, val text: String)

    /** Result of checking a key with `GET /models`. */
    sealed interface KeyCheck {
        data object Ok : KeyCheck
        data object Unauthorized : KeyCheck
        data class NetworkError(val detail: String) : KeyCheck
        data class HttpError(val code: Int) : KeyCheck
    }

    /** Result of a chat completion. */
    sealed interface Reply {
        data class Ok(val text: String) : Reply
        /** 401 — the parent's key was rejected (revoked/replaced). */
        data object Unauthorized : Reply
        /** 402 — the DeepSeek account is out of credit. */
        data object OutOfCredit : Reply
        /** Network trouble, timeouts, rate limits, 5xx or an unreadable body. */
        data class Failed(val detail: String) : Reply
    }

    /** Light sanity check before calling the network. */
    fun looksLikeKey(key: String): Boolean {
        val k = key.trim()
        return k.startsWith("sk-") && k.length >= 12 && k.none { it.isWhitespace() }
    }

    /** Validate [key] with `GET https://api.deepseek.com/models`. */
    suspend fun checkKey(key: String): KeyCheck = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/models")
            .header("Authorization", "Bearer ${key.trim()}")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> KeyCheck.Ok
                    resp.code == 401 || resp.code == 403 -> KeyCheck.Unauthorized
                    else -> KeyCheck.HttpError(resp.code)
                }
            }
        } catch (e: IOException) {
            KeyCheck.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Ask DeepSeek for Sunny's next reply. [history] is the in-memory
     * conversation (newest last); only the last [MAX_HISTORY_TURNS] are sent.
     */
    suspend fun chat(key: String, system: String, history: List<Turn>): Reply = withContext(Dispatchers.IO) {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
            messages.put(
                JSONObject()
                    .put("role", if (turn.fromKid) "user" else "assistant")
                    .put("content", turn.text),
            )
        }
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("max_tokens", 700)
            .put("temperature", 0.8)
            .put("stream", false)

        val request = Request.Builder()
            .url("$BASE/chat/completions")
            .header("Authorization", "Bearer ${key.trim()}")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 || resp.code == 403 -> Reply.Unauthorized
                    resp.code == 402 -> Reply.OutOfCredit
                    !resp.isSuccessful -> Reply.Failed("HTTP ${resp.code}")
                    else -> runCatching {
                        JSONObject(text)
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                    }.getOrNull()?.let { Reply.Ok(it) } ?: Reply.Failed("empty reply")
                }
            }
        } catch (e: IOException) {
            Reply.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
