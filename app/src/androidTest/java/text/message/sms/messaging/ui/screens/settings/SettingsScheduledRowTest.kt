package text.message.sms.messaging.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.ui.navigation.MessagingDestination
import text.message.sms.messaging.ui.theme.AppTheme

/**
 * General's "Scheduled messages" row, rendered through the same [scheduledMessagesRow] factory
 * and [SettingsList] [SettingsScreen] uses, inside a NavHost wired the way MessagingNavHost wires
 * `onScheduledMessagesClick` -- so a tap is checked to land on the real scheduled list route.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScheduledRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    @Test
    fun lightTheme_rowOpensTheScheduledListRoute() = rowOpensTheList(darkTheme = false)

    @Test
    fun darkTheme_rowOpensTheScheduledListRoute() = rowOpensTheList(darkTheme = true)

    private fun rowOpensTheList(darkTheme: Boolean) {
        composeRule.setContent {
            navController = rememberNavController()
            AppTheme(darkTheme = darkTheme) {
                NavHost(navController = navController, startDestination = MessagingDestination.Settings.route) {
                    composable(MessagingDestination.Settings.route) {
                        SettingsList(
                            sections = listOf(
                                SettingsSection(
                                    title = "General",
                                    rows = listOf(
                                        scheduledMessagesRow(onClick = {
                                            navController.navigate(MessagingDestination.ScheduledMessages.route)
                                        }),
                                    ),
                                ),
                            ),
                        )
                    }
                    composable(MessagingDestination.ScheduledMessages.route) { Text("scheduled list") }
                }
            }
        }

        composeRule.onNodeWithText("Scheduled messages").assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(MessagingDestination.ScheduledMessages.route, navController.currentDestination?.route)
        composeRule.onNodeWithText("scheduled list").assertIsDisplayed()
    }
}
