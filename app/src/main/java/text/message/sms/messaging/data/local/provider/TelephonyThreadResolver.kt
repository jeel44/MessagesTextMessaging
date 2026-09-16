package text.message.sms.messaging.data.local.provider

import android.content.Context
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the system thread id for a set of addresses. The platform owns thread identity, so
 * the app asks it rather than inventing ids of its own.
 */
@Singleton
class TelephonyThreadResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Returns the existing thread id for [addresses], creating one when there is none. */
    fun resolve(addresses: Set<String>): Long =
        Telephony.Threads.getOrCreateThreadId(context, addresses)
}
