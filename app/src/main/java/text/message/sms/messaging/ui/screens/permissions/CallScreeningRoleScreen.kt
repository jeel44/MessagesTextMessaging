package text.message.sms.messaging.ui.screens.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.hilt.navigation.compose.hiltViewModel
import text.message.sms.messaging.R
import text.message.sms.messaging.service.CallScreeningRoleGuard
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
 * Onboarding's request for `ROLE_CALL_SCREENING` ([CallScreeningRoleGuard]), between
 * [text.message.sms.messaging.ui.screens.onboarding.WelcomeScreen] and
 * [text.message.sms.messaging.ui.screens.onboarding.SetDefaultSmsScreen] (see
 * [text.message.sms.messaging.ui.navigation.MessagingNavHost]). Holding the role is what makes the
 * system open the call-end screen after each call (see
 * [text.message.sms.messaging.ui.screens.callend.PostCallActivity]).
 *
 * Never blocks onboarding: [onDone] fires when the role request comes back, whether it was granted
 * or declined (same "re-check, don't trust the result code" rule as SetDefaultSmsScreen, but
 * either way the user moves on); on "Not now", which doesn't open the dialog at all; and straight
 * away -- without showing anything -- when the role is already held or isn't available on this
 * device or API level. The user can grant it later from Settings. Each user-driven outcome is
 * reported to [text.message.sms.messaging.service.CallScreeningRoleTracker] (via
 * [CallScreeningRoleViewModel]), which also remembers that the role was granted.
 *
 * The dialog is never opened automatically: only by the button.
 *
 * [onDone] runs from [LaunchedEffect]/the result callback, never from composition, so a
 * recomposition can't fire a second `navController.navigate`.
 */
@Composable
fun CallScreeningRoleScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallScreeningRoleViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val guard = remember(context) { CallScreeningRoleGuard(context.applicationContext) }

    // Seeded once: "was there anything to ask for when we arrived", not something to re-read.
    val nothingToAsk = remember(context) { !guard.isAvailable || guard.isHeld }
    LaunchedEffect(nothingToAsk) {
        if (nothingToAsk) {
            if (guard.isHeld) viewModel.onRoleAlreadyHeld()
            onDone()
        }
    }
    if (nothingToAsk) return

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onRoleRequestResult()
        onDone()
    }

    CallScreeningRoleScreenContent(
        onAllowClick = {
            // Never null here: nothingToAsk already covers API < 29.
            guard.buildRoleRequestIntent()?.let(roleRequestLauncher::launch) ?: onDone()
        },
        onNotNowClick = {
            viewModel.onNotNow()
            onDone()
        },
        modifier = modifier,
    )
}

/**
 * The screen's visual content, with no role check or launch -- factored out so it can be exercised
 * by a `@Preview`. [CallScreeningRoleScreen] above is the only real caller.
 */
@Composable
internal fun CallScreeningRoleScreenContent(
    onAllowClick: () -> Unit,
    onNotNowClick: () -> Unit,
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
                CallScreeningRoleDeviceFrame(modifier = Modifier.padding(vertical = 16.dp))

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.call_screening_role_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = onboardingPrimaryTextColor(),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.call_screening_role_body),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = onboardingSecondaryTextColor(OnboardingSubtitleGray),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))

                ShineButton(
                    text = stringResource(R.string.call_screening_role_button),
                    onClick = onAllowClick,
                    modifier = Modifier.padding(horizontal = 18.dp),
                    showHalo = true,
                )

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(onClick = onNotNowClick) {
                    Text(
                        text = stringResource(R.string.call_screening_role_not_now),
                        fontSize = 15.sp,
                        color = onboardingSecondaryTextColor(OnboardingSubtitleGray),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.call_screening_role_reassurance),
                    fontSize = 13.sp,
                    color = onboardingSecondaryTextColor(OnboardingSubtitleGray),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Decorative mock-up of the system's caller ID & spam app choice, illustrating what the button
 * asks for. Purely illustrative -- the rows and radio here have no function.
 */
@Composable
private fun CallScreeningRoleDeviceFrame(modifier: Modifier = Modifier) {
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
        MockRoleRow(text = stringResource(R.string.call_screening_role_mock_row_title))
        MockRoleRow(text = stringResource(R.string.call_screening_role_mock_row_app), showAppIcon = true)
    }
}

@Composable
private fun MockRoleRow(
    text: String,
    modifier: Modifier = Modifier,
    showAppIcon: Boolean = false,
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
            if (showAppIcon) {
                Spacer(modifier = Modifier.width(10.dp))
                MockRadioSelected()
            }
        }
    }
}

private val MockRowTextDark = Color(0xFF202124)

@Composable
private fun MockRadioSelected(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .border(2.dp, ConversationFabBlue, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(ConversationFabBlue, CircleShape),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CallScreeningRoleScreenPreview() {
    AppTheme {
        CallScreeningRoleScreenContent(onAllowClick = {}, onNotNowClick = {})
    }
}
