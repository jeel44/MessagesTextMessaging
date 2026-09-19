package text.message.sms.messaging.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.SimInfo
import text.message.sms.messaging.ui.components.AppBackButton
import text.message.sms.messaging.ui.components.MessageBubble
import text.message.sms.messaging.ui.components.SelectionMenuItem
import text.message.sms.messaging.ui.components.SelectionOverflowMenu
import text.message.sms.messaging.ui.components.sharpIconPainter
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor
import text.message.sms.messaging.ui.theme.ChatAvatarAccentBlue
import text.message.sms.messaging.ui.theme.ChatAvatarPlaceholderGray
import text.message.sms.messaging.ui.theme.ConversationFabBlue
import text.message.sms.messaging.ui.theme.ChatCantReplyGray
import text.message.sms.messaging.ui.theme.ChatDateSeparatorGray
import text.message.sms.messaging.ui.theme.ChatHintGray
import text.message.sms.messaging.ui.theme.ChatNeutralFill
import text.message.sms.messaging.ui.theme.ChatOtpCopyBorderGray
import text.message.sms.messaging.ui.theme.ChatSecurityCardBackground
import text.message.sms.messaging.ui.theme.ChatSecurityCardText
import text.message.sms.messaging.ui.theme.ChatSendButtonDisabled
import text.message.sms.messaging.ui.theme.ChatSendButtonEnabled
import text.message.sms.messaging.ui.theme.ChatTopBarDivider
import text.message.sms.messaging.ui.theme.Pill
import text.message.sms.messaging.ui.theme.SelectionAccentBlue
import text.message.sms.messaging.ui.theme.SelectionAccentBlueDark
import text.message.sms.messaging.util.NavPerfTracer
import text.message.sms.messaging.util.OtpDetector
import text.message.sms.messaging.util.PhoneNumbers
import text.message.sms.messaging.util.RelativeDateFormatter
import text.message.sms.messaging.util.placeCall
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** Resource-id hook for the baseline profile generator (`:baselineprofile` module) to scroll this
 * screen via UiAutomator -- see [text.message.sms.messaging.MainActivity]'s `testTagsAsResourceId`.
 * Not read by anything else; purely a testing hook, invisible at runtime. */
internal const val ChatMessageListTestTag = "chat_message_list"

/** Whether the composer's one-time "tap to schedule" tooltip has already been shown, kept as a
 * screen-local flag (rather than something [ChatViewModel] exposes) since it's a pure UI
 * onboarding hint with no bearing on message data. */
private val Context.chatComposerTooltipDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "chat_composer_tooltip")
private val ScheduleTooltipShownKey = booleanPreferencesKey("schedule_tooltip_shown")

/** How long the composer's one-time tooltip waits after first composition before it's allowed to
 * pop in -- long enough that it never reads as part of the chat opening. */
private const val ScheduleTooltipOpenDelayMillis = 500L

