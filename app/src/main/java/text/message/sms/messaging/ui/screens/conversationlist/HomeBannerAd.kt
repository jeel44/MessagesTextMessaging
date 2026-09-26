package text.message.sms.messaging.ui.screens.conversationlist

import android.view.ViewGroup
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import text.message.sms.messaging.ads.AdConsentState
import text.message.sms.messaging.ads.BannerAdLoader
import text.message.sms.messaging.ads.BannerAdState
import text.message.sms.messaging.ads.BannerRequestStatus
import text.message.sms.messaging.ads.HomeBannerAdManager
import text.message.sms.messaging.ui.components.ads.AdShimmerPlaceholder

/**
 * Home's bottom anchored adaptive banner, for [ConversationListScreen]'s Scaffold `bottomBar`.
 * [manager] decides when to request (see [HomeBannerAdManager]); this slot only feeds it the
 * Activity context, the adaptive size and Home's visibility, and renders the current ad.
 *
 * Visibility is this destination's own lifecycle (Home's NavBackStackEntry, via
 * [LifecycleStartEffect]) -- started only while Home is on screen and the Activity is started, so
 * a return from the background, from the ad's click-through, or from another in-app screen all
 * arrive as the same ON_START.
 *
 * Pads itself above the navigation bar (the app is edge-to-edge), so a collapsed slot still
 * leaves the list's bottom inset where it was. Shimmers at the ad's exact height while consent is
 * pending or any request is in flight -- the first load and every refresh alike, the current ad
 * giving way to the shimmer until its replacement arrives. Empty if consent doesn't allow ads or
 * the latest request failed (the next request that succeeds brings it back).
 */
@Composable
internal fun HomeBannerAd(
    manager: HomeBannerAdManager,
    consent: AdConsentState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Full-width slot, so the screen width is the banner width -- known here, before layout,
    // so the slot is attached before the start effect below fires.
    val widthDp = LocalConfiguration.current.screenWidthDp
    val adSize = remember(context, widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }

    // Declared before the start effect: effects run in order, and ON_START is delivered the
    // moment that one registers, so it must already find this slot attached.
    DisposableEffect(manager, context, adSize) {
        manager.attach(context, adSize)
        onDispose {}
    }
    LifecycleStartEffect(manager) {
        manager.onHomeStarted()
        onStopOrDispose { manager.onHomeStopped() }
    }

    val loader by manager.loader.collectAsStateWithLifecycle()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .animateContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        val current = loader
        if (current != null) HomeBannerContent(loader = current, consent = consent, adSize = adSize)
    }
}

@Composable
private fun HomeBannerContent(loader: BannerAdLoader, consent: AdConsentState, adSize: AdSize) {
    LifecycleResumeEffect(loader) {
        loader.resume()
        onPauseOrDispose { loader.pause() }
    }

    val state by loader.state.collectAsStateWithLifecycle()
    val requestStatus by loader.requestStatus.collectAsStateWithLifecycle()
    val shimmer = @Composable { AdShimmerPlaceholder(height = adSize.height.dp, shape = RectangleShape) }
    when {
        consent == AdConsentState.Pending -> shimmer()
        consent != AdConsentState.Allowed -> Unit
        // Checked before [state]: that stays Loaded through a refresh, and through a failed one.
        requestStatus == BannerRequestStatus.InFlight -> shimmer()
        requestStatus == BannerRequestStatus.Failed -> Unit
        else -> when (val current = state) {
            BannerAdState.Loading -> shimmer()
            is BannerAdState.Loaded -> key(current.adView) { ReattachedAdView(current.adView) }
            BannerAdState.Failed -> Unit
        }
    }
}

/** Like [text.message.sms.messaging.ui.components.ads.LoadedBannerAd], but for an [AdView] that
 * leaves and re-enters composition: every refresh swaps it out for the shimmer and back, and
 * [HomeBannerAdManager] keeps it across in-app navigation -- either way it comes back still inside
 * the previous (now detached) holder. */
@Composable
private fun ReattachedAdView(adView: AdView) {
    AndroidView(
        factory = {
            (adView.parent as? ViewGroup)?.removeView(adView)
            adView
        },
    )
}
