package text.message.sms.messaging.ui.screens.conversationlist

import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import text.message.sms.messaging.R
import text.message.sms.messaging.data.local.datastore.SwipeAction
import text.message.sms.messaging.data.local.datastore.SwipeActionPreference
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.repository.SyncProgress
import text.message.sms.messaging.ui.components.SelectionMenuItem
import text.message.sms.messaging.ui.components.SelectionOverflowMenu
import text.message.sms.messaging.ui.components.icon
import text.message.sms.messaging.ui.components.sharpIconPainter
import text.message.sms.messaging.ui.theme.ChatTopBarDivider
import text.message.sms.messaging.ui.theme.ConversationFabBlue
import text.message.sms.messaging.ui.theme.ConversationRowDivider
import text.message.sms.messaging.ui.theme.ConversationRowSelected
import text.message.sms.messaging.ui.theme.ConversationSelectedAvatar
import text.message.sms.messaging.ui.theme.FilterChipContentGray
import text.message.sms.messaging.ui.theme.FilterChipSelectedBorder
import text.message.sms.messaging.ui.theme.FilterChipSelectedContainer
import text.message.sms.messaging.ui.theme.FilterChipUnselectedContainer
import text.message.sms.messaging.ui.theme.Pill
import text.message.sms.messaging.ui.theme.SearchBarBorder
import text.message.sms.messaging.ui.theme.SelectionAccentBlue
import text.message.sms.messaging.ui.theme.SelectionAccentBlueDark
import text.message.sms.messaging.ui.theme.SelectionMenuIconDark
import text.message.sms.messaging.util.ChatOpenHint
import text.message.sms.messaging.util.NavPerfTracer
import text.message.sms.messaging.util.OtpDetector
import text.message.sms.messaging.util.RelativeDateFormatter
import text.message.sms.messaging.util.placeCall
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Resource-id hooks for the baseline profile generator (`:baselineprofile` module) to drive this
 * screen via UiAutomator as a black box -- see [text.message.sms.messaging.MainActivity]'s
 * `testTagsAsResourceId`. Not read by anything else; purely a testing hook, invisible at runtime. */
internal const val HomeSearchBarTestTag = "home_search_bar"
internal const val ConversationRowTestTag = "conversation_row"