/**
 * A single thread: message timeline, date separators and the composer, all driven live by
 * [ChatViewModel]. threadId comes to the ViewModel via `SavedStateHandle`, not as a parameter
 * here, so this screen only ever needs plain navigation callbacks.
 *
 * Sits directly on [screenSurfaceColor] (flat white in light theme, reused from
 * [text.message.sms.messaging.ui.screens.conversationlist.ConversationListScreen]) -- no rounded
 * panel behind the message list, matching the reference design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onAttachmentClick: (contentUri: String) -> Unit,
    onConversationInfoClick: () -> Unit,
    onForwardClick: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.threadId) {
        NavPerfTracer.logChatFirstFrame(viewModel.threadId)
        NavPerfTracer.logChatListBlinkScreenComposed(viewModel.threadId)
    }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val chatMode by viewModel.chatMode.collectAsStateWithLifecycle()

    // Gates ChatMessageList below -- see ChatViewModel.chatMessagesState's doc comment. Becomes
    // Loaded (once, ever, for this screen instance) the moment the first real Room page for this
    // thread arrives, already grouped into chatItems, whether or not that page turns out to be
    // empty.
    val chatMessagesState by viewModel.chatMessagesState.collectAsStateWithLifecycle()
    LaunchedEffect(chatMessagesState) {
        val loaded = chatMessagesState as? ChatMessagesState.Loaded ?: return@LaunchedEffect
        NavPerfTracer.logChatListBlinkFirstPageArrived(viewModel.threadId, loaded.items.size)
    }

    // Frame-level breadcrumb for the "opening a chat" perf/glitch pass -- see NavPerfTracer's
    // "ChatOpenPerf" tag doc comments. Runs once per threadId, same as logChatFirstFrame above.
    LaunchedEffect(viewModel.threadId) {
        val firstFrameMode = chatMode
        val firstFrameCount = messages.size
        NavPerfTracer.logChatOpenDetails(viewModel.threadId, firstFrameMode.name, firstFrameCount)
        delay(800)
        NavPerfTracer.logChatMessagesChangedAfterFirstFrame(viewModel.threadId, firstFrameCount, messages.size)
    }

    val messageText by viewModel.messageText.collectAsStateWithLifecycle()
    val pendingAttachmentUri by viewModel.pendingAttachmentUri.collectAsStateWithLifecycle()
    val activeSims by viewModel.activeSims.collectAsStateWithLifecycle()
    val simIndicatorSlot by viewModel.simIndicatorSlot.collectAsStateWithLifecycle()
    val simPickerVisible by viewModel.simPickerVisible.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectionMode = selectedIds.isNotEmpty()

    var showAttachmentSheet by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showLearnMoreDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var detailsMessageId by remember { mutableStateOf<Long?>(null) }

    // System back and the selection top bar's close icon both exit selection mode first, rather
    // than leaving the screen -- only a second back press (with selection already cleared) really
    // navigates away.
    BackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }

    // Never defaults to personal (or non-personal) while the mode is still unknown -- see
    // [ChatMode]'s doc comment. [isPersonal] is only ever true once [chatMode] has actually
    // settled on [ChatMode.PERSONAL]; both the call icon and the whole bottom area stay hidden for
    // the narrow [ChatMode.UNKNOWN] window in between (see this screen's `bottomBar` below).
    val isPersonal = chatMode == ChatMode.PERSONAL

    // The most recent *received* message with an OTP, if any -- reuses [OtpDetector], the same
    // extractor behind Home's inbox quick-copy chip, so the two can never disagree about which
    // message has a code. Only this one message (not every OTP bubble in the thread) gets the
    // copy button, per the reference design.
    val latestOtpMessageId = remember(messages) {
        messages.asReversed().firstOrNull { !it.isOutgoing && OtpDetector.extractCode(it.body) != null }?.id
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onAttachmentSelected(uri.toString()) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success -> if (success) pendingCameraUri?.let { viewModel.onAttachmentSelected(it.toString()) } }

    // Nothing in the attachment picker's "coming soon" list is wired up yet -- the composer's new
    // Schedule button reuses this exact stub rather than getting its own, so both affordances stay
    // in sync once real scheduling ships.
    val onDeferredAttachmentAction = {
        Toast.makeText(context, R.string.chat_attachment_coming_soon, Toast.LENGTH_SHORT).show()
    }

    val copiedLabel = stringResource(R.string.chat_selection_copied_toast)
    val forwardAttachmentsUnsupportedLabel = stringResource(R.string.chat_selection_forward_attachments_unsupported)
    val shareTitle = stringResource(R.string.chat_selection_share_title)

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                // Handled by ChatMessageList's own collector (auto-scroll to the new message).
                ChatEvent.MessageSent -> Unit

                is ChatEvent.SimFallback -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.chat_sim_fallback_notice, event.slotNumber),
                        Toast.LENGTH_LONG,
                    ).show()
                }

                is ChatEvent.CopyRequested -> {
                    clipboardManager.setText(AnnotatedString(event.text))
                    Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                }

                is ChatEvent.ForwardRequested -> {
                    if (event.hadUnsupportedAttachments) {
                        Toast.makeText(context, forwardAttachmentsUnsupportedLabel, Toast.LENGTH_SHORT).show()
                    }
                    onForwardClick(event.text)
                }

                is ChatEvent.ShareRequested -> {
                    val intent = if (event.imageContentUri != null) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, event.imageContentUri.toUri())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, event.text)
                        }
                    }
                    context.startActivity(Intent.createChooser(intent, shareTitle))
                }

                is ChatEvent.MessagesDeleted -> {
                    val message = context.resources.getQuantityString(
                        R.plurals.chat_selection_deleted_snackbar,
                        event.count,
                        event.count,
                    )
                    snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                }

                ChatEvent.ThreadDeleted -> onBack()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = screenSurfaceColor(),
        topBar = {
            Crossfade(
                targetState = isSelectionMode,
                animationSpec = tween(durationMillis = 150),
                label = "chatTopBar",
            ) { selecting ->
                if (selecting) {
                    ChatSelectionTopBar(
                        selectedCount = selectedIds.size,
                        copyEnabled = selectedIds.any { id -> messages.firstOrNull { it.id == id }?.body?.isNotBlank() == true },
                        showDetails = selectedIds.size == 1,
                        onClose = viewModel::clearSelection,
                        onSelectAll = viewModel::selectAllLoaded,
                        onCopy = viewModel::copySelected,
                        onForward = viewModel::forwardSelected,
                        onShare = viewModel::shareSelected,
                        onDelete = { showDeleteConfirm = true },
                        onDetails = { detailsMessageId = selectedIds.firstOrNull() },
                    )
                } else {
                    ChatTopBar(
                        conversation = conversation,
                        isPersonal = isPersonal,
                        onBack = onBack,
                        onInfoClick = onConversationInfoClick,
                    )
                }
            }
        },
        bottomBar = {
            // No branch at all for ChatMode.UNKNOWN -- neither the composer nor the non-personal
            // security card renders until the real mode is known, so there's nothing to visibly
            // swap out once it resolves (see ChatMode's doc comment).
            when (chatMode) {
                ChatMode.PERSONAL -> ChatComposer(
                    text = messageText,
                    onTextChange = viewModel::onMessageTextChanged,
                    pendingAttachmentUri = pendingAttachmentUri,
                    onRemoveAttachment = viewModel::clearAttachment,
                    onAttachClick = { showAttachmentSheet = true },
                    onSendClick = viewModel::send,
                    onScheduleClick = onDeferredAttachmentAction,
                    isDualSim = activeSims.size >= 2,
                    simIndicatorSlot = simIndicatorSlot,
                    onSimIndicatorClick = viewModel::onSimBadgeClick,
                )
                ChatMode.NON_PERSONAL -> NonPersonalBottomBar(onLearnMoreClick = { showLearnMoreDialog = true })
                ChatMode.UNKNOWN -> Unit
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // Not composed at all until the first real page has arrived -- see
        // ChatViewModel.chatMessagesState's doc comment. Before that, this slot is just the
        // Scaffold's background (top bar and composer stay visible above/below it), never an
        // empty LazyColumn that visibly pops once content lands. `key(threadId)` around the whole
        // branch (rather than just the listState default inside ChatMessageList) means a stale
        // LazyListState from a previous thread can never be reused for this one, even if this
        // composable instance were ever reused across a threadId change.
        val loadedMessages = chatMessagesState as? ChatMessagesState.Loaded
        if (loadedMessages != null) {
            key(viewModel.threadId) {
                ChatMessageList(
                    chatItems = loadedMessages.items,
                    messageSentEvents = viewModel.events,
                    onAttachmentClick = { attachment -> attachment.contentUri?.let(onAttachmentClick) },
                    onLoadOlder = viewModel::loadOlderMessages,
                    isPersonal = isPersonal,
                    latestOtpMessageId = latestOtpMessageId,
                    selectedIds = selectedIds,
                    isSelectionMode = isSelectionMode,
                    onToggleSelection = viewModel::toggleSelection,
                    onStartSelection = viewModel::startSelection,
                    threadId = viewModel.threadId,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }

    if (showLearnMoreDialog) {
        LearnMoreDialog(onDismiss = { showLearnMoreDialog = false })
    }

    if (showDeleteConfirm) {
        DeleteMessagesDialog(
            count = selectedIds.size,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteSelected()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    val detailsMessage = detailsMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    if (detailsMessage != null) {
        MessageDetailsDialog(
            message = detailsMessage,
            conversation = conversation,
            activeSims = activeSims,
            onDismiss = { detailsMessageId = null },
        )
    }

    if (showAttachmentSheet) {
        AttachmentSheet(
            onDismiss = { showAttachmentSheet = false },
            onGalleryClick = {
                showAttachmentSheet = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onCameraClick = {
                showAttachmentSheet = false
                val uri = createCameraCaptureUri(context)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            },
            onDeferredClick = {
                showAttachmentSheet = false
                onDeferredAttachmentAction()
            },
        )
    }

    if (simPickerVisible) {
        SimPickerSheet(
            sims = activeSims,
            onPick = viewModel::onSimPicked,
            onDismiss = viewModel::onSimPickerDismissed,
        )
    }
}

/** `true` whenever the active [MaterialTheme.colorScheme] reads as a light theme -- same
 * luminance check [screenSurfaceColor] uses, so this screen's fixed reference-design colors only
 * apply in light theme and dark theme keeps following [MaterialTheme.colorScheme] as usual. */
