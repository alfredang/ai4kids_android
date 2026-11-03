package sg.com.tertiarycourses.ai4kids.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidSafetyTest {

    private fun blocked(text: String) = KidSafety.check(text) == KidSafety.Verdict.PersonalInfo

    @Test
    fun blocksEmails() {
        assertTrue(blocked("email me at kid.name@example.com please"))
    }

    @Test
    fun blocksPhoneLikeDigitRuns() {
        assertTrue(blocked("call 91234567"))
        assertTrue(blocked("my no is 9123 4567"))
        assertTrue(blocked("6512-3456"))
    }

    @Test
    fun blocksNric() {
        assertTrue(blocked("S1234567D"))
        assertTrue(blocked("it is t7654321z ok"))
    }

    @Test
    fun blocksPersonalPhrases() {
        assertTrue(blocked("My Address is Blk 12"))
        assertTrue(blocked("what is my   password"))
        assertTrue(blocked("I live at Tampines"))
    }

    @Test
    fun allowsOrdinaryKidMessages() {
        assertFalse(blocked("Why is the sky blue?"))
        assertFalse(blocked("I think the answer is 125"))
        assertFalse(blocked("12 + 34 = 46"))
        assertFalse(blocked("The otter found 3 shells and 4 stones"))
        assertEquals(KidSafety.Verdict.Ok, KidSafety.check("B"))
    }

    @Test
    fun lengthAndEmpty() {
        assertEquals(KidSafety.Verdict.Empty, KidSafety.check("   "))
        assertEquals(KidSafety.Verdict.Ok, KidSafety.check("a".repeat(KidSafety.MAX_CHARS)))
        assertEquals(KidSafety.Verdict.TooLong, KidSafety.check("a".repeat(KidSafety.MAX_CHARS + 1)))
    }

    @Test
    fun messages() {
        assertEquals(KidSafety.PRIVATE_MESSAGE, KidSafety.messageFor(KidSafety.Verdict.PersonalInfo))
        assertEquals(null, KidSafety.messageFor(KidSafety.Verdict.Ok))
    }
}
