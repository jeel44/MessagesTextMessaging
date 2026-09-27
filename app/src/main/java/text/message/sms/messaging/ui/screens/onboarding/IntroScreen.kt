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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
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
import text.message.sms.messaging.ads.NativeAdState
import text.message.sms.messaging.ui.components.ads.AdShimmerPlaceholder
import text.message.sms.messaging.ui.components.ads.MediumNativeAdCard
import text.message.sms.messaging.ui.components.ads.MediumNativeAdCardReservedHeight
import text.message.sms.messaging.ui.components.ads.NativeAdCardShape
import text.message.sms.messaging.ui.theme.Pill

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
 * Onboarding's last step, between Language's Apply and the inbox: three Lottie slides in a
 * [HorizontalPager], a dots indicator, a Next button ("Get started" on the last slide, which
 * finishes onboarding via [IntroViewModel.onFinishClicked] -- interstitial first, if ready -- then
 * calls [onFinished]), and a native ad slot below it all (see [IntroViewModel] for the ad rules).
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
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(Unit) { viewModel.startAds() }

    IntroScreenContent(
        nativeAdState = nativeAdState,
        onPageSettled = viewModel::onPageSettled,
        onGetStarted = { viewModel.onFinishClicked(activity, onDone = onFinished) },
    )
}

/** [IntroScreen]'s UI with no ViewModel or ad requests of its own -- what render tests use. */
@Composable
internal fun IntroScreenContent(
    nativeAdState: NativeAdState,
    onPageSettled: (Int) -> Unit,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
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
            .systemBarsPadding(),
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

        Button(
            onClick = {
                if (isLastPage) {
                    onGetStarted()
                } else {
                    scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                }
            },
            shape = Pill,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .height(52.dp),
        ) {
            Text(stringResource(if (isLastPage) R.string.intro_get_started else R.string.intro_next))
        }

        // Below Next, not above, like Language's slot -- the ad's own CTA is never stacked right
        // against the button.
        IntroNativeAdSlot(
            state = nativeAdState,
            showsAd = IntroSlides[currentPage].showsAd,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        )
    }
}

/**
 * The animation takes a fixed share of the page and the text block the rest, top-aligned, so the
 * animation area and the title sit at the same height on every slide however long each
 * slide's text is.
 */
@Composable
private fun IntroPage(slide: IntroSlide, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(slide.animation))
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f)
                .padding(top = 24.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
                .padding(top = 24.dp),
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
