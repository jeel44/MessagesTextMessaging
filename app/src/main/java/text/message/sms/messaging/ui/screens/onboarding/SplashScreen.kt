package text.message.sms.messaging.ui.screens.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.R
import text.message.sms.messaging.ads.NativeAdState
import text.message.sms.messaging.ui.components.ads.AdShimmerPlaceholder
import text.message.sms.messaging.ui.components.ads.CompactNativeAdCard
import text.message.sms.messaging.ui.components.ads.CompactNativeAdCardHeight
import text.message.sms.messaging.ui.components.ads.CompactNativeAdCardShape

/**
 * The app's first destination. MainActivity's `installSplashScreen()` keeps the system splash
 * (brand icon, see `Theme.App.Starting` in themes.xml) over this screen until [onBrandingVisible]
 * fires or Splash is left, so for most launches this UI is never actually seen: [SplashViewModel]
 * reads the onboarding flag and hands off to [onOnboardingComplete] or [onOnboardingIncomplete]
 * with no artificial delay.
 *
 * The exception is a returning user's launch eligible for an App Open ad (see
 * [text.message.sms.messaging.ads.AppOpenAdManager]): the system splash is released so this
 * screen's own branding is visible, an ads disclosure and a compact native ad appear at the
 * bottom, and once that resolves the App Open ad (if it loads in time) is shown over it all; the
 * hand-off happens once it's dismissed. New users and [isDeepLinkLaunch] launches (notification
 * tap, call-end hand-off) always skip that entirely.
 *
 * The branding icon is `ic_splash_logo`, the same drawable `Theme.App.Starting` gives the system
 * splash, so the two match on hand-off.
 *
 * The full-bleed background is [MaterialTheme.colorScheme.primary] rather than
 * `primaryContainer`: `primary`/`onPrimary` is the pairing Material 3 guarantees legible contrast
 * for -- by design, in both the dynamic-color and hand-authored fallback schemes -- and it reads
 * as a confident, branded splash the way `primaryContainer`'s deliberately muted tone would not.
 */
@Composable
internal fun SplashScreen(
    isDeepLinkLaunch: Boolean,
    onBrandingVisible: () -> Unit,
    onOnboardingComplete: () -> Unit,
    onOnboardingIncomplete: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val adSectionVisible by viewModel.adSectionVisible.collectAsStateWithLifecycle()
    val nativeAdState by viewModel.nativeAdState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(Unit) { viewModel.start(isDeepLinkLaunch) }

    LaunchedEffect(step) {
        when (val current = step) {
            SplashStep.Deciding -> Unit
            SplashStep.Holding -> onBrandingVisible()
            SplashStep.ShowAd -> {
                onBrandingVisible()
                viewModel.showAd(activity)
            }
            is SplashStep.Done ->
                if (current.onboardingComplete) onOnboardingComplete() else onOnboardingIncomplete()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = null,
                    modifier = Modifier.size(SPLASH_MARK_SIZE),
                )

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(top = 24.dp),
                )

                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (adSectionVisible) {
                SplashAdSection(nativeAdState = nativeAdState)
            }
        }
    }
}

/** The disclosure line, then the compact native slot: shimmer while it loads, the card once it
 * has, nothing if it failed or timed out. The disclosure stays either way -- an App Open ad may
 * still follow. */
@Composable
private fun SplashAdSection(nativeAdState: NativeAdState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.splash_ads_disclosure),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        when (nativeAdState) {
            NativeAdState.Loading -> AdShimmerPlaceholder(
                height = CompactNativeAdCardHeight,
                shape = CompactNativeAdCardShape,
                modifier = Modifier.padding(top = 8.dp),
            )
            is NativeAdState.Loaded -> CompactNativeAdCard(
                nativeAd = nativeAdState.nativeAd,
                modifier = Modifier.padding(top = 8.dp),
            )
            NativeAdState.Failed -> Unit
        }
    }
}

private val SPLASH_MARK_SIZE = 128.dp

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
