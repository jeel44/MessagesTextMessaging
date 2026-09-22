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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import text.message.sms.messaging.R
import text.message.sms.messaging.service.DefaultSmsAppGuard
import text.message.sms.messaging.ui.components.ShineButton
import text.message.sms.messaging.ui.components.sharpIconPainter
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor
import text.message.sms.messaging.ui.theme.AppTheme
import text.message.sms.messaging.ui.theme.OnboardingSubtitleGray

private val MinComfortableHeight = 600.dp
private val IllustrationMaxWidth = 300.dp

/**
 * Every runtime permission this app needs other than READ_CONTACTS (requested later, on first use
 * -- see [text.message.sms.messaging.ui.screens.newmessage.NewMessageScreen]), requested together
 * right after the default-SMS role grant.
 *
 * [DefaultSmsAppGuard.CoreSmsPermissions] is included here even though holding the role auto-grants
 * it on API 29+: this app's minSdk is 26, and the pre-Q `ACTION_CHANGE_DEFAULT` fallback
 * [DefaultSmsAppGuard.buildRoleRequestIntent] uses below API 29 does NOT auto-grant anything, so on
 * API 26-28 those four permissions would otherwise never be requested at all.
 *
 * `CALL_PHONE` is deliberately absent: [WelcomeScreen] already requests it, silently, the moment
 * Continue is tapped -- by the time the user reaches this screen it's already been asked for once,
 * and re-requesting an already-resolved permission here would just be a redundant second dialog.
 */
private fun postRoleGrantPermissions(): Array<String> {
    val permissions = (
        DefaultSmsAppGuard.CoreSmsPermissions + listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_CALL_LOG,
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
 * Second onboarding screen: asks the user to make Messages the default SMS app, then -- once that
 * role grant is confirmed via [DefaultSmsAppGuard.isDefault], never the activity result code alone
 * (the user can back out of the system dialog without an explicit deny and still get a
 * "success-looking" result) -- immediately requests the rest of the runtime permissions this app
 * needs in one shot (see [postRoleGrantPermissions]; `CALL_PHONE` is the one exception, already
 * requested by [WelcomeScreen]).
 *
 * [onDefaultSet] fires once the role is held AND [DefaultSmsAppGuard.CoreSmsPermissions] are
 * granted -- SMS/MMS is this app's reason to exist, so it's the only part of this screen that
 * blocks onboarding. The rest of the requested set (phone state, phone numbers, call log,
 * notifications) is best-effort, same as when this request lived in [WelcomeScreen]: a denial there
 * degrades a feature later rather than blocking onboarding now.
 *
 * The single "Set as Default" button always advances whichever step is still outstanding -- the
 * role request if the role isn't held yet, or the permission request if it already is (covers
 * re-entering this screen with the role already granted from an earlier attempt). That same moment
 * also calls [SetDefaultSmsViewModel.onDefaultSmsAppGranted], so a catch-up sync starts right away
 * instead of waiting for the next app launch.
 */
@Composable
fun SetDefaultSmsScreen(
    onDefaultSet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetDefaultSmsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val guard = remember(context) { DefaultSmsAppGuard(context.applicationContext) }

    var showDeclinedHint by remember { mutableStateOf(false) }
    var promptState by remember { mutableStateOf<PermissionPromptState>(PermissionPromptState.Hidden) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val coreGranted = DefaultSmsAppGuard.CoreSmsPermissions.all { results[it] == true }
        promptState = when {
            coreGranted -> {
                viewModel.onDefaultSmsAppGranted()
                onDefaultSet()
                PermissionPromptState.Hidden
            }
            activity != null && DefaultSmsAppGuard.CoreSmsPermissions.any { permission ->
                results[permission] == false &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            } -> PermissionPromptState.PermanentlyDenied
            else -> PermissionPromptState.NeedsRetry
        }
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (guard.isDefault) {
            permissionLauncher.launch(postRoleGrantPermissions())
        } else {
            showDeclinedHint = true
        }
    }

    SetDefaultSmsScreenContent(
        showDeclinedHint = showDeclinedHint,
        promptState = promptState,
        onSetDefaultClick = {
            if (guard.isDefault) {
                permissionLauncher.launch(postRoleGrantPermissions())
            } else {
                roleRequestLauncher.launch(guard.buildRoleRequestIntent())
            }
        },
        onOpenSettings = { context.openAppSettings() },
        modifier = modifier,
    )
}

/**
 * The screen's actual visual content, with no launcher dependency -- factored out purely so
 * [SetDefaultSmsScreen]'s layout can be exercised by `SetDefaultSmsScreenRenderTest` and a
 * `@Preview` without needing a Hilt-backed [androidx.hilt.navigation.compose.hiltViewModel] (this
 * module has no Hilt test harness set up yet). [SetDefaultSmsScreen] above is the only real caller;
 * it owns every actual side effect ([onSetDefaultClick] and [onOpenSettings] are both just that
 * composable's real callbacks passed straight through).
 */
@Composable
internal fun SetDefaultSmsScreenContent(
    showDeclinedHint: Boolean,
    promptState: PermissionPromptState,
    onSetDefaultClick: () -> Unit,
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
                verticalArrangement = Arrangement.Center,
            ) {
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

                // promptState takes priority over showDeclinedHint: it only ever becomes non-Hidden
                // once the role is already granted and the permission request itself came back
                // denied, i.e. it's a strictly later failure than the role being declined.
                when {
                    promptState != PermissionPromptState.Hidden -> {
                        PermissionNotice(
                            state = promptState,
                            onOpenSettings = onOpenSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                    }
                    // Not part of the approved design's normal path -- only ever visible once the
                    // user has already declined the role request once, so it can't be dropped
                    // without losing the only in-screen hint that they can just tap the button again.
                    showDeclinedHint -> {
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
private fun SetDefaultSmsScreenPreview() {
    AppTheme {
        SetDefaultSmsScreenContent(
            showDeclinedHint = false,
            promptState = PermissionPromptState.Hidden,
            onSetDefaultClick = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, name = "Role declined once")
@Composable
private fun SetDefaultSmsScreenDeclinedPreview() {
    AppTheme {
        SetDefaultSmsScreenContent(
            showDeclinedHint = true,
            promptState = PermissionPromptState.Hidden,
            onSetDefaultClick = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, name = "Permissions permanently denied")
@Composable
private fun SetDefaultSmsScreenPermanentlyDeniedPreview() {
    AppTheme {
        SetDefaultSmsScreenContent(
            showDeclinedHint = false,
            promptState = PermissionPromptState.PermanentlyDenied,
            onSetDefaultClick = {},
            onOpenSettings = {},
        )
    }
}