@Composable
private fun isLightChatTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

/**
 * The message timeline, plus the scroll-position logic that decides where it lands. `internal`
 * (rather than `private`) so a UI test can drive it directly with a plain [chatItems] list, without
 * a [ChatViewModel] -- see [ChatMessageListScrollTest].
 *
 * [chatItems] is oldest-first (matching [text.message.sms.messaging.data.local.db.dao.MessageDao
 * .observeThread]'s `ORDER BY received_at ASC`), and this [LazyColumn] is not `reverseLayout`, so
 * index 0 renders at the top and the last index at the bottom -- the most recent message is always
 * the *last* item, never the first.
 *
 * [listState]'s default value bakes the newest message straight into the state's *creation*
 * (`initialFirstVisibleItemIndex = chatItems.lastIndex`) instead of scrolling to it after the
 * first frame -- that post-layout scroll used to be the list-blink bug: [chatItems] arrives after
 * [ChatMessageList] itself has already composed once at index 0 (the oldest message, top of the
 * list), so the list would draw one real frame there before a `LaunchedEffect` jumped it to the
 * bottom on the next frame. Callers (see [ChatScreen]) now hold off composing this function at all
 * until the first real page has loaded, so on the one and only composition that matters,
 * [chatItems] already has its final first-page contents and [listState]'s initial index is already
 * correct -- no programmatic scroll happens for the initial position, ever.
 *
 * [hasCompletedInitialComposition] separates that already-correct first frame from every
 * *subsequent* [chatItems] size change (a genuinely new message arriving, or a widened page from
 * [onLoadOlder]): only those later changes consult [isNearBottom] to decide whether to
 * `animateScrollToItem`, so an incoming message while the user has scrolled up to read history
 * doesn't yank them back down.
 *
 * Spacing between items is driven entirely by each item's own padding (a [ChatListItem.DateHeader]'s
 * 16dp top/bottom, a [ChatListItem.Bubble] row's 4dp/8dp top depending on [ChatListItem.Bubble
 * .isFirstInRun]) rather than a uniform [Arrangement.spacedBy], so consecutive same-sender bubbles
 * can sit closer together than a run boundary.
 */
