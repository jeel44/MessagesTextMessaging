package text.message.sms.messaging.ui.components.ads

import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.delay
import text.message.sms.messaging.R

internal const val FullScreenNativeAdCloseTag = "fullscreen_native_ad_close"
internal const val FullScreenNativeAdShimmerTag = "fullscreen_native_ad_shimmer"

/** Past this width (tablets, landscape, unfolded foldables) the ad stays a centered column
 * instead of stretching its media and CTA edge to edge. */
private val MaxContentWidth = 600.dp
private val TopRowHeight = 56.dp
private val HorizontalPadding = 20.dp
private val IconSize = 48.dp
private val CtaHeight = 52.dp
private val CtaVerticalPadding = 16.dp

/** Keeps the headline clear of the AdChoices icon the SDK draws in the ad view's top-end
 * corner. */
private val AdChoicesClearance = 28.dp

/** Asset-area heights (everything under the badge/close row) below which the body loses lines:
 * it's the one optional asset, so it goes before the media is squeezed any further -- the CTA is
 * pinned regardless. */
private val BodyHiddenBelowHeight = 380.dp
private val BodyShortBelowHeight = 560.dp
private const val BODY_MAX_LINES = 3
private const val BODY_SHORT_MAX_LINES = 2

private val IconShape = RoundedCornerShape(12.dp)
private val MediaShape = RoundedCornerShape(16.dp)
private val CtaShape = RoundedCornerShape(14.dp)
private val TextLineShape = RoundedCornerShape(6.dp)

/** The media's widest and tallest shapes: 16:9 at full width, down to 1:1. */
private const val MEDIA_WIDEST_ASPECT = 16f / 9f
private const val MEDIA_TALLEST_ASPECT = 1f

/**
 * The media slot's size inside a [maxWidth] x [maxHeight] area: full width at an aspect between
 * 16:9 and 1:1, as tall as the area allows. An area too short for 16:9 at full width keeps 16:9
 * and narrows instead. Never a fixed height.
 */
internal fun fullScreenNativeMediaSize(maxWidth: Dp, maxHeight: Dp): DpSize {
    val shortest = maxWidth / MEDIA_WIDEST_ASPECT
    val tallest = maxWidth / MEDIA_TALLEST_ASPECT
    return if (maxHeight >= shortest) {
        DpSize(maxWidth, minOf(maxHeight, tallest))
    } else {
        DpSize(maxHeight * MEDIA_WIDEST_ASPECT, maxHeight)
    }
}

/** How many body lines fit an asset area [maxHeight] tall -- 0 hides the body. */
internal fun fullScreenNativeBodyMaxLines(maxHeight: Dp): Int = when {
    maxHeight < BodyHiddenBelowHeight -> 0
    maxHeight < BodyShortBelowHeight -> BODY_SHORT_MAX_LINES
    else -> BODY_MAX_LINES
}

/**
 * A loaded [NativeAd] filling the screen (first user: Intro's slide 1 Next): the "Ad" badge and,
 * [closeDelayMillis] after the ad is actually on screen, a close button; then icon + headline, the
 * media, the body and the CTA (see [FullScreenNativeAdAssets] for how they share the space).
 *
 * The assets are real Views registered on a [NativeAdView] for click/impression tracking, like
 * [NativeAdCard]'s, but laid out by Compose inside it ([NativeAdViewHost]) so the media and body
 * can adapt to any screen size. Colors are theme tokens, read here and passed into the Views.
 */
