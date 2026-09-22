package text.message.sms.messaging.ui.screens.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import text.message.sms.messaging.R
import text.message.sms.messaging.service.OverlayPermissionGuard
import text.message.sms.messaging.ui.components.ShineButton
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor
import text.message.sms.messaging.ui.screens.onboarding.onboardingPrimaryTextColor
import text.message.sms.messaging.ui.screens.onboarding.onboardingSecondaryTextColor
import text.message.sms.messaging.ui.theme.AppTheme
import text.message.sms.messaging.ui.theme.ConversationFabBlue
import text.message.sms.messaging.ui.theme.OnboardingSubtitleGray
import text.message.sms.messaging.ui.theme.SurfaceContainerGray

private val MinComfortableHeight = 600.dp

/**
 * Explains and requests "draw over other apps" ([OverlayPermissionGuard]). Wired into onboarding
 * (see [text.message.sms.messaging.ui.navigation.MessagingNavHost], between
 * [text.message.sms.messaging.ui.screens.onboarding.WelcomeScreen] and
 * [text.message.sms.messaging.ui.screens.onboarding.SetDefaultSmsScreen]) ahead of the call-alert
 * overlay feature it's actually for, which isn't built yet -- nothing in this app draws over other
 * apps today.
 *
 * `ACTION_MANAGE_OVERLAY_PERMISSION` doesn't return a reliable result code (the user can back out
 * of the system page without an explicit deny, same as the default-SMS role request), so
 * [onGranted] fires from a plain on-resume re-check of [OverlayPermissionGuard.isGranted] rather
 * than trusting the launched intent's result -- simpler than watching `AppOpsManager` for the op
 * change, and this screen is always a full navigation destination the user returns to, so resume
 * is a reliable enough signal here.
 *
 * [onGranted] is called from [LaunchedEffect], not straight out of the composable body: this
 * screen re-renders (with [isGranted] still `true`) for reasons that have nothing to do with the
 * grant -- a config change, an unrelated recomposition -- and calling a `navController.navigate`
 * lambda directly from composition would fire again on every one of those, racing the `popUpTo`
 * from the first call. Keying on [isGranted] means it only actually runs on the false-to-true
 * transition.
 *
 * The Settings page also auto-launches ~1s after this screen first appears (a second, keyed-on-
 * `Unit` [LaunchedEffect], so it fires exactly once and never again on recomposition), on top of
 * the button already launching it on tap. The button stays live the whole time: the user can act
 * before the 1s delay elapses, and if they back out of Settings without granting, tapping it again
 * is still the only way back in since the auto-launch never fires a second time.
 */
@Composable
fun OverlayPermissionScreen(
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var isGranted by remember { mutableStateOf(OverlayPermissionGuard.isGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isGranted = OverlayPermissionGuard.isGranted(context)
    }

    LaunchedEffect(isGranted) {
        if (isGranted) onGranted()
    }

    // Guards only against the auto-launch firing a SECOND time on top of a manual tap that
    // already launched Settings within this same first second -- LaunchedEffect coroutines keep
    // running on real wall-clock time even while this Activity is stopped behind the Settings
    // page a tap just opened, so without this the delayed launch below fires anyway and stacks a
    // second Settings instance on top of the first (two back-presses to return). onAllowClick
    // deliberately never checks this flag itself -- it must stay live and always launch, so the
    // user can still retry after backing out of Settings without granting.
    var hasLaunchedSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1_000)
        if (!isGranted && !hasLaunchedSettings) {
            hasLaunchedSettings = true
            context.startActivity(OverlayPermissionGuard.buildRequestIntent(context))
        }
    }

    if (isGranted) return

    OverlayPermissionScreenContent(
        onAllowClick = {
            hasLaunchedSettings = true
            context.startActivity(OverlayPermissionGuard.buildRequestIntent(context))
        },
        modifier = modifier,
    )
}

/**
 * The screen's actual visual content, with no permission-check/launch side effect -- factored out
 * purely so it can be exercised by a `@Preview` without a real grant state. [OverlayPermissionScreen]
 * above is the only real caller; it owns the real [onAllowClick] side effect.
 */
@Composable
internal fun OverlayPermissionScreenContent(
    onAllowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = screenSurfaceColor()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            val useScroll = maxHeight < MinComfortableHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (useScroll) it.verticalScroll(rememberScrollState()) else it },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OverlayPermissionDeviceFrame(modifier = Modifier.padding(vertical = 16.dp))

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.overlay_permission_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = onboardingPrimaryTextColor(),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.overlay_permission_body),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = onboardingSecondaryTextColor(OnboardingSubtitleGray),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))

                ShineButton(
                    text = stringResource(R.string.overlay_permission_button),
                    onClick = onAllowClick,
                    modifier = Modifier.padding(horizontal = 18.dp),
                    showHalo = true,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.overlay_permission_reassurance),
                    fontSize = 13.sp,
                    color = onboardingSecondaryTextColor(OnboardingSubtitleGray),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Decorative mock-up of Android's "Display over other apps" settings screen, illustrating the
 * permission this screen is about to request. Purely illustrative Compose UI -- the toggles and
 * rows here have no function.
 */
@Composable
private fun OverlayPermissionDeviceFrame(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(275.dp)
            .border(
                width = 3.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(35.dp),
            )
            .background(SurfaceContainerGray, RoundedCornerShape(35.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        MockSettingsRow(text = stringResource(R.string.overlay_permission_mock_row_allow))
        MockSettingsRow(
            text = stringResource(R.string.overlay_permission_mock_row_app),
            showAppIcon = true,
        )
    }
}

@Composable
private fun MockSettingsRow(
    text: String,
    showAppIcon: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(13.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAppIcon) {
                Box(
                    modifier = Modifier
                        .size(23.dp)
                        .background(ConversationFabBlue, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = MockRowTextDark,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            MockToggleOn()
        }
    }
}

private val MockRowTextDark = Color(0xFF202124)

@Composable
private fun MockToggleOn(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 20.dp)
            .background(ConversationFabBlue, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(15.dp)
                .background(Color.White, CircleShape),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OverlayPermissionScreenPreview() {
    AppTheme {
        OverlayPermissionScreenContent(onAllowClick = {})
    }
}
