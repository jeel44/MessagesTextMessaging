package text.message.sms.messaging.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [isBlockedCaller], the allow/reject decision [CallScreeningServiceImpl] makes for every incoming
 * call: reject only on a definite "blocked", allow on anything else. */
class CallScreeningDecisionTest {

    @Test
    fun blockedNumber_isRejected() = runTest {
        assertTrue(isBlockedCaller("+919876543210", CALL_SCREENING_TIMEOUT_MILLIS) { true })
    }

    @Test
    fun unblockedNumber_isAllowed() = runTest {
        assertFalse(isBlockedCaller("+919876543210", CALL_SCREENING_TIMEOUT_MILLIS) { false })
    }

    @Test
    fun noNumber_isAllowedWithoutALookup() = runTest {
        val neverCalled: suspend (String) -> Boolean = { throw AssertionError("must not look up") }
        assertFalse(isBlockedCaller(null, CALL_SCREENING_TIMEOUT_MILLIS, neverCalled))
        assertFalse(isBlockedCaller("", CALL_SCREENING_TIMEOUT_MILLIS, neverCalled))
        assertFalse(isBlockedCaller("  ", CALL_SCREENING_TIMEOUT_MILLIS, neverCalled))
    }

    @Test
    fun lookupError_allowsTheCall() = runTest {
        assertFalse(isBlockedCaller("+919876543210", CALL_SCREENING_TIMEOUT_MILLIS) { throw IllegalStateException("db closed") })
    }

    @Test
    fun lookupTimeout_allowsTheCall() = runTest {
        val slow: suspend (String) -> Boolean = { delay(CALL_SCREENING_TIMEOUT_MILLIS + 1); true }
        assertFalse(isBlockedCaller("+919876543210", CALL_SCREENING_TIMEOUT_MILLIS, slow))
    }

    @Test
    fun lookupJustInsideTheTimeout_stillRejects() = runTest {
        val slowButInTime: suspend (String) -> Boolean = { delay(CALL_SCREENING_TIMEOUT_MILLIS - 1); true }
        assertTrue(isBlockedCaller("+919876543210", CALL_SCREENING_TIMEOUT_MILLIS, slowButInTime))
    }
}
