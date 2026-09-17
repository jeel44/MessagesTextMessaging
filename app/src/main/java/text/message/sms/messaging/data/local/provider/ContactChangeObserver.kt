package text.message.sms.messaging.data.local.provider

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.SyncContacts
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the system contacts provider for changes made outside this app -- a contact added,
 * edited, deleted, or a whole account re-synced by the platform's own contacts sync adapter --
 * and folds them into the local cache. Debounced the same way and for the same reason as
 * [ProviderChangeObserver]: editing one contact or running a full account sync can touch the
 * provider many times in quick succession, and each of those should not trigger its own full
 * re-read.
 */
@Singleton
class ContactChangeObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val syncContacts: SyncContacts,
) {

    private var pendingSync: Job? = null
    private var registered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            pendingSync?.cancel()
            pendingSync = applicationScope.launch {
                delay(DEBOUNCE_MILLIS)
                if (isActive) syncContacts()
            }
        }
    }

    fun register() {
        if (registered) return
        registered = true
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer,
        )
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
