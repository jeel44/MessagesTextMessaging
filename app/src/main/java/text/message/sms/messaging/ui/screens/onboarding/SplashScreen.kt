package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import text.message.sms.messaging.R

/**
 * The app's first destination. MainActivity's `installSplashScreen()` keeps the system splash
 * (brand icon, see `Theme.App.Starting` in themes.xml) over this screen until [onBrandingVisible]
 * fires or Splash is left, so for most launches this UI is never actually seen: [SplashViewModel]
 * reads the onboarding flag and hands off to [onOnboardingComplete] or [onOnboardingIncomplete]
 * with no artificial delay.
 *
 * The exception is a returning user's launch eligible for an App Open ad (see
 * [text.message.sms.messaging.ads.AppOpenAdManager]): the system splash is released so this
 * screen's own branding is visible for a moment, the ad (if it loads in time) is shown over it,
 * and the hand-off happens once it's dismissed. New users and [isDeepLinkLaunch] launches
 * (notification tap, call-end hand-off) always skip that entirely.
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SplashMark(modifier = Modifier.size(SPLASH_MARK_SIZE))

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
    }
}

/**
 * The Lottie slot, sized consistently so dropping in the real `splash_animation.json` later
 * needs no layout changes. [composition] is `null` both while it is still loading and if it
 * failed to parse (which is expected right now -- the checked-in file is a placeholder, not a
 * real export, see `res/raw/splash_animation.json`) -- either way [SplashFallbackMark] covers it
 * rather than leaving a blank space.
 */
@Composable
private fun SplashMark(modifier: Modifier = Modifier) {
    val compositionResult = rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.splash_animation))
    val composition = compositionResult.value

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                iterations = 1,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SplashFallbackMark(modifier = Modifier.fillMaxSize())
        }
    }
}

/** A simple Compose-drawn mark: no illustration asset, just the theme's own colors and a stock
 * icon, so it never looks like an unfinished/missing-image placeholder. */
@Composable
private fun SplashFallbackMark(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "splash-fallback-pulse")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash-fallback-scale",
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Sms,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(SPLASH_MARK_SIZE / 2)
                .scale(scale),
        )
    }
}

private val SPLASH_MARK_SIZE = 180.dp

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