/**
 * Inbox. A flat list of every conversation on the screen background (no card behind it), fed live
 * from [ConversationListViewModel.conversations] -- itself a direct view over
 * [text.message.sms.messaging.domain.repository.ConversationRepository.observeInbox], so rows
 * from an in-progress background sync appear as Room commits them, no manual refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (threadId: Long) -> Unit,
    onNewMessageClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onArchivedClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationListViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val homeBodyState by viewModel.homeBodyState.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val swipeActionPreference by viewModel.swipeActionPreference.collectAsStateWithLifecycle()
    val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.filter.collectAsStateWithLifecycle()
    val selectedThreadIds by viewModel.selectedThreadIds.collectAsStateWithLifecycle()
    val selectedConversations by viewModel.selectedConversations.collectAsStateWithLifecycle()
    val isSelectionMode = selectedThreadIds.isNotEmpty()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val archivedLabel = stringResource(R.string.home_archived_snackbar)
    val deletedLabel = stringResource(R.string.home_deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    // System back and the selection top bar's close icon both exit selection mode first, rather
    // than leaving the screen -- matching ChatScreen's own BackHandler for its selection mode.
    BackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }

    // Each swipe (or selection action) emits exactly one event, resolved here by awaiting the
    // snackbar's result before the next is processed -- SnackbarHostState already queues
    // concurrent callers, so a rapid string of swipes just shows one undo-able snackbar after
    // another rather than clobbering each other. See ConversationListViewModel.ConversationListEvent
    // for what each branch means.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ConversationListEvent.Archived -> {
                    val result = snackbarHostState.showSnackbar(
                        message = archivedLabel,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoArchive(event.conversation.threadId)
                    }
                }

                is ConversationListEvent.PendingDelete -> {
                    val result = snackbarHostState.showSnackbar(
                        message = deletedLabel,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.cancelPendingDelete(event.conversation.threadId)
                    } else {
                        viewModel.confirmPendingDelete(event.conversation.threadId)
                    }
                }

                is ConversationListEvent.SelectionArchived -> {
                    val message = context.resources.getQuantityString(
                        R.plurals.home_selection_archived_snackbar,
                        event.threadIds.size,
                        event.threadIds.size,
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoSelectionArchive(event.threadIds)
                    }
                }

                is ConversationListEvent.SelectionDeleted -> {
                    val message = context.resources.getQuantityString(
                        R.plurals.home_selection_deleted_snackbar,
                        event.count,
                        event.count,
                    )
                    snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                }
            }
        }
    }

    // Dismissing the failure banner only hides *this* failure -- remembering the exact instance
    // (rather than a plain boolean) means a fresh failure from a later sync/retry, which is a
    // different SyncProgress.Failed value, reappears instead of staying hidden forever.
    var dismissedFailure by remember { mutableStateOf<SyncProgress.Failed?>(null) }

    // Catches both a return from the role request launched below and a default-SMS-app/contacts
    // permission change made outside the app entirely (system Settings, or onboarding granting
    // READ_CONTACTS mid-session) while this screen was backgrounded or hadn't been reached yet --
    // either way, the screen is visible again (or first appears) exactly when this fires.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshDefaultSmsAppStatus()
        viewModel.refreshContactsPermissionStatus()
        onPauseOrDispose {}
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshDefaultSmsAppStatus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = screenSurfaceColor(),
        topBar = {
            Crossfade(
                targetState = isSelectionMode,
                animationSpec = tween(durationMillis = 150),
                label = "homeTopBar",
            ) { selecting ->
                if (selecting) {
                    HomeSelectionTopBar(
                        selectedCount = selectedThreadIds.size,
                        totalCount = conversations.size,
                        allRead = selectedConversations.isNotEmpty() && selectedConversations.all { !it.hasUnread },
                        allPinned = selectedConversations.isNotEmpty() && selectedConversations.all { it.isPinned },
                        onClose = viewModel::clearSelection,
                        onArchive = viewModel::archiveSelection,
                        onDelete = { showDeleteConfirm = true },
                        onToggleRead = viewModel::toggleReadSelection,
                        onTogglePin = viewModel::togglePinSelection,
                        onBlock = { showBlockConfirm = true },
                        onSelectAll = viewModel::selectAllLoaded,
                    )
                } else {
                    ConversationListTopBar(onSearchClick = onSearchClick, onSettingsClick = onSettingsClick)
                }
            }
        },
        floatingActionButton = {
            // Hides while selecting rather than disabling -- selection mode has no use for
            // starting a brand-new conversation, and the reference design's FAB fades out rather
            // than sitting there dead.
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = fadeIn(tween(durationMillis = 120)),
                exit = fadeOut(tween(durationMillis = 120)),
            ) {
                FloatingActionButton(
                    onClick = onNewMessageClick,
                    containerColor = ConversationFabBlue,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = stringResource(R.string.home_new_chat_label),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ConversationFilterRow(selected = selectedFilter, onSelect = viewModel::selectFilter)

            if (syncProgress is SyncProgress.Running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val failure = syncProgress as? SyncProgress.Failed
            if (failure != null && failure != dismissedFailure) {
                SyncFailedBanner(
                    onRetry = viewModel::retrySync,
                    onDismiss = { dismissedFailure = failure },
                )
            }

            if (archivedCount > 0) {
                ArchivedSummaryRow(count = archivedCount, onClick = onArchivedClick)
            }

            // Plain background, no rounded card behind the list -- matches the reference design,
            // which sits the inbox directly on the screen background rather than a distinct
            // surface underneath it.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (homeBodyState) {
                    HomeBodyState.Loading -> ShimmerConversationList()
                    HomeBodyState.EmptyInbox -> EmptyInbox()
                    HomeBodyState.NotDefault -> NotDefaultSmsAppEmptyState(
                        onRequestDefault = {
                            roleRequestLauncher.launch(viewModel.defaultSmsAppRoleRequestIntent())
                        },
                    )
                    HomeBodyState.List -> ConversationList(
                        conversations = conversations,
                        swipeActionPreference = swipeActionPreference,
                        isSelectionMode = isSelectionMode,
                        selectedThreadIds = selectedThreadIds,
                        onConversationClick = { threadId ->
                            NavPerfTracer.markConversationClicked()
                            onConversationClick(threadId)
                        },
                        onSwipeAction = { action, conversation ->
                            when (action) {
                                SwipeAction.ARCHIVE -> viewModel.archiveConversation(conversation)
                                SwipeAction.TOGGLE_READ -> viewModel.toggleRead(conversation)
                                SwipeAction.CALL ->
                                    conversation.recipients.firstOrNull()?.address?.let { placeCall(context, it) }
                                SwipeAction.DELETE, SwipeAction.NONE -> Unit
                            }
                        },
                        onDeleteRequested = { conversation -> viewModel.requestDelete(conversation) },
                        onToggleSelection = viewModel::toggleSelection,
                        onStartSelection = viewModel::startSelection,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConversationsDialog(
            count = selectedThreadIds.size,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteSelection()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showBlockConfirm) {
        BlockConversationsDialog(
            count = selectedThreadIds.size,
            onConfirm = {
                showBlockConfirm = false
                viewModel.blockSelection()
            },
            onDismiss = { showBlockConfirm = false },
        )
    }
}

/**
 * Pure white in light theme -- [MaterialTheme.colorScheme.background]/`surface` are
 * [text.message.sms.messaging.ui.theme.LightColors]'s `0xFFF9F9FF`, an off-white with the blue
 * channel maxed relative to red/green, which reads as a faint lavender cast once it fills a whole
 * screen. This screen needs genuinely flat white per the reference design, so it swaps in
 * [Color.White] whenever the active scheme's background is light enough to be "white" in the first
 * place -- falling back to the scheme's own (dark) background otherwise, so dark theme, a custom
 * accent, and dynamic color all still render correctly. Scoped to this screen only, not
 * [text.message.sms.messaging.ui.theme.LightColors] itself, so every other screen keeps its
 * current background untouched.
 */
@Composable
internal fun screenSurfaceColor(): Color {
    val background = MaterialTheme.colorScheme.background
    return if (background.luminance() > 0.5f) Color.White else background
}

/**
 * The reference design's search affordance: a white, rounded-pill container (not a plain
 * [androidx.compose.material3.TopAppBar]) holding a hamburger icon, the "Search Messages"
 * placeholder, and the settings hexagon -- deliberately no ads-block icon and no overflow menu.
 * The hamburger isn't wired to anything yet (no drawer exists): purely visual until there's
 * something for it to open. Each icon relies on [IconButton]'s own default 48dp touch target
 * around its 24dp icon to get the reference design's ~12dp visual inset for free, rather than
 * hand-tuning padding around a smaller touch target.
 */
