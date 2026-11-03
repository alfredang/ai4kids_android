package sg.com.tertiarycourses.ai4kids.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun pinValidation() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("123456"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("1234567"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin(""))
    }

    @Test
    fun saltIs16RandomBytes() {
        val a = PinHasher.newSalt()
        val b = PinHasher.newSalt()
        assertEquals(32, a.length) // 16 bytes, hex
        assertNotEquals(a, b)
    }

    @Test
    fun hashIsSaltedSha256() {
        val salt = "00".repeat(16)
        // SHA-256 of 16 zero bytes followed by "1234".
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest(ByteArray(16) + "1234".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, PinHasher.hash("1234", salt))
        assertNotEquals(PinHasher.hash("1234", salt), PinHasher.hash("1234", "11".repeat(16)))
        assertFalse(PinHasher.hash("1234", salt).contains("1234"))
    }

    @Test
    fun verifyAcceptsOnlyTheRightPin() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt)
        assertTrue(PinHasher.verify("4821", salt, hash))
        assertFalse(PinHasher.verify("4822", salt, hash))
        assertFalse(PinHasher.verify("4821", PinHasher.newSalt(), hash))
        assertFalse(PinHasher.verify("4821", "", ""))
    }
}
