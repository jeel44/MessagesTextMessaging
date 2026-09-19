package text.message.sms.messaging.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Critical-user-journey baseline profile: cold start -> Home's conversation list -> open a chat ->
 * scroll it -> back -> open Search -> back. Drives the real installed app as a black box via
 * UiAutomator, matching the `text.message.sms.messaging.MainActivity`/`ConversationListScreen`/
 * `ChatScreen` resource-id test tags added alongside this module (`testTagsAsResourceId`) -- there
 * is no other reliable, localization-proof way to find a specific element from outside the app's
 * own process.
 *
 * Run with `./gradlew :baselineprofile:generateBaselineProfile` against a connected device or
 * emulator (API 28+, matching this module's `minSdk`). The result is written straight into
 * `app/src/main/baselineProfiles/`, which `app/build.gradle.kts`'s own `androidx.baselineprofile`
 * plugin application picks up and compiles into the release build automatically -- no manual copy
 * step.
 */
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = TargetPackage,
    ) {
        pressHome()
        startActivityAndWait()

        // Home's conversation list. If this device/emulator has nothing synced yet, there's no
        // row to open -- Home's own first frame (already reached above) still ends up in the
        // profile, so the journey just ends here rather than failing the whole generation run.
        val conversationRow = device.wait(
            Until.findObject(By.res(TargetPackage, ConversationRowResId)),
            UiTimeoutMillis,
        ) ?: return@collect
        conversationRow.click()

        // Chat -- wait for the message list, then scroll through it like a user reading history.
        val messageList = device.wait(
            Until.findObject(By.res(TargetPackage, ChatMessageListResId)),
            UiTimeoutMillis,
        )
        messageList?.apply {
            scroll(Direction.UP, 0.8f)
            device.waitForIdle()
            scroll(Direction.DOWN, 0.8f)
            device.waitForIdle()
        }

        device.pressBack()
        device.wait(Until.findObject(By.res(TargetPackage, ConversationRowResId)), UiTimeoutMillis)

        // Search.
        val searchBar = device.wait(
            Until.findObject(By.res(TargetPackage, HomeSearchBarResId)),
            UiTimeoutMillis,
        )
        searchBar?.click()
        device.waitForIdle()

        device.pressBack()
        device.waitForIdle()
    }
}

private const val TargetPackage = "text.message.sms.messaging"
private const val UiTimeoutMillis = 5_000L

// Must match ConversationRowTestTag/ChatMessageListTestTag/HomeSearchBarTestTag in the app module.
private const val ConversationRowResId = "conversation_row"
private const val ChatMessageListResId = "chat_message_list"
private const val HomeSearchBarResId = "home_search_bar"