@Composable
internal fun ChatMessageList(
    chatItems: List<ChatListItem>,
    messageSentEvents: SharedFlow<ChatEvent>,
    onAttachmentClick: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    onLoadOlder: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = chatItems.lastIndex.coerceAtLeast(0),
    ),
    isPersonal: Boolean = true,
    latestOtpMessageId: Long? = null,
    selectedIds: Set<Long> = emptySet(),
    isSelectionMode: Boolean = false,
    onToggleSelection: (Long) -> Unit = {},
    onStartSelection: (Long) -> Unit = {},
    threadId: Long = -1L,
) {
    var hasCompletedInitialComposition by remember { mutableStateOf(false) }
    var hasLoggedFirstLayout by remember { mutableStateOf(false) }

    // Only auto-scroll for a newly-arrived message if the user is already near the bottom --
    // otherwise an incoming message would yank them away from history they scrolled up to read.
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }

    // ChatListBlink measurement (see NavPerfTracer): logs firstVisibleItemIndex and the last
    // visible item's key the first time this list actually has a laid-out frame, so a logcat
    // filter on "ChatListBlink" can confirm the first frame already sits at the newest message.
    LaunchedEffect(threadId, chatItems.isEmpty()) {
        if (hasLoggedFirstLayout) return@LaunchedEffect
        if (chatItems.isEmpty()) {
            hasLoggedFirstLayout = true
            NavPerfTracer.logChatListBlinkFirstLayout(threadId, 0, null, 0)
            return@LaunchedEffect
        }
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
        hasLoggedFirstLayout = true
        val visible = listState.layoutInfo.visibleItemsInfo
        NavPerfTracer.logChatListBlinkFirstLayout(
            threadId,
            listState.firstVisibleItemIndex,
            visible.lastOrNull()?.key,
            chatItems.size,
        )
    }

    // Requests an older page once the user scrolls near the top of what's currently loaded --
    // ChatViewModel.loadOlderMessages widens the query rather than this screen paging through a
    // separate result set, so the scroll position naturally holds steady as more history arrives
    // above it. Only fires once real messages exist (never on the empty first frame), only past
    // hasCompletedInitialComposition (so it can't race the very first frame, which already opens
    // at the bottom -- see this function's doc comment), and only when there's actually more
    // content than fits the viewport -- otherwise a short thread (every loaded message already
    // fits on screen, so the initial position already leaves firstVisibleItemIndex at 0) would
    // spuriously read as "near the top" the instant it opens and widen the page for no reason.
    val isNearTop by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val hasOverflow = layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size
            hasOverflow && listState.firstVisibleItemIndex <= 5
        }
    }
    LaunchedEffect(isNearTop, hasCompletedInitialComposition, chatItems.isEmpty()) {
        if (hasCompletedInitialComposition && isNearTop && chatItems.isNotEmpty()) {
            onLoadOlder()
        }
    }

    LaunchedEffect(chatItems.size) {
        if (chatItems.isEmpty()) return@LaunchedEffect
        if (!hasCompletedInitialComposition) {
            // Position is already correct via listState's initial index above -- this only marks
            // the first frame as done, no scroll is performed here.
            hasCompletedInitialComposition = true
            return@LaunchedEffect
        }
        if (isNearBottom) {
            NavPerfTracer.logChatListBlinkScrollAfterFirstLayout(threadId, "newMessage")
            listState.animateScrollToItem(chatItems.lastIndex)
        }
    }
    val latestChatItems by rememberUpdatedState(chatItems)
    LaunchedEffect(messageSentEvents) {
        messageSentEvents.collect { event ->
            if (event is ChatEvent.MessageSent && latestChatItems.isNotEmpty()) {
                NavPerfTracer.logChatListBlinkScrollAfterFirstLayout(threadId, "messageSent")
                listState.animateScrollToItem(latestChatItems.lastIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag(ChatMessageListTestTag),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
    ) {
        items(chatItems, key = { it.key }) { item ->
            when (item) {
                is ChatListItem.DateHeader -> ChatDateSeparator(item.timestampMillis)
                is ChatListItem.Bubble -> ChatMessageRow(
                    message = item.message,
                    isFirstInRun = item.isFirstInRun,
                    isLastInRun = item.isLastInRun,
                    onAttachmentClick = onAttachmentClick,
                    showOtpCopy = !isPersonal && item.message.id == latestOtpMessageId,
                    isSelectionMode = isSelectionMode,
                    isSelected = item.message.id in selectedIds,
                    onToggleSelection = { onToggleSelection(item.message.id) },
                    onStartSelection = { onStartSelection(item.message.id) },
                )
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    conversation: Conversation?,
    isPersonal: Boolean,
    onBack: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val callAddress = conversation?.recipients?.firstOrNull()?.address
    val isLight = isLightChatTheme()
    val contentColor = if (isLight) Color.Black else MaterialTheme.colorScheme.onSurface
    val dividerColor = if (isLight) ChatTopBarDivider else MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.background(screenSurfaceColor())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppBackButton(onClick = onBack)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(Pill)
                    .clickable(onClick = onInfoClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatPeerAvatar(conversation, size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = conversation?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isPersonal) {
                IconButton(
                    onClick = { callAddress?.let { placeCall(context, it) } },
                    enabled = callAddress != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = stringResource(R.string.chat_call),
                        tint = contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))
            }

            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.chat_conversation_info),
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
    }
}

/**
 * Replaces [ChatTopBar] while one or more messages are selected -- see [ChatScreen]'s `Crossfade`.
 * Same height/divider as the normal top bar; [onDetails] only ever renders when [showDetails] is
 * true (exactly one message selected), per the multi-select spec. Only Copy and Delete show
 * inline -- Select all, Forward, Share, and (single-selection) Message details always live behind
 * the overflow menu, matching the reference design's Google-Messages-style selection bar.
 */
@Composable
private fun ChatSelectionTopBar(
    selectedCount: Int,
    copyEnabled: Boolean,
    showDetails: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = isLightChatTheme()
    val accentColor = if (isLight) SelectionAccentBlue else SelectionAccentBlueDark
    val dividerColor = if (isLight) ChatTopBarDivider else MaterialTheme.colorScheme.outlineVariant
    var showOverflow by remember { mutableStateOf(false) }

    Column(modifier = modifier.background(screenSurfaceColor())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionTopBarIconButton(
                painter = sharpIconPainter(R.drawable.ic_closes),
                contentDescription = stringResource(R.string.chat_selection_close),
                tint = accentColor,
                onClick = onClose,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = selectedCount.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Normal),
                color = accentColor,
            )

            Spacer(modifier = Modifier.weight(1f))

            SelectionTopBarIconButton(
                painter = sharpIconPainter(R.drawable.ic_copy),
                contentDescription = stringResource(R.string.chat_selection_copy),
                tint = accentColor,
                enabled = copyEnabled,
                onClick = onCopy,
            )
            SelectionTopBarIconButton(
                painter = sharpIconPainter(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.chat_selection_delete),
                tint = accentColor,
                onClick = onDelete,
            )

            val selectAllLabel = stringResource(R.string.chat_selection_select_all)
            val forwardLabel = stringResource(R.string.chat_selection_forward)
            val shareLabel = stringResource(R.string.chat_selection_share)
            val detailsLabel = stringResource(R.string.chat_selection_details)
            val selectAllIcon = sharpIconPainter(R.drawable.ic_select_all)
            val forwardIcon = sharpIconPainter(R.drawable.ic_forward)
            val shareIcon = sharpIconPainter(R.drawable.ic_share)
            val detailsIcon = sharpIconPainter(R.drawable.ic_info_outline)

            Box {
                IconButton(onClick = { showOverflow = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.chat_selection_more),
                        tint = accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
                SelectionOverflowMenu(
                    expanded = showOverflow,
                    onDismiss = { showOverflow = false },
                    items = buildList {
                        add(SelectionMenuItem(selectAllIcon, selectAllLabel) { showOverflow = false; onSelectAll() })
                        add(SelectionMenuItem(forwardIcon, forwardLabel) { showOverflow = false; onForward() })
                        add(SelectionMenuItem(shareIcon, shareLabel) { showOverflow = false; onShare() })
                        if (showDetails) {
                            add(SelectionMenuItem(detailsIcon, detailsLabel) { showOverflow = false; onDetails() })
                        }
                    },
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
    }
}

@Composable
private fun SelectionTopBarIconButton(
    painter: Painter,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp)) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** First letter/digit in [address], iterated by Unicode code point rather than raw `Char` -- see
 * [text.message.sms.messaging.domain.model.Contact.initials]'s private `firstLetterOrDigitOrNull`
 * for why naive `Char`-based indexing breaks on a leading emoji. Falls back to "#" if [address]
 * has no letter/digit at all. */
private fun firstLetterOrDigit(address: String): String {
    var offset = 0
    while (offset < address.length) {
        val codePoint = address.codePointAt(offset)
        if (Character.isLetterOrDigit(codePoint)) {
            return String(Character.toChars(codePoint)).uppercase()
        }
        offset += Character.charCount(codePoint)
    }
    return "#"
}

/**
 * Contact photo when [conversation]'s first recipient has one; otherwise a gray person
 * silhouette for a saved contact with no synced photo, or a blue circle with [firstLetterOrDigit]
 * for an unresolved sender (bank/OTP/business sender ID) -- matching
 * [text.message.sms.messaging.ui.screens.conversationlist.ConversationAvatar]'s same three-way
 * split for the inbox row avatar, independently implemented here at [size] rather than that
 * function's fixed 48dp.
 */
@Composable
private fun ChatPeerAvatar(conversation: Conversation?, size: Dp, modifier: Modifier = Modifier) {
    val recipient = conversation?.recipients?.firstOrNull()
    val contact = recipient?.contact
    val hasPhoto = !contact?.photoUri.isNullOrBlank()
    val isLight = isLightChatTheme()
    val backgroundColor = when {
        conversation?.isGroup == true -> MaterialTheme.colorScheme.primaryContainer
        contact != null && isLight -> ChatAvatarPlaceholderGray
        contact != null -> MaterialTheme.colorScheme.surfaceContainerHigh
        isLight -> ChatAvatarAccentBlue
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            conversation?.isGroup == true -> Icon(
                imageVector = Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size / 2),
            )
            // Saved contact, no photo -- generic silhouette, not initials, matching
            // ConversationAvatar's own reasoning for the inbox row.
            contact != null -> Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size / 2),
            )
            else -> Text(
                text = firstLetterOrDigit(recipient?.address.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
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

/** One [Message], or a date-section label in front of a run of messages sharing a day.
 * `internal` (rather than `private`) so [ChatMessageListScrollTest] can build a plain list of
 * these directly, without a [ChatViewModel] or real [Message] data. */
internal sealed interface ChatListItem {
    val key: Any

    data class DateHeader(val timestampMillis: Long) : ChatListItem {
        override val key: Any get() = "header_$timestampMillis"
    }

    /** [isFirstInRun] and [isLastInRun] both flip at the same run boundaries as the date
     * separators ([groupMessages]'s `isRunBoundary`) -- a run never spans a date-separator, so a
     * bubble right after one always opens a fresh run too. */
    data class Bubble(val message: Message, val isFirstInRun: Boolean = true, val isLastInRun: Boolean = true) : ChatListItem {
        override val key: Any get() = message.id
    }
}

private fun Message.displayTimeMillis(): Long = if (isOutgoing) sentAtMillis else receivedAtMillis

/** Minimum gap between two consecutive messages that, on its own (regardless of the calendar
 * date), still earns a new date separator -- matches the reference design's "more than about an
 * hour" rule. */
private const val DateSeparatorGapMillis = 60 * 60 * 1000L

/** Whether [a] and [b] -- consecutive messages, [a] before [b] -- should have a date separator
 * between them: either the calendar day changed, or [b] arrived more than
 * [DateSeparatorGapMillis] after [a]. Also used as the run-boundary test for bubble grouping (see
 * [ChatListItem.Bubble]'s doc comment), since a run of consecutive same-sender bubbles should
 * never cross a boundary that got its own separator. */
private fun isRunBoundary(a: Message, b: Message, zone: ZoneId): Boolean {
    val aMillis = a.displayTimeMillis()
    val bMillis = b.displayTimeMillis()
    val aDate = Instant.ofEpochMilli(aMillis).atZone(zone).toLocalDate()
    val bDate = Instant.ofEpochMilli(bMillis).atZone(zone).toLocalDate()
    return aDate != bDate || bMillis - aMillis > DateSeparatorGapMillis
}

/** `internal` (rather than `private`) so [ChatViewModel.chatMessagesState] can group the page it
 * loads directly, in the same flow step that decides the page has loaded -- see that property's
 * doc comment for why the grouping and the "loaded" decision must be one value, not two. */
internal fun groupMessages(messages: List<Message>, zone: ZoneId): List<ChatListItem> {
    val items = mutableListOf<ChatListItem>()
    messages.forEachIndexed { index, message ->
        val previous = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)

        val startsNewSeparator = previous == null || isRunBoundary(previous, message, zone)
        if (startsNewSeparator) {
            items += ChatListItem.DateHeader(message.displayTimeMillis())
        }

        val isFirstInRun = previous == null || previous.isOutgoing != message.isOutgoing || startsNewSeparator
        val isLastInRun = next == null || next.isOutgoing != message.isOutgoing || isRunBoundary(message, next, zone)
        items += ChatListItem.Bubble(message, isFirstInRun, isLastInRun)
    }
    return items
}

@Composable
private fun ChatMessageRow(
    message: Message,
    isFirstInRun: Boolean,
    isLastInRun: Boolean,
    onAttachmentClick: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    showOtpCopy: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onStartSelection: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isFirstInRun) 8.dp else 4.dp),
            horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
        ) {
            MessageBubble(
                text = message.body,
                isOutgoing = message.isOutgoing,
                isLastInRun = isLastInRun,
                deliveryState = message.deliveryState,
                attachments = message.attachments,
                onAttachmentClick = onAttachmentClick,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                onToggleSelection = onToggleSelection,
                onStartSelection = onStartSelection,
            )
        }

        if (showOtpCopy) {
            val otpCode = remember(message.body) { OtpDetector.extractCode(message.body) }
            if (otpCode != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OtpCopyButton(code = otpCode, modifier = Modifier.padding(end = 16.dp))
                }
            }
        }
    }
}

