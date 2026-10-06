package text.message.sms.messaging.service

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks and requests `ROLE_CALL_SCREENING` -- the same role shape [DefaultSmsAppGuard] handles for
 * `ROLE_SMS`. Holding it is what makes Telecom launch
 * [text.message.sms.messaging.ui.screens.callend.PostCallActivity] after each call, which is how
 * the call-end screen opens. [CallScreeningServiceImpl] is what makes this app eligible for it.
 *
 * The role only exists from API 29; below that, and on devices that don't offer it,
 * [isAvailable] is false and there is nothing to request.
 */
@Singleton
class CallScreeningRoleGuard @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true

    val isHeld: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

    /**
     * The system's "set as your caller ID & spam app" dialog, or null below API 29 where the role
     * doesn't exist. Launch it with an activity result contract, then re-check [isHeld] rather than
     * trusting the result code.
     */
    fun buildRoleRequestIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        } else {
            null
        }

    /** The system's Default apps page, where a held role can be moved to another app -- the role
     * request dialog isn't shown again for a role this app already holds. */
    fun buildManageIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}
