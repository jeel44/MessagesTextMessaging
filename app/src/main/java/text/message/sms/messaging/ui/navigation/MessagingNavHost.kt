package text.message.sms.messaging.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import text.message.sms.messaging.ui.screens.chat.ChatScreen
import text.message.sms.messaging.ui.screens.conversationlist.ConversationListScreen
import text.message.sms.messaging.ui.screens.newmessage.NewMessageScreen
import text.message.sms.messaging.ui.screens.search.SearchScreen

/** Wires every screen together. Screens receive plain lambdas, never the controller itself. */
@Composable
fun MessagingNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = MessagingDestination.ConversationList.route,
        modifier = modifier,
    ) {
        composable(MessagingDestination.ConversationList.route) {
            ConversationListScreen(
                onConversationClick = { threadId ->
                    navController.navigate(MessagingDestination.Chat.routeFor(threadId))
                },
                onNewMessageClick = {
                    navController.navigate(MessagingDestination.NewMessage.route)
                },
                onSearchClick = {
                    navController.navigate(MessagingDestination.Search.route)
                },
            )
        }

        composable(
            route = MessagingDestination.Chat.route,
            arguments = listOf(
                navArgument(MessagingDestination.ARG_THREAD_ID) { type = NavType.LongType },
            ),
        ) { entry ->
            ChatScreen(
                threadId = entry.arguments
                    ?.getLong(MessagingDestination.ARG_THREAD_ID)
                    ?: 0L,
                onBack = navController::popBackStack,
            )
        }

        composable(MessagingDestination.NewMessage.route) {
            NewMessageScreen(
                onBack = navController::popBackStack,
                onConversationStarted = { threadId ->
                    navController.navigate(MessagingDestination.Chat.routeFor(threadId)) {
                        popUpTo(MessagingDestination.ConversationList.route)
                    }
                },
            )
        }

        composable(MessagingDestination.Search.route) {
            SearchScreen(
                onBack = navController::popBackStack,
                onResultClick = { threadId ->
                    navController.navigate(MessagingDestination.Chat.routeFor(threadId))
                },
            )
        }
    }
}
