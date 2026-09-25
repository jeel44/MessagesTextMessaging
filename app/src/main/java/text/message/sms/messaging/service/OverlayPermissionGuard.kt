package text.message.sms.messaging.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Checks and requests "draw over other apps" (`SYSTEM_ALERT_WINDOW`), the same special-permission
 * shape [DefaultSmsAppGuard] handles for the default-SMS role: no runtime permission dialog, just
 * an `ACTION_MANAGE_OVERLAY_PERMISSION` Settings page and a state re-check afterwards.
 *
 * Requested during onboarding, by [text.message.sms.messaging.ui.screens.permissions
 * .OverlayPermissionScreen]. Used two ways: [text.message.sms.messaging.service
 * .CallEndTriggerService] checks [isGranted]'s underlying [android.provider.Settings
 * .canDrawOverlays] at call-end time, launching [text.message.sms.messaging.ui.screens.callend
 * .CallEndActivity] directly only if it's true; and [BackgroundActivityLaunchOverlay] spends the
 * permission on an actual transient 1x1 overlay window, attached around each background launch
 * this app makes, as a background-activity-launch (BAL) exemption independent of the first check.
 */
object OverlayPermissionGuard {

    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun buildRequestIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
