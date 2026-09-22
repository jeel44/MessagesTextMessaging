package text.message.sms.messaging.ui.screens.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import text.message.sms.messaging.R
import text.message.sms.messaging.ui.components.ShineButton
import text.message.sms.messaging.ui.components.sharpIconPainter
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor
import text.message.sms.messaging.ui.theme.AppTheme
import text.message.sms.messaging.ui.theme.OnboardingEyebrowGray
import text.message.sms.messaging.ui.theme.OnboardingSubtitleGray

// Placeholder URL -- replace with the real hosted Privacy Policy page.
private const val PRIVACY_POLICY_URL = "https://example.com/privacy"

/** Below this available height, the middle illustration switches from a flexible `weight(1f)`
 * slot to a capped fixed size and the whole screen becomes scrollable, so nothing clips on a very
 * small or unusually cropped viewport (e.g. split-screen, a tiny device, or a large font scale
 * pushing the text taller). Comfortably above a typical ~5" phone's content height in portrait, so
 * normal phones never scroll. */
private val MinComfortableHeight = 600.dp
private val IllustrationMaxWidth = 280.dp

/**
 * First screen after Splash: the approved-design welcome illustration and pitch. Requests
 * `CALL_PHONE` alone, silently -- no explanation UI, just the bare system dialog -- the moment
 * Continue is tapped, then advances via [onContinue] regardless of the result:
 * [text.message.sms.messaging.util.PhoneCalls] already falls back to `ACTION_DIAL` (no permission
 * needed) when it's denied, so this is
 * best-effort the same way the rest of the secondary permission set is, just requested a step
 * earlier than that batch. Every other runtime permission this app needs is requested later:
 * [SetDefaultSmsScreen] requests the core SMS/MMS set and the remaining secondary permissions
 * together, immediately after the default-SMS role grant; READ_CONTACTS is requested later still,
 * on first use, from [text.message.sms.messaging.ui.screens.newmessage.NewMessageScreen].
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val callPhonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // Result ignored either way -- best-effort, see this function's doc comment.
        onContinue()
    }

    WelcomeScreenContent(
        onContinueClick = { callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE) },
        modifier = modifier,
    )
}

/**
 * The screen's actual visual content -- factored out purely so [WelcomeScreen]'s layout can be
 * exercised by `WelcomeScreenRenderTest` and a `@Preview`. [WelcomeScreen] above is the only real
 * caller; it owns the real [onContinueClick] side effect.
 */
@Composable
internal fun WelcomeScreenContent(
    onContinueClick: () -> Unit,
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
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = stringResource(R.string.welcome_eyebrow),
                    fontSize = 16.sp,
                    color = onboardingSecondaryTextColor(OnboardingEyebrowGray),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    color = onboardingPrimaryTextColor(),
                )

                val illustration = Modifier
                    .widthIn(max = IllustrationMaxWidth)
                    .padding(vertical = 16.dp)
                Image(
                    painter = sharpIconPainter(R.drawable.ic_welcome_illustration),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = if (useScroll) illustration.heightIn(max = 240.dp) else illustration.weight(1f),
                )

                Text(
                    text = stringResource(R.string.welcome_tagline),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = onboardingPrimaryTextColor(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.welcome_subtitle),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = onboardingSecondaryTextColor(OnboardingSubtitleGray),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )

                Spacer(modifier = Modifier.height(28.dp))

                ShineButton(
                    text = stringResource(R.string.welcome_continue),
                    onClick = onContinueClick,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    showHalo = true,
                )

                Spacer(modifier = Modifier.height(16.dp))

                PrivacyLine()

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PrivacyLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = onboardingPrivacyLinkColor(),
            textDecoration = TextDecoration.Underline,
        ),
    )

    val annotated = buildAnnotatedString {
        append(stringResource(R.string.welcome_privacy_prefix))
        withLink(
            LinkAnnotation.Clickable(tag = "privacy", styles = linkStyles) {
                // Placeholder URL -- replace with the real Privacy Policy page.
                openUrl(context, PRIVACY_POLICY_URL)
            },
        ) {
            append(stringResource(R.string.welcome_consent_privacy))
        }
        append(stringResource(R.string.welcome_privacy_suffix))
    }

    Text(
        text = annotated,
        fontSize = 12.sp,
        color = onboardingSecondaryTextColor(OnboardingEyebrowGray),
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    AppTheme {
        WelcomeScreenContent(onContinueClick = {})
    }
}
