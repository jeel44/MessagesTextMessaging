package text.message.sms.messaging.service

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** How long after one call-end screen is opened any further attempt counts as the same call end.
 * Covers the gap between the two launch paths for one call -- [CallEndLauncher] (at most ~4s after
 * the IDLE broadcast) and [text.message.sms.messaging.ui.screens.callend.PostCallActivity] (~1s
 * after the disconnect, plus up to 1.5s on the call log) -- with margin. The cost: a second, real
 * call that ends within this window of the previous screen opening gets no screen of its own. */
internal const val CALL_END_DEDUPE_WINDOW_MILLIS = 15_000L

private const val NEVER = Long.MIN_VALUE

/**
 * Makes sure one call end opens [text.message.sms.messaging.ui.screens.callend.CallEndActivity]
 * once, whichever of its two launch paths gets there first: the system's `POST_CALL` launch of
 * [text.message.sms.messaging.ui.screens.callend.PostCallActivity] (call-screening role holders)
 * or [PhoneStateReceiver] -> [CallEndLauncher] (users with "draw over other apps"). A user with
 * both has both paths run for every call.
 *
 * In memory only: both paths run in this app's one process within seconds of each other, and the
 * visible activity keeps that process alive, so there's nothing to persist.
 */
@Singleton
class CallEndShownGate internal constructor(
    private val elapsedRealtime: () -> Long,
) {

    @Inject
    constructor() : this(SystemClock::elapsedRealtime)

    private val lastShownAt = AtomicLong(NEVER)

    /** True if the caller may open the call-end screen now, and records that it did; false if one
     * was already opened within [CALL_END_DEDUPE_WINDOW_MILLIS]. Call it right before the launch,
     * so the slower path loses even when it started first. */
    fun tryClaim(): Boolean {
        val now = elapsedRealtime()
        while (true) {
            val last = lastShownAt.get()
            if (last != NEVER && now - last < CALL_END_DEDUPE_WINDOW_MILLIS) return false
            if (lastShownAt.compareAndSet(last, now)) return true
        }
    }
}
