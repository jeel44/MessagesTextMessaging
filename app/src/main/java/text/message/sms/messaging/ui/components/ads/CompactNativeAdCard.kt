package text.message.sms.messaging.ui.components.ads

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import text.message.sms.messaging.R

/** The compact card's own rounding -- also what its placeholder should be clipped to. */
internal val CompactNativeAdCardShape = RoundedCornerShape(12.dp)

/** [R.layout.compact_native_ad_layout]'s minimum height -- for a placeholder that should take
 * the card's space while it loads. */
internal val CompactNativeAdCardHeight = 60.dp

private val CompactNativeAdCtaShape = RoundedCornerShape(16.dp)

/**
 * Renders a loaded [NativeAd] as a single ~60dp row via [R.layout.compact_native_ad_layout]:
 * icon | headline + body | "Ad" label | CTA, no media. The small-slot counterpart of
 * [NativeAdCard] (first user: Splash) -- a real View hierarchy for the same reason, with the CTA
 * as the shared [NativeAdCtaButton] in a nested [ComposeView]. Passing a different [nativeAd]
 * rebinds the same view in place.
 */
@Composable
internal fun CompactNativeAdCard(nativeAd: NativeAd, modifier: Modifier = Modifier) {
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
            LayoutInflater.from(context).inflate(R.layout.compact_native_ad_layout, null, false) as NativeAdView
        },
        update = { view ->
            val density = view.resources.displayMetrics.density

            val card = view.findViewById<LinearLayout>(R.id.compact_native_ad_card)
            card.background = GradientDrawable().apply {
                cornerRadius = 12f * density
                setColor(cardColor)
            }
            card.clipToOutline = true

            val attribution = view.findViewById<TextView>(R.id.compact_native_ad_attribution)
            attribution.text = adLabel
            attribution.setTextColor(attributionTextColor)
            attribution.background = GradientDrawable().apply {
                cornerRadius = 4f * density
                setColor(attributionBackground)
            }

            val icon = view.findViewById<ImageView>(R.id.compact_native_ad_icon)
            val iconAsset = nativeAd.icon
            if (iconAsset != null) {
                icon.visibility = View.VISIBLE
                icon.setImageDrawable(iconAsset.drawable)
            } else {
                icon.visibility = View.GONE
            }

            val headline = view.findViewById<TextView>(R.id.compact_native_ad_headline)
            headline.text = nativeAd.headline.orEmpty()
            headline.setTextColor(headlineColor)

            val body = view.findViewById<TextView>(R.id.compact_native_ad_body)
            val bodyText = nativeAd.body
            body.visibility = if (bodyText.isNullOrBlank()) View.GONE else View.VISIBLE
            body.text = bodyText
            body.setTextColor(bodyColor)

            val ctaView = view.findViewById<ComposeView>(R.id.compact_native_ad_cta)
            ctaView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            ctaView.setContent {
                NativeAdCtaButton(
                    text = ctaLabel,
                    height = 32.dp,
                    fontSize = 13.sp,
                    shape = CompactNativeAdCtaShape,
                    horizontalPadding = 14.dp,
                )
            }

            view.iconView = icon
            view.headlineView = headline
            view.bodyView = body
            view.callToActionView = ctaView
            view.setNativeAd(nativeAd)
        },
    )
}
