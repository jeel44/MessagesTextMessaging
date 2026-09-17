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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import text.message.sms.messaging.R
import text.message.sms.messaging.service.DefaultSmsAppGuard
import text.message.sms.messaging.ui.theme.Pill

// Placeholder URLs -- replace with the real hosted Terms & Conditions / Privacy Policy pages.
private const val TERMS_AND_CONDITIONS_URL = "https://example.com/terms"
private const val PRIVACY_POLICY_URL = "https://example.com/privacy"

private fun onboardingPermissions(): Array<String> {
    val permissions = (
        DefaultSmsAppGuard.CoreSmsPermissions + listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE,
        )
        ).toMutableList()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions.toTypedArray()
}

private sealed interface PermissionPromptState {
    data object Hidden : PermissionPromptState
    data object NeedsRetry : PermissionPromptState
    data object PermanentlyDenied : PermissionPromptState
}

private data class WelcomeFeature(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

/**
 * First screen after Splash: sells the app's core value in four feature rows, collects
 * one-tap consent, then drives the real runtime permission dialogs.
 *
 * [onContinue] fires once the core SMS/MMS permissions ([DefaultSmsAppGuard.CoreSmsPermissions])
 * are granted --
 * SMS/MMS is this app's reason to exist, so Continue is gated on those alone. The rest of the
 * requested set (contacts, phone state, call log, call, notifications) back secondary or
 * not-yet-built features and are best-effort: a denial there degrades a feature later rather
 * than blocking onboarding now.
 */
@Composable
fun WelcomeScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    if (promptState != PermissionPromptState.Hidden) {
                        PermissionNotice(
                            state = promptState,
                            onOpenSettings = { context.openAppSettings() },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    Button(
                        onClick = { permissionLauncher.launch(onboardingPermissions()) },
                        shape = Pill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.welcome_continue))
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.welcome_headline),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(32.dp))

            val features = listOf(
                WelcomeFeature(
                    icon = Icons.Filled.Sms,
                    title = stringResource(R.string.welcome_feature_sms_title),
                    description = stringResource(R.string.welcome_feature_sms_desc),
                ),
                WelcomeFeature(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.welcome_feature_notifications_title),
                    description = stringResource(R.string.welcome_feature_notifications_desc),
                ),
                WelcomeFeature(
                    icon = Icons.Filled.Shield,
                    title = stringResource(R.string.welcome_feature_privacy_title),
                    description = stringResource(R.string.welcome_feature_privacy_desc),
                ),
                WelcomeFeature(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.welcome_feature_search_title),
                    description = stringResource(R.string.welcome_feature_search_desc),
                ),
            )

            features.forEachIndexed { index, feature ->
                WelcomeFeatureRow(feature)
                if (index != features.lastIndex) {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            ConsentText()
        }
    }
}

@Composable
private fun WelcomeFeatureRow(feature: WelcomeFeature, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConsentText(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )

    val annotated = buildAnnotatedString {
        append(stringResource(R.string.welcome_consent_prefix))
        withLink(
            LinkAnnotation.Clickable(tag = "terms", styles = linkStyles) {
                // Placeholder URL -- replace with the real Terms & Conditions page.
                openUrl(context, TERMS_AND_CONDITIONS_URL)
            },
        ) {
            append(stringResource(R.string.welcome_consent_terms))
        }
        append(stringResource(R.string.welcome_consent_and))
        withLink(
            LinkAnnotation.Clickable(tag = "privacy", styles = linkStyles) {
                // Placeholder URL -- replace with the real Privacy Policy page.
                openUrl(context, PRIVACY_POLICY_URL)
            },
        ) {
            append(stringResource(R.string.welcome_consent_privacy))
        }
        append(stringResource(R.string.welcome_consent_suffix))
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        modifier = modifier.fillMaxWidth(),
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
