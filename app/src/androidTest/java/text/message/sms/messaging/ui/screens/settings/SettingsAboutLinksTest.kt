package text.message.sms.messaging.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.ui.theme.AppTheme
import text.message.sms.messaging.util.AppLinks
import text.message.sms.messaging.util.ExternalLinks

/**
 * The About section's Rate the app / Privacy policy / Help & feedback rows, rendered through the
 * same row factories and [SettingsList] that [SettingsScreen] uses, wired to a real
 * [ExternalLinks] the way [SettingsViewModel] wires them. Nothing actually leaves the test: the
 * context handed to [ExternalLinks] records each `startActivity` instead of performing it, and
 * can be told which URI schemes have no app to handle them.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAboutLinksTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** "mark" for each `markSelfInitiatedNavigation()`, "start" for each `startActivity`. */
    private val events = mutableListOf<String>()
    private val started = mutableListOf<Intent>()
    private val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
    private val externalLinks = ExternalLinks(markSelfInitiatedNavigation = { events += "mark" })

    @Test
    fun rateTheApp_marksThenOpensThePlayStoreListing() {
        setContent()

        composeRule.onNodeWithText("Rate the app").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("mark", "start"), events)
        assertEquals(Intent.ACTION_VIEW, started.single().action)
        assertEquals("market://details?id=text.message.sms.messaging", started.single().dataString)
    }

    @Test
    fun rateTheApp_fallsBackToTheWebListingWithoutAPlayStoreApp() {
        context.unhandledSchemes += "market"
        setContent()

        composeRule.onNodeWithText("Rate the app").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("mark", "start", "start"), events)
        assertEquals(
            listOf(AppLinks.PLAY_STORE_APP_URI, "https://play.google.com/store/apps/details?id=text.message.sms.messaging"),
            started.map { it.dataString },
        )
    }

    @Test
    fun privacyPolicy_marksThenOpensThePolicyUrl() {
        setContent()

        composeRule.onNodeWithText("Privacy policy").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("mark", "start"), events)
        assertEquals(Intent.ACTION_VIEW, started.single().action)
        assertEquals("https://sites.google.com/view/textmessagesapp/home", started.single().dataString)
    }

    @Test
    fun helpAndFeedback_marksThenOpensAnEmailToSupportWithTheSubject() {
        setContent()

        composeRule.onNodeWithText("Help & feedback").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("mark", "start"), events)
        val intent = started.single()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        assertEquals(
            "tarangsutariya3440@gmail.com?subject=%23Messages%20feedback",
            intent.data?.encodedSchemeSpecificPart,
        )
        assertEquals("#Messages feedback", intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun everyRow_survivesNoAppBeingAbleToOpenIt() {
        context.unhandledSchemes += listOf("market", "https", "mailto")
        setContent()

        composeRule.onNodeWithText("Rate the app").performClick()
        composeRule.onNodeWithText("Privacy policy").performClick()
        composeRule.onNodeWithText("Help & feedback").performClick()
        composeRule.waitForIdle()

        // Rate the app tries both the market: and the web listing; the other two have one target.
        assertEquals(
            listOf("mark", "start", "start", "mark", "start", "mark", "start"),
            events,
        )
    }

    private fun setContent() {
        composeRule.setContent {
            AppTheme {
                SettingsList(
                    sections = listOf(
                        SettingsSection(
                            title = "About",
                            rows = listOf(
                                rateAppRow(onClick = { externalLinks.openPlayStoreListing(context) }),
                                privacyPolicyRow(onClick = { externalLinks.openPrivacyPolicy(context) }),
                                helpFeedbackRow(onClick = { externalLinks.composeFeedbackEmail(context) }),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    private inner class RecordingContext(base: Context) : ContextWrapper(base) {
        val unhandledSchemes = mutableSetOf<String>()

        override fun startActivity(intent: Intent) = startActivity(intent, null)

        override fun startActivity(intent: Intent, options: Bundle?) {
            events += "start"
            started += intent
            if (intent.data?.scheme in unhandledSchemes) throw ActivityNotFoundException()
        }
    }
}