@Composable
internal fun FullScreenNativeAdCard(
    nativeAd: NativeAd,
    closeDelayMillis: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDisplayed: () -> Unit = {},
    onCloseRevealed: () -> Unit = {},
) {
    val headlineColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val ctaLabel = nativeAd.callToAction.orEmpty()
    val iconAsset = nativeAd.icon
    val bodyText = nativeAd.body?.takeIf { it.isNotBlank() }

    FullScreenNativeAdFrame(
        closeDelayMillis = closeDelayMillis,
        onClose = onClose,
        modifier = modifier,
        onDisplayed = onDisplayed,
        onCloseRevealed = onCloseRevealed,
    ) { onAssetsReady ->
        NativeAdViewHost(nativeAd = nativeAd, onBound = onAssetsReady) { adView ->
            FullScreenNativeAdAssets(
                icon = if (iconAsset != null) {
                    {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    adView.iconView = this
                                }
                            },
                            update = { it.setImageDrawable(iconAsset.drawable) },
                        )
                    }
                } else {
                    null
                },
                headline = {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context ->
                            TextView(context).apply {
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                                typeface = Typeface.DEFAULT_BOLD
                                maxLines = 2
                                ellipsize = TextUtils.TruncateAt.END
                                adView.headlineView = this
                            }
                        },
                        update = {
                            it.text = nativeAd.headline.orEmpty()
                            it.setTextColor(headlineColor)
                        },
                    )
                },
                media = {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            MediaView(context).apply {
                                setImageScaleType(ImageView.ScaleType.FIT_CENTER)
                                adView.mediaView = this
                            }
                        },
                        update = { view -> nativeAd.mediaContent?.let { view.mediaContent = it } },
                    )
                },
                body = if (bodyText != null) {
                    { lines ->
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { context ->
                                TextView(context).apply {
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                                    ellipsize = TextUtils.TruncateAt.END
                                    adView.bodyView = this
                                }
                            },
                            update = {
                                it.text = bodyText
                                it.maxLines = lines
                                it.setTextColor(bodyColor)
                            },
                        )
                    }
                } else {
                    null
                },
                cta = {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            ComposeView(context).apply { adView.callToActionView = this }
                        },
                        update = {
                            it.setContent {
                                NativeAdCtaButton(
                                    text = ctaLabel,
                                    height = CtaHeight,
                                    fontSize = 17.sp,
                                    shape = CtaShape,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                    )
                },
            )
        }
    }
}

/**
 * A [NativeAdView] whose content is Compose: [content] creates the asset Views (registering each
 * on the [NativeAdView] it's handed), then the ad is bound once they all exist, and [onBound] fires
 * two frames later -- once the bound assets have been laid out and drawn.
 */
@Composable
private fun NativeAdViewHost(
    nativeAd: NativeAd,
    onBound: () -> Unit,
    content: @Composable (NativeAdView) -> Unit,
) {
    val currentOnBound by rememberUpdatedState(onBound)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            NativeAdView(context).apply {
                addView(
                    ComposeView(context),
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        },
        update = { adView ->
            (adView.getChildAt(0) as ComposeView).setContent {
                content(adView)
                // After content(): effects run once the composition is applied, so every asset
                // View above has been created and registered by now.
                LaunchedEffect(nativeAd) {
                    adView.setNativeAd(nativeAd)
                    withFrameNanos {}
                    withFrameNanos {}
                    currentOnBound()
                }
            }
        },
    )
}

/**
 * The full-screen ad's chrome, with no ad of its own -- what render tests use, with stand-in
 * [content]. Top to bottom inside the safe-drawing insets (status/navigation bars in either
 * gesture or 3-button mode, display cutouts): the "Ad" badge and the close button in the top-end
 * corner, then [content] taking the rest.
 *
 * [content] calls `onAssetsReady` once its assets are rendered; until then a shimmer copy of
 * [FullScreenNativeAdAssets] covers it, so the screen is never blank. The close button appears
 * [closeDelayMillis] after that -- the time the ad has actually been visible -- and then stays.
 * Back does nothing until the close button is up, then acts like it. Touches never reach the
 * screen underneath.
 */
