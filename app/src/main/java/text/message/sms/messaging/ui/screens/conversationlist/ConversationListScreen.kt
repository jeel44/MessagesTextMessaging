package text.message.sms.messaging.ui.screens.conversationlist

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.R
import text.message.sms.messaging.data.local.datastore.SwipeAction
import text.message.sms.messaging.data.local.datastore.SwipeActionPreference
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.repository.SyncProgress
import text.message.sms.messaging.ui.components.icon
import text.message.sms.messaging.ui.theme.Pill
import text.message.sms.messaging.util.placeCall
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Inbox. A single rounded-top-corner surface holding every conversation, fed live from
 * [ConversationListViewModel.conversations] -- itself a direct view over
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
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val isDefaultSmsApp by viewModel.isDefaultSmsApp.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val swipeActionPreference by viewModel.swipeActionPreference.collectAsStateWithLifecycle()
    val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val archivedLabel = stringResource(R.string.home_archived_snackbar)
    val deletedLabel = stringResource(R.string.home_deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)

    // Each swipe emits exactly one event, resolved here by awaiting the snackbar's result before
    // the next is processed -- SnackbarHostState already queues concurrent callers, so a rapid
    // string of swipes just shows one undo-able snackbar after another rather than clobbering
    // each other. See ConversationListViewModel.ConversationListEvent for what each branch means.
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
            }
        }
    }

    // Dismissing the failure banner only hides *this* failure -- remembering the exact instance
    // (rather than a plain boolean) means a fresh failure from a later sync/retry, which is a
    // different SyncProgress.Failed value, reappears instead of staying hidden forever.
    var dismissedFailure by remember { mutableStateOf<SyncProgress.Failed?>(null) }

    // Catches both a return from the role request launched below and a default-SMS-app change
    // made outside the app entirely (system Settings) while this screen was backgrounded --
    // either way, the screen is visible again exactly when this fires.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshDefaultSmsAppStatus()
        onPauseOrDispose {}
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshDefaultSmsAppStatus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_conversations)) },
                navigationIcon = { BrandMark() },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewMessageClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.action_new_message),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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

            FilterChipRow(
                selected = filter,
                onSelect = viewModel::selectFilter,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (archivedCount > 0) {
                ArchivedSummaryRow(count = archivedCount, onClick = onArchivedClick)
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                if (conversations.isEmpty()) {
                    if (syncProgress is SyncProgress.Running) {
                        ShimmerConversationList()
                    } else if (isDefaultSmsApp) {
                        EmptyInbox()
                    } else {
                        NotDefaultSmsAppEmptyState(
                            onRequestDefault = {
                                roleRequestLauncher.launch(viewModel.defaultSmsAppRoleRequestIntent())
                            },
                        )
                    }
                } else {
                    ConversationList(
                        conversations = conversations,
                        swipeActionPreference = swipeActionPreference,
                        onConversationClick = onConversationClick,
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
                    )
                }
            }
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

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Sms,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
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

@Composable
private fun FilterChipRow(
    selected: ConversationFilter,
    onSelect: (ConversationFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = listOf(
        ConversationFilter.ALL to stringResource(R.string.home_filter_all),
        ConversationFilter.UNREAD to stringResource(R.string.home_filter_unread),
        ConversationFilter.PINNED to stringResource(R.string.home_filter_pinned),
    )
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.first }) { (chipFilter, label) ->
            FilterChip(
                selected = chipFilter == selected,
                onClick = { onSelect(chipFilter) },
                label = { Text(label) },
            )
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

@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    swipeActionPreference: SwipeActionPreference,
    onConversationClick: (threadId: Long) -> Unit,
    onSwipeAction: (SwipeAction, Conversation) -> Unit,
    onDeleteRequested: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val items = remember(conversations, zone) { groupedByDate(conversations, zone) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(
            items = items,
            key = { item ->
                when (item) {
                    is ConversationListItem.SectionHeader -> "header_${item.date}"
                    is ConversationListItem.Row -> item.conversation.threadId
                }
            },
        ) { item ->
            when (item) {
                is ConversationListItem.SectionHeader -> DateSectionHeader(item.date)
                is ConversationListItem.Row -> SwipeableConversationRow(
                    conversation = item.conversation,
                    swipeActionPreference = swipeActionPreference,
                    onClick = { onConversationClick(item.conversation.threadId) },
                    onSwipeAction = { action -> onSwipeAction(action, item.conversation) },
                    onDeleteRequested = { onDeleteRequested(item.conversation) },
                )
            }
        }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableConversationRow(
    conversation: Conversation,
    swipeActionPreference: SwipeActionPreference,
    onClick: () -> Unit,
    onSwipeAction: (SwipeAction) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val action = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> swipeActionPreference.startToEnd
                SwipeToDismissBoxValue.EndToStart -> swipeActionPreference.endToStart
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState true
            }
            when (action) {
                SwipeAction.NONE -> Unit
                SwipeAction.DELETE -> onDeleteRequested()
                SwipeAction.ARCHIVE, SwipeAction.TOGGLE_READ, SwipeAction.CALL -> onSwipeAction(action)
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = swipeActionPreference.startToEnd != SwipeAction.NONE,
        enableDismissFromEndToStart = swipeActionPreference.endToStart != SwipeAction.NONE,
        backgroundContent = {
            val action = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> swipeActionPreference.startToEnd
                SwipeToDismissBoxValue.EndToStart -> swipeActionPreference.endToStart
                SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
            }
            val alignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            SwipeActionBackground(action = action, alignment = alignment)
        },
    ) {
        ConversationRow(conversation = conversation, onClick = onClick)
    }
}

@Composable
private fun SwipeActionBackground(action: SwipeAction, alignment: Alignment, modifier: Modifier = Modifier) {
    val (container, onContainer) = when (action) {
        SwipeAction.ARCHIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SwipeAction.DELETE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        SwipeAction.TOGGLE_READ -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        SwipeAction.CALL -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        SwipeAction.NONE -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.surfaceContainer
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(container)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        action.icon()?.let { icon ->
            Icon(imageVector = icon, contentDescription = null, tint = onContainer)
        }
    }
}

/** `internal` so [text.message.sms.messaging.ui.screens.archived.ArchivedScreen] can reuse it
 * for its own date-grouped list. */
@Composable
internal fun DateSectionHeader(date: LocalDate, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val label = when (date) {
        today -> stringResource(R.string.home_date_today)
        today.minusDays(1) -> stringResource(R.string.home_date_yesterday)
        else -> remember(date) {
            val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            DateFormat.getMediumDateFormat(context).format(Date(millis))
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** `internal` so [text.message.sms.messaging.ui.screens.archived.ArchivedScreen] can reuse the
 * same row rendering rather than duplicating it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                // TODO: long-press action sheet (archive/pin/mute/delete) -- deferred, needs its
                // own design pass; this is just the gesture hook so it's not silently missing.
                onLongClick = {},
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationAvatar(conversation)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (conversation.hasUnread) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conversation.hasUnread) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatMessageTime(conversation.lastMessageAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conversation.hasUnread) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = conversation.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConversationAvatar(conversation: Conversation, modifier: Modifier = Modifier) {
    val contact = conversation.recipients.firstOrNull()?.contact

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when {
            conversation.isGroup -> Icon(
                imageVector = Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            contact != null -> Text(
                text = contact.initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            else -> Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
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

@Composable
private fun formatMessageTime(timestampMillis: Long): String {
    val context = LocalContext.current
    return remember(timestampMillis) {
        DateFormat.getTimeFormat(context).format(Date(timestampMillis))
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
