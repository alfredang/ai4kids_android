package sg.com.tertiarycourses.ai4kids.data

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Salted SHA-256 hashing for the AI Workshops parent PIN. The PIN itself is
 * never stored — only a random 16-byte salt and `SHA-256(salt ‖ PIN)`, both
 * hex-encoded, inside the Keystore-encrypted [ParentAuth] store.
 *
 * Pure JVM (no Android APIs) so it is unit-tested directly. Mirrors the iOS
 * `PinHasher`.
 */
object PinHasher {

    const val SALT_BYTES = 16
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    /** A PIN is 4–6 digits, nothing else. */
    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /** A fresh random salt, hex-encoded. */
    fun newSalt(random: SecureRandom = SecureRandom()): String =
        ByteArray(SALT_BYTES).also { random.nextBytes(it) }.toHex()

    /** Hex `SHA-256(salt bytes ‖ UTF-8 PIN)`. */
    fun hash(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.hexToBytes())
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest().toHex()
    }

    /** Constant-time comparison of [pin] against a stored salt + hash. */
    fun verify(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        if (saltHex.isEmpty() || expectedHashHex.isEmpty()) return false
        val actual = hash(pin, saltHex).hexToBytes()
        return MessageDigest.isEqual(actual, expectedHashHex.hexToBytes())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "odd hex length" }
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
