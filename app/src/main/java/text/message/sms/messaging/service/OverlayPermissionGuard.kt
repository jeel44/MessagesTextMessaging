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
 * .OverlayPermissionScreen] -- ahead of the call-alert overlay feature this is actually for, which
 * isn't built yet. No feature in this app draws over other apps today: the call-end screen, the
 * obvious candidate, launches via a full-screen-intent notification instead (see AndroidManifest's
 * `USE_FULL_SCREEN_INTENT` comment), which doesn't need this permission.
 */
object OverlayPermissionGuard {

    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun buildRequestIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
}
