package text.message.sms.messaging.ui.navigation

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.config.OverlayFeatureFlag
import text.message.sms.messaging.data.local.datastore.OnboardingStep
import text.message.sms.messaging.data.local.datastore.applyOverlayFlag
import text.message.sms.messaging.ui.screens.archived.ArchivedScreen
import text.message.sms.messaging.ui.screens.blocked.BlockedScreen
import text.message.sms.messaging.ui.screens.callend.ComingSoonScreen
import text.message.sms.messaging.ui.screens.chat.ChatScreen
import text.message.sms.messaging.ui.screens.chat.MediaViewerScreen
import text.message.sms.messaging.ui.screens.contactslist.ContactsListScreen
import text.message.sms.messaging.ui.screens.scheduled.ScheduledMessagesScreen
import text.message.sms.messaging.ui.screens.conversationinfo.ConversationInfoScreen
import text.message.sms.messaging.ui.screens.conversationlist.ConversationListScreen
import text.message.sms.messaging.ui.screens.newmessage.NewMessageScreen
import text.message.sms.messaging.ui.screens.onboarding.IntroScreen
import text.message.sms.messaging.ui.screens.onboarding.LanguageScreen
import text.message.sms.messaging.ui.screens.onboarding.OnboardingProgressViewModel
import text.message.sms.messaging.ui.screens.onboarding.SetDefaultSmsScreen
import text.message.sms.messaging.ui.screens.onboarding.SplashScreen
import text.message.sms.messaging.ui.screens.onboarding.WelcomeScreen
import text.message.sms.messaging.ui.screens.permissions.OverlayPermissionScreen
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
    isDeepLinkLaunch: Boolean = false,
    onSplashBrandingVisible: () -> Unit = {},
) {
    // Activity-scoped (this is outside NavHost, so the owner is the Activity), so a step write
    // isn't cancelled by the navigation it precedes.
    val onboardingProgress: OnboardingProgressViewModel = hiltViewModel()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    if (BuildConfig.DEBUG) {
        LaunchedEffect(currentRoute) {
            Log.d("NavRoute", "t=${SystemClock.elapsedRealtime()} route=$currentRoute")
        }
    }

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
                isDeepLinkLaunch = isDeepLinkLaunch,
                onBrandingVisible = onSplashBrandingVisible,
                onExit = { step ->
                    navController.navigate(step.route()) {
                        popUpTo(MessagingDestination.Splash.route) { inclusive = true }
                    }
                },
            )
        }

        // Onboarding, in order: Language -> Intro -> Welcome -> OverlayPermission -> SetDefaultSms
        // -> inbox. Each step's "done" stores the next step before navigating there (see
        // OnboardingProgressViewModel), so Splash resumes at it after a process death.
        composable(MessagingDestination.Language.route) {
            LanguageScreen(
                onApplied = {
                    onboardingProgress.advanceTo(OnboardingStep.INTRO) {
                        navController.navigate(MessagingDestination.Intro.route) {
                            popUpTo(MessagingDestination.Language.route) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(MessagingDestination.Intro.route) {
            IntroScreen(
                onFinished = {
                    onboardingProgress.advanceTo(OnboardingStep.WELCOME) {
                        navController.navigate(MessagingDestination.Welcome.route) {
                            popUpTo(MessagingDestination.Intro.route) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(MessagingDestination.Welcome.route) {
            WelcomeScreen(
                onContinue = {
                    // OverlayPermission, or straight to SetDefaultSms with the overlay/call-end
                    // flag off.
                    val next = applyOverlayFlag(OnboardingStep.OVERLAY, OverlayFeatureFlag.isEnabled())
                    onboardingProgress.advanceTo(next) {
                        navController.navigate(next.route()) {
                            popUpTo(MessagingDestination.Welcome.route) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(MessagingDestination.OverlayPermission.route) {
            val onOverlayDone = {
                onboardingProgress.advanceTo(OnboardingStep.SET_DEFAULT_SMS) {
                    navController.navigate(MessagingDestination.SetDefaultSms.route) {
                        popUpTo(MessagingDestination.OverlayPermission.route) { inclusive = true }
                    }
                }
            }
            if (OverlayFeatureFlag.isEnabled()) {
                OverlayPermissionScreen(onGranted = onOverlayDone)
            } else {
                // Backstop only -- Welcome and Splash never route here with the flag off. Moves
                // straight on instead of ever showing (and requesting) the permission.
                LaunchedEffect(Unit) { onOverlayDone() }
            }
        }

        composable(MessagingDestination.SetDefaultSms.route) {
            SetDefaultSmsScreen(
                onDefaultSet = {
                    onboardingProgress.advanceTo(OnboardingStep.DONE) {
                        // Pop to the graph root, not Splash: Splash (and every earlier onboarding
                        // screen) was already popped by its own hop, and popUpTo a route that isn't
                        // on the back stack is a silent no-op -- which left the last step under the
                        // list.
                        navController.navigate(MessagingDestination.ConversationList.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
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
                onBlockedClick = {
                    navController.navigate(MessagingDestination.Blocked.route)
                },
                // Settings' mode of the picker (back arrow, pops back here on confirm) -- never
                // onboarding's Language route, which moves on to Intro.
                onLanguageClick = {
                    navController.navigate(MessagingDestination.LanguageSettings.route)
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

        composable(MessagingDestination.Blocked.route) {
            BlockedScreen(onBack = navController::popBackStack)
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
                onApplied = navController::popBackStack,
                onBack = navController::popBackStack,
            )
        }

        composable(MessagingDestination.ContactsList.route) {
            ContactsListScreen(
                onBack = navController::popBackStack,
            )
        }

        composable(MessagingDestination.ScheduledMessages.route) {
            ScheduledMessagesScreen(
                onBack = navController::popBackStack,
                onConversationClick = { threadId ->
                    navController.navigate(MessagingDestination.Chat.routeFor(threadId))
                },
            )
        }

        composable(
            route = MessagingDestination.ComingSoon.route,
            arguments = listOf(
                navArgument(MessagingDestination.ARG_FEATURE_TITLE) { type = NavType.StringType },
            ),
        ) { entry ->
            val encoded = entry.arguments?.getString(MessagingDestination.ARG_FEATURE_TITLE).orEmpty()
            ComingSoonScreen(
                featureTitle = MessagingDestination.ComingSoon.decodeFeatureTitle(encoded),
                onBack = navController::popBackStack,
            )
        }
    }
}

/** Where Splash resumes for a stored onboarding [OnboardingStep] -- the inbox once it's DONE. */
private fun OnboardingStep.route(): String = when (this) {
    OnboardingStep.LANGUAGE -> MessagingDestination.Language.route
    OnboardingStep.INTRO -> MessagingDestination.Intro.route
    OnboardingStep.WELCOME -> MessagingDestination.Welcome.route
    OnboardingStep.OVERLAY -> MessagingDestination.OverlayPermission.route
    OnboardingStep.SET_DEFAULT_SMS -> MessagingDestination.SetDefaultSms.route
    OnboardingStep.DONE -> MessagingDestination.ConversationList.route
}