/**
 * Non-personal chat mode's OTP quick-copy affordance -- shown only below the thread's single most
 * recent OTP message (see [ChatMessageList]'s `latestOtpMessageId`), never one per OTP bubble.
 * Copy behavior mirrors [text.message.sms.messaging.ui.screens.conversationlist.ConversationListScreen]'s
 * own inbox quick-copy chip so both affordances behave identically, just styled as the reference
 * design's outlined button rather than a text link.
 */
@Composable
private fun OtpCopyButton(code: String, modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.home_otp_copied_toast)
    val copyDescription = stringResource(R.string.chat_otp_copy_content_description)

    OutlinedButton(
        onClick = {
            clipboardManager.setText(AnnotatedString(code))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier.semantics { contentDescription = copyDescription },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ChatOtpCopyBorderGray),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.chat_otp_copy_label, code),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            color = Color.Black,
        )
    }
}

/**
 * Plain centered gray text, no pill/background -- "Wed 12:25 AM" (weekday + time) for the last 7
 * days, "19 Sep, 3:16 AM" (day + month + time) for anything older this year, "19 Sep 2025, 3:16 AM"
 * for a previous year -- see [RelativeDateFormatter.separatorLabel]. Unlike
 * [text.message.sms.messaging.ui.screens.conversationlist.DateSectionHeader], this always
 * includes a time, since [groupMessages] can insert a separator mid-day (a >1hr gap), not only on
 * a day change.
 */
