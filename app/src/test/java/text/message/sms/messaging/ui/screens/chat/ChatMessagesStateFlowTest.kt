package text.message.sms.messaging.ui.screens.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import java.time.ZoneId

/**
 * Regression coverage for the "list blink" race traced via logcat tag ChatListBlink: a thread with
 * messages could show `hasLoadedInitialMessages=true` (a separate `MutableStateFlow<Boolean>` set
 * as a side effect inside `onEach`, see the old `ChatViewModel.hasLoadedInitialPage`) on the same
 * frame [ChatScreen]'s `chatItems` -- derived from a *different* StateFlow (`messages`), which had
 * to travel through `combine`'s internal channel hand-off, `distinctUntilChanged`, and its own
 * `stateIn` -- was still that StateFlow's `emptyList()` seed. The fix ([ChatViewModel
 * .chatMessagesState], built by [chatMessagesStateFlow]) collapses both into one [ChatMessagesState]
 * produced by a single `map` step on one upstream flow, so there is no frame where a collector can
 * see [ChatMessagesState.Loaded] without also seeing its real, grouped items.
 *
 * This drives [chatMessagesStateFlow] directly -- the exact function [ChatViewModel.chatMessagesState]
 * calls -- rather than a full [ChatViewModel], since [ChatViewModel]'s other dependencies
 * ([text.message.sms.messaging.data.local.telephony.SimRepository],
 * [text.message.sms.messaging.data.local.datastore.SimPreferences]) need a real Android `Context`
 * that a plain JVM unit test in this project has no way to supply (no Robolectric/mocking library
 * is set up here).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMessagesStateFlowTest {

    private fun message(id: Long, threadId: Long, sentAtMillis: Long) = Message(
        id = id,
        threadId = threadId,
        providerId = id,
        channel = MessageChannel.SMS,
        folder = if (id % 2 == 0L) MessageFolder.INBOX else MessageFolder.SENT,
        deliveryState = DeliveryState.NONE,
        address = "+15551234567",
        body = "Message $id",
        subject = null,
        sentAtMillis = sentAtMillis,
        receivedAtMillis = sentAtMillis,
        isRead = true,
        isSeen = true,
        subscriptionId = -1,
        errorCode = 0,
    )

    /** Mirrors [ChatViewModel.rawMessagePage]'s own construction -- `messagePageSize.flatMapLatest
     * { messageRepository.observeThreadPage(...) }` -- but the fake page source below suspends
     * before emitting, the same cross-dispatcher hand-off Room's real Flow has, which is exactly
     * what let the old boolean flag win the race against the still-empty list. */
    private fun rawMessagePage(scope: CoroutineScope, page: List<Message>, emitDelayMillis: Long): StateFlow<List<Message>?> {
        val pageSize = MutableStateFlow(40)
        val source: Flow<List<Message>> = flow {
            delay(emitDelayMillis)
            emit(page)
        }
        return pageSize
            .flatMapLatest { source }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** 50 iterations, each with a different simulated arrival delay for the "Room emission", to
     * shake out any timing-dependent gap between the "loaded" decision and the list -- the same
     * kind of variability the real bug depended on (observed in logcat as an intermittent race,
     * not a deterministic failure). */
    @Test
    fun loadedStateIsNeverEmptyWhenThePageHadMessages() = runTest {
        val zoneId = ZoneId.systemDefault()
        val threadId = 6L
        val page = (0 until 47).map { index -> message(index.toLong(), threadId, 1_700_000_000_000L + index * 60_000L) }

        repeat(50) { iteration ->
            val dispatcher = StandardTestDispatcher(testScheduler, name = "iteration-$iteration")
            val job = Job()
            val scope = CoroutineScope(dispatcher + job)

            val rawPage = rawMessagePage(scope, page, emitDelayMillis = (iteration % 7).toLong())
            val chatMessagesState = chatMessagesStateFlow(scope, rawPage, zoneId)

            val observed = mutableListOf<ChatMessagesState>()
            val collectJob = scope.launch { chatMessagesState.collect { observed += it } }

            dispatcher.scheduler.advanceUntilIdle()
            collectJob.cancel()
            job.cancel()

            assertTrue(
                "iteration $iteration: expected Loading first, got $observed",
                observed.first() is ChatMessagesState.Loading,
            )
            val loaded = observed.filterIsInstance<ChatMessagesState.Loaded>()
            assertTrue("iteration $iteration: chatMessagesState never reached Loaded", loaded.isNotEmpty())
            loaded.forEach { state ->
                assertTrue(
                    "iteration $iteration: Loaded emitted with 0 items even though the page had " +
                        "${page.size} messages -- this is the ChatListBlink race",
                    state.items.isNotEmpty(),
                )
            }
            // The page has 47 messages plus at least one date header -- confirms grouping actually
            // ran as part of this same emission, not a stale/partial list.
            assertEquals(page.size + 1, loaded.last().items.size)
        }
    }

    /** A genuinely empty thread must still resolve to `Loaded(emptyList())`, not get stuck at
     * `Loading` forever -- distinguishing "no page yet" (`null`) from "page arrived, empty"
     * (`emptyList()`) is exactly what [chatMessagesStateFlow] uses `rawMessagePage`'s nullability
     * for (see [ChatViewModel.rawMessagePage]'s doc comment). */
    @Test
    fun loadedStateIsEmittedForGenuinelyEmptyThread() = runTest {
        val zoneId = ZoneId.systemDefault()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())

        val rawPage = rawMessagePage(scope, emptyList(), emitDelayMillis = 2)
        val chatMessagesState = chatMessagesStateFlow(scope, rawPage, zoneId)

        val observed = mutableListOf<ChatMessagesState>()
        val collectJob = scope.launch { chatMessagesState.collect { observed += it } }
        dispatcher.scheduler.advanceUntilIdle()
        collectJob.cancel()

        val loaded = observed.filterIsInstance<ChatMessagesState.Loaded>()
        assertTrue(loaded.isNotEmpty())
        assertTrue(loaded.last().items.isEmpty())
    }
}
