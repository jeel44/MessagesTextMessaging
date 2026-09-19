package text.message.sms.messaging.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import text.message.sms.messaging.ui.screens.archived.ArchivedScreen
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

/** Every destination's transition duration -- short enough to feel immediate, unlike Navigation
 * Compose's own longer default crossfade, while still being a real, visible transition rather than
 * an instant cut (the perf pass this was added for keeps animations, just makes them snappy). */
private const val NavTransitionMillis = 200

/** Small forward slide (not a full-screen-width push) paired with a fade -- set once on [NavHost]
 * itself so every destination gets the same fast, single transition instead of composable() call
 * sites each duplicating (or omitting) their own. */
private val NavForwardEnter = slideInHorizontally(
    animationSpec = tween(NavTransitionMillis, easing = FastOutSlowInEasing),
    initialOffsetX = { fullWidth -> fullWidth / 8 },
) + fadeIn(animationSpec = tween(NavTransitionMillis, easing = FastOutSlowInEasing))

private val NavForwardExit = fadeOut(animationSpec = tween(NavTransitionMillis, easing = FastOutSlowInEasing))

private val NavBackEnter = fadeIn(animationSpec = tween(NavTransitionMillis, easing = FastOutSlowInEasing))

private val NavBackExit = slideOutHorizontally(
    animationSpec = tween(NavTransitionMillis, easing = FastOutSlowInEasing),
    targetOffsetX = { fullWidth -> fullWidth / 8 },
) + fadeOut(animationSpec = tween(NavTransitionMillis, easing = FastOutSlowInEasing))

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
        enterTransition = { NavForwardEnter },
        exitTransition = { NavForwardExit },
        popEnterTransition = { NavBackEnter },
        popExitTransition = { NavBackExit },
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
                    navController.navigate(MessagingDestination.NewMessage.routeFor())
                },
                onSearchClick = {
                    navController.navigate(MessagingDestination.Search.route)
                },
                onSettingsClick = {
                    navController.navigate(MessagingDestination.Settings.route)
                },
                onArchivedClick = {
                    navController.navigate(MessagingDestination.Archived.route)
                },
            )
        }

        composable(MessagingDestination.Archived.route) {
            ArchivedScreen(
                onBack = navController::popBackStack,
                onConversationClick = { threadId ->
                    navController.navigate(MessagingDestination.Chat.routeFor(threadId))
                },
            )
        }

        composable(
            route = MessagingDestination.Chat.route,
            arguments = listOf(
                navArgument(MessagingDestination.ARG_THREAD_ID) { type = NavType.LongType },
                navArgument(MessagingDestination.ARG_INITIAL_TEXT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
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
                onForwardClick = { text ->
                    navController.navigate(MessagingDestination.NewMessage.routeFor(prefillText = text))
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

        composable(
            route = MessagingDestination.NewMessage.route,
            arguments = listOf(
                navArgument(MessagingDestination.ARG_PREFILL_TEXT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val prefillText = entry.arguments?.getString(MessagingDestination.ARG_PREFILL_TEXT)
                ?.let(MessagingDestination.NewMessage::decodePrefill)
            NewMessageScreen(
                onBack = navController::popBackStack,
                onConversationStarted = { threadId ->
                    navController.navigate(MessagingDestination.Chat.routeFor(threadId, initialText = prefillText)) {
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
                onLanguageClick = {
                    navController.navigate(MessagingDestination.LanguageSettings.route)
                },
            )
        }

        composable(MessagingDestination.LanguageSettings.route) {
            LanguageScreen(
                onBack = navController::popBackStack,
            )
        }
    }
}
