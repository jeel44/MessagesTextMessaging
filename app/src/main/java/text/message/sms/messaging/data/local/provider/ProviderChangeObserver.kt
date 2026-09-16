package text.message.sms.messaging.data.local.provider

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.SyncMessages
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches `content://sms` and `content://mms` for changes made outside this app's own write
 * paths -- another app inserting a message, a carrier/OEM component writing directly, or the
 * platform itself -- and folds them into the local cache. Debounced, since a single incoming
 * MMS can touch the provider several times in quick succession (notification row, then the
 * retrieved message, then its parts) and each of those should not trigger its own full walk.
 */
@Singleton
class ProviderChangeObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val syncMessages: SyncMessages,
) {

    private var pendingSync: Job? = null
    private var registered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            pendingSync?.cancel()
            pendingSync = applicationScope.launch {
                delay(DEBOUNCE_MILLIS)
                if (isActive) syncMessages()
            }
        }
    }

    fun register() {
        if (registered) return
        registered = true
        val resolver = context.contentResolver
        resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        resolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)
    }

    fun unregister() {
        if (!registered) return
        registered = false
        context.contentResolver.unregisterContentObserver(observer)
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 800L
    }
}
