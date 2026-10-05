package text.message.sms.messaging.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import text.message.sms.messaging.ads.ChatInterstitialManager
import javax.inject.Inject

/** How long a left chat's previous screen gets to finish its pop transition and resume before the
 * exit ad is skipped -- the transition itself is [NavTransitionMillis]. */
private const val LeaveVisibleTimeoutMillis = 1_500L

/** Hands MessagingNavHost the app-scoped [ChatInterstitialManager]. */
@HiltViewModel
class ChatInterstitialViewModel @Inject constructor(
    val manager: ChatInterstitialManager,
) : ViewModel()

/**
 * Watches the back stack for an armed chat being left (see
 * [text.message.sms.messaging.ads.ChatInterstitialController.onBackStackChanged]). The exit ad
 * waits until the screen the chat was opened from has finished its pop transition -- its entry
 * reaching RESUMED -- and is still on top, so it never lands mid-transition.
 */
@Composable
internal fun ChatInterstitialLeaveWatcher(
    navController: NavHostController,
    manager: ChatInterstitialManager,
    activity: Activity?,
) {
    LaunchedEffect(navController, manager, activity) {
        navController.currentBackStack.collect { stack ->
            if (!manager.controller.onBackStackChanged(stack.map { it.id })) return@collect
            val origin = stack.last()
            launch {
                var visible = false
                try {
                    visible = withTimeoutOrNull(LeaveVisibleTimeoutMillis) {
                        origin.lifecycle.currentStateFlow.first { it == Lifecycle.State.RESUMED }
                    } != null && navController.currentBackStackEntry === origin
                } finally {
                    // Also on cancellation (Activity going away), so the leave never dangles.
                    manager.controller.onLeaveVisible(activity, visible)
                }
            }
        }
    }
}
