package text.message.sms.messaging.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import text.message.sms.messaging.R
import text.message.sms.messaging.service.DefaultSmsAppGuard
import text.message.sms.messaging.ui.components.ShineButton
import text.message.sms.messaging.ui.components.sharpIconPainter
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor
import text.message.sms.messaging.ui.theme.AppTheme
import text.message.sms.messaging.ui.theme.OnboardingSubtitleGray

/** Same reasoning and threshold as [WelcomeScreen]'s: below this available height the screen
 * becomes scrollable instead of clipping. */
private val MinComfortableHeight = 600.dp
private val IllustrationMaxWidth = 300.dp

/**
 * Second onboarding screen: asks the user to make Messages the default SMS app, which the
 * pipeline needs for write access to the SMS/MMS content providers (sending, receiving, marking
 * read, etc.).
 *
 * [onDefaultSet] fires once [DefaultSmsAppGuard.isDefault] is actually true after the request
 * flow returns -- never from the activity result code alone, since the user can back out of the
 * system dialog without an explicit deny and still get a "success-looking" result. That same
 * moment also calls [SetDefaultSmsViewModel.onDefaultSmsAppGranted], so a catch-up sync starts
 * right away instead of waiting for the next app launch.
 */
@Composable
fun SetDefaultSmsScreen(
    onDefaultSet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetDefaultSmsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val guard = remember(context) { DefaultSmsAppGuard(context.applicationContext) }

    var showDeclinedHint by remember { mutableStateOf(false) }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (guard.isDefault) {
            viewModel.onDefaultSmsAppGranted()
            onDefaultSet()
        } else {
            showDeclinedHint = true
        }
    }

    SetDefaultSmsScreenContent(
        showDeclinedHint = showDeclinedHint,
        onSetDefaultClick = { roleRequestLauncher.launch(guard.buildRoleRequestIntent()) },
        modifier = modifier,
    )
}

/**
 * The screen's actual visual content, with no [SetDefaultSmsViewModel]/role-request-launcher
 * dependency -- factored out purely so [SetDefaultSmsScreen]'s layout can be exercised by
 * `SetDefaultSmsScreenRenderTest` and a `@Preview` without needing a Hilt-backed
 * [androidx.hilt.navigation.compose.hiltViewModel] (this module has no Hilt test harness set up
 * yet). [SetDefaultSmsScreen] above is the only real caller; it owns the real role-request side
 * effect ([onSetDefaultClick] is just that composable's real callback passed straight through).
 */
@Composable
internal fun SetDefaultSmsScreenContent(
    showDeclinedHint: Boolean,
    onSetDefaultClick: () -> Unit,
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
                // The illustration is simply the tallest element in this centered group, so
                // centering the whole column vertically already lands it in the upper-middle of
                // the screen (roughly the top 45%) without needing its own separate placement.
                Image(
                    painter = sharpIconPainter(R.drawable.ic_default_sms_illustration),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = IllustrationMaxWidth)
                        .heightIn(max = 260.dp)
                        .padding(vertical = 16.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.set_default_sms_body),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = onboardingPrimaryTextColor(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Not part of the approved design's normal path -- only ever visible once the
                // user has already declined the role request once, so it can't be dropped without
                // losing the only in-screen hint that they can just tap the button again.
                if (showDeclinedHint) {
                    Text(
                        text = stringResource(R.string.set_default_sms_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = onboardingSecondaryTextColor(OnboardingSubtitleGray),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                }

                ShineButton(
                    text = stringResource(R.string.set_default_sms_button),
                    onClick = onSetDefaultClick,
                    modifier = Modifier.padding(horizontal = 18.dp),
                    showHalo = true,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetDefaultSmsScreenPreview() {
    AppTheme {
        SetDefaultSmsScreenContent(
            showDeclinedHint = false,
            onSetDefaultClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Declined once")
@Composable
private fun SetDefaultSmsScreenDeclinedPreview() {
    AppTheme {
        SetDefaultSmsScreenContent(
            showDeclinedHint = true,
            onSetDefaultClick = {},
        )
    }
}