@Composable
private fun ConversationListTopBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(screenSurfaceColor())
            .border(1.dp, SearchBarBorder, RoundedCornerShape(28.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.action_menu),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.home_search_placeholder),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .testTag(HomeSearchBarTestTag)
                .clickable(onClickLabel = stringResource(R.string.action_search), onClick = onSearchClick),
        )
        IconButton(onClick = onSettingsClick) {
            Icon(
                painter = sharpIconPainter(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.action_settings),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** `true` whenever the active [MaterialTheme.colorScheme] reads as a light theme -- same
 * luminance check [screenSurfaceColor] uses, so this screen's fixed reference-design selection
 * colors only apply in light theme and dark theme keeps following [MaterialTheme.colorScheme] as
 * usual, matching [text.message.sms.messaging.ui.screens.chat.ChatScreen]'s own
 * `isLightChatTheme`. */
@Composable
private fun isLightHomeTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

/**
 * Replaces [ConversationListTopBar] while one or more conversations are selected -- same visual
 * language as [text.message.sms.messaging.ui.screens.chat.ChatScreen]'s own selection top bar
 * (56dp height, [screenSurfaceColor] background, [ChatTopBarDivider] hairline, [SelectionAccentBlue]
 * tint), so switching between Home and Chat selection never feels like two different apps. Unlike
 * Chat's bar, Copy/Forward/Share/Details have no equivalent here -- Archive and Delete are the
 * only always-visible actions, with everything else (mark read/unread, pin, block, select all)
 * behind the shared [SelectionOverflowMenu].
 */
@Composable
private fun HomeSelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    allRead: Boolean,
    allPinned: Boolean,
    onClose: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onToggleRead: () -> Unit,
    onTogglePin: () -> Unit,
    onBlock: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = isLightHomeTheme()
    val accentColor = if (isLight) SelectionAccentBlue else SelectionAccentBlueDark
    val dividerColor = if (isLight) ChatTopBarDivider else MaterialTheme.colorScheme.outlineVariant
    var showOverflow by remember { mutableStateOf(false) }

    val markReadLabel = stringResource(R.string.home_selection_mark_read)
    val markUnreadLabel = stringResource(R.string.home_selection_mark_unread)
    val pinLabel = stringResource(R.string.home_selection_pin)
    val unpinLabel = stringResource(R.string.home_selection_unpin)
    val blockLabel = stringResource(R.string.home_selection_block)
    val selectAllLabel = stringResource(R.string.home_selection_select_all)
    val markReadIcon = rememberVectorPainter(if (allRead) Icons.Outlined.MarkChatUnread else Icons.Outlined.MarkChatRead)
    val pinIcon = rememberVectorPainter(Icons.Outlined.PushPin)
    val blockIcon = rememberVectorPainter(Icons.Outlined.Block)
    val selectAllIcon = rememberVectorPainter(Icons.Outlined.SelectAll)

    Column(modifier = modifier.background(screenSurfaceColor())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = sharpIconPainter(R.drawable.ic_closes),
                    contentDescription = stringResource(R.string.home_selection_close),
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.home_selection_count_format, selectedCount, totalCount),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Normal),
                color = accentColor,
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onArchive, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Archive,
                    contentDescription = stringResource(R.string.home_selection_archive),
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = sharpIconPainter(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.home_selection_delete),
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            Box {
                IconButton(onClick = { showOverflow = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.home_selection_more),
                        tint = accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
                SelectionOverflowMenu(
                    expanded = showOverflow,
                    onDismiss = { showOverflow = false },
                    items = listOf(
                        SelectionMenuItem(markReadIcon, if (allRead) markUnreadLabel else markReadLabel) {
                            showOverflow = false
                            onToggleRead()
                        },
                        SelectionMenuItem(pinIcon, if (allPinned) unpinLabel else pinLabel) {
                            showOverflow = false
                            onTogglePin()
                        },
                        SelectionMenuItem(blockIcon, blockLabel) {
                            showOverflow = false
                            onBlock()
                        },
                        SelectionMenuItem(selectAllIcon, selectAllLabel) {
                            showOverflow = false
                            onSelectAll()
                        },
                    ),
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
    }
}

/** Confirms the selection top bar's Delete action -- matches
 * [text.message.sms.messaging.ui.screens.chat.ChatScreen]'s own multi-select delete dialog:
 * plain [AlertDialog], destructive action colored [MaterialTheme.colorScheme.error]. */
@Composable
private fun DeleteConversationsDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        title = { Text(text = pluralStringResource(R.plurals.home_selection_delete_confirm_title, count, count)) },
        text = { Text(text = stringResource(R.string.home_selection_delete_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.home_selection_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
        modifier = modifier,
    )
}

/** Confirms the selection top bar's overflow Block action -- same shape as
 * [text.message.sms.messaging.ui.screens.conversationinfo.ConversationInfoScreen]'s single-thread
 * block dialog, pluralized for a multi-thread selection. */
@Composable
private fun BlockConversationsDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        title = { Text(text = pluralStringResource(R.plurals.home_selection_block_confirm_title, count, count)) },
        text = { Text(text = stringResource(R.string.home_selection_block_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.home_selection_block), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
        modifier = modifier,
    )
}

/** [ConversationFilter.ALL] has no icon -- there's nothing to categorize, so a leading icon would
 * just be noise. The other three always show theirs, selected or not, so the row stays scannable
 * by shape/color rather than requiring the label to be read.
 */
private fun ConversationFilter.iconOrNull() = when (this) {
    ConversationFilter.ALL -> null
    ConversationFilter.PERSONAL -> Icons.Filled.Person
    ConversationFilter.TRANSACTIONS -> Icons.Filled.CreditCard
    ConversationFilter.OTP -> Icons.Filled.Password
}

private fun ConversationFilter.labelRes(): Int = when (this) {
    ConversationFilter.ALL -> R.string.home_filter_all
    ConversationFilter.PERSONAL -> R.string.home_filter_personal
    ConversationFilter.TRANSACTIONS -> R.string.home_filter_transactions
    ConversationFilter.OTP -> R.string.home_filter_otp
}

/** The selected chip renders as a filled [FilterChipSelectedContainer] pill with a matching blue
 * outline and dark text/icon; an unselected chip is a flat [FilterChipUnselectedContainer] fill
 * with gray content and no outline -- see those constants' own doc comments in Color.kt for why
 * neither can just be a `colorScheme.surfaceContainer*`/`primaryContainer` token.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationFilterRow(
    selected: ConversationFilter,
    onSelect: (ConversationFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConversationFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text = stringResource(filter.labelRes()),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    )
                },
                leadingIcon = filter.iconOrNull()?.let { icon ->
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = FilterChipUnselectedContainer,
                    labelColor = FilterChipContentGray,
                    iconColor = FilterChipContentGray,
                    selectedContainerColor = FilterChipSelectedContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = FilterChipSelectedBorder,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Color.Transparent,
                    selectedBorderColor = FilterChipSelectedBorder,
                    borderWidth = 0.dp,
                    selectedBorderWidth = 1.5.dp,
                ),
            )
        }
    }
}

/**
 * Entry point into the archived-conversations list ([text.message.sms.messaging.ui.screens.archived.ArchivedScreen])
 * -- shown above the inbox whenever [count] is positive, hidden entirely otherwise, matching
 * QKSMS's own "only show it if there's something behind it" pattern.
 */
@Composable
private fun ArchivedSummaryRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.home_archived_row_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Dismissible inline banner for [SyncProgress.Failed] -- a sync that fails (wholly or partially,
 * see [SyncProgress.Failed.failedCount]) must not just look like an empty/stalled inbox with no
 * explanation. [onRetry] re-runs the sync via [ConversationListViewModel.retrySync];
 * [onDismiss] only hides this specific failure, see the call site.
 */
@Composable
private fun SyncFailedBanner(onRetry: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_sync_failed_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.home_sync_retry),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/** One [Conversation] row, or a date-section label standing in front of a run of rows that
 * share a day -- see [groupedByDate]. `internal` so
 * [text.message.sms.messaging.ui.screens.archived.ArchivedScreen] can reuse the same grouping. */
internal sealed interface ConversationListItem {
    data class SectionHeader(val date: LocalDate) : ConversationListItem
    data class Row(val conversation: Conversation) : ConversationListItem
}

/**
 * Walks [conversations] in the order the repository already returned them (pinned first, then
 * most recent) and inserts a header wherever the day changes. Pinned rows can therefore cause a
 * day's header to reappear further down the list if an older pinned thread sits above newer
 * unpinned ones -- an accepted trade-off for keeping the repository's pinned-first ordering
 * intact rather than re-sorting purely by date.
 */
internal fun groupedByDate(conversations: List<Conversation>, zone: ZoneId): List<ConversationListItem> {
    val items = mutableListOf<ConversationListItem>()
    var lastDate: LocalDate? = null
    for (conversation in conversations) {
        val date = Instant.ofEpochMilli(conversation.lastMessageAtMillis).atZone(zone).toLocalDate()
        if (date != lastDate) {
            items += ConversationListItem.SectionHeader(date)
            lastDate = date
        }
        items += ConversationListItem.Row(conversation)
    }
    return items
}

/** Left inset for the divider between rows -- lines up with the start of the title/snippet text
 * (16dp row padding + 48dp avatar + 16dp spacer), not the row's own edge, matching the reference
 * design's dividers. */
private val RowDividerInset = 80.dp

/** How close to the very top (in px) [ConversationList] still counts the user as "at the top" for
 * [rememberStayAtTopOnArrival]'s purposes -- a small tolerance rather than requiring an exact
 * `offset == 0`, since a resting scroll position is rarely pixel-perfect. */
private val AtTopOffsetThreshold = 48.dp

/**
 * Flat, ungrouped inbox -- no date-section headers, matching the reference design. [ArchivedScreen]
 * still wants those (see [groupedByDate]/[DateSectionHeader]), so this deliberately doesn't reuse
 * that grouping here rather than changing it out from under that screen too.
 */
@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    swipeActionPreference: SwipeActionPreference,
    onConversationClick: (threadId: Long) -> Unit,
    onSwipeAction: (SwipeAction, Conversation) -> Unit,
    onDeleteRequested: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedThreadIds: Set<Long> = emptySet(),
    onToggleSelection: (Long) -> Unit = {},
    onStartSelection: (Long) -> Unit = {},
) {
    val listState = rememberLazyListState()
    rememberStayAtTopOnArrival(listState, conversations, isSelectionMode)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(
            items = conversations,
            key = { it.threadId },
        ) { conversation ->
            Column {
                SwipeableConversationRow(
                    conversation = conversation,
                    swipeActionPreference = swipeActionPreference,
                    onClick = {
                        ChatOpenHint.prime(conversation)
                        onConversationClick(conversation.threadId)
                    },
                    onSwipeAction = { action -> onSwipeAction(action, conversation) },
                    onDeleteRequested = { onDeleteRequested(conversation) },
                    isSelectionMode = isSelectionMode,
                    isSelected = conversation.threadId in selectedThreadIds,
                    onToggleSelection = { onToggleSelection(conversation.threadId) },
                    onStartSelection = { onStartSelection(conversation.threadId) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = RowDividerInset),
                    thickness = 0.75.dp,
                    color = ConversationRowDivider,
                )
            }
        }
    }
}

/**
 * Keeps [listState] pinned to the top when a new conversation is prepended (a new SMS arrives),
 * but only if the user was already at/near the top *before* that happened -- someone scrolled down
 * reading older threads shouldn't get yanked back up. Skipped entirely in selection mode, so an
 * in-progress multi-select never jumps under the user's thumb.
 *
 * The tricky part is "before": [conversations]' first item's key changing is itself what causes
 * [androidx.compose.foundation.lazy.LazyColumn]'s own key-based scroll anchoring to silently shift
 * [listState]'s `firstVisibleItemIndex` from 0 to 1 (the item that used to be on top is anchored in
 * place, so the new item above it ends up scrolled just out of view) -- exactly the bug this fixes.
 * Reading [listState] *after* that shift would always see "not at top" and never fire. So [wasAtTop]
 * is tracked continuously by its own effect, gated to stop updating the instant the top key changes
 * (`latestTopKey.value == previousTopKey`) until [previousTopKey] catches up again below -- freezing
 * it at whatever it last was *before* the arrival, which is the value this function needs.
 *
 * A changed top key only counts as a genuine arrival (not a filter chip switch or a search) when
 * the previous top item is still present somewhere further down [conversations] -- a filter/search
 * swap tends to drop or reorder items wholesale rather than just prepending one.
 */
@Composable
private fun rememberStayAtTopOnArrival(
    listState: LazyListState,
    conversations: List<Conversation>,
    isSelectionMode: Boolean,
) {
    val topKey = conversations.firstOrNull()?.threadId
    val latestTopKey = rememberUpdatedState(topKey)
    val latestIsSelectionMode = rememberUpdatedState(isSelectionMode)
    var previousTopKey by remember { mutableStateOf(topKey) }
    var wasAtTop by remember { mutableStateOf(true) }
    val atTopOffsetThresholdPx = with(LocalDensity.current) { AtTopOffsetThreshold.toPx() }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (latestTopKey.value == previousTopKey) {
                    wasAtTop = index <= 1 && offset < atTopOffsetThresholdPx
                }
            }
    }

    LaunchedEffect(topKey) {
        val isGenuineArrival = previousTopKey != null && topKey != null && topKey != previousTopKey &&
            conversations.any { it.threadId == previousTopKey }
        if (isGenuineArrival && wasAtTop && !latestIsSelectionMode.value) {
            listState.animateScrollToItem(0)
        }
        previousTopKey = topKey
    }
}