@Composable
private fun ChatDateSeparator(timestampMillis: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isLight = isLightChatTheme()
    val textColor = if (isLight) ChatDateSeparatorGray else MaterialTheme.colorScheme.onSurfaceVariant
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val label = remember(timestampMillis, is24Hour) {
        RelativeDateFormatter.separatorLabel(timestampMillis, is24Hour)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = textColor,
        )
    }
}

/**
 * The message input bar. `internal` (rather than `private`) so a UI test can host it directly,
 * without a [ChatViewModel], to verify [Modifier.imePadding] actually pushes it above the
 * keyboard -- see [ChatComposerImePaddingTest].
 *
 * [Modifier.imePadding] here is load-bearing, not decorative: [text.message.sms.messaging
 * .MainActivity] calls `enableEdgeToEdge()`, which stops the system from automatically resizing
 * -- or otherwise reserving space in -- the window for the keyboard the way a non-edge-to-edge
 * activity would. `windowSoftInputMode="adjustResize"` in the manifest is still correct and still
 * needed (it's what makes the IME inset arrive at all instead of the window simply being drawn
 * behind the keyboard), but with edge-to-edge on, nothing above this composable -- not
 * [ChatScreen]'s [androidx.compose.material3.Scaffold], which never pushes its `bottomBar` slot
 * for the IME the way it does for its `content` slot -- pads for the keyboard automatically. This
 * bar sat directly underneath (and was hidden by) the keyboard, and unusable, without it.
 * [Modifier.navigationBarsPadding] is the same story for the gesture/3-button nav bar.
 *
 * The text field is a [BasicTextField] wrapped in a hand-built pill, not M3's
 * [androidx.compose.material3.TextField]: M3's
 * filled text field bakes in ~16dp of internal padding and a fixed leading-icon gap that can't be
 * overridden without the same decoration-box plumbing, so matching the reference design's exact
 * 12dp/8dp spacing was simpler to build directly. [lineCount] (from [BasicTextField]'s
 * `onTextLayout`) drives whether the row aligns its buttons to [Alignment.Bottom] (multiline, so
 * they stay pinned low while the pill grows upward) or [Alignment.CenterVertically] (single line).
 */
