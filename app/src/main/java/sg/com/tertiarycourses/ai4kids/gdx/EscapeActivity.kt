package sg.com.tertiarycourses.ai4kids.gdx

import android.content.Intent
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import sg.com.tertiarycourses.ai4kids.escape.CoopSession

/**
 * Hosts the LibGDX [EscapeGdxGame] full-screen. Launched from the Compose escape
 * lobby; returns the stars earned (via the result intent) so the app can credit
 * them to the shared ProgressStore.
 *
 * If an [EXTRA_CODE] is supplied the game runs as a co-op session (shared solved
 * set synced with teammates); otherwise it's a solo offline run.
 */
class EscapeActivity : AndroidApplication() {
    private var coop: CoopSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
        }
        val code = intent.getStringExtra(EXTRA_CODE)
        val level = intent.getIntExtra(EXTRA_LEVEL, 0)
        coop = code?.let { CoopSession(it, intent.getBooleanExtra(EXTRA_HOST, false)).apply { start() } }
        initialize(
            EscapeGdxGame(level, coop) { stars ->
                runOnUiThread {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_STARS, stars))
                    finish()
                }
            },
            config,
        )
    }

    override fun onDestroy() {
        coop?.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_STARS = "stars"
        const val EXTRA_CODE = "code"
        const val EXTRA_HOST = "host"
        const val EXTRA_LEVEL = "level"
    }
}
