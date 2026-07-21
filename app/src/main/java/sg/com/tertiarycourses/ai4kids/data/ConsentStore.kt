package sg.com.tertiarycourses.ai4kids.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Remembers whether a parent/guardian has given consent for the child to use
 * the app. Asked once on first launch via the parental gate and persisted to
 * `SharedPreferences`, so the consent screen does not reappear on every open.
 * No network, no personal data — just a local boolean flag.
 */
class ConsentStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether a grown-up has consented, exposed as Compose state. */
    var granted by mutableStateOf(prefs.getBoolean(KEY, false))
        private set

    /** Record that a parent/guardian has given consent. */
    fun grant() {
        granted = true
        prefs.edit().putBoolean(KEY, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "ai4kids.prefs"
        const val KEY = "ai4kids.parentalConsent.v1"
    }
}
