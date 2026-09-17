package text.message.sms.messaging.service

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the app currently holds the default SMS role.
 *
 * Without the role the app can read the Telephony provider but cannot write to it, and it never
 * receives `SMS_DELIVER`. Every write path therefore checks [isDefault] first.
 */
@Singleton
class DefaultSmsAppGuard @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val isDefault: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }

    /**
     * Builds the intent that asks the user to make this app the default SMS handler. Launch it
     * with an activity result contract, then re-check [isDefault] rather than trusting the
     * result code -- the user can back out of the system dialog without an explicit deny.
     *
     * Android 10 replaced the old change-default intent with the role request, so both paths
     * are kept.
     */
    fun buildRoleRequestIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                .createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
}
