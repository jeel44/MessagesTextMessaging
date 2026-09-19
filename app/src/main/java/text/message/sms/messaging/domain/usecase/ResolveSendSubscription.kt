package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.first
import text.message.sms.messaging.data.local.datastore.SimPreferences
import text.message.sms.messaging.data.local.datastore.SimSendPreference
import text.message.sms.messaging.data.local.telephony.SimRepository
import text.message.sms.messaging.domain.model.SimInfo
import text.message.sms.messaging.domain.repository.ConversationRepository
import javax.inject.Inject

/** What to send a message with, decided by [ResolveSendSubscription]. */
sealed interface SendSubscriptionResult {

    /** Send with [subscriptionId] -- either a resolved dual-SIM choice, or
     * [SendMessage.DEFAULT_SUBSCRIPTION_ID] on a single-SIM/no-SIM device, which never reaches
     * any of the other cases below. */
    data class Resolved(val subscriptionId: Int) : SendSubscriptionResult

    /** The "always use this SIM" preference's slot is no longer active (SIM removed) -- send with
     * [subscriptionId] (the other active SIM) anyway, but the caller should tell the user why,
     * since this silently used a SIM other than the one they picked. */
    data class FellBack(val subscriptionId: Int) : SendSubscriptionResult

    /** "Ask every time" with no (or no longer valid) remembered choice for this thread -- the
     * caller must show the SIM picker and hold the message until the user picks; nothing should
     * be sent yet. */
    data object NeedsUserChoice : SendSubscriptionResult
}

/**
 * Single source of truth for which SIM subscription a message sends on -- used by both the SMS
 * and MMS paths (see `TelephonyMessageTransmitter`, which already routes any [Message
 * .subscriptionId] through the subscription-specific `SmsManager`/MMS transport for both
 * channels). Never shows a picker, and always resolves immediately, on a device with fewer than
 * two active SIMs.
 */
class ResolveSendSubscription @Inject constructor(
    private val simRepository: SimRepository,
    private val simPreferences: SimPreferences,
    private val conversationRepository: ConversationRepository,
) : UseCase {

    suspend operator fun invoke(threadId: Long): SendSubscriptionResult {
        val sims = simRepository.currentActiveSims()
        if (sims.size < 2) return SendSubscriptionResult.Resolved(SendMessage.DEFAULT_SUBSCRIPTION_ID)

        return when (simPreferences.sendPreference.first()) {
            SimSendPreference.SLOT_0 -> resolveFixedSlot(sims, slotIndex = 0)
            SimSendPreference.SLOT_1 -> resolveFixedSlot(sims, slotIndex = 1)
            SimSendPreference.ASK -> resolveAsk(threadId, sims)
        }
    }

    private fun resolveFixedSlot(sims: List<SimInfo>, slotIndex: Int): SendSubscriptionResult {
        val sim = sims.firstOrNull { it.slotIndex == slotIndex }
        if (sim != null) return SendSubscriptionResult.Resolved(sim.subscriptionId)

        // The preferred slot has no active SIM right now (removed mid-session, or simply the
        // wrong device) -- fall back to whichever SIM *is* active rather than failing the send;
        // the caller surfaces this so it isn't silent.
        val fallback = sims.first()
        return SendSubscriptionResult.FellBack(fallback.subscriptionId)
    }

    private suspend fun resolveAsk(threadId: Long, sims: List<SimInfo>): SendSubscriptionResult {
        val rememberedSlot = conversationRepository.findByThreadId(threadId)?.subscriptionSlot
            ?: return SendSubscriptionResult.NeedsUserChoice

        val remembered = sims.firstOrNull { it.slotIndex == rememberedSlot }
        if (remembered != null) return SendSubscriptionResult.Resolved(remembered.subscriptionId)

        // The remembered SIM no longer exists (removed) -- clear the stale memory and ask again,
        // rather than silently guessing a replacement for a choice the user made deliberately.
        conversationRepository.setSubscriptionSlot(threadId, null)
        return SendSubscriptionResult.NeedsUserChoice
    }
}
