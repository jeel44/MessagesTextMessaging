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
import text.message.sms.messaging.ui.screens.chat.MediaViewerScreen
import text.message.sms.messaging.ui.screens.conversationinfo.ConversationInfoScreen
import text.message.sms.messaging.ui.screens.conversationlist.ConversationListScreen
import text.message.sms.messaging.ui.screens.newmessage.NewMessageScreen
import text.message.sms.messaging.ui.screens.onboarding.LanguageScreen
import text.message.sms.messaging.ui.screens.onboarding.SetDefaultSmsScreen
import text.message.sms.messaging.ui.screens.onboarding.SplashScreen
import text.message.sms.messaging.ui.screens.onboarding.WelcomeScreen
import text.message.sms.messaging.ui.screens.search.SearchScreen
import text.message.sms.messaging.ui.screens.settings.SettingsScreen

/** Wires every screen together. Screens receive plain lambdas, never the controller itself. */
@Composable
fun MessagingNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = MessagingDestination.Splash.route,
        modifier = modifier,
    ) {
        composable(MessagingDestination.Splash.route) {
            SplashScreen(
                onOnboardingComplete = {
                    navController.navigate(MessagingDestination.ConversationList.route) {
                        popUpTo(MessagingDestination.Splash.route) { inclusive = true }
                    }
                },
                onOnboardingIncomplete = {
                    navController.navigate(MessagingDestination.Welcome.route) {
                        popUpTo(MessagingDestination.Splash.route) { inclusive = true }
                    }
                },
            )
        }

        composable(MessagingDestination.Welcome.route) {
            WelcomeScreen(
                onContinue = {
                    navController.navigate(MessagingDestination.SetDefaultSms.route) {
                        popUpTo(MessagingDestination.Welcome.route) { inclusive = true }
                    }
                },
            )
        }

        composable(MessagingDestination.SetDefaultSms.route) {
            SetDefaultSmsScreen(
                onDefaultSet = {
                    navController.navigate(MessagingDestination.Language.route) {
                        popUpTo(MessagingDestination.SetDefaultSms.route) { inclusive = true }
                    }
                },
            )
        }

        composable(MessagingDestination.Language.route) {
            LanguageScreen(
                onContinue = {
                    navController.navigate(MessagingDestination.ConversationList.route) {
                        popUpTo(MessagingDestination.Splash.route) { inclusive = true }
                    }
                },
            )
        }

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
                onSettingsClick = {
                    navController.navigate(MessagingDestination.Settings.route)
                },
            )
        }

        composable(
            route = MessagingDestination.Chat.route,
            arguments = listOf(
                navArgument(MessagingDestination.ARG_THREAD_ID) { type = NavType.LongType },
            ),
        ) { entry ->
            val threadId = entry.arguments?.getLong(MessagingDestination.ARG_THREAD_ID) ?: 0L
            ChatScreen(
                onBack = navController::popBackStack,
                onAttachmentClick = { contentUri ->
                    navController.navigate(MessagingDestination.MediaViewer.routeFor(contentUri))
                },
                onConversationInfoClick = {
                    navController.navigate(MessagingDestination.ConversationInfo.routeFor(threadId))
                },
            )
        }

        composable(
            route = MessagingDestination.ConversationInfo.route,
            arguments = listOf(
                navArgument(MessagingDestination.ARG_THREAD_ID) { type = NavType.LongType },
            ),
        ) {
            ConversationInfoScreen(
                onBack = navController::popBackStack,
                onMediaClick = { contentUri ->
                    navController.navigate(MessagingDestination.MediaViewer.routeFor(contentUri))
                },
            )
        }

        composable(
            route = MessagingDestination.MediaViewer.route,
            arguments = listOf(
                navArgument(MessagingDestination.ARG_CONTENT_URI) { type = NavType.StringType },
            ),
        ) { entry ->
            val encoded = entry.arguments?.getString(MessagingDestination.ARG_CONTENT_URI).orEmpty()
            MediaViewerScreen(
                contentUri = MessagingDestination.MediaViewer.decodeContentUri(encoded),
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

        composable(MessagingDestination.Settings.route) {
            SettingsScreen(
                onBack = navController::popBackStack,
            )
        }
    }
}
