package sg.com.tertiarycourses.ai4kids.cards

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Networked client for the online "Brain Arcade" card games. Talks to the
 * ai4kids Next.js backend:
 *  - NextAuth credentials login (CSRF token → /api/auth/callback/credentials),
 *    persisting the session cookie so the learner stays signed in.
 *  - The five /api/learn/cards/ endpoints (create / join / start / move / sync),
 *    each returning a viewer-redacted [CardState].
 *
 * All calls are blocking; invoke them from a background dispatcher.
 */
object CardApi {

    private const val PREFS = "ai4kids.cards"
    private const val SECURE_PREFS = "ai4kids.cards.secure"
    private const val KEY_BASE = "baseUrl"
    private const val KEY_SESSION_COOKIE = "sessionCookie" // "name\tvalue"
    private const val DEFAULT_BASE = "https://ai4kids.tertiarycourses.com.sg"

    private lateinit var appContext: Context

    /** In-memory cookie store, seeded from prefs (session cookie) on init. */
    private val cookieStore = mutableListOf<Cookie>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                for (c in cookies) {
                    cookieStore.removeAll { it.name == c.name && it.domain == c.domain }
                    cookieStore.add(c)
                    if (c.name.contains("session-token")) persistSessionCookie(c)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookieStore) {
            cookieStore.filter { it.matches(url) }
        }
    }

    // Shared with EscapeApi (co-op escape rooms) so the same NextAuth session
    // cookie authenticates both features.
    internal val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        restoreSessionCookie()
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Keystore-backed prefs holding only the auth session cookie, so the
     * credential never sits in plaintext on disk. Built lazily; if the keyset is
     * unreadable (e.g. the encrypted file was restored onto a device whose
     * Keystore master key can't be recovered) we wipe and rebuild it — the
     * learner just signs in again rather than the app crashing.
     */
    private val securePrefs: SharedPreferences by lazy {
        runCatching { buildSecurePrefs() }.getOrElse {
            appContext.deleteSharedPreferences(SECURE_PREFS)
            buildSecurePrefs()
        }
    }

    private fun buildSecurePrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var baseUrl: String
        get() = prefs().getString(KEY_BASE, DEFAULT_BASE) ?: DEFAULT_BASE
        set(value) {
            prefs().edit().putString(KEY_BASE, value.trimEnd('/')).apply()
        }

    private fun host(): String = baseUrl.toHttpUrlOrNull()?.host ?: "ai4kids.tertiarycourses.com.sg"

    /* ----------------------------- Session ----------------------------- */

    fun isLoggedIn(): Boolean = synchronized(cookieStore) {
        cookieStore.any { it.name.contains("session-token") && it.value.isNotEmpty() }
    }

    fun logout() {
        synchronized(cookieStore) { cookieStore.clear() }
        securePrefs.edit().remove(KEY_SESSION_COOKIE).apply()
    }

    /* ---- Local solo best times (offline play, no sign-in needed) ---- */

    fun soloBest(activitySlug: String): Long? {
        val v = prefs().getLong("best.$activitySlug", -1L)
        return if (v < 0) null else v
    }

    fun recordSoloBest(activitySlug: String, ms: Long) {
        val current = soloBest(activitySlug)
        if (current == null || ms < current) {
            prefs().edit().putLong("best.$activitySlug", ms).apply()
        }
    }

    private fun persistSessionCookie(c: Cookie) {
        securePrefs.edit().putString(KEY_SESSION_COOKIE, "${c.name}\t${c.value}").apply()
    }

    private fun restoreSessionCookie() {
        // One-time migration: an earlier build stored the cookie in plaintext
        // prefs. Move it into the encrypted store, then scrub the old copy.
        prefs().getString(KEY_SESSION_COOKIE, null)?.let { legacy ->
            securePrefs.edit().putString(KEY_SESSION_COOKIE, legacy).apply()
            prefs().edit().remove(KEY_SESSION_COOKIE).apply()
        }
        val saved = securePrefs.getString(KEY_SESSION_COOKIE, null) ?: return
        val (name, value) = saved.split("\t", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        if (value.isEmpty()) return
        val url = baseUrl.toHttpUrlOrNull() ?: return
        val cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(url.host)
            .path("/")
            .httpOnly()
            .apply { if (url.isHttps) secure() }
            .build()
        synchronized(cookieStore) { cookieStore.add(cookie) }
    }

    /* ----------------------------- Auth ----------------------------- */

    /**
     * Sign in a learner with their username/email + password via NextAuth's
     * credentials flow. Returns a friendly error message on failure, or null on
     * success.
     */
    fun login(identifier: String, password: String): String? {
        return try {
            val csrf = fetchCsrfToken() ?: return "Couldn't reach the server. Check the address and your connection."
            val form = FormBody.Builder()
                .add("identifier", identifier.trim())
                .add("password", password)
                .add("csrfToken", csrf)
                .add("callbackUrl", "$baseUrl/dashboard")
                .add("json", "true")
                .build()
            val req = Request.Builder()
                .url("$baseUrl/api/auth/callback/credentials")
                .header("Accept", "application/json")
                .post(form)
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val url = runCatching { JSONObject(body).optString("url") }.getOrDefault("")
                // NextAuth redirects to a URL containing "error" on a bad login.
                if (url.contains("error", ignoreCase = true)) {
                    return "Oops! That username or password didn't work. Try again 🙂"
                }
                if (!isLoggedIn()) {
                    return "Login failed. Please try again."
                }
                null
            }
        } catch (e: Exception) {
            "Couldn't connect: ${e.message ?: "network error"}"
        }
    }

    private fun fetchCsrfToken(): String? {
        val req = Request.Builder().url("$baseUrl/api/auth/csrf").get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            return runCatching { JSONObject(body).getString("csrfToken") }.getOrNull()
        }
    }

    /* ----------------------------- Card endpoints ----------------------------- */

    class ApiException(message: String) : Exception(message)

    private fun postJson(path: String, body: JSONObject): CardState {
        val req = Request.Builder()
            .url("$baseUrl/api/learn/cards/$path")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!resp.isSuccessful) {
                val msg = json?.optString("error").takeUnless { it.isNullOrBlank() }
                    ?: "Something went wrong (${resp.code})."
                throw ApiException(msg)
            }
            val stateObj = json?.optJSONObject("state")
                ?: throw ApiException("Unexpected response from server.")
            return CardState.parse(stateObj)
        }
    }

    fun create(gameSlug: String, mode: CardMode, pairs: Int?): CardState {
        val body = JSONObject().put("gameSlug", gameSlug).put("mode", mode.slug)
        if (pairs != null) body.put("options", JSONObject().put("pairs", pairs))
        return postJson("create", body)
    }

    fun join(code: String, gameSlug: String): CardState =
        postJson("join", JSONObject().put("code", code.trim().uppercase()).put("gameSlug", gameSlug))

    fun start(code: String, pairs: Int?): CardState {
        val body = JSONObject().put("code", code)
        if (pairs != null) body.put("options", JSONObject().put("pairs", pairs))
        return postJson("start", body)
    }

    fun move(code: String, move: JSONObject): CardState =
        postJson("move", JSONObject().put("code", code).put("move", move))

    fun sync(code: String): CardState =
        postJson("sync", JSONObject().put("code", code))

    private val JSON = "application/json; charset=utf-8".toMediaType()
}
