package text.message.sms.messaging.ui.screens.onboarding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How long after the last language tap [LanguageScreen]'s confirm check appears. */
internal const val CONFIRM_REVEAL_DELAY_MILLIS = 2_000L

/**
 * Drives [LanguageScreen]'s confirm check: hidden until [delayMillis] after the last
 * [onSelection], then visible for good -- later selections never hide it again or restart
 * anything. Each selection before then cancels the pending reveal and starts a fresh one, so it
 * lands [delayMillis] after the *last* tap.
 *
 * The pending reveal runs on [scope] ([LanguageViewModel]'s viewModelScope in the app), so it
 * survives rotation and is cancelled with the screen -- it can never fire after the screen's gone.
 */
internal class ConfirmRevealTimer(
    private val scope: CoroutineScope,
    private val delayMillis: Long = CONFIRM_REVEAL_DELAY_MILLIS,
    initiallyVisible: Boolean = false,
) {
    private val _visible = MutableStateFlow(initiallyVisible)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private var pending: Job? = null

    fun onSelection() {
        if (_visible.value) return
        pending?.cancel()
        pending = scope.launch {
            delay(delayMillis)
            _visible.value = true
        }
    }
}
