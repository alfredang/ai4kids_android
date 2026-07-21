package sg.com.tertiarycourses.ai4kids.escape

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Drives a co-op escape session from the running game. Networking runs on two
 * background threads — a poller and an action sender — and the latest [state] is
 * published as a volatile the GL render loop can read each frame. The game pushes
 * local solves + presence in; the poller pulls the shared team state out (~0.8s).
 */
class CoopSession(val code: String, val host: Boolean) {

    /** Latest shared state from the server (null until the first poll lands). */
    @Volatile var state: EscapeState? = null
        private set

    /** Last network error message, for surfacing in the HUD if needed. */
    @Volatile var lastError: String? = null
        private set

    /** Which room/object the local player is at — sent on the next heartbeat. */
    @Volatile var atStation: String? = null

    @Volatile private var finishing = false

    private val poller = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "coop-poll").apply { isDaemon = true }
    }
    // Solves/actions run on their own thread so they never queue behind an
    // in-flight poll — a teammate sees your solve on their very next poll.
    private val actions = Executors.newSingleThreadExecutor { r ->
        Thread(r, "coop-act").apply { isDaemon = true }
    }

    fun start() {
        // ~0.8s cadence (the backend is built for ~1s polling); fixed-delay keeps
        // requests from piling up if the network is briefly slow.
        poller.scheduleWithFixedDelay({ poll() }, 0, 800, TimeUnit.MILLISECONDS)
    }

    private fun poll() {
        try {
            val s = EscapeApi.syncAt(code, atStation)
            state = s
            lastError = null
            // Any client can flip the team to "escaped" once every station is solved.
            if (s.allSolved && !s.escaped && !finishing) {
                finishing = true
                runCatching { state = EscapeApi.finish(code) }
                finishing = false
            }
        } catch (e: Exception) {
            lastError = e.message
        }
    }

    /** Report a station the local player just solved (deduped server-side). */
    fun reportSolve(stationId: String, firstTry: Boolean) {
        actions.submit {
            runCatching { state = EscapeApi.solve(code, stationId, firstTry) }
                .onFailure { lastError = it.message }
        }
    }

    fun stop() {
        runCatching { poller.shutdownNow() }
        runCatching { actions.shutdownNow() }
    }
}