/**
 * Wraps [ConversationRow] in a [SwipeToDismissBox] whose two directions perform whatever
 * [swipeActionPreference] configures (Settings' "Swipe actions" row) -- QKSMS-style archive-right
 * /delete-left are just the defaults.
 *
 * [confirmValueChange] always returns `false`, for every action including [SwipeAction.ARCHIVE]
 * and [SwipeAction.DELETE] -- it never lets [dismissState] commit to a dismissed value, only ever
 * performs the side effect and springs the box back to [SwipeToDismissBoxValue.Settled]. Whether
 * the row then actually disappears is driven entirely by [conversation] leaving the list this
 * composable's caller feeds it (removed from the inbox by [SwipeAction.ARCHIVE]'s write landing,
 * or hidden client-side by [SwipeAction.DELETE]'s
 * [text.message.sms.messaging.ui.screens.conversationlist.ConversationListViewModel.requestDelete]),
 * which happens fast enough to look identical to a committed swipe-dismiss in practice.
 *
 * This is deliberate, not an oversight: letting `confirmValueChange` return `true` and commit
 * [dismissState] to e.g. `StartToEnd` used to seem like the "proper" swipe-dismiss animation, but
 * it is exactly the footgun the API's own deprecation notice warns about. [LazyColumn] retains
 * remembered state per item key across a brief disappear-then-reappear -- which is exactly what
 * archiving-then-undoing does to this row, same [Conversation.threadId] key both times -- so a
 * committed [dismissState] survived the round trip and came back still "dismissed": the row
 * rendered only [SwipeActionBackground] (a plain color fill with an icon, no name or preview)
 * forever after Undo, because [SwipeToDismissBox] never draws [content] for a non-[Settled]
 * value. Always returning `false` here means there is never a committed value to (incorrectly)
 * restore, so a row that reappears -- from Undo or any other data change -- always reappears
 * looking normal.
 */