@Composable
internal fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    pendingAttachmentUri: String?,
    onRemoveAttachment: () -> Unit,
    onAttachClick: () -> Unit,
    onSendClick: () -> Unit,
    onScheduleClick: () -> Unit = {},
    isDualSim: Boolean = false,
    simIndicatorSlot: Int? = null,
    onSimIndicatorClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isLight = isLightChatTheme()
    val neutralFill = if (isLight) ChatNeutralFill else MaterialTheme.colorScheme.surfaceContainerHigh
    val iconTint = if (isLight) Color.Black else MaterialTheme.colorScheme.onSurface
    val hintColor = if (isLight) ChatHintGray else MaterialTheme.colorScheme.onSurfaceVariant

    val context = LocalContext.current
    var showScheduleTooltip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val alreadyShown = context.chatComposerTooltipDataStore.data.first()[ScheduleTooltipShownKey] ?: false
        if (!alreadyShown) {
            context.chatComposerTooltipDataStore.edit { it[ScheduleTooltipShownKey] = true }
            // Waits for the screen's own open animation/layout to settle first, so this never
            // pops in as part of what looks like the chat opening.
            delay(ScheduleTooltipOpenDelayMillis)
            showScheduleTooltip = true
            delay(5_000)
            showScheduleTooltip = false
        }
    }

    var lineCount by remember { mutableStateOf(1) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = screenSurfaceColor(),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (pendingAttachmentUri != null) {
                AttachmentPreview(
                    uri = pendingAttachmentUri,
                    onRemove = onRemoveAttachment,
                    modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                )
            }
            Row(
                verticalAlignment = if (lineCount > 1) Alignment.Bottom else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(neutralFill)
                        .clickable(onClick = onAttachClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.chat_add_attachment),
                        tint = iconTint,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(neutralFill),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Decorative only -- there's no emoji picker in this app yet to wire it to.
                        Icon(
                            imageVector = Icons.Outlined.EmojiEmotions,
                            contentDescription = null,
                            tint = hintColor,
                            modifier = Modifier.size(24.dp),
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.chat_composer_hint),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                    color = hintColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = text,
                                onValueChange = onTextChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 96.dp)
                                    .verticalScroll(rememberScrollState()),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    color = iconTint,
                                ),
                                cursorBrush = SolidColor(ChatSendButtonEnabled),
                                maxLines = 6,
                                onTextLayout = { lineCount = it.lineCount },
                            )
                        }

                        // Dual-SIM only -- entirely absent on a single-SIM/no-SIM device, matching
                        // every other SIM affordance in this app. Lives inside the pill (trailing
                        // end) rather than the outer Row, so it travels with the text field instead
                        // of competing with the fixed-size buttons for space. Shows which SIM the
                        // next send will use and, tapped, opens the same "Send with" picker
                        // [onSendClick] falls into for "Ask every time" (see
                        // ChatViewModel.onSimBadgeClick).
                        if (isDualSim) {
                            Spacer(modifier = Modifier.width(8.dp))
                            ChatComposerSimBadge(
                                slotIndex = simIndicatorSlot,
                                onClick = onSimIndicatorClick,
                                modifier = Modifier.align(
                                    if (lineCount > 1) Alignment.Bottom else Alignment.CenterVertically,
                                ).then(
                                    if (lineCount > 1) Modifier.padding(bottom = 8.dp) else Modifier,
                                ),
                            )
                        }
                    }
                }

                Box {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(neutralFill)
                            .clickable(onClick = onScheduleClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = stringResource(R.string.chat_schedule_button_description),
                            tint = iconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    if (showScheduleTooltip) {
                        val tooltipOffsetPx = with(LocalDensity.current) { (-52).dp.roundToPx() }
                        Popup(
                            alignment = Alignment.TopCenter,
                            offset = IntOffset(0, tooltipOffsetPx),
                            onDismissRequest = { showScheduleTooltip = false },
                        ) {
                            ScheduleTooltipBubble(onDismiss = { showScheduleTooltip = false })
                        }
                    }
                }

                val canSend = text.isNotBlank() || pendingAttachmentUri != null
                val sendContainerColor = when {
                    canSend && isLight -> ChatSendButtonEnabled
                    canSend -> MaterialTheme.colorScheme.primary
                    isLight -> ChatSendButtonDisabled
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                val sendContentColor = if (isLight || canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                FilledIconButton(
                    onClick = onSendClick,
                    enabled = canSend,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = sendContainerColor,
                        contentColor = sendContentColor,
                        disabledContainerColor = sendContainerColor,
                        disabledContentColor = sendContentColor,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * The composer text pill's trailing "which SIM will send" badge -- dual-SIM only, see
 * [ChatComposer]'s `isDualSim`. Visually a 24dp solid-blue circle with the 1-based slot number,
 * but wrapped in a 40dp clickable box so the tap target stays comfortably larger than the visual
 * without inflating the badge itself (which would crowd the pill's text).
 */
@Composable
private fun ChatComposerSimBadge(slotIndex: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = if (slotIndex != null) {
        stringResource(R.string.chat_sim_indicator_description, slotIndex + 1)
    } else {
        stringResource(R.string.chat_sim_indicator_description_unknown)
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(ChatSendButtonEnabled),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = slotIndex?.let { (it + 1).toString() } ?: "?",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                color = Color.White,
            )
        }
    }
}

/**
 * One-time hint bubble above [ChatComposer]'s Schedule button -- see that composable's
 * `showScheduleTooltip` for when it's shown. Deliberately a plain [Popup] rather than a full
 * tooltip API: it only ever needs to render once per install and dismiss itself, so the extra
 * machinery isn't worth it. [Popup]'s default `dismissOnClickOutside` handles "tap anywhere else";
 * tapping the bubble itself is wired to the same [onDismiss].
 */
@Composable
private fun ScheduleTooltipBubble(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = 220.dp)
            .clickable(onClick = onDismiss),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = ConversationFabBlue) {
            Text(
                text = stringResource(R.string.chat_schedule_tooltip),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Canvas(modifier = Modifier.size(width = 12.dp, height = 6.dp)) {
            val pointer = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(pointer, color = ConversationFabBlue)
        }
    }
}

/**
 * Bottom area for a non-personal thread (business/short-code/OTP/transactional sender) -- replaces
 * [ChatComposer] entirely, since these threads can't be replied to. Just the security notice card
 * and the "can't reply" row, matching the reference design; [showOtpCopy] on each
 * [ChatMessageRow] (not this bar) handles the OTP quick-copy affordance.
 */
@Composable
private fun NonPersonalBottomBar(onLearnMoreClick: () -> Unit, modifier: Modifier = Modifier) {
    val isLight = isLightChatTheme()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = screenSurfaceColor(),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isLight) ChatSecurityCardBackground else MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = if (isLight) ChatAvatarAccentBlue else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.chat_security_notice),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = if (isLight) ChatSecurityCardText else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_cant_reply_short_code),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = if (isLight) ChatCantReplyGray else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onLearnMoreClick) {
                    Text(
                        text = stringResource(R.string.chat_learn_more),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Medium,
                        color = if (isLight) Color.Black else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Explains why a non-personal thread's composer is missing -- opened from
 * [NonPersonalBottomBar]'s "Learn More" button. */
@Composable
private fun LearnMoreDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.chat_learn_more_ok))
            }
        },
        title = { Text(text = stringResource(R.string.chat_learn_more)) },
        text = { Text(text = stringResource(R.string.chat_learn_more_dialog_message)) },
        modifier = modifier,
    )
}

/** Confirms a multi-select Delete -- matches [LearnMoreDialog]/[ConfirmDialog]-style dialogs
 * elsewhere in the app: plain [AlertDialog], destructive action colored
 * [MaterialTheme.colorScheme.error]. */
