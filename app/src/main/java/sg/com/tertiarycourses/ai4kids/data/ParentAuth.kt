package sg.com.tertiarycourses.ai4kids.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The **parent login** that unlocks the AI Workshops — entirely on-device, no
 * server and no account:
 *
 * - a 4–6 digit parent PIN, stored only as a salted SHA-256 hash ([PinHasher]);
 * - the parent's **own** DeepSeek API key (the build ships no developer key);
 * - the parent's consent flag.
 *
 * All of it lives in Keystore-backed [EncryptedSharedPreferences]
 * (`ai4kids.parent.secure`), excluded from cloud backup and device transfer
 * (see `backup_rules.xml` and `data_extraction_rules.xml`), the same pattern `CardApi` uses for the
 * Brain Arcade session cookie.
 *
 * The unlocked **session** is in-memory only: it ends when the app goes to the
 * background ([MainActivity.onStop]), when the parent taps "Lock now", or after
 * [SESSION_MS] (30 minutes). Mirrors the iOS `ParentAuth`.
 */
object ParentAuth {

    const val MAX_ATTEMPTS = 5
    const val COOLDOWN_MS = 60_000L
    const val SESSION_MS = 30 * 60_000L

    private lateinit var appContext: Context

    /** True once a PIN + key + consent have been saved. Compose state. */
    var isConfigured by mutableStateOf(false)
        private set

    /** True when the saved key was rejected (HTTP 401) and needs replacing. */
    var keyNeedsReplacing by mutableStateOf(false)
        private set

    /** `SystemClock.elapsedRealtime()` at which the session expires; 0 = locked. */
    var unlockedUntil by mutableLongStateOf(0L)
        private set

    /** Stars awarded per workshop during the current unlocked session (cap 3). */
    private val sessionStars = mutableMapOf<String, Int>()

    fun init(context: Context) {
        appContext = context.applicationContext
        isConfigured = readConfigured()
        keyNeedsReplacing = securePrefs.getBoolean(KEY_KEY_REJECTED, false)
    }

    /**
     * Keystore-backed prefs. If the keyset is unreadable (e.g. restored onto a
     * device whose master key can't be recovered) we wipe and rebuild — the
     * parent simply sets up again rather than the app crashing.
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

    private fun readConfigured(): Boolean =
        !securePrefs.getString(KEY_PIN_HASH, null).isNullOrEmpty() &&
            !securePrefs.getString(KEY_API_KEY, null).isNullOrEmpty() &&
            securePrefs.getBoolean(KEY_CONSENT, false)

    /* ------------------------------ Setup ------------------------------ */

    /** Save a new PIN, key and consent (first-time setup), and unlock. */
    fun completeSetup(pin: String, apiKey: String) {
        require(PinHasher.isValidPin(pin))
        val salt = PinHasher.newSalt()
        securePrefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, PinHasher.hash(pin, salt))
            .putString(KEY_API_KEY, apiKey.trim())
            .putBoolean(KEY_CONSENT, true)
            .putBoolean(KEY_KEY_REJECTED, false)
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_COOLDOWN_UNTIL, 0L)
            .apply()
        isConfigured = true
        keyNeedsReplacing = false
        unlock()
    }

    fun replaceKey(apiKey: String) {
        securePrefs.edit().putString(KEY_API_KEY, apiKey.trim()).putBoolean(KEY_KEY_REJECTED, false).apply()
        keyNeedsReplacing = false
    }

    fun changePin(newPin: String) {
        require(PinHasher.isValidPin(newPin))
        val salt = PinHasher.newSalt()
        securePrefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, PinHasher.hash(newPin, salt))
            .apply()
    }

    /** "Forgot PIN" / "Remove key & turn off AI": wipe PIN, key and consent. */
    fun wipe() {
        securePrefs.edit().clear().apply()
        isConfigured = false
        keyNeedsReplacing = false
        lock()
    }

    /** The parent's key — only read while the session is unlocked. */
    fun apiKey(): String? = if (isUnlocked()) securePrefs.getString(KEY_API_KEY, null) else null

    /** Called when DeepSeek answers 401: flag the key and lock the session. */
    fun markKeyRejected() {
        securePrefs.edit().putBoolean(KEY_KEY_REJECTED, true).apply()
        keyNeedsReplacing = true
        lock()
    }

    /* ------------------------------ PIN entry ------------------------------ */

    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val attemptsLeft: Int) : PinResult
        data class CoolingDown(val remainingMs: Long) : PinResult
    }

    /** Remaining cooldown after too many wrong PINs (persisted wall-clock stamp). */
    fun cooldownRemainingMs(now: Long = System.currentTimeMillis()): Long {
        val until = securePrefs.getLong(KEY_COOLDOWN_UNTIL, 0L)
        val remaining = until - now
        // A clock moved backwards must not extend the lock-out beyond 60 s.
        return if (remaining in 1..COOLDOWN_MS) remaining else 0L
    }

    fun verifyPin(pin: String): PinResult {
        val cooling = cooldownRemainingMs()
        if (cooling > 0) return PinResult.CoolingDown(cooling)
        val ok = PinHasher.verify(
            pin,
            securePrefs.getString(KEY_PIN_SALT, null).orEmpty(),
            securePrefs.getString(KEY_PIN_HASH, null).orEmpty(),
        )
        if (ok) {
            securePrefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_COOLDOWN_UNTIL, 0L).apply()
            unlock()
            return PinResult.Ok
        }
        val fails = securePrefs.getInt(KEY_FAILS, 0) + 1
        return if (fails >= MAX_ATTEMPTS) {
            securePrefs.edit()
                .putInt(KEY_FAILS, 0)
                .putLong(KEY_COOLDOWN_UNTIL, System.currentTimeMillis() + COOLDOWN_MS)
                .apply()
            PinResult.CoolingDown(COOLDOWN_MS)
        } else {
            securePrefs.edit().putInt(KEY_FAILS, fails).apply()
            PinResult.Wrong(MAX_ATTEMPTS - fails)
        }
    }

    /* ------------------------------ Session ------------------------------ */

    fun isUnlocked(): Boolean = unlockedUntil > SystemClock.elapsedRealtime()

    private fun unlock() {
        unlockedUntil = SystemClock.elapsedRealtime() + SESSION_MS
        sessionStars.clear()
    }

    fun lock() {
        unlockedUntil = 0L
        sessionStars.clear()
    }

    /** Award one star for a completed workshop exchange, at most 3 per workshop
     *  per unlocked session. Returns true if a star was given. */
    fun tryAwardStar(workshopId: String, progress: ProgressStore): Boolean {
        val given = sessionStars[workshopId] ?: 0
        if (given >= 3) return false
        sessionStars[workshopId] = given + 1
        progress.award(1, "workshop.$workshopId")
        return true
    }

    private const val SECURE_PREFS = "ai4kids.parent.secure"
    private const val KEY_PIN_SALT = "pin.salt"
    private const val KEY_PIN_HASH = "pin.hash"
    private const val KEY_API_KEY = "deepseek.key"
    private const val KEY_CONSENT = "consent.v1"
    private const val KEY_KEY_REJECTED = "deepseek.keyRejected"
    private const val KEY_FAILS = "pin.fails"
    private const val KEY_COOLDOWN_UNTIL = "pin.cooldownUntil"
}