/** Fraction of the row's width the drag must cross before a swipe commits its action -- see
 * [SwipeableConversationRow]'s doc comment for why this needs to be a large fraction rather than
 * [androidx.compose.material3.SwipeToDismissBoxDefaults]' own default (a fixed 56.dp, a small
 * sliver of any real row width, which is what made a slight/partial swipe commit instantly). */
private const val SwipeCommitThresholdFraction = 0.6f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableConversationRow(
    conversation: Conversation,
    swipeActionPreference: SwipeActionPreference,
    onClick: () -> Unit,
    onSwipeAction: (SwipeAction) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onStartSelection: () -> Unit = {},
) {
    // AnchoredDraggableState (which backs dismissState) re-evaluates confirmValueChange on every
    // drag-move frame for as long as the touch stays past the anchor's threshold, then once more
    // on release. A `true` return normally advances currentValue to the crossed anchor, and
    // AnchoredDraggableState skips re-invoking confirmValueChange for an anchor it's already at --
    // but the `false` return required by the fix above (see its doc comment) means currentValue
    // never advances, so that dedup never engages: one physical swipe invokes confirmValueChange,
    // and therefore performs the action, many times over. Harmless repetition for an idempotent
    // write like ARCHIVE/TOGGLE_READ, but each firing for DELETE queued its own PendingDelete
    // event -- Undo tapped on the first snackbar only cancelled that one, and the duplicates
    // queued right behind it got their own snackbars, timed out untapped, and each actually
    // deleted the conversation moments after the user had already tapped Undo. actionFired gates
    // the action to firing once per excursion away from Settled; the LaunchedEffect below resets
    // it from dismissState's live drag position (targetValue is a derivedStateOf over the raw
    // offset) rather than another confirmValueChange call, since a rejected change's rollback
    // animates straight back to Settled without ever invoking confirmValueChange(Settled).
    var actionFired by remember { mutableStateOf(false) }

    // Measured directly via onSizeChanged below (not derived from positionalThreshold's own
    // totalDistance argument) because positionalThreshold is only consulted lazily, the first
    // time AnchoredDraggableState actually needs to decide a release/fling target -- which can be
    // *after* a raw held-drag has already crossed the anchor's hardcoded 50% midpoint and invoked
    // confirmValueChange once already. Measuring the row's width up front at layout time, well
    // before any drag is possible, means the confirmValueChange distance re-check below always has
    // a real width to compare against, even on a row's very first swipe.
    var rowWidthPx by remember { mutableFloatStateOf(0f) }

    // AnchoredDraggableState's own commit decision isn't purely distance-based: a quick short
    // flick crosses its internal (fixed, not publicly configurable) ~125dp/s velocity threshold
    // and commits regardless of positionalThreshold or how little of the row was actually dragged
    // -- confirmed by hand and by test (a 10%-of-width/400ms drag still fired). Since a "slight"
    // swipe is very often exactly that -- a short, quick nudge -- positionalThreshold alone can't
    // guarantee a full swipe is required. dismissStateRef lets confirmValueChange re-derive the
    // actual instantaneous drag distance itself and veto on distance alone, ignoring whatever
    // velocity/fling reasoning the library used to decide to call it in the first place. It's
    // assigned right after construction below, before this composition can yield control back to
    // the caller -- confirmValueChange itself is never invoked synchronously during composition,
    // only later in response to a real drag, so it is always non-null by the time it's read.
    var dismissStateRef by remember { mutableStateOf<SwipeToDismissBoxState?>(null) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val action = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> swipeActionPreference.startToEnd
                SwipeToDismissBoxValue.EndToStart -> swipeActionPreference.endToStart
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState true
            }
            val draggedFarEnough = rowWidthPx > 0f &&
                abs(dismissStateRef?.requireOffset() ?: 0f) >= rowWidthPx * SwipeCommitThresholdFraction
            if (draggedFarEnough && !actionFired) {
                actionFired = true
                when (action) {
                    SwipeAction.NONE -> Unit
                    SwipeAction.DELETE -> onDeleteRequested()
                    SwipeAction.ARCHIVE, SwipeAction.TOGGLE_READ, SwipeAction.CALL -> onSwipeAction(action)
                }
            }
            false
        },
        // Raised well past SwipeToDismissBoxDefaults' fixed 56.dp default -- 60% of the row's own
        // width, so a slight nudge can no longer read as a "full" swipe just because 56.dp happens
        // to be a small fraction of a wide row. This governs the slow-drag-then-release case (no
        // meaningful fling velocity); the draggedFarEnough re-check above covers the fast-flick
        // case this alone can't, and AnchoredDraggableState's own drag-follow logic separately
        // (and unavoidably, it's not configurable) commits a *held* drag once it physically crosses
        // the anchor's own 50%-of-width midpoint, which is already a deliberate, most-of-the-row
        // gesture on its own.
        positionalThreshold = { totalDistance -> totalDistance * SwipeCommitThresholdFraction },
    )
    dismissStateRef = dismissState

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) {
            actionFired = false
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.onSizeChanged { rowWidthPx = it.width.toFloat() },
        // Swipe actions are disabled entirely while selecting -- a swipe gesture on a row the user
        // is trying to tap-select would otherwise race the selection tap and archive/delete a row
        // out from under an in-progress selection.
        enableDismissFromStartToEnd = !isSelectionMode && swipeActionPreference.startToEnd != SwipeAction.NONE,
        enableDismissFromEndToStart = !isSelectionMode && swipeActionPreference.endToStart != SwipeAction.NONE,
        backgroundContent = {
            // dismissDirection (unlike targetValue, which only flips once the drag crosses the
            // anchor's 50% midpoint) reacts to any nonzero drag offset, so the correct color/icon
            // appears from the very first pixel of the drag -- the user can see and predict the
            // action long before it's anywhere near committing.
            val action = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> swipeActionPreference.startToEnd
                SwipeToDismissBoxValue.EndToStart -> swipeActionPreference.endToStart
                SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
            }
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            val rawOffset = try {
                dismissState.requireOffset()
            } catch (error: IllegalStateException) {
                0f
            }
            val commitDistancePx = rowWidthPx * SwipeCommitThresholdFraction
            val dragProgress = if (commitDistancePx > 0f) {
                (abs(rawOffset) / commitDistancePx).coerceIn(0f, 1f)
            } else {
                0f
            }
            SwipeActionBackground(action = action, alignment = alignment, progress = dragProgress)
        },
    ) {
        ConversationRow(
            conversation = conversation,
            onClick = onClick,
            isSelectionMode = isSelectionMode,
            isSelected = isSelected,
            onToggleSelection = onToggleSelection,
            onStartSelection = onStartSelection,
        )
    }
}

