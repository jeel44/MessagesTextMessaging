package text.message.sms.messaging.ui.screens.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.launch
import text.message.sms.messaging.R
import text.message.sms.messaging.config.IntroAdConfig
import text.message.sms.messaging.ui.components.ShineButton
import text.message.sms.messaging.ads.NativeAdState
import text.message.sms.messaging.ui.components.ads.AdShimmerPlaceholder
import text.message.sms.messaging.ui.components.ads.FullScreenNativeAdCard
import text.message.sms.messaging.ui.components.ads.MediumNativeAdCard
import text.message.sms.messaging.ui.components.ads.MediumNativeAdCardReservedHeight
import text.message.sms.messaging.ui.components.ads.NativeAdCardShape

/** One Intro slide: its Lottie animation, title and subtitle. */
private data class IntroSlide(
    @param:RawRes val animation: Int,
    @param:StringRes val title: Int,
    @param:StringRes val subtitle: Int,
    /** Whether the shared native slot under the pager shows the ad on this slide -- slide 1 keeps
     * the slot's space, empty, so nothing moves between slides. */
    val showsAd: Boolean,
)

private val IntroSlides = listOf(
    IntroSlide(R.raw.intro_schedule, R.string.intro_schedule_title, R.string.intro_schedule_subtitle, showsAd = false),
    IntroSlide(R.raw.intro_swipe, R.string.intro_swipe_title, R.string.intro_swipe_subtitle, showsAd = true),
    IntroSlide(R.raw.intro_theme, R.string.intro_theme_title, R.string.intro_theme_subtitle, showsAd = true),
)

internal val IntroPageCount = IntroSlides.size

/**
 * Onboarding's second step, between Language's Apply and Welcome: three Lottie slides in a
 * [HorizontalPager], a dots indicator, a Next button ("Get started" on the last slide, which
 * leaves Intro via [IntroViewModel.onFinishClicked] -- interstitial first, if ready -- then calls
 * [onFinished]), and a native ad slot below it all (see [IntroViewModel] for the ad rules). Slide
 * 1's Next may first bring up a full-screen native as an overlay on this screen (not a separate
 * route or Activity); closing it lands on slide 2.
 *
 * System back steps to the previous slide; on the first slide it's left alone, so it behaves like
 * every other onboarding screen's back (the Activity finishes).
 *
 * Every color is a [MaterialTheme] token, so this follows the app's light/dark choice (system or
 * Settings' theme picker, applied once in MainActivity) with no detection of its own. The Lottie
 * files are drawn with their own baked-in colors -- no runtime recoloring.
 */
@Composable
internal fun IntroScreen(
    onFinished: () -> Unit,
    viewModel: IntroViewModel = hiltViewModel(),
) {
    val nativeAdState by viewModel.nativeAdState.collectAsStateWithLifecycle()
    val fullScreenNativeAd by viewModel.fullScreenNativeAd.collectAsStateWithLifecycle()
    val fullScreenAdvancePending by viewModel.fullScreenAdvancePending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val pagerState = rememberPagerState(pageCount = { IntroPageCount })

    LaunchedEffect(Unit) { viewModel.startAds() }

    // The full-screen native is done: land on slide 2 (instantly, under the ad), then drop it.
    LaunchedEffect(fullScreenAdvancePending) {
        if (!fullScreenAdvancePending) return@LaunchedEffect
        pagerState.scrollToPage(1)
        viewModel.onFullScreenNativeAdvanced()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        IntroScreenContent(
            nativeAdState = nativeAdState,
            onPageSettled = viewModel::onPageSettled,
            onGetStarted = { viewModel.onFinishClicked(activity, onDone = onFinished) },
            onFirstSlideNext = viewModel::onFirstSlideNext,
            pagerState = pagerState,
        )
        fullScreenNativeAd?.let { ad ->
            FullScreenNativeAdCard(
                nativeAd = ad,
                closeDelayMillis = IntroAdConfig.CLOSE_BUTTON_DELAY_MILLIS,
                onClose = viewModel::onFullScreenNativeClosed,
                onDisplayed = viewModel::onFullScreenNativeDisplayed,
                onCloseRevealed = viewModel::onFullScreenNativeCloseRevealed,
            )
        }
    }
}

/** The system bars on every side, plus the display cutout at the top: on a phone whose notch
 * reaches below its status bar, the status bar inset alone would leave the animation under it. */
private val IntroInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))

internal const val IntroAnimationTag = "intro_animation"

/** [IntroScreen]'s UI with no ViewModel or ad requests of its own -- what render tests use.
 * [onFirstSlideNext] is slide 1's Next: true means the full-screen native took over and the pager
 * stays put (the caller moves it on later), false means go to slide 2 now. */
