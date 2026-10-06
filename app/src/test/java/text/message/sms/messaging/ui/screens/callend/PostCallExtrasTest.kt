package text.message.sms.messaging.ui.screens.callend

import android.telecom.DisconnectCause
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome

private const val ENDED_AT = 1_700_000_600_000L

/** [PostCallActivity]'s handling of its untrusted `POST_CALL` extras. */
class PostCallExtrasTest {

    @Test
    fun telHandle_isAccepted() {
        assertEquals("+15551234567", validPostCallNumber("tel", "+15551234567"))
        assertEquals("(555) 123-4567", validPostCallNumber("tel", " (555) 123-4567 "))
    }

    @Test
    fun nonTelHandles_areRejected() {
        assertNull(validPostCallNumber("sip", "alice@example.com"))
        assertNull(validPostCallNumber("voicemail", "123"))
        assertNull(validPostCallNumber(null, "+15551234567"))
    }

    @Test
    fun malformedNumbers_areRejected() {
        assertNull(validPostCallNumber("tel", null))
        assertNull(validPostCallNumber("tel", "  "))
        assertNull(validPostCallNumber("tel", "+-()"))
        assertNull(validPostCallNumber("tel", "<script>"))
        assertNull(validPostCallNumber("tel", "1".repeat(33)))
    }

    @Test
    fun missedAndRejected_mapToTheirOutcomes() {
        val missed = postCallFallbackSession("+15551234567", DisconnectCause.MISSED, ENDED_AT)!!
        assertEquals(CallDirection.INCOMING, missed.direction)
        assertEquals(CallOutcome.MISSED, missed.outcome)
        assertEquals("+15551234567", missed.phoneNumber)
        assertEquals(0L, missed.durationMillis)

        val rejected = postCallFallbackSession(null, DisconnectCause.REJECTED, ENDED_AT)!!
        assertEquals(CallOutcome.REJECTED, rejected.outcome)
        assertNull(rejected.phoneNumber)
    }

    @Test
    fun ordinaryHangUp_isAnsweredWithNoDuration() {
        val session = postCallFallbackSession("+15551234567", DisconnectCause.LOCAL, ENDED_AT)!!
        assertEquals(CallOutcome.ANSWERED, session.outcome)
        assertEquals(ENDED_AT, session.endedAt)
        assertEquals(0L, session.durationMillis)
    }

    @Test
    fun callsTheScreenHasNothingToSayAbout_giveNoSession() {
        assertNull(postCallFallbackSession("+15551234567", DisconnectCause.ANSWERED_ELSEWHERE, ENDED_AT))
        assertNull(postCallFallbackSession("+15551234567", DisconnectCause.CALL_PULLED, ENDED_AT))
        assertNull(postCallFallbackSession("+15551234567", 9_999, ENDED_AT))
        assertNull(postCallFallbackSession("+15551234567", -1, ENDED_AT))
    }

    @Test
    fun noExtrasAtAll_giveNoSession() {
        assertNull(postCallFallbackSession(null, null, ENDED_AT))
    }

    @Test
    fun numberWithoutCause_isStillShown() {
        assertEquals("+15551234567", postCallFallbackSession("+15551234567", null, ENDED_AT)?.phoneNumber)
    }

    @Test
    fun blockedCaller_skipsTheCallEndScreen() = runTest {
        assertTrue(skipsCallEndScreen("+919876543210") { true })
    }

    @Test
    fun allowedCaller_orNoNumber_stillOpensTheScreen() = runTest {
        assertFalse(skipsCallEndScreen("+919876543210") { false })
        assertFalse(skipsCallEndScreen(null) { throw AssertionError("no number, no lookup") })
    }

    @Test
    fun failedOrSlowBlockCheck_stillOpensTheScreen() = runTest {
        assertFalse(skipsCallEndScreen("+919876543210") { throw IllegalStateException("db closed") })
        assertFalse(skipsCallEndScreen("+919876543210") { delay(POST_CALL_BLOCK_CHECK_MILLIS + 1); true })
    }
}
