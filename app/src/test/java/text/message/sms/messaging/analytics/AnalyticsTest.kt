package text.message.sms.messaging.analytics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import text.message.sms.messaging.ads.AdPlacement
import text.message.sms.messaging.data.local.datastore.OnboardingStep

class AnalyticsTest {

    private val logged = mutableListOf<Pair<String, Map<String, Any>>>()

    @Before
    fun installFakeSink() {
        Analytics.install { name, params -> logged += name to params }
    }

    @After
    fun uninstallSink() {
        Analytics.install(null)
    }

    @Test
    fun `typed helpers log the fixed event names and keys`() {
        Analytics.onboardingStep(OnboardingStep.SET_DEFAULT_SMS)
        Analytics.languageSelected("system")
        Analytics.introSlideViewed(2)
        Analytics.defaultSmsResult(granted = false)
        Analytics.drawerItemOpened(DrawerItem.BLOCKED)
        Analytics.numberBlocked()
        Analytics.numberUnblocked()
        Analytics.conversationArchived()
        Analytics.messageScheduled()
        Analytics.scheduledCancelled()
        Analytics.scheduledSendNow()
        Analytics.scheduledFailed()
        Analytics.adShown(AdPlacement.HOME_BANNER, AdFormat.BANNER)
        Analytics.adFailed(AdPlacement.APP_OPEN, AdFormat.APP_OPEN)

        assertEquals(
            listOf(
                "onboarding_step" to mapOf("step" to "set_default_sms"),
                "language_selected" to mapOf("language" to "system"),
                "intro_slide_viewed" to mapOf("index" to 2L),
                "default_sms_result" to mapOf("result" to "denied"),
                "drawer_item_opened" to mapOf("item" to "blocked"),
                "number_blocked" to emptyMap(),
                "number_unblocked" to emptyMap(),
                "conversation_archived" to emptyMap(),
                "message_scheduled" to emptyMap(),
                "scheduled_cancelled" to emptyMap(),
                "scheduled_send_now" to emptyMap(),
                "scheduled_failed" to emptyMap(),
                "ad_shown" to mapOf("placement" to "home_banner", "format" to "banner"),
                "ad_failed" to mapOf("placement" to "app_open", "format" to "app_open"),
            ),
            logged,
        )
    }

    @Test
    fun `chat interstitials log their own placement values`() {
        assertTrue(Analytics.adShown(AdPlacement.CHAT_ENTER_INTERSTITIAL, AdFormat.INTERSTITIAL))
        assertTrue(Analytics.adFailed(AdPlacement.CHAT_EXIT_INTERSTITIAL, AdFormat.INTERSTITIAL))
        assertEquals(
            listOf(
                "ad_shown" to mapOf("placement" to "chat_enter_interstitial", "format" to "interstitial"),
                "ad_failed" to mapOf("placement" to "chat_exit_interstitial", "format" to "interstitial"),
            ),
            logged,
        )
    }

    @Test
    fun `every placement and enum value passes validation`() {
        AdPlacement.entries.forEach { placement ->
            AdFormat.entries.forEach { format -> assertTrue(Analytics.adShown(placement, format)) }
        }
        OnboardingStep.entries.forEach { assertTrue(Analytics.onboardingStep(it)) }
        DrawerItem.entries.forEach { assertTrue(Analytics.drawerItemOpened(it)) }
    }

    @Test
    fun `the drawer's Scheduled item logs item=scheduled`() {
        assertTrue(Analytics.drawerItemOpened(DrawerItem.SCHEDULED))
        assertEquals(listOf("drawer_item_opened" to mapOf("item" to "scheduled")), logged)
        assertEquals(
            listOf("archived", "blocked", "scheduled", "language"),
            DrawerItem.entries.map { it.name.lowercase() },
        )
    }

    @Test
    fun `a key not allowed on the event is rejected and nothing is sent`() {
        assertFalse(Analytics.log(AnalyticsEvent.NUMBER_BLOCKED, AnalyticsParam.STEP to "welcome"))
        assertFalse(Analytics.log(AnalyticsEvent.LANGUAGE_SELECTED, AnalyticsParam.PLACEMENT to "en"))
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `values that could be personal data are rejected`() {
        val pii = listOf(
            "+15551234567", // phone number
            "5551234567",
            "Jane Doe", // contact name
            "Your code is 1234", // message text
            "jane@example.com", // address
            "12 high street",
            "",
            "a".repeat(37),
        )
        pii.forEach { value ->
            assertFalse(value, Analytics.languageSelected(value))
            assertNull(value, validatedParams(AnalyticsEvent.ONBOARDING_STEP, listOf(AnalyticsParam.STEP to value)))
        }
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `numeric keys take only small numbers, string keys only strings`() {
        assertFalse(Analytics.introSlideViewed(-1))
        assertFalse(Analytics.introSlideViewed(100))
        assertFalse(Analytics.log(AnalyticsEvent.INTRO_SLIDE_VIEWED, AnalyticsParam.INDEX to "1"))
        assertFalse(Analytics.log(AnalyticsEvent.LANGUAGE_SELECTED, AnalyticsParam.LANGUAGE to 15551234567L))
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `a repeated key is rejected`() {
        assertFalse(
            Analytics.log(
                AnalyticsEvent.AD_SHOWN,
                AnalyticsParam.PLACEMENT to "home_banner",
                AnalyticsParam.PLACEMENT to "chat_native",
            ),
        )
    }

    @Test
    fun `event names and keys are valid Firebase names`() {
        val firebaseName = Regex("^[a-zA-Z][a-zA-Z0-9_]{0,39}$")
        AnalyticsEvent.entries.forEach { assertTrue(it.eventName, firebaseName.matches(it.eventName)) }
        AnalyticsParam.entries.forEach { assertTrue(it.key, firebaseName.matches(it.key)) }
    }

    @Test
    fun `nothing is sent without a sink`() {
        Analytics.install(null)
        assertTrue(Analytics.numberBlocked())
        assertTrue(logged.isEmpty())
    }
}
