package text.message.sms.messaging.ui.screens.callend

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import text.message.sms.messaging.ads.CallEndAdState
import text.message.sms.messaging.ui.components.ads.AdShimmerPlaceholder
import text.message.sms.messaging.ui.components.ads.LoadedBannerAd
import text.message.sms.messaging.ui.components.ads.NativeAdCard
import text.message.sms.messaging.ui.components.ads.NativeAdCardShape

/**
 * The call-end screen's single ad slot: a native ad if one loaded, else a fixed-size banner
 * fallback, else nothing at all -- see [text.message.sms.messaging.ads.CallEndAdLoader]'s own doc
 * comment for the native-then-banner-then-collapse policy. Shows an animated shimmer placeholder,
 * sized to the eventual banner's 300x250 aspect ratio, while [adState] is still
 * [CallEndAdState.Loading]. The native ad itself renders through the shared [NativeAdCard].
 */
@Composable
internal fun CallEndAdSlot(adState: CallEndAdState, modifier: Modifier = Modifier) {
    when (adState) {
        CallEndAdState.Loading -> CallEndAdShimmer(modifier = modifier)
        is CallEndAdState.NativeLoaded -> NativeAdCard(nativeAd = adState.nativeAd, modifier = modifier)
        is CallEndAdState.BannerLoaded -> LoadedBannerAd(adView = adState.adView, modifier = modifier)
        CallEndAdState.Failed -> Unit
    }
}

/** [AdShimmerPlaceholder] sized to a 300x250 ad unit's aspect ratio scaled to the available width
 * -- neither format is known yet while this shows, and the banner fallback is 300x250. */
@Composable
private fun CallEndAdShimmer(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        AdShimmerPlaceholder(height = maxWidth * (250f / 300f), shape = NativeAdCardShape)
    }
}
