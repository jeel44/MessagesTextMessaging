package text.message.sms.messaging.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInterstitialControllerTest {

    private class FakeSlot(val name: String) : ChatInterstitialSlot<String> {
        var ready = true
        var preloads = 0
        var commits = 0
        val shownOn = mutableListOf<String>()
        var pendingFinish: (() -> Unit)? = null

        override val isReady: Boolean get() = ready
        override fun preload() {
            preloads++
        }
        override fun commitToShow() {
            commits++
        }
        override fun showIfReady(host: String, onFinished: () -> Unit): Boolean {
            if (!ready) return false
            ready = false
            shownOn += host
            pendingFinish = onFinished
            return true
        }

        /** Dismiss, or fail to show -- both end in onFinished. */
        fun finish() {
            pendingFinish?.invoke().also { pendingFinish = null }
        }
    }

    private class FakeConditions : ChatInterstitialConditions<String> {
        override var onboardingComplete = true
        override var consentAllowed = true
        override var fullScreenAdBusy = false
        var foreground = true
        override fun isInForeground(host: String) = foreground
    }

    private val enter = FakeSlot("enter")
    private val exit = FakeSlot("exit")
    private val conditions = FakeConditions()
    private var now = 1_000L
    private val logs = mutableListOf<String>()

    /** Back stack: inbox under whatever chats are open. */
    private val stack = mutableListOf<Any>("graph", INBOX)
    private var nextChat = 0
    private val opened = mutableListOf<String>()

    private fun controller(
        cycle: List<ChatVisitAd> = ChatInterstitialConfig.CYCLE,
        minGapMillis: Long = 0L,
    ) = ChatInterstitialController(
        enterSlot = enter,
        exitSlot = exit,
        conditions = conditions,
        clock = { now },
        cycle = cycle,
        minGapMillis = minGapMillis,
        log = { logs += it },
    )

    private fun ChatInterstitialController<String>.tap(source: ChatEntrySource = ChatEntrySource.INBOX) =
        onChatOpenRequested(source, HOST, originKey = stack.last()) {
            val key = "chat${nextChat++}"
            opened += key
            stack += key
            key
        }

    /** Back arrow / system back from the top chat; the previous screen then becomes visible. */
    private fun ChatInterstitialController<String>.back(visible: Boolean = true) {
        stack.removeAt(stack.lastIndex)
        if (onBackStackChanged(stack.toList())) onLeaveVisible(HOST, visible)
    }

    /** One full visit from the inbox: open, dismiss any enter ad, leave, dismiss any exit ad.
     * Returns which ads showed ("enter", "exit"). */
    private fun ChatInterstitialController<String>.visit(): List<String> {
        val before = enter.shownOn.size to exit.shownOn.size
        enter.ready = true
        exit.ready = true
        tap()
        enter.finish()
        back()
        exit.finish()
        return buildList {
            if (enter.shownOn.size > before.first) add("enter")
            if (exit.shownOn.size > before.second) add("exit")
        }
    }

    @Test
    fun `visits produce exit, none, none, enter, then repeat`() {
        val c = controller()
        val pattern = (1..8).map { c.visit() }
        val oneCycle = listOf(listOf("exit"), emptyList(), emptyList(), listOf("enter"))
        assertEquals(oneCycle + oneCycle, pattern)
    }

    @Test
    fun `the default config is exit, none, none, enter and enabled with no gap`() {
        assertEquals(
            listOf(ChatVisitAd.EXIT_AD, ChatVisitAd.NONE, ChatVisitAd.NONE, ChatVisitAd.ENTER_AD),
            ChatInterstitialConfig.CYCLE,
        )
        assertTrue(ChatInterstitialConfig.ENABLED)
        assertEquals(0L, ChatInterstitialConfig.MIN_GAP_MILLIS)
        assertEquals(setOf(ChatEntrySource.INBOX), ChatInterstitialConfig.COUNTED_SOURCES)
    }

    @Test
    fun `exit ad shows only after the chat is popped and the previous screen is visible`() {
        val c = controller()
        c.tap()
        assertTrue(exit.shownOn.isEmpty())
        stack.removeAt(stack.lastIndex)
        assertTrue(c.onBackStackChanged(stack.toList()))
        assertTrue("not shown before the inbox is visible", exit.shownOn.isEmpty())
        c.onLeaveVisible(HOST, visible = true)
        assertEquals(listOf(HOST), exit.shownOn)
    }

    @Test
    fun `exit ad is skipped when the previous screen never becomes visible`() {
        val c = controller()
        c.tap()
        c.back(visible = false)
        assertTrue(exit.shownOn.isEmpty())
        assertEquals(2, c.nextVisitNumber)
    }

    @Test
    fun `position survives navigation that is not leaving`() {
        val c = controller()
        c.tap() // visit 1, exit armed
        // Conversation info and a media viewer pushed on top, then popped again.
        stack += "info"
        assertFalse(c.onBackStackChanged(stack.toList()))
        stack += "media"
        assertFalse(c.onBackStackChanged(stack.toList()))
        stack.removeAt(stack.lastIndex)
        stack.removeAt(stack.lastIndex)
        assertFalse(c.onBackStackChanged(stack.toList()))
        // Rotation: the NavController is rebuilt -- an empty stack, then the restored one.
        assertFalse(c.onBackStackChanged(emptyList()))
        assertFalse(c.onBackStackChanged(stack.toList()))
        assertTrue(exit.shownOn.isEmpty())
        assertEquals(2, c.nextVisitNumber)
        // Leaving for real still shows visit 1's exit ad.
        c.back()
        assertEquals(1, exit.shownOn.size)
        // And visits 2..4 carry on from there.
        exit.finish()
        assertEquals(listOf(emptyList(), emptyList(), listOf("enter")), (2..4).map { c.visit() })
    }

    @Test
    fun `a chat replaced by another screen is not a leave`() {
        val c = controller()
        c.tap() // visit 1, exit armed
        // Forward: the chat is popped and a new chat pushed in one go.
        stack.removeAt(stack.lastIndex)
        stack += "forwarded-chat"
        assertFalse(c.onBackStackChanged(stack.toList()))
        stack.removeAt(stack.lastIndex)
        assertFalse(c.onBackStackChanged(stack.toList()))
        assertTrue(exit.shownOn.isEmpty())
    }

    @Test
    fun `skipped ads still advance the cycle`() {
        // One run per skip reason, each starting from a fresh controller on visit 1 (an exit ad).
        val setups: List<Pair<String, () -> Unit>> = listOf(
            "not ready" to { exit.ready = false },
            "gate busy" to { conditions.fullScreenAdBusy = true },
            "no consent" to { conditions.consentAllowed = false },
            "onboarding" to { conditions.onboardingComplete = false },
            "call-end screen up" to { conditions.foreground = false },
        )
        for ((label, setup) in setups) {
            resetWorld()
            val c = controller()
            exit.ready = true
            c.tap()
            setup()
            c.back()
            assertTrue("$label: shown", exit.shownOn.isEmpty())
            assertEquals("$label: cycle didn't advance", 2, c.nextVisitNumber)
        }
    }

    @Test
    fun `skipped enter ads still advance the cycle and open the chat`() {
        val c = controller(cycle = listOf(ChatVisitAd.ENTER_AD, ChatVisitAd.NONE))
        conditions.fullScreenAdBusy = true
        c.tap()
        assertTrue(enter.shownOn.isEmpty())
        assertEquals(1, opened.size)
        assertEquals(2, c.nextVisitNumber)
        assertEquals(0, enter.commits)
    }

    @Test
    fun `non-counted sources never advance the cycle or show ads`() {
        val c = controller(cycle = listOf(ChatVisitAd.ENTER_AD))
        for (source in listOf(ChatEntrySource.SEARCH, ChatEntrySource.ARCHIVED)) {
            c.tap(source)
            assertEquals(1, c.nextVisitNumber)
            c.back()
        }
        assertEquals(2, opened.size)
        assertTrue(enter.shownOn.isEmpty())
        assertTrue(exit.shownOn.isEmpty())
        assertTrue(logs.any { "not counted source" in it })

        val exitFirst = controller()
        exitFirst.tap(ChatEntrySource.SEARCH)
        exitFirst.back()
        assertTrue("leaving a non-counted chat showed an exit ad", exit.shownOn.isEmpty())
        assertEquals(1, exitFirst.nextVisitNumber)
    }

    @Test
    fun `MIN_GAP_MILLIS skips an ad inside the gap and still advances`() {
        val c = controller(cycle = listOf(ChatVisitAd.EXIT_AD), minGapMillis = 60_000L)
        assertEquals(listOf("exit"), c.visit())
        now += 30_000L
        assertEquals("inside the gap", emptyList<String>(), c.visit())
        assertTrue(logs.any { "MIN_GAP_MILLIS" in it })
        now += 31_000L
        assertEquals("gap elapsed", listOf("exit"), c.visit())
    }

    @Test
    fun `MIN_GAP_MILLIS of zero never skips`() {
        val c = controller(cycle = listOf(ChatVisitAd.EXIT_AD), minGapMillis = 0L)
        repeat(3) { assertEquals(listOf("exit"), c.visit()) }
    }

    @Test
    fun `a different list in the config changes the pattern`() {
        val c = controller(cycle = listOf(ChatVisitAd.ENTER_AD, ChatVisitAd.EXIT_AD, ChatVisitAd.NONE))
        assertEquals(
            listOf(listOf("enter"), listOf("exit"), emptyList(), listOf("enter"), listOf("exit"), emptyList()),
            (1..6).map { c.visit() },
        )
    }

    @Test
    fun `disabled shows nothing and never counts`() {
        val c = ChatInterstitialController(enter, exit, conditions, { now }, enabled = false)
        c.tap()
        c.back()
        c.preloadNext()
        assertEquals(1, opened.size)
        assertTrue(exit.shownOn.isEmpty())
        assertEquals(0, exit.preloads + enter.preloads)
        assertEquals(1, c.nextVisitNumber)
    }

    @Test
    fun `enter ad opens the chat after dismiss`() {
        val c = controller(cycle = listOf(ChatVisitAd.ENTER_AD))
        c.tap()
        assertEquals(1, enter.commits)
        assertEquals(listOf(HOST), enter.shownOn)
        assertTrue("chat opened under the ad", opened.isEmpty())
        enter.finish()
        assertEquals(1, opened.size)
    }

    @Test
    fun `enter ad opens the chat after a show failure, exactly once`() {
        val c = controller(cycle = listOf(ChatVisitAd.ENTER_AD))
        c.tap()
        val finish = checkNotNull(enter.pendingFinish)
        finish() // onAdFailedToShowFullScreenContent
        finish() // a stray second callback
        assertEquals(1, opened.size)
    }

    @Test
    fun `an unready enter ad never delays opening`() {
        val c = controller(cycle = listOf(ChatVisitAd.ENTER_AD))
        enter.ready = false
        c.tap()
        assertEquals("opened synchronously, inside the tap", 1, opened.size)
        assertTrue(enter.shownOn.isEmpty())
        assertTrue(logs.any { "not ready" in it })
        assertEquals(1, c.nextVisitNumber) // a 1-entry cycle wraps straight back to visit 1
    }

    @Test
    fun `a tap while the enter ad is up is ignored`() {
        val c = controller(cycle = listOf(ChatVisitAd.ENTER_AD, ChatVisitAd.NONE))
        c.tap()
        c.tap()
        assertEquals(2, c.nextVisitNumber)
        enter.finish()
        assertEquals(1, opened.size)
    }

    @Test
    fun `each ad uses only its own slot`() {
        val c = controller()
        c.visit() // exit visit
        assertEquals(1, exit.shownOn.size)
        assertEquals(0, enter.shownOn.size)
        repeat(3) { c.visit() } // none, none, enter
        assertEquals(1, exit.shownOn.size)
        assertEquals(1, enter.shownOn.size)
    }

    @Test
    fun `preloads only the placement the cycle needs next`() {
        val c = controller()
        c.preloadNext() // before visit 1: exit
        assertEquals(1 to 0, exit.preloads to enter.preloads)
        c.tap() // visit 1 (exit armed) -- still the exit one
        c.preloadNext()
        assertEquals(2 to 0, exit.preloads to enter.preloads)
        c.back() // exit ad shown
        exit.finish() // reload after show: next needed is visit 4's enter ad
        assertEquals(2 to 1, exit.preloads to enter.preloads)
        c.visit() // visit 2
        c.visit() // visit 3
        val exitBefore = exit.preloads
        c.visit() // visit 4: enter ad, then next needed is visit 5's exit
        assertTrue(exit.preloads > exitBefore)
    }

    @Test
    fun `no preload before onboarding or without consent`() {
        val c = controller()
        conditions.onboardingComplete = false
        c.preloadNext()
        conditions.onboardingComplete = true
        conditions.consentAllowed = false
        c.preloadNext()
        assertEquals(0, exit.preloads + enter.preloads)
    }

    @Test
    fun `the two placements have separate IDs and loaders`() {
        assertEquals(AdPlacement.CHAT_ENTER_INTERSTITIAL, ChatVisitAd.ENTER_AD.placement)
        assertEquals(AdPlacement.CHAT_EXIT_INTERSTITIAL, ChatVisitAd.EXIT_AD.placement)
        assertEquals(null, ChatVisitAd.NONE.placement)
        assertNotEquals(AdPlacement.CHAT_ENTER_INTERSTITIAL.realId, AdPlacement.CHAT_EXIT_INTERSTITIAL.realId)
        assertEquals("REPLACE_ME_CHAT_ENTER_INTERSTITIAL", AdPlacement.CHAT_ENTER_INTERSTITIAL.realId)
        assertEquals("REPLACE_ME_CHAT_EXIT_INTERSTITIAL", AdPlacement.CHAT_EXIT_INTERSTITIAL.realId)
        val interstitialTest = AdPlacement.SET_DEFAULT_SMS_INTERSTITIAL.testId
        assertEquals(interstitialTest, AdPlacement.CHAT_ENTER_INTERSTITIAL.testId)
        assertEquals(interstitialTest, AdPlacement.CHAT_EXIT_INTERSTITIAL.testId)
    }

    @Test
    fun `an enter ad never consumes the exit slot's loaded ad, and vice versa`() {
        val c = controller(cycle = listOf(ChatVisitAd.ENTER_AD, ChatVisitAd.EXIT_AD))
        enter.ready = false
        exit.ready = true
        c.tap() // enter due, enter not ready: the ready exit ad must stay untouched
        assertTrue(exit.shownOn.isEmpty())
        assertTrue(exit.ready)
        c.back()
        enter.ready = true
        exit.ready = false
        c.tap() // exit due
        c.back() // exit not ready: the ready enter ad must stay untouched
        assertTrue(enter.shownOn.isEmpty())
        assertTrue(enter.ready)
    }

    private fun resetWorld() {
        enter.ready = true
        exit.ready = true
        exit.shownOn.clear()
        enter.shownOn.clear()
        conditions.onboardingComplete = true
        conditions.consentAllowed = true
        conditions.fullScreenAdBusy = false
        conditions.foreground = true
        stack.clear()
        stack.addAll(listOf("graph", INBOX))
    }

    private companion object {
        const val HOST = "activity"
        const val INBOX = "inbox"
    }
}
