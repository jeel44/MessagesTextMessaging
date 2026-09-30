package text.message.sms.messaging.util

import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [launchFirstAvailable] is the whole decision behind [ExternalLinks]: mark the trip as
 * self-initiated, then start the first candidate something on the device handles, else report
 * that nothing does. Driven with plain strings in place of `Intent`s -- this module's plain-JVM
 * tests can't build a real one; `SettingsAboutLinksTest` covers the real intents on a device.
 */
class LaunchFirstAvailableTest {

    private val events = mutableListOf<String>()

    private fun launch(candidates: List<String>, unhandled: Set<String> = emptySet()) {
        launchFirstAvailable(
            candidates = candidates,
            markSelfInitiatedNavigation = { events += "mark" },
            start = { candidate ->
                events += "start:$candidate"
                if (candidate in unhandled) throw ActivityNotFoundException()
            },
            onNoAppFound = { events += "noAppFound" },
        )
    }

    @Test
    fun marksSelfInitiatedNavigationBeforeStarting() {
        launch(listOf("browser"))

        assertEquals(listOf("mark", "start:browser"), events)
    }

    @Test
    fun fallsBackToTheNextCandidateWhenTheFirstHasNoHandler() {
        launch(listOf("market", "web"), unhandled = setOf("market"))

        assertEquals(listOf("mark", "start:market", "start:web"), events)
    }

    @Test
    fun stopsAtTheFirstCandidateThatStarts() {
        launch(listOf("market", "web"))

        assertEquals(listOf("mark", "start:market"), events)
    }

    @Test
    fun reportsNoAppFoundInsteadOfThrowingWhenNothingHandlesAnyCandidate() {
        launch(listOf("market", "web"), unhandled = setOf("market", "web"))

        assertEquals(listOf("mark", "start:market", "start:web", "noAppFound"), events)
    }
}
