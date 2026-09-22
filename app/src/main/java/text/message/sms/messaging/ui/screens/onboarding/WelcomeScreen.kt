package text.message.sms.messaging.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import text.message.sms.messaging.R
import text.message.sms.messaging.service.DefaultSmsAppGuard
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

private fun onboardingPermissions(): Array<String> {
    val permissions = (
        DefaultSmsAppGuard.CoreSmsPermissions + listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE,
        )
        ).toMutableList()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions.toTypedArray()
}

internal sealed interface PermissionPromptState {
    data object Hidden : PermissionPromptState
    data object NeedsRetry : PermissionPromptState
    data object PermanentlyDenied : PermissionPromptState
}

/**
 * First screen after Splash: the approved-design welcome illustration and pitch, then collects
 * one-tap consent and drives the real runtime permission dialogs.
 *
 * [onContinue] fires once the core SMS/MMS permissions ([DefaultSmsAppGuard.CoreSmsPermissions])
 * are granted -- SMS/MMS is this app's reason to exist, so Continue is gated on those alone. The
 * rest of the requested set (phone state, phone numbers, call log, call, notifications) back
 * secondary or not-yet-built features and are best-effort: a denial there degrades a feature later
 * rather than blocking onboarding now. READ_CONTACTS is deliberately not requested here -- see
 * [text.message.sms.messaging.ui.screens.newmessage.NewMessageScreen] for its first-use request.
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var promptState by remember { mutableStateOf<PermissionPromptState>(PermissionPromptState.Hidden) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val coreGranted = DefaultSmsAppGuard.CoreSmsPermissions.all { results[it] == true }
        promptState = when {
            coreGranted -> {
                onContinue()
                PermissionPromptState.Hidden
            }
            activity != null && DefaultSmsAppGuard.CoreSmsPermissions.any { permission ->
                results[permission] == false &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            } -> PermissionPromptState.PermanentlyDenied
            else -> PermissionPromptState.NeedsRetry
        }
    }

    WelcomeScreenContent(
        promptState = promptState,
        onContinueClick = { permissionLauncher.launch(onboardingPermissions()) },
        onOpenSettings = { context.openAppSettings() },
        modifier = modifier,
    )
}

/**
 * The screen's actual visual content, with no permission-launcher dependency -- factored out
 * purely so [WelcomeScreen]'s layout can be exercised by `WelcomeScreenRenderTest` and a
 * `@Preview` without needing a real [android.app.Activity] to host the runtime permission dialog.
 * [WelcomeScreen] above is the only real caller; it owns every actual side effect
 * ([onContinueClick] and [onOpenSettings] are both just that composable's real callbacks passed
 * straight through).
 */
@Composable
internal fun WelcomeScreenContent(
    promptState: PermissionPromptState,
    onContinueClick: () -> Unit,
    onOpenSettings: () -> Unit,
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

                // Not part of the approved design's normal path -- only ever visible after a real
                // permission denial, so it can't be dropped without losing the only way this screen
                // lets the user recover (retry, or a shortcut to Settings once permanently denied).
                if (promptState != PermissionPromptState.Hidden) {
                    PermissionNotice(
                        state = promptState,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                }

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

@Composable
private fun PermissionNotice(
    state: PermissionPromptState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    if (state is PermissionPromptState.PermanentlyDenied) {
                        R.string.welcome_permission_settings_message
                    } else {
                        R.string.welcome_permission_denied_message
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (state is PermissionPromptState.PermanentlyDenied) {
                TextButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(stringResource(R.string.welcome_open_settings))
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    AppTheme {
        WelcomeScreenContent(
            promptState = PermissionPromptState.Hidden,
            onContinueClick = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, name = "Permanently denied")
@Composable
private fun WelcomeScreenPermanentlyDeniedPreview() {
    AppTheme {
        WelcomeScreenContent(
            promptState = PermissionPromptState.PermanentlyDenied,
            onContinueClick = {},
            onOpenSettings = {},
        )
    }
}
