package text.message.sms.messaging.ui.screens.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.ContactGroup
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.domain.repository.ContactsState
import text.message.sms.messaging.domain.usecase.SyncContacts

/**
 * Regression coverage for the gap traced in the "contacts still empty after onboarding" bug: a
 * READ_CONTACTS grant during [WelcomeScreen] must trigger a contacts sync right then, since by
 * the time [text.message.sms.messaging.ui.screens.conversationlist.ConversationListViewModel] is
 * ever constructed, the permission was already granted earlier in the same onboarding flow -- so
 * its own resume-based "just granted" check never sees a transition and never fires.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onContactsPermissionGranted_refreshesContactsFromProvider() = runTest {
        var refreshCalled = false
        val contactRepository = object : ContactRepository {
            override val contactsState: StateFlow<ContactsState> =
                MutableStateFlow(ContactsState.Loaded(emptyList()))
            override suspend fun findByAddress(address: String): Contact? = null
            override fun search(query: String): Flow<List<Contact>> = MutableStateFlow(emptyList())
            override fun observeGroups(): Flow<List<ContactGroup>> = MutableStateFlow(emptyList())
            override suspend fun refreshFromProvider() {
                refreshCalled = true
            }
        }
        val viewModel = WelcomeViewModel(SyncContacts(contactRepository))

        viewModel.onContactsPermissionGranted()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(refreshCalled)
    }
}
