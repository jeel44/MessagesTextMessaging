package text.message.sms.messaging.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import text.message.sms.messaging.analytics.Analytics
import text.message.sms.messaging.analytics.CallScreeningResult
import text.message.sms.messaging.analytics.CallScreeningSource
import text.message.sms.messaging.config.CallEndFeatureFlag
import text.message.sms.messaging.data.local.datastore.CallScreeningPreferences
import text.message.sms.messaging.data.local.datastore.CallScreeningState
import text.message.sms.messaging.data.local.datastore.CallScreeningStore
import text.message.sms.messaging.di.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** After a dismissal, how long before the re-ask banner may show again. */
internal const val REASK_SNOOZE_MILLIS = 7L * 24 * 60 * 60 * 1_000

/** Dismissals after which the re-ask banner never shows again on this install. */
internal const val REASK_MAX_DISMISSALS = 3

/** The role was granted once and isn't held any more -- another app took it, so `POST_CALL` (and
 * with it the call-end screen) goes there instead. Only counts while the call-end screen is on
 * and the role exists on this device. */
internal fun isCallScreeningLost(
    callEndEnabled: Boolean,
    roleAvailable: Boolean,
    roleHeld: Boolean,
    state: CallScreeningState,
): Boolean = callEndEnabled && roleAvailable && state.grantedOnce && !roleHeld

/**
 * Whether the inbox shows its "turn caller info back on" banner: the role is lost
 * ([isCallScreeningLost]), this app is still the default SMS app (otherwise the inbox's
 * lost-default-SMS banner already takes that spot), and the user hasn't dismissed it
 * [REASK_MAX_DISMISSALS] times or within the last [REASK_SNOOZE_MILLIS].
 */
internal fun shouldShowCallScreeningReask(
    callEndEnabled: Boolean,
    roleAvailable: Boolean,
    roleHeld: Boolean,
    isDefaultSmsApp: Boolean,
    state: CallScreeningState,
    nowMillis: Long,
): Boolean {
    if (!isCallScreeningLost(callEndEnabled, roleAvailable, roleHeld, state)) return false
    if (!isDefaultSmsApp) return false
    if (state.reaskDismissCount >= REASK_MAX_DISMISSALS) return false
    val lastDismissed = state.reaskLastDismissedAtMillis ?: return true
    return nowMillis - lastDismissed >= REASK_SNOOZE_MILLIS
}

/**
 * Everything the app remembers and reports about `ROLE_CALL_SCREENING` after asking for it:
 * - "granted once": recorded whenever the role is seen held (onboarding, Settings, the inbox).
 * - The inbox's re-ask banner ([showReaskBanner]) for a user who had the role and lost it to
 *   another app. Decided once per process the first time the inbox opens ([checkOnce]) -- not
 *   on every recomposition or return to the inbox -- and only ever hidden after that.
 * - `call_screening_lost` (once per loss) and `call_screening_result` (with its source).
 *
 * Only one app can hold the role, so losing it is ordinary (e.g. another caller-ID app asking for
 * it); the banner is the one gentle way back, capped by [shouldShowCallScreeningReask].
 *
 * The primary constructor takes every platform touchpoint as a function so plain-JVM unit tests
 * can drive it; Hilt uses the secondary one.
 */
@Singleton
class CallScreeningRoleTracker internal constructor(
    private val store: CallScreeningStore,
    private val isCallEndEnabled: () -> Boolean,
    private val isRoleAvailable: () -> Boolean,
    private val isRoleHeld: () -> Boolean,
    private val isDefaultSmsApp: () -> Boolean,
    private val nowMillis: () -> Long,
    private val scope: CoroutineScope,
    private val logLost: () -> Unit,
    private val logResult: (CallScreeningResult, CallScreeningSource) -> Unit,
) {

    @Inject
    constructor(
        preferences: CallScreeningPreferences,
        roleGuard: CallScreeningRoleGuard,
        defaultSmsAppGuard: DefaultSmsAppGuard,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        store = preferences,
        isCallEndEnabled = CallEndFeatureFlag::isEnabled,
        isRoleAvailable = { roleGuard.isAvailable },
        isRoleHeld = { roleGuard.isHeld },
        isDefaultSmsApp = { defaultSmsAppGuard.isDefault },
        nowMillis = System::currentTimeMillis,
        scope = scope,
        logLost = { Analytics.callScreeningLost() },
        logResult = { result, source -> Analytics.callScreeningResult(result, source) },
    )

    private val checked = AtomicBoolean(false)
    private val _showReaskBanner = MutableStateFlow(false)
    val showReaskBanner: StateFlow<Boolean> = _showReaskBanner.asStateFlow()

    /** The role is held right now -- e.g. onboarding found it already held, or Settings resumed. */
    fun onRoleSeenHeld() {
        scope.launch { store.markRoleSeenHeld() }
    }

    /** The inbox opened. Only the first call per process does anything. */
    fun checkOnce() {
        if (!checked.compareAndSet(false, true)) return
        scope.launch {
            val held = isRoleHeld()
            if (held) {
                store.markRoleSeenHeld()
                return@launch
            }
            val state = store.read()
            val callEndEnabled = isCallEndEnabled()
            val available = isRoleAvailable()
            if (isCallScreeningLost(callEndEnabled, available, held, state) && !state.lossReported) {
                store.markLossReported()
                logLost()
            }
            _showReaskBanner.value = shouldShowCallScreeningReask(
                callEndEnabled = callEndEnabled,
                roleAvailable = available,
                roleHeld = held,
                isDefaultSmsApp = isDefaultSmsApp(),
                state = state,
                nowMillis = nowMillis(),
            )
        }
    }

    /** The inbox resumed: hide the banner if the role came back from anywhere. Never shows it. */
    fun onInboxResumed() {
        if (_showReaskBanner.value && isRoleHeld()) {
            _showReaskBanner.value = false
            onRoleSeenHeld()
        }
    }

    /** The role request launched from [source] came back. Re-checks the role rather than trusting
     * the result code. A decline from the banner counts as a dismissal. */
    internal fun onRoleRequestResult(source: CallScreeningSource) {
        val held = isRoleHeld()
        logResult(if (held) CallScreeningResult.GRANTED else CallScreeningResult.DECLINED, source)
        if (held) onRoleSeenHeld()
        if (source == CallScreeningSource.BANNER) {
            _showReaskBanner.value = false
            if (!held) recordDismissal()
        }
    }

    /** Onboarding's "Not now". */
    internal fun onOnboardingSkipped() {
        logResult(CallScreeningResult.SKIPPED, CallScreeningSource.ONBOARDING)
    }

    /** The banner's close button. */
    internal fun onReaskDismissed() {
        logResult(CallScreeningResult.SKIPPED, CallScreeningSource.BANNER)
        _showReaskBanner.value = false
        recordDismissal()
    }

    private fun recordDismissal() {
        val at = nowMillis()
        scope.launch { store.recordReaskDismissal(at) }
    }
}
