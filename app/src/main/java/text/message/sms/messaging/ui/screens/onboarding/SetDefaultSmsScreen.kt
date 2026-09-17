package text.message.sms.messaging.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import text.message.sms.messaging.R
import text.message.sms.messaging.service.DefaultSmsAppGuard
import text.message.sms.messaging.ui.theme.Pill

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    if (showDeclinedHint) {
                        Text(
                            text = stringResource(R.string.set_default_sms_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    Button(
                        onClick = { roleRequestLauncher.launch(guard.buildRoleRequestIntent()) },
                        shape = Pill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.set_default_sms_button))
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LottiePlaceholderSlot()

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.set_default_sms_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Empty slot reserved for a Lottie animation, clearly marked so it can't be mistaken for a
 * finished illustration. Replace with the real `LottieAnimation` composable once the animation
 * file is available -- do not add illustration art here in the meantime.
 */
@Composable
private fun LottiePlaceholderSlot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(260.dp)
            .dashedBorder(
                color = MaterialTheme.colorScheme.outlineVariant,
                cornerRadius = 24.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.set_default_sms_placeholder_label).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 6.dp,
): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(dashLength.toPx(), gapLength.toPx()),
                phase = 0f,
            ),
        ),
    )
}
