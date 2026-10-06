package text.message.sms.messaging.service

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** How soon after one call-end screen was opened for a number another attempt for the same number
 * counts as a duplicate of that same call end. Kept short on purpose: a 15s window dropped a real
 * missed call that came 10s after the previous call's screen (seen on Android 16). Any other number,
 * or the same number after this, is a new call and always gets its screen. */
internal const val CALL_END_DUPLICATE_WINDOW_MILLIS = 2_000L

/**
 * Makes sure one call end opens [text.message.sms.messaging.ui.screens.callend.CallEndActivity]
 * once, even if it's asked to twice: [text.message.sms.messaging.ui.screens.callend.PostCallActivity]
 * for the system's `POST_CALL` launch, and [PhoneStateReceiver] -> [CallEndLauncher] for users
 * with "draw over other apps".
 *
 * Only a true duplicate is dropped -- the same number ([CallSession.phoneNumber], null for a
 * withheld caller) within [CALL_END_DUPLICATE_WINDOW_MILLIS] of the last screen. Back-to-back
 * calls always get a screen each.
 *
 * In memory only: duplicates arrive in this app's one process within seconds of each other, so
 * there's nothing to persist.
 */
@Singleton
class CallEndShownGate internal constructor(
    private val elapsedRealtime: () -> Long,
) {

    @Inject
    constructor() : this(SystemClock::elapsedRealtime)

    private class Shown(val phoneNumber: String?, val atMillis: Long)

    private val lastShown = AtomicReference<Shown?>(null)

    /** True if the caller may open the call-end screen for [phoneNumber] now, and records that it
     * did; false if it was already opened for the same number within
     * [CALL_END_DUPLICATE_WINDOW_MILLIS]. Call it right before the launch. */
    fun tryClaim(phoneNumber: String?): Boolean {
        val claim = Shown(phoneNumber, elapsedRealtime())
        while (true) {
            val last = lastShown.get()
            if (last != null && last.phoneNumber == phoneNumber &&
                claim.atMillis - last.atMillis < CALL_END_DUPLICATE_WINDOW_MILLIS
            ) {
                return false
            }
            if (lastShown.compareAndSet(last, claim)) return true
        }
    }
}
