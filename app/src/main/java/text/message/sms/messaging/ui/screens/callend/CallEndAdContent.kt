package text.message.sms.messaging.ui.screens.callend

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import text.message.sms.messaging.R
import text.message.sms.messaging.ads.CallEndAdState
import text.message.sms.messaging.ui.components.ads.AdShimmerPlaceholder
import text.message.sms.messaging.ui.components.ads.LoadedBannerAd
import text.message.sms.messaging.ui.components.shineEffect
import text.message.sms.messaging.ui.theme.ConversationFabBlue

private val AdCardShape = RoundedCornerShape(16.dp)
private const val CTA_SHINE_PERIOD_MILLIS = 3000

/**
 * The call-end screen's single ad slot: a native ad if one loaded, else a fixed-size banner
 * fallback, else nothing at all -- see [text.message.sms.messaging.ads.CallEndAdLoader]'s own doc
 * comment for the native-then-banner-then-collapse policy. Shows an animated shimmer placeholder,
 * sized to the eventual banner's 300x250 aspect ratio, while [adState] is still
 * [CallEndAdState.Loading].
 */
@Composable
internal fun CallEndAdSlot(adState: CallEndAdState, modifier: Modifier = Modifier) {
    when (adState) {
        CallEndAdState.Loading -> CallEndAdShimmer(modifier = modifier)
        is CallEndAdState.NativeLoaded -> CallEndNativeAd(nativeAd = adState.nativeAd, modifier = modifier)
        is CallEndAdState.BannerLoaded -> LoadedBannerAd(adView = adState.adView, modifier = modifier)
        CallEndAdState.Failed -> Unit
    }
}

/** [AdShimmerPlaceholder] sized to a 300x250 ad unit's aspect ratio scaled to the available width
 * -- neither format is known yet while this shows, and the banner fallback is 300x250. */
@Composable
private fun CallEndAdShimmer(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        AdShimmerPlaceholder(height = maxWidth * (250f / 300f), shape = AdCardShape)
    }
}

/**
 * Renders a loaded [NativeAd] via [R.layout.native_ad_layout] -- a real Android View hierarchy
 * wrapped in [NativeAdView], not pure Compose, because the Native Ads API requires registering
 * genuine [View] instances (icon/headline/body/media/CTA) for its own click and impression
 * tracking (see [NativeAdView.setNativeAd]'s own docs); [MediaView] specifically must be that exact
 * SDK class, since it renders the ad's image/video itself.
 *
 * The CTA is the one asset rendered via a nested [ComposeView] (see [NativeAdCtaButton]) so it can
 * carry [shineEffect] and the app's [ConversationFabBlue] styling; icon, headline, body and media
 * stay plain Views, matching the "no shine anywhere else on this card" requirement.
 */
@Composable
internal fun CallEndNativeAd(nativeAd: NativeAd, modifier: Modifier = Modifier) {
    val headlineColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val attributionBackground = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val attributionTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val adLabel = stringResource(R.string.call_end_ad_label)
    val ctaLabel = nativeAd.callToAction.orEmpty()

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.native_ad_layout, null, false) as NativeAdView
        },
        update = { view ->
            val density = view.resources.displayMetrics.density

            val card = view.findViewById<LinearLayout>(R.id.native_ad_card)
            card.background = GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(cardColor)
            }
            card.clipToOutline = true

            val attribution = view.findViewById<TextView>(R.id.ad_attribution)
            attribution.text = adLabel
            attribution.setTextColor(attributionTextColor)
            attribution.background = GradientDrawable().apply {
                cornerRadius = 4f * density
                setColor(attributionBackground)
            }

            val icon = view.findViewById<ImageView>(R.id.native_ad_icon)
            val iconAsset = nativeAd.icon
            if (iconAsset != null) {
                icon.visibility = View.VISIBLE
                icon.setImageDrawable(iconAsset.drawable)
            } else {
                icon.visibility = View.GONE
            }

            val headline = view.findViewById<TextView>(R.id.native_ad_headline)
            headline.text = nativeAd.headline.orEmpty()
            headline.setTextColor(headlineColor)

            val body = view.findViewById<TextView>(R.id.native_ad_body)
            val bodyText = nativeAd.body
            body.visibility = if (bodyText.isNullOrBlank()) View.GONE else View.VISIBLE
            body.text = bodyText
            body.setTextColor(bodyColor)

            val media = view.findViewById<MediaView>(R.id.native_ad_media)
            nativeAd.mediaContent?.let { media.mediaContent = it }

            val ctaView = view.findViewById<ComposeView>(R.id.native_ad_cta)
            ctaView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            ctaView.setContent { NativeAdCtaButton(text = ctaLabel) }

            view.iconView = icon
            view.headlineView = headline
            view.bodyView = body
            view.mediaView = media
            view.callToActionView = ctaView
            view.setNativeAd(nativeAd)
        },
    )
}

private val NativeAdCtaShape = RoundedCornerShape(12.dp)

/** The native ad's call-to-action -- the only piece of [CallEndNativeAd] that gets [shineEffect];
 * icon, headline, body and media stay static. Deliberately not `.clickable`:
 * [NativeAdView.setCallToActionView] installs its own click handling directly on the [ComposeView]
 * this is hosted in (see [CallEndNativeAd]), and an inner Compose click target would intercept the
 * touch before that outer View-level listener ever saw it. */
@Composable
private fun NativeAdCtaButton(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(NativeAdCtaShape)
            .background(ConversationFabBlue)
            .shineEffect(periodMillis = CTA_SHINE_PERIOD_MILLIS),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
