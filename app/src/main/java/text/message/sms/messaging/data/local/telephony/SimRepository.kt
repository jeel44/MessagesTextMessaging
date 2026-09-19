package text.message.sms.messaging.data.local.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import text.message.sms.messaging.domain.model.SimInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the device's active SIM subscriptions for the dual-SIM send picker (see
 * `text.message.sms.messaging.domain.usecase.ResolveSendSubscription`, the settings SIM row, and
 * the Chat composer's SIM badge).
 *
 * [isMultiSimCapable] is a hardware question ("does this device have 2+ SIM slots?") and needs no
 * runtime permission, unlike everything else here, which reads live subscriber state and needs
 * `READ_PHONE_STATE`. That split lets the Settings row render (disabled, with a "permission
 * needed" hint) on a dual-SIM device before the user has granted anything, while every other
 * caller -- which actually needs to know *which* SIMs are active -- safely falls back to
 * single-SIM behavior until permission is granted.
 */
@Singleton
class SimRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val subscriptionManager: SubscriptionManager,
    private val telephonyManager: TelephonyManager,
) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED

    val isMultiSimCapable: Boolean
        get() = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager.activeModemCount
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.phoneCount
            }
        }.getOrDefault(1) >= 2

    /** Live view of the active SIM list, updating on insert/removal while collected. Empty
     * (rather than throwing) whenever [hasPermission] is false. */
    val activeSims: Flow<List<SimInfo>> = callbackFlow {
        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                trySend(readActiveSims())
            }
        }
        if (hasPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                subscriptionManager.addOnSubscriptionsChangedListener(
                    ContextCompat.getMainExecutor(context),
                    listener,
                )
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager.addOnSubscriptionsChangedListener(listener)
            }
        } else {
            trySend(emptyList())
        }
        awaitClose { runCatching { subscriptionManager.removeOnSubscriptionsChangedListener(listener) } }
    }.flowOn(Dispatchers.Main.immediate)

    /** One-shot snapshot for send-time resolution -- see [ResolveSendSubscription]. */
    suspend fun currentActiveSims(): List<SimInfo> = withContext(Dispatchers.Default) { readActiveSims() }

    fun defaultSmsSubscriptionId(): Int = SubscriptionManager.getDefaultSmsSubscriptionId()

    private fun readActiveSims(): List<SimInfo> {
        if (!hasPermission()) return emptyList()
        return runCatching { subscriptionManager.activeSubscriptionInfoList }
            .getOrNull()
            .orEmpty()
            .map { it.toSimInfo() }
    }

    @Suppress("DEPRECATION")
    private fun SubscriptionInfo.toSimInfo() = SimInfo(
        subscriptionId = subscriptionId,
        slotIndex = simSlotIndex,
        displayName = displayName?.toString().orEmpty(),
        carrierName = carrierName?.toString().orEmpty(),
        // SubscriptionInfo.number needs READ_PHONE_NUMBERS on top of READ_PHONE_STATE on newer
        // platform versions -- a SecurityException here just means "no number to show", not a
        // reason to drop the whole SIM from the list.
        number = runCatching { number }.getOrNull()?.takeIf { it.isNotBlank() },
    )
}
