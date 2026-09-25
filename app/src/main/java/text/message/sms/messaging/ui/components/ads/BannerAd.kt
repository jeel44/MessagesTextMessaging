package text.message.sms.messaging.ui.components.ads

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import dagger.hilt.android.EntryPointAccessors
import text.message.sms.messaging.ads.AdConsentEntryPoint
import text.message.sms.messaging.ads.AdConsentManager
import text.message.sms.messaging.ads.AdConsentState
import text.message.sms.messaging.ads.BannerAdLoader
import text.message.sms.messaging.ads.BannerAdState

private const val SHIMMER_SWEEP_DURATION_MILLIS = 1400

/**
 * A full-width anchored adaptive banner for [adUnitId], with an [AdShimmerPlaceholder] reserving
 * its exact height while it loads -- the adaptive size is known before the request goes out, so
 * the real creative replaces the shimmer without shifting anything around it. Collapses (animated)
 * to nothing if the load fails.
 *
 * Owns its own [BannerAdLoader] for screens without a ViewModel to hold one: loaded once
 * [AdConsentManager] allows ad requests (shimmering until then, collapsed if consent never allows
 * them), paused/resumed with the lifecycle, destroyed when it leaves composition. Keyed on
 * the available width, so a rotation reloads at the new orientation's adaptive size -- which
 * Google's adaptive-banner guidance calls for anyway.
 */
@Composable
internal fun BannerAdWithShimmer(adUnitId: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().animateContentSize()) {
        val context = LocalContext.current
        val widthDp = maxWidth.value.toInt()
        val adSize = remember(widthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        }
        val loader = remember(adUnitId, adSize) { BannerAdLoader(context, adUnitId, adSize) }
        val consentManager = remember(context) {
            EntryPointAccessors.fromApplication(context.applicationContext, AdConsentEntryPoint::class.java)
                .adConsentManager()
        }
        val consent by consentManager.state.collectAsStateWithLifecycle()

        DisposableEffect(loader) {
            onDispose { loader.destroy() }
        }
        LaunchedEffect(loader, consent) {
            if (consent == AdConsentState.Allowed) loader.start()
        }
        // Declared after the effect above so it disposes first: pause, then destroy.
        LifecycleResumeEffect(loader) {
            loader.resume()
            onPauseOrDispose { loader.pause() }
        }

        val state by loader.state.collectAsStateWithLifecycle()
        when (val current = state.takeIf { consent == AdConsentState.Allowed }) {
            null -> if (consent == AdConsentState.Pending) {
                AdShimmerPlaceholder(height = adSize.height.dp, shape = RectangleShape)
            }
            BannerAdState.Loading -> AdShimmerPlaceholder(height = adSize.height.dp, shape = RectangleShape)
            is BannerAdState.Loaded -> LoadedBannerAd(adView = current.adView)
            BannerAdState.Failed -> Unit
        }
    }
}

/** Placeholder box of [height], clipped to [shape], with an animated light band sweeping
 * continuously left-to-right across it -- unlike
 * [text.message.sms.messaging.ui.components.shineEffect]'s periodic sweep, a loading placeholder
 * should read as "still working" for as long as it's up. */
@Composable
internal fun AdShimmerPlaceholder(height: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    val infiniteTransition = rememberInfiniteTransition(label = "ad-shimmer")
    val sweep = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_SWEEP_DURATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-sweep",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(baseColor)
            .drawWithContent {
                drawContent()
                val bandWidth = size.width * 0.5f
                val x = -bandWidth + sweep.value * (size.width + bandWidth)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, highlightColor, Color.Transparent),
                        start = Offset(x, 0f),
                        end = Offset(x + bandWidth, 0f),
                    ),
                )
            },
    )
}

/** An already-loaded banner [AdView], centered at its own ad size rather than stretched to fill
 * the width, since resizing a loaded ad's rendered creative isn't something publishers are allowed
 * to do. */
@Composable
internal fun LoadedBannerAd(adView: AdView, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(factory = { adView })
    }
}