/** Fixed, theme-independent colors -- unlike the app's other swipe actions, archive/delete must
 * always read as blue/red respectively (the universal email-app convention this screen matches),
 * regardless of whichever accent color the user has picked in Settings or dynamic color has
 * derived from their wallpaper. */
private val SwipeArchiveBlue = Color(0xFF1A73E8)
private val SwipeDeleteRed = Color(0xFFD93025)

@Composable
private fun SwipeActionBackground(
    action: SwipeAction,
    alignment: Alignment,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val (container, onContainer) = when (action) {
        SwipeAction.ARCHIVE -> SwipeArchiveBlue to Color.White
        SwipeAction.DELETE -> SwipeDeleteRed to Color.White
        SwipeAction.TOGGLE_READ -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        SwipeAction.CALL -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        // Settled/no-drag is the only state this branch ever renders (both swipe directions are
        // gesture-gated off whenever their configured action is NONE, so dismissDirection can only
        // report Settled while idle) -- transparent here, not a surface token, so the row's real
        // background (this screen's flat white) shows through instead of an unset `surfaceContainer`
        // baseline tint that reads as a second, lavender-tinted background behind every row.
        SwipeAction.NONE -> Color.Transparent to Color.Transparent
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(container)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        action.icon()?.let { icon ->
            // Grows and fades in as the drag approaches the commit threshold (see
            // SwipeableConversationRow's dragProgress), rather than popping in at full size the
            // instant the direction is decided -- the same progressive reveal the color/icon
            // choice above already gives the *direction*, extended to how close to committing.
            val scale = 0.6f + 0.4f * progress
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.4f + 0.6f * progress
                },
            )
        }
    }
}

