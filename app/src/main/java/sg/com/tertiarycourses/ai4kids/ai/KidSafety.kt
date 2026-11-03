package sg.com.tertiarycourses.ai4kids.ai

/**
 * Client-side safety for the AI Workshops, applied **before** anything is sent
 * to DeepSeek: a length cap and a personal-information filter (emails, phone /
 * NRIC-like digit runs, Singapore NRIC/FIN pattern, "my address"-style phrases).
 *
 * Pure Kotlin (no Android / org.json) so it is unit-tested directly. Mirrors the
 * iOS `KidSafety`.
 */
object KidSafety {

    /** Max characters per kid message. */
    const val MAX_CHARS = 300

    const val PRIVATE_MESSAGE =
        "Let's keep personal stuff private! Try asking without your name, phone or address."

    const val TOO_LONG_MESSAGE = "That's a lot of words! Try a shorter message (up to $MAX_CHARS letters)."

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    /** 7+ digits in a row, allowing single spaces/dashes between them
     *  ("91234567", "9123 4567", "9123-4567"). */
    private val LONG_DIGITS = Regex("\\d(?:[ -]?\\d){6,}")

    /** Singapore NRIC / FIN, e.g. S1234567D. */
    private val NRIC = Regex("\\b[STFGM]\\d{7}[A-Z]\\b", RegexOption.IGNORE_CASE)

    private val PHRASES = listOf(
        "my address", "my home address", "i live at", "i live in block", "my house is at",
        "my password", "my passcode", "my pin is",
        "my phone number", "my phone no", "my mobile number", "my handphone", "my hp number",
        "my email", "my e-mail",
        "my nric", "my ic number", "my ic no", "my fin number",
        "my school is", "i study at",
        "my full name",
    )

    /** Why a message was blocked (or [Ok]). */
    sealed interface Verdict {
        data object Ok : Verdict
        data object Empty : Verdict
        data object TooLong : Verdict
        data object PersonalInfo : Verdict
    }

    fun check(raw: String): Verdict {
        val text = raw.trim()
        if (text.isEmpty()) return Verdict.Empty
        if (text.length > MAX_CHARS) return Verdict.TooLong
        if (containsPersonalInfo(text)) return Verdict.PersonalInfo
        return Verdict.Ok
    }

    fun containsPersonalInfo(text: String): Boolean {
        if (EMAIL.containsMatchIn(text)) return true
        if (LONG_DIGITS.containsMatchIn(text)) return true
        if (NRIC.containsMatchIn(text)) return true
        val normalised = text.lowercase().replace(Regex("\\s+"), " ")
        return PHRASES.any { it in normalised }
    }

    /** Kid-readable explanation for a blocked [verdict], or null when OK. */
    fun messageFor(verdict: Verdict): String? = when (verdict) {
        Verdict.Ok, Verdict.Empty -> null
        Verdict.TooLong -> TOO_LONG_MESSAGE
        Verdict.PersonalInfo -> PRIVATE_MESSAGE
    }

    /** Shared system-prompt prefix for every workshop. */
    const val SYSTEM_PREFIX =
        "You are Sunny, a cheerful, patient learning buddy in the AI4Kids app for children " +
            "aged 7–12 in Singapore. A parent is supervising. Rules: use simple words and short " +
            "sentences; keep replies under 120 words unless asked for a story; be kind and " +
            "encouraging; never ask for or repeat personal information (names of real people, " +
            "school, address, phone, photos); refuse anything violent, scary, romantic, unsafe, " +
            "or about weapons, drugs, self-harm, or adult topics — gently suggest a fun learning " +
            "topic instead; never pretend to be a human; if the child seems upset or in danger, " +
            "tell them to talk to a trusted grown-up right away. Use British/Singapore spelling."
}