@Composable
internal fun IntroScreenContent(
    nativeAdState: NativeAdState,
    onPageSettled: (Int) -> Unit,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
    onFirstSlideNext: () -> Boolean = { false },
    pagerState: PagerState = rememberPagerState(pageCount = { IntroPageCount }),
) {
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == IntroPageCount - 1

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(onPageSettled)
    }

    BackHandler(enabled = currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(IntroInsets),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            IntroPage(slide = IntroSlides[page])
        }

        IntroDots(
            pageCount = IntroPageCount,
            currentPage = currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 12.dp),
        )

        // Welcome's CTA (halo + shine sweep), so the two screens match. ShineButton reserves its own
        // halo space inside its bounds, so 17dp + its 7dp halo keeps the pill's face at the old 24dp
        // inset.
        ShineButton(
            text = stringResource(if (isLastPage) R.string.intro_get_started else R.string.intro_next),
            onClick = {
                when {
                    isLastPage -> onGetStarted()
                    currentPage == 0 && onFirstSlideNext() -> Unit
                    else -> scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                }
            },
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 4.dp),
        )

        // Below Next, not above, like Language's slot -- the ad's own CTA is never stacked right
        // against the button.
        IntroNativeAdSlot(
            state = nativeAdState,
            showsAd = IntroSlides[currentPage].showsAd,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        )
    }
}

/** Space above the animation (below the status bar and any notch), between it and the title,
 * and the least height the title + subtitle block is ever left (one-line title + three-line
 * subtitle is ~103dp at default font scale). */
private val AnimationTopGap = 32.dp
private val AnimationTextGap = 20.dp
private val TextBlockMinHeight = 120.dp

/** Inside the animation slot, above the drawing: intro_swipe's art runs to within ~4dp of the top
 * of its own canvas, so without this it reads as flush against [AnimationTopGap]. Applied on every
 * slide, inside the slot, so the slot's height -- and each title's position -- doesn't change. */
private val AnimationCanvasTopPadding = 12.dp

/** The animation's share of the page height when nothing else limits it. */
private const val ANIMATION_HEIGHT_FRACTION = 0.5f

/** The tallest slide's height/width (intro_swipe, 70/95): the slot never gets taller than that
 * slide can actually fill at full width, so there's no dead band between animation and title. */
private const val TALLEST_ANIMATION_ASPECT = 70f / 95f

/**
 * The animation is the hero: half the page height, capped so it never grows taller than the
 * tallest animation can fill at full width, and shrunk on short screens so the text block always
 * keeps [TextBlockMinHeight]. Every input is the page's own constraints -- identical on all three
 * pages -- so the slot and the title sit at the same height on every slide however long each
 * slide's text is. [LottieAnimation] draws with ContentScale.Fit (its default, set explicitly
 * here), so each file's own aspect ratio is kept, centered in the slot.
 */
@Composable
private fun IntroPage(slide: IntroSlide, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(slide.animation))
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val animationWidth = maxWidth - 32.dp
        val animationHeight = minOf(
            maxHeight * ANIMATION_HEIGHT_FRACTION,
            animationWidth * TALLEST_ANIMATION_ASPECT,
            maxHeight - AnimationTopGap - AnimationTextGap - TextBlockMinHeight,
        ).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(AnimationTopGap))
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .testTag(IntroAnimationTag)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(animationHeight)
                    .padding(top = AnimationCanvasTopPadding),
            )
            Spacer(modifier = Modifier.height(AnimationTextGap))
            IntroText(slide = slide, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun IntroText(slide: IntroSlide, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(slide.title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(slide.subtitle),
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The current page's dot stretches into a [MaterialTheme.colorScheme.primary] pill; the rest are
 * small [MaterialTheme.colorScheme.outlineVariant] dots. */
@Composable
private fun IntroDots(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) 20.dp else 8.dp, label = "intro-dot-width")
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

/** Always reserves [MediumNativeAdCardReservedHeight], so the pager above never moves: empty on a
 * slide with no ad, shimmer while the first load is in flight, the card once loaded, empty again
 * if the load failed. A refresh keeps the current card up until its replacement arrives (see
 * [text.message.sms.messaging.ads.NativeAdLoader.refresh]). */
@Composable
private fun IntroNativeAdSlot(state: NativeAdState, showsAd: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MediumNativeAdCardReservedHeight),
    ) {
        if (!showsAd) return@Box
        when (state) {
            NativeAdState.Loading -> AdShimmerPlaceholder(
                height = MediumNativeAdCardReservedHeight,
                shape = NativeAdCardShape,
            )
            is NativeAdState.Loaded -> MediumNativeAdCard(
                nativeAd = state.nativeAd,
                modifier = Modifier.heightIn(min = MediumNativeAdCardReservedHeight),
            )
            NativeAdState.Failed -> Unit
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