/** `internal` so [text.message.sms.messaging.ui.screens.archived.ArchivedScreen] can reuse it
 * for its own date-grouped list -- see [RelativeDateFormatter.dayHeaderLabel]. Never the words
 * "Today"/"Yesterday". */
@Composable
internal fun DateSectionHeader(date: LocalDate, modifier: Modifier = Modifier) {
    val label = remember(date) { RelativeDateFormatter.dayHeaderLabel(date) }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** `internal` so [text.message.sms.messaging.ui.screens.archived.ArchivedScreen] can reuse the
 * same row rendering rather than duplicating it -- [isSelectionMode]/[isSelected] both default to
 * `false` and the two selection callbacks default to no-ops, so that screen (which has no
 * multi-select of its own) needs no changes to keep compiling and behaving exactly as before.
 *
 * In selection mode a tap toggles [isSelected] instead of opening the chat, and a long-press does
 * nothing further (the row is already part of a selection); outside selection mode a tap opens
 * the chat as always and a long-press is what starts selection -- see [SwipeableConversationRow]'s
 * caller for where [onToggleSelection]/[onStartSelection] are wired to
 * [ConversationListViewModel.toggleSelection]/[ConversationListViewModel.startSelection].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onStartSelection: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val isLight = isLightHomeTheme()
    val selectedBackground = if (isLight) ConversationRowSelected else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val rowBackground by animateColorAsState(
        targetValue = if (isSelected) selectedBackground else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "conversationRowBackground",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .testTag(ConversationRowTestTag)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onClick() },
                onLongClick = {
                    if (!isSelectionMode) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartSelection()
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(
            targetState = isSelected,
            animationSpec = tween(durationMillis = 120),
            label = "conversationRowAvatar",
        ) { selected ->
            if (selected) SelectedRowAvatar() else ConversationAvatar(conversation)
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                fontWeight = if (conversation.hasUnread) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = conversation.snippet,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                fontWeight = if (conversation.hasUnread) FontWeight.Bold else FontWeight.Normal,
                color = if (conversation.hasUnread) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = if (conversation.hasUnread) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )

            val otpCode = remember(conversation.snippet) { OtpDetector.extractCode(conversation.snippet) }
            if (otpCode != null) {
                Spacer(modifier = Modifier.height(6.dp))
                OtpQuickCopyChip(code = otpCode)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatConversationDate(conversation.lastMessageAtMillis),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (conversation.hasUnread) {
                Spacer(modifier = Modifier.height(4.dp))
                UnreadCountBadge(count = conversation.unreadCount)
            }
        }
    }
}

@Composable
private fun UnreadCountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(AccentBlue)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/**
 * Text-link affordance offering to copy [code] straight from the inbox, without opening the
 * conversation -- [Modifier.clickable] here consumes the tap itself, so it never also fires
 * [ConversationRow]'s own [combinedClickable] underneath it. [code] comes from [OtpDetector],
 * shared with nothing else yet: there's no equivalent affordance inside the Chat screen today.
 * Shows the fixed "Copy OTP" label rather than [code] itself -- the code is still what actually
 * gets copied, just not printed a second time next to the snippet that already contains it.
 */
