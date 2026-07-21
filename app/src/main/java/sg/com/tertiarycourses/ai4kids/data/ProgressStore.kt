package sg.com.tertiarycourses.ai4kids.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONArray
import org.json.JSONObject
import sg.com.tertiarycourses.ai4kids.model.Activity

/**
 * App-wide progress: stars earned per activity, persisted locally to
 * `SharedPreferences` (JSON). No accounts, no network, no personal data — just
 * a fun running tally so kids see their stars grow. Android port of the iOS
 * `ProgressStore`.
 */
class ProgressStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Stars earned per activity id, exposed as Compose state. */
    private var starsByActivity by mutableStateOf<Map<String, Int>>(emptyMap())

    /** Cleared level indices per game bucket (e.g. "code"), exposed as Compose
     *  state so level-select screens recompose as levels are unlocked. */
    private var clearedByBucket by mutableStateOf<Map<String, Set<Int>>>(emptyMap())

    init {
        starsByActivity = load()
        clearedByBucket = loadLevels()
    }

    /** Total stars across every activity. */
    val totalStars: Int
        get() = starsByActivity.values.sum()

    /** Stars earned in a single activity. */
    fun stars(activity: Activity): Int = starsByActivity[activity.id] ?: 0

    /** Award [count] stars for an activity and persist immediately. */
    fun award(count: Int, activity: Activity) = award(count, activity.id)

    /** Award [count] stars to a free-form bucket id and persist immediately. Used by
     *  the online Brain Arcade, which isn't one of the home-screen [Activity] cards
     *  but still contributes to the running star [totalStars]. */
    fun award(count: Int, bucketId: String) {
        if (count <= 0) return
        val updated = starsByActivity.toMutableMap()
        updated[bucketId] = (updated[bucketId] ?: 0) + count
        starsByActivity = updated
        persist()
    }

    /** The set of cleared level indices for a game [bucket] (e.g. "code"). */
    fun clearedLevels(bucket: String): Set<Int> = clearedByBucket[bucket] ?: emptySet()

    /** Record that [level] in [bucket] has been cleared, persisting immediately so
     *  the unlock survives leaving the activity. */
    fun markLevelCleared(bucket: String, level: Int) {
        val current = clearedByBucket[bucket] ?: emptySet()
        if (level in current) return
        val updated = clearedByBucket.toMutableMap()
        updated[bucket] = current + level
        clearedByBucket = updated
        persistLevels()
    }

    /** Reset all progress (used by the Parents' Corner). */
    fun resetAll() {
        starsByActivity = emptyMap()
        clearedByBucket = emptyMap()
        persist()
        persistLevels()
    }

    private fun load(): Map<String, Int> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key -> put(key, json.getInt(key)) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun persist() {
        val json = JSONObject()
        starsByActivity.forEach { (key, value) -> json.put(key, value) }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun loadLevels(): Map<String, Set<Int>> {
        val raw = prefs.getString(KEY_LEVELS, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { bucket ->
                    val arr = json.getJSONArray(bucket)
                    put(bucket, buildSet { for (i in 0 until arr.length()) add(arr.getInt(i)) })
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun persistLevels() {
        val json = JSONObject()
        clearedByBucket.forEach { (bucket, levels) -> json.put(bucket, JSONArray(levels.sorted())) }
        prefs.edit().putString(KEY_LEVELS, json.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "ai4kids.prefs"
        const val KEY = "ai4kids.progress.v1"
        const val KEY_LEVELS = "ai4kids.levels.v1"
    }
}

/** Provides the shared [ProgressStore] down the Compose tree (mirrors the iOS
 *  environment object injection). */
val LocalProgressStore = staticCompositionLocalOf<ProgressStore> {
    error("ProgressStore not provided")
}
