package text.message.sms.messaging.ui.screens.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.ui.components.MessageBubble
import text.message.sms.messaging.ui.components.ScheduledCaptionTestTag
import text.message.sms.messaging.ui.theme.AppTheme

/** The schedule dialog, the composer's "Scheduled for" bar and the scheduled bubble, in both
 * themes. Stateless hosts only -- no ViewModel, no database. */
@RunWith(AndroidJUnit4::class)
class ScheduleRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightTheme_dialogShowsQuickOptionsAndHint() = dialogShowsQuickOptions(darkTheme = false)

    @Test
    fun darkTheme_dialogShowsQuickOptionsAndHint() = dialogShowsQuickOptions(darkTheme = true)

    @Test
    fun dialog_inOneHour_confirmsAboutAnHourAhead() {
        var picked: Long? = null
        val now = System.currentTimeMillis()
        composeRule.setContent {
            AppTheme(darkTheme = false) {
                ScheduleMessageDialog(onDismiss = {}, onConfirm = { picked = it }, nowMillis = now)
            }
        }

        composeRule.onNodeWithText("In 1 hour").performClick()

        val sendAt = checkNotNull(picked)
        assertTrue(sendAt - now in (59 * 60_000L)..(60 * 60_000L))
    }

    @Test
    fun dialog_pickDateAndTime_opensTheDatePicker() {
        composeRule.setContent {
            AppTheme(darkTheme = true) { ScheduleMessageDialog(onDismiss = {}, onConfirm = {}) }
        }

        composeRule.onNodeWithText("Pick date and time").performClick()

        composeRule.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test
    fun lightTheme_composerBarAndScheduleButton() = composerBar(darkTheme = false)

    @Test
    fun darkTheme_composerBarAndScheduleButton() = composerBar(darkTheme = true)

    @Test
    fun lightTheme_pendingBubble() = pendingBubble(darkTheme = false)

    @Test
    fun darkTheme_pendingBubble() = pendingBubble(darkTheme = true)

    @Test
    fun darkTheme_failedBubbleShowsNotSent() {
        composeRule.setContent {
            AppTheme(darkTheme = true) {
                MessageBubble(
                    text = "see you at 9",
                    isOutgoing = true,
                    isLastInRun = true,
                    deliveryState = DeliveryState.FAILED,
                    attachments = emptyList(),
                    onAttachmentClick = {},
                    scheduledCaption = "Not sent · was due Tue, Oct 6, 9:00 AM",
                    scheduleFailed = true,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Not sent · was due Tue, Oct 6, 9:00 AM").assertIsDisplayed()
        // The scheduled caption replaces the generic "Failed. Tap to retry" status.
        composeRule.onNodeWithText("Failed. Tap to retry", substring = true).assertDoesNotExist()
    }

    private fun dialogShowsQuickOptions(darkTheme: Boolean) {
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme) { ScheduleMessageDialog(onDismiss = {}, onConfirm = {}) }
        }

        composeRule.onNodeWithTag(ScheduleDialogTestTag).assertIsDisplayed()
        composeRule.onNodeWithText("Schedule message").assertIsDisplayed()
        composeRule.onNodeWithText("In 1 hour").assertIsDisplayed()
        composeRule.onNodeWithText("Tomorrow", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Pick date and time").assertIsDisplayed()
        composeRule.onNodeWithText("Sent at about this time.").assertIsDisplayed()
    }

    private fun composerBar(darkTheme: Boolean) {
        var cleared = 0
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme) {
                ChatComposer(
                    text = "see you at 9",
                    onTextChange = {},
                    pendingAttachmentUri = null,
                    onRemoveAttachment = {},
                    onAttachClick = {},
                    onSendClick = {},
                    scheduledLabel = "Scheduled for Tue, Oct 6, 9:00 AM",
                    onClearSchedule = { cleared++ },
                )
            }
        }

        composeRule.onNodeWithTag(ScheduledComposerBarTestTag).assertIsDisplayed()
        composeRule.onNodeWithText("Scheduled for Tue, Oct 6, 9:00 AM").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Schedule").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove scheduled time").performClick()
        assertEquals(1, cleared)
    }

    private fun pendingBubble(darkTheme: Boolean) {
        var taps = 0
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme) {
                MessageBubble(
                    text = "see you at 9",
                    isOutgoing = true,
                    isLastInRun = true,
                    deliveryState = DeliveryState.PENDING,
                    attachments = emptyList(),
                    onAttachmentClick = {},
                    scheduledCaption = "Scheduled · Tue, Oct 6, 9:00 AM",
                    onClick = { taps++ },
                )
            }
        }

        composeRule.onNodeWithTag(ScheduledCaptionTestTag).assertIsDisplayed()
        composeRule.onNodeWithText("Scheduled · Tue, Oct 6, 9:00 AM").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Scheduled message").assertIsDisplayed()
        // A pending scheduled message isn't "Sending…".
        composeRule.onNodeWithText("Sending…").assertDoesNotExist()
        composeRule.onNodeWithText("see you at 9").performClick()
        assertEquals(1, taps)
    }
}