@Composable
private fun OtpQuickCopyChip(code: String, modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.home_otp_copied_toast)
    val copyDescription = stringResource(R.string.home_otp_copy_content_description)
    val copyLabel = stringResource(R.string.home_otp_copy_label)

    Row(
        modifier = modifier
            .clickable(onClickLabel = copyDescription) {
                clipboardManager.setText(AnnotatedString(code))
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = copyLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Bold,
            color = AccentBlue,
        )
    }
}

/** The saturated blue used for both the OTP chip above and [AvatarPalette]'s blue swatch below --
 * pulled out to a shared constant so the two can never drift apart again. Deliberately not
 * [MaterialTheme.colorScheme.primary]: that token is themeable (custom accent / dynamic color) and
 * measurably less saturated than this hue, which is what caused the chip to visibly mismatch the
 * avatar it sits next to. */
private val AccentBlue = Color(0xFF3B7DED)

/** Flat, theme-independent fill for a saved contact with no synced photo -- a generic person
 * silhouette on gray, not a colored/initialed avatar, since a saved contact isn't a bank/OTP
 * sender that needs to stand out; only [AccentBlue] below marks that distinction. */
private val ContactPlaceholderGray = Color(0xFFBDBDBD)

/** Initials for a raw sender address (a bank/OTP/business sender ID, not a saved contact) --
 * [text.message.sms.messaging.domain.model.Contact.initials] only makes sense for a real display
 * name, so an unresolved sender needs its own fallback: the address's own letters if it has any
 * (e.g. "HDFCBK" -> "HD"), else its first digit, else "#". */
private fun initialsForAddress(address: String): String {
    val letters = address.filter { it.isLetter() }
    if (letters.isNotEmpty()) return letters.take(2).uppercase()
    val digits = address.filter { it.isDigit() }
    return digits.take(1).ifEmpty { "#" }
}

/** Replaces [ConversationAvatar] for a selected row in Home's multi-select mode -- same 48dp
 * footprint so the row's layout never shifts when a selection is toggled, see [ConversationRow]'s
 * [Crossfade] between the two. */
@Composable
private fun SelectedRowAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(ConversationSelectedAvatar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(R.string.chat_selection_selected_indicator),
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun ConversationAvatar(conversation: Conversation, modifier: Modifier = Modifier) {
    val recipient = conversation.recipients.firstOrNull()
    val contact = recipient?.contact
    val hasPhoto = !contact?.photoUri.isNullOrBlank()
    val backgroundColor = when {
        conversation.isGroup -> MaterialTheme.colorScheme.primaryContainer
        contact != null -> ContactPlaceholderGray
        else -> AccentBlue
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            conversation.isGroup -> Icon(
                imageVector = Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            // Saved contact, no photo -- generic silhouette, not initials: see
            // ContactPlaceholderGray's doc comment for why this doesn't use AvatarPalette-style
            // per-contact color.
            contact != null -> Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
            )
            // Unresolved sender (bank/OTP/business sender ID) -- always AccentBlue, not hashed
            // per-sender, so it reads as "not a saved contact" at a glance.
            else -> Text(
                text = initialsForAddress(recipient?.address.orEmpty()),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
        if (hasPhoto) {
            AsyncImage(
                model = contact?.photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape),
            )
        }
    }
}

/**
 * 8 fixed skeleton rows shaped like [ConversationRow], shown in place of the conversation list
 * while the first sync is still running and nothing has landed in `conversations` yet -- see the
 * call site in [ConversationListScreen].
 */
@Composable
private fun ShimmerConversationList(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        repeat(8) {
            ShimmerConversationRow()
        }
    }
}

@Composable
private fun ShimmerConversationRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(shimmerBrush()),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush()),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush()),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush()),
            )
        }
    }
}

/**
 * A [Brush] that sweeps a lighter highlight across [MaterialTheme.colorScheme.surfaceVariant] on
 * a loop, ~1200ms per pass -- the shimmering background behind each skeleton shape in
 * [ShimmerConversationRow].
 */
@Composable
private fun shimmerBrush(): Brush {
    val color = MaterialTheme.colorScheme.surfaceVariant
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    return Brush.linearGradient(
        colors = listOf(
            color.copy(alpha = 0.6f),
            color.copy(alpha = 1f),
            color.copy(alpha = 0.6f),
        ),
        start = Offset(translate - 400f, translate - 400f),
        end = Offset(translate, translate),
    )
}

/**
 * Short-form date label for a row's right column -- a bare time for today, a weekday abbreviation
 * ("Thu", "Wed") for the rest of the last 7 days, and "12 Sep" / "12 Sep 2025" for anything older,
 * see [RelativeDateFormatter.listLabel]. Never the words "Today"/"Yesterday".
 */
@Composable
private fun formatConversationDate(timestampMillis: Long): String {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    return remember(timestampMillis, is24Hour) {
        RelativeDateFormatter.listLabel(timestampMillis, is24Hour)
    }
}

@Composable
private fun EmptyInbox(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Shown instead of [EmptyInbox] when the inbox is empty *and* this app does not currently hold
 * the default-SMS-app role -- the accurate reason nothing has synced yet, rather than a generic
 * "no messages" state that looks like a bug. [onRequestDefault] reuses
 * [text.message.sms.messaging.service.DefaultSmsAppGuard]'s own role-request intent directly from
 * here, so granting it doesn't require backing out to onboarding.
 */
@Composable
private fun NotDefaultSmsAppEmptyState(onRequestDefault: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.MarkChatUnread,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.home_not_default_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.home_not_default_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onRequestDefault, shape = Pill) {
            Text(stringResource(R.string.set_default_sms_button))
        }
    }
}