@Composable
internal fun FullScreenNativeAdFrame(
    closeDelayMillis: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDisplayed: () -> Unit = {},
    onCloseRevealed: () -> Unit = {},
    content: @Composable (onAssetsReady: () -> Unit) -> Unit,
) {
    // Not saveable: after a rotation the ad is laid out afresh, so it counts as displayed again
    // only once that's done. The close button, once revealed, survives it.
    var assetsReady by remember { mutableStateOf(false) }
    var closeVisible by rememberSaveable { mutableStateOf(false) }
    val currentOnDisplayed by rememberUpdatedState(onDisplayed)
    val currentOnCloseRevealed by rememberUpdatedState(onCloseRevealed)

    LaunchedEffect(assetsReady) {
        if (!assetsReady) return@LaunchedEffect
        currentOnDisplayed()
        if (closeVisible) return@LaunchedEffect
        delay(closeDelayMillis)
        closeVisible = true
        currentOnCloseRevealed()
    }

    BackHandler {
        if (closeVisible) onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                // Being hit at all keeps touches from reaching the Intro pager underneath.
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = MaxContentWidth)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TopRowHeight)
                    .padding(start = HorizontalPadding, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdAttributionBadge()
                Spacer(modifier = Modifier.weight(1f))
                if (closeVisible) CloseAdButton(onClick = onClose)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                content { assetsReady = true }
                if (!assetsReady) {
                    FullScreenNativeAdShimmer(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag(FullScreenNativeAdShimmerTag),
                    )
                }
            }
        }
    }
}

/**
 * The ad's assets, top to bottom: [icon] + [headline], [media] taking the flexible space (see
 * [fullScreenNativeMediaSize]), [body] (fewer lines, or none, on short screens -- see
 * [fullScreenNativeBodyMaxLines]), and [cta] pinned to the bottom. The CTA is laid out apart from
 * the rest, so nothing above it can ever push it off screen.
 */
@Composable
internal fun FullScreenNativeAdAssets(
    headline: @Composable () -> Unit,
    media: @Composable (DpSize) -> Unit,
    cta: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    body: (@Composable (maxLines: Int) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bodyMaxLines = fullScreenNativeBodyMaxLines(maxHeight)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = CtaHeight + CtaVerticalPadding * 2),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = HorizontalPadding, end = AdChoicesClearance, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Box(modifier = Modifier.size(IconSize).clip(IconShape)) { icon() }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Box(modifier = Modifier.weight(1f)) { headline() }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = HorizontalPadding, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val mediaSize = fullScreenNativeMediaSize(maxWidth, maxHeight)
                Box(modifier = Modifier.size(mediaSize)) { media(mediaSize) }
            }
            if (body != null && bodyMaxLines > 0) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = HorizontalPadding)) {
                    body(bodyMaxLines)
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = HorizontalPadding, vertical = CtaVerticalPadding)
                .height(CtaHeight),
        ) {
            cta()
        }
    }
}

/** [FullScreenNativeAdAssets]'s layout with [AdShimmerPlaceholder]s in every slot. */
@Composable
private fun FullScreenNativeAdShimmer(modifier: Modifier = Modifier) {
    FullScreenNativeAdAssets(
        modifier = modifier,
        icon = { AdShimmerPlaceholder(height = IconSize, shape = IconShape) },
        headline = { AdShimmerPlaceholder(height = 22.dp, shape = TextLineShape) },
        media = { size -> AdShimmerPlaceholder(height = size.height, shape = MediaShape) },
        body = { lines -> AdShimmerPlaceholder(height = (20 * lines).dp, shape = TextLineShape) },
        cta = { AdShimmerPlaceholder(height = CtaHeight, shape = CtaShape) },
    )
}

/** Same look as the other native cards' "Ad" label, a size up to suit a full screen. */
@Composable
private fun AdAttributionBadge() {
    Text(
        text = stringResource(R.string.call_end_ad_label),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A 48dp filled circle in the inverse-surface pair -- the highest-contrast tokens against the
 * surface the ad sits on, in either theme. Outside the [NativeAdView], so it can't register as an
 * ad click. */
@Composable
private fun CloseAdButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        modifier = Modifier
            .size(48.dp)
            .testTag(FullScreenNativeAdCloseTag),
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.ad_close),
        )
    }
}