@Composable
private fun DeleteMessagesDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        title = { Text(text = pluralStringResource(R.plurals.chat_selection_delete_confirm_title, count, count)) },
        text = { Text(text = stringResource(R.string.chat_selection_delete_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.chat_selection_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
        modifier = modifier,
    )
}

/** The selection top bar's Message details action (single message only) -- everything shown here
 * already lives on [Message]/[Conversation]/[SimInfo], no new data-layer query per Step 8 of the
 * multi-select spec. */
@Composable
private fun MessageDetailsDialog(
    message: Message,
    conversation: Conversation?,
    activeSims: List<SimInfo>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val typeLabel = if (message.channel == MessageChannel.MMS) {
        stringResource(R.string.message_details_type_mms)
    } else {
        stringResource(R.string.message_details_type_sms)
    }

    val fromToLabel = if (message.isOutgoing) {
        stringResource(R.string.message_details_to)
    } else {
        stringResource(R.string.message_details_from)
    }
    val fromToValue = remember(message.address, conversation) {
        val address = message.address
        val recipient = address?.let { addr ->
            conversation?.recipients?.firstOrNull { PhoneNumbers.areEquivalent(it.address, addr) }
        }
        recipient?.displayName ?: address ?: conversation?.title.orEmpty()
    }

    val timeLabel = if (message.isOutgoing) {
        stringResource(R.string.message_details_time_sent)
    } else {
        stringResource(R.string.message_details_time_received)
    }
    val timeMillis = if (message.isOutgoing) message.sentAtMillis else message.receivedAtMillis
    val timeValue = remember(timeMillis) {
        SimpleDateFormat("EEE, d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(timeMillis))
    }

    val sizeValue = if (message.channel == MessageChannel.MMS) {
        val totalBytes = message.attachments.filterNot { it.isTextPart }.sumOf { it.byteSize }
        android.text.format.Formatter.formatShortFileSize(context, totalBytes)
    } else {
        stringResource(R.string.message_details_size_characters, message.body.length)
    }

    val statusValue = if (message.isOutgoing) {
        when (message.deliveryState) {
            DeliveryState.PENDING, DeliveryState.SENDING -> stringResource(R.string.chat_status_sending)
            DeliveryState.SENT, DeliveryState.NONE -> stringResource(R.string.chat_status_sent)
            DeliveryState.DELIVERED -> stringResource(R.string.chat_status_delivered)
            DeliveryState.FAILED -> stringResource(R.string.chat_status_failed)
        }
    } else {
        stringResource(R.string.message_details_status_received)
    }

    // Dual-SIM only, and only when the message's subscription id still maps to a currently
    // active SIM -- hidden otherwise per Step 8 ("hide the row if unknown or single-SIM").
    val sim = remember(message.subscriptionId, activeSims) {
        activeSims.takeIf { it.size >= 2 }?.firstOrNull { it.subscriptionId == message.subscriptionId }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        title = { Text(text = stringResource(R.string.message_details_title)) },
        text = {
            SelectionContainer {
                Column {
                    MessageDetailRow(stringResource(R.string.message_details_type), typeLabel)
                    MessageDetailRow(fromToLabel, fromToValue)
                    MessageDetailRow(timeLabel, timeValue)
                    MessageDetailRow(stringResource(R.string.message_details_size), sizeValue)
                    MessageDetailRow(stringResource(R.string.message_details_status), statusValue)
                    if (sim != null) {
                        MessageDetailRow(
                            stringResource(R.string.message_details_sim),
                            stringResource(R.string.message_details_sim_value, sim.slotNumber),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.message_details_ok)) }
        },
        modifier = modifier,
    )
}

@Composable
private fun MessageDetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AttachmentPreview(uri: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(56.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.chat_remove_attachment),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    onDismiss: () -> Unit,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onDeferredClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.chat_attachment_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            val options = listOf(
                AttachmentOptionSpec(Icons.Filled.PhotoLibrary, R.string.chat_attachment_gallery, onGalleryClick),
                AttachmentOptionSpec(Icons.Filled.CameraAlt, R.string.chat_attachment_camera, onCameraClick),
                AttachmentOptionSpec(Icons.AutoMirrored.Filled.InsertDriveFile, R.string.chat_attachment_files, onDeferredClick),
                AttachmentOptionSpec(Icons.Filled.LocationOn, R.string.chat_attachment_location, onDeferredClick),
                AttachmentOptionSpec(Icons.Filled.Person, R.string.chat_attachment_contact, onDeferredClick),
                AttachmentOptionSpec(Icons.Filled.Schedule, R.string.chat_attachment_schedule, onDeferredClick),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                items(options) { option -> AttachmentOption(option) }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Dual-SIM "Send with" picker -- shown when [ChatViewModel.send] hits
 * [text.message.sms.messaging.domain.usecase.SendSubscriptionResult.NeedsUserChoice] under the
 * "Ask every time" send preference, and reused by the composer's SIM badge to change a
 * conversation's SIM ahead of a send. Reuses [AttachmentSheet]'s sheet styling (rounded top
 * corners, same title treatment).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimPickerSheet(
    sims: List<SimInfo>,
    onPick: (SimInfo, remember: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rememberChoice by rememberSaveable { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.chat_sim_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            sims.sortedBy { it.slotIndex }.forEach { sim ->
                SimPickerOption(sim = sim, onClick = { onPick(sim, rememberChoice) })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { rememberChoice = !rememberChoice }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.chat_sim_picker_remember),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SimPickerOption(sim: SimInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SimCard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            val label = sim.displayName.ifBlank { sim.carrierName }
            Text(
                text = stringResource(R.string.chat_sim_picker_row_title, sim.slotNumber, label),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (!sim.number.isNullOrBlank()) {
                Text(
                    text = sim.number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class AttachmentOptionSpec(
    val icon: ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
)

@Composable
private fun AttachmentOption(spec: AttachmentOptionSpec, modifier: Modifier = Modifier) {
    Surface(
        onClick = spec.onClick,
        shape = Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(spec.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Creates a `content://` target for [ActivityResultContracts.TakePicture] to write into,
 * backed by the app's existing FileProvider (see `file_paths.xml`). */
private fun createCameraCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
