package text.message.sms.messaging.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import text.message.sms.messaging.R
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor

/**
 * Settings' "Call alerts" row opens this: a plain explanation of the call-end screen's
 * full-screen-intent launch, and -- API 34+ only -- a button to the system page that lets the
 * user turn `USE_FULL_SCREEN_INTENT` on manually.
 *
 * That permission is "special access" on API 34+, granted automatically only to the default
 * dialer/CALL-category apps; a messaging app (this one) is not expected to receive it
 * automatically, so without this explanation there is no way for most users on a modern Android
 * version to ever see [text.message.sms.messaging.ui.screens.callend.CallEndScreen] launch --
 * the notification [text.message.sms.messaging.service.CallEndTriggerService] posts would just
 * silently sit in the tray instead. Below API 34 there is no dedicated management page for this
 * permission (it's granted differently), so the button is hidden rather than shown broken.
 */
@Composable
internal fun CallAlertsInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.settings_call_alerts_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_call_alerts_explanation))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            {
                TextButton(onClick = { openFullScreenIntentSettings(context) }) {
                    Text(stringResource(R.string.settings_call_alerts_open_settings))
                }
            }
        } else {
            null
        },
    )
}

private fun openFullScreenIntentSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
        .setData(Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No-op -- some OEM builds omit this page even on API 34+; there's nothing else to fall
        // back to, and this must never crash Settings over a missing system screen.
    }
}
