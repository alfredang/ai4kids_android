package sg.com.tertiarycourses.ai4kids.escape

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import sg.com.tertiarycourses.ai4kids.cards.CardApi

/**
 * One player in a co-op escape session (presence + scoring).
 * [atStation] is the room/object the teammate is currently at (or null).
 */
data class EscapePlayer(
    val learnerId: Int,
    val name: String,
    val avatar: String?,
    val atStation: String?,
    val isHost: Boolean,
)

/**
 * The shared state of a co-op escape session, as returned by every endpoint.
 * Mirrors the server's `SessionStateDTO`.
 */
data class EscapeState(
    val code: String,
    val roomSlug: String,
    val status: String,          // "lobby" | "playing" | "escaped"
    val solved: List<String>,    // solved station ids (shared across the team)
    val points: Int,
    val total: Int,              // stations in the room (server-defined)
    val hostId: Int,
    val you: Int,                // the requesting learner's id
    val players: List<EscapePlayer>,
) {
    val inLobby get() = status == "lobby"
    val playing get() = status == "playing"
    val escaped get() = status == "escaped"
    val youAreHost get() = hostId == you
    val allSolved get() = total > 0 && solved.size >= total

    companion object {
        fun parse(o: JSONObject): EscapeState {
            val solved = o.optJSONArray("solved")?.let { arr ->
                List(arr.length()) { arr.getString(it) }
            } ?: emptyList()
            val players = o.optJSONArray("players")?.let { arr ->
                List(arr.length()) { i ->
                    val p = arr.getJSONObject(i)
                    EscapePlayer(
                        learnerId = p.optInt("learnerId"),
                        name = p.optString("name"),
                        avatar = if (p.isNull("avatar")) null else p.optString("avatar").ifBlank { null },
                        atStation = if (p.isNull("atStation")) null else p.optString("atStation").ifBlank { null },
                        isHost = p.optBoolean("isHost"),
                    )
                }
            } ?: emptyList()
            return EscapeState(
                code = o.optString("code"),
                roomSlug = o.optString("roomSlug"),
                status = o.optString("status"),
                solved = solved,
                points = o.optInt("points"),
                total = o.optInt("total"),
                hostId = o.optInt("hostId"),
                you = o.optInt("you"),
                players = players,
            )
        }
    }
}

/**
 * Networked client for co-op (multiplayer) escape rooms. Talks to the same
 * ai4kids backend as [CardApi] and reuses its NextAuth session cookie — a learner
 * signed in for Brain Arcade is already authenticated here.
 *
 * The six `/api/learn/escape/` endpoints all return the shared [EscapeState]:
 *  - create / join  — host opens a room (returns a code) / a learner joins by code
 *  - start          — host moves the lobby into play
 *  - solve          — record a station solved (awards team points; deduped server-side)
 *  - sync           — heartbeat + presence + poll (call ~1s while playing)
 *  - finish         — when every station is solved, mark the team escaped
 *
 * All calls are blocking; invoke them from a background dispatcher.
 */
object EscapeApi {

    class ApiException(message: String) : Exception(message)

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Reuse Brain Arcade's login/session — no separate auth for escape rooms. */
    fun isLoggedIn() = CardApi.isLoggedIn()

    /** Sign out — clears the shared session cookie (also signs out Brain Arcade). */
    fun logout() = CardApi.logout()

    fun create(roomSlug: String): EscapeState =
        post("create", JSONObject().put("roomSlug", roomSlug))

    fun join(code: String, roomSlug: String? = null): EscapeState {
        val body = JSONObject().put("code", code.trim().uppercase())
        if (roomSlug != null) body.put("roomSlug", roomSlug)
        return post("join", body)
    }

    fun start(code: String): EscapeState =
        post("start", JSONObject().put("code", code))

    fun solve(code: String, stationId: String, firstTry: Boolean): EscapeState =
        post("solve", JSONObject().put("code", code).put("stationId", stationId).put("firstTry", firstTry))

    /** Heartbeat-only poll (leaves the player's `atStation` unchanged). */
    fun sync(code: String): EscapeState =
        post("sync", JSONObject().put("code", code))

    /** Poll and update presence: which room/object the player is at (null = none). */
    fun syncAt(code: String, atStation: String?): EscapeState =
        post("sync", JSONObject().put("code", code).put("atStation", atStation ?: JSONObject.NULL))

    fun finish(code: String): EscapeState =
        post("finish", JSONObject().put("code", code))

    private fun post(path: String, body: JSONObject): EscapeState {
        val req = Request.Builder()
            .url("${CardApi.baseUrl}/api/learn/escape/$path")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        CardApi.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!resp.isSuccessful) {
                val msg = json?.optString("error").takeUnless { it.isNullOrBlank() }
                    ?: "Something went wrong (${resp.code})."
                throw ApiException(msg)
            }
            val stateObj = json?.optJSONObject("state")
                ?: throw ApiException("Unexpected response from server.")
            return EscapeState.parse(stateObj)
        }
    }
}
