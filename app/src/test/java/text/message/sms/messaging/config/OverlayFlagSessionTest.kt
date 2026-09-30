package text.message.sms.messaging.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * [OverlayFlagSession] against a fake [OverlayFlagSource] standing in for Remote Config: the value
 * it reads, the default when there is none, the once-per-session cache, and that a read never
 * waits on a fetch.
 */
class OverlayFlagSessionTest {

    private class FakeSource(
        var activated: Boolean? = null,
        private val onFetch: FakeSource.() -> Unit = {},
    ) : OverlayFlagSource {
        var reads = 0
        var fetches = 0

        override fun activatedValue(): Boolean? {
            reads++
            return activated
        }

        override fun fetchForNextSession() {
            fetches++
            onFetch()
        }
    }

    @Test
    fun readsTheActivatedRemoteConfigValue() {
        assertFalse(OverlayFlagSession(FakeSource(activated = false)).enabled)
        assertTrue(OverlayFlagSession(FakeSource(activated = true)).enabled)
    }

    @Test
    fun unset_defaultsToTrue() {
        assertTrue(OVERLAY_AND_CALL_END_DEFAULT)
        assertTrue(OverlayFlagSession(FakeSource(activated = null)).enabled)
    }

    /** FirebaseApp not initialized yet: FirebaseRemoteConfig.getInstance() throws. */
    @Test
    fun unreadableSource_defaultsToTrueWithoutThrowing() {
        val source = object : OverlayFlagSource {
            override fun activatedValue(): Boolean? = throw IllegalStateException("Default FirebaseApp is not initialized")
            override fun fetchForNextSession() = throw IllegalStateException("Default FirebaseApp is not initialized")
        }
        val session = OverlayFlagSession(source)
        assertTrue(session.enabled)
        session.snapshotThenFetch()
        assertTrue(session.enabled)
    }

    @Test
    fun parameterName() {
        assertEquals("overlay_and_call_end_enabled", OVERLAY_AND_CALL_END_KEY)
    }

    @Test
    fun readOnce_thenCachedForTheSession() {
        val source = FakeSource(activated = true)
        val session = OverlayFlagSession(source)
        assertTrue(session.enabled)
        source.activated = false
        assertTrue(session.enabled)
        assertEquals(1, source.reads)
    }

    @Test
    fun reading_neverStartsAFetch() {
        val source = FakeSource(activated = false)
        assertFalse(OverlayFlagSession(source).enabled)
        assertEquals(0, source.fetches)
    }

    /** A fetch that activates a new value mid-session only shows up in the next session. */
    @Test
    fun fetchResult_takesEffectNextSession() {
        val source = FakeSource(activated = true, onFetch = { activated = false })
        val session = OverlayFlagSession(source)
        session.snapshotThenFetch()
        assertEquals(1, source.fetches)
        assertTrue(session.enabled)
        assertFalse(OverlayFlagSession(source).enabled)
    }

    /** Even when nothing read the flag before the fetch landed. */
    @Test
    fun snapshotIsTakenBeforeTheFetch() {
        val source = FakeSource(activated = null, onFetch = { activated = false })
        val session = OverlayFlagSession(source)
        session.snapshotThenFetch()
        assertTrue(session.enabled)
    }

    @Test(timeout = 5_000)
    fun read_doesNotWaitOnAFetchInFlight() {
        val fetchStarted = CountDownLatch(1)
        val releaseFetch = CountDownLatch(1)
        val source = FakeSource(
            activated = false,
            onFetch = {
                fetchStarted.countDown()
                releaseFetch.await()
            },
        )
        val session = OverlayFlagSession(source)
        val fetcher = thread { session.snapshotThenFetch() }
        assertTrue(fetchStarted.await(2, TimeUnit.SECONDS))
        assertFalse(session.enabled)
        releaseFetch.countDown()
        fetcher.join()
    }
}
