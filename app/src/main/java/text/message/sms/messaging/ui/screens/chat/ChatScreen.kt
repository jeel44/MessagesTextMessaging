package text.message.sms.messaging.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.ui.components.MessageBubble
import text.message.sms.messaging.ui.theme.Pill
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * A single thread: message timeline, date separators and the composer, all driven live by
 * [ChatViewModel]. threadId comes to the ViewModel via `SavedStateHandle`, not as a parameter
 * here, so this screen only ever needs plain navigation callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onAttachmentClick: (contentUri: String) -> Unit,
    onConversationInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messageText by viewModel.messageText.collectAsStateWithLifecycle()
    val pendingAttachmentUri by viewModel.pendingAttachmentUri.collectAsStateWithLifecycle()

    var showAttachmentSheet by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onAttachmentSelected(uri.toString()) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success -> if (success) pendingCameraUri?.let { viewModel.onAttachmentSelected(it.toString()) } }

    val listState = rememberLazyListState()
    val zone = remember { ZoneId.systemDefault() }
    val chatItems = remember(messages, zone) { groupMessages(messages, zone) }

    // Only auto-scroll for a newly-arrived message if the user is already near the bottom --
    // otherwise an incoming message would yank them away from history they scrolled up to read.
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(chatItems.lastIndex)
        }
    }
    val latestChatItems by rememberUpdatedState(chatItems)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is ChatEvent.MessageSent && latestChatItems.isNotEmpty()) {
                listState.animateScrollToItem(latestChatItems.lastIndex)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ChatTopBar(
                conversation = conversation,
                onBack = onBack,
                onInfoClick = onConversationInfoClick,
            )
        },
        bottomBar = {
            ChatComposer(
                text = messageText,
                onTextChange = viewModel::onMessageTextChanged,
                pendingAttachmentUri = pendingAttachmentUri,
                onRemoveAttachment = viewModel::clearAttachment,
                onAttachClick = { showAttachmentSheet = true },
                onSendClick = viewModel::send,
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(chatItems, key = { it.key }) { item ->
                    when (item) {
                        is ChatListItem.DateHeader -> ChatDateSeparator(item.date)
                        is ChatListItem.Bubble -> ChatMessageRow(
                            message = item.message,
                            isLastInRun = item.isLastInRun,
                            onAttachmentClick = { attachment ->
                                attachment.contentUri?.let(onAttachmentClick)
                            },
                        )
                    }
                }
            }
        }
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
                Toast.makeText(context, R.string.chat_attachment_coming_soon, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    conversation: Conversation?,
    onBack: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val callAddress = conversation?.recipients?.firstOrNull()?.address

    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(Pill)
                    .clickable(onClick = onInfoClick),
            ) {
                ChatPeerAvatar(conversation)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = conversation?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            IconButton(
                onClick = { callAddress?.let { placeCall(context, it) } },
                enabled = callAddress != null,
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = stringResource(R.string.chat_call),
                )
            }
            // Overflow menu is a stub for this pass -- no menu items are required yet.
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.chat_more_options),
                )
            }
        },
    )
}

@Composable
private fun ChatPeerAvatar(conversation: Conversation?, modifier: Modifier = Modifier) {
    val contact = conversation?.recipients?.firstOrNull()?.contact
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when {
            conversation?.isGroup == true -> Icon(
                imageVector = Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            contact != null -> Text(
                text = contact.initials,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            else -> Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** One [Message], or a date-section label in front of a run of messages sharing a day. */
private sealed interface ChatListItem {
    val key: Any

    data class DateHeader(val date: LocalDate) : ChatListItem {
        override val key: Any get() = "header_$date"
    }

    data class Bubble(val message: Message, val isLastInRun: Boolean) : ChatListItem {
        override val key: Any get() = message.id
    }
}

private fun Message.displayTimeMillis(): Long = if (isOutgoing) sentAtMillis else receivedAtMillis

private fun groupMessages(messages: List<Message>, zone: ZoneId): List<ChatListItem> {
    val items = mutableListOf<ChatListItem>()
    var lastDate: LocalDate? = null
    messages.forEachIndexed { index, message ->
        val date = Instant.ofEpochMilli(message.displayTimeMillis()).atZone(zone).toLocalDate()
        if (date != lastDate) {
            items += ChatListItem.DateHeader(date)
            lastDate = date
        }
        val next = messages.getOrNull(index + 1)
        val nextDate = next?.let {
            Instant.ofEpochMilli(it.displayTimeMillis()).atZone(zone).toLocalDate()
        }
        val isLastInRun = next == null || next.isOutgoing != message.isOutgoing || nextDate != date
        items += ChatListItem.Bubble(message, isLastInRun)
    }
    return items
}

@Composable
private fun ChatMessageRow(
    message: Message,
    isLastInRun: Boolean,
    onAttachmentClick: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        MessageBubble(
            text = message.body,
            isOutgoing = message.isOutgoing,
            isLastInRun = isLastInRun,
            timestampMillis = message.displayTimeMillis(),
            deliveryState = message.deliveryState,
            attachments = message.attachments,
            onAttachmentClick = onAttachmentClick,
        )
    }
}

@Composable
private fun ChatDateSeparator(date: LocalDate, modifier: Modifier = Modifier) {
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = Pill,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    pendingAttachmentUri: String?,
    onRemoveAttachment: () -> Unit,
    onAttachClick: () -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            if (pendingAttachmentUri != null) {
                AttachmentPreview(
                    uri = pendingAttachmentUri,
                    onRemove = onRemoveAttachment,
                    modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttachClick) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.chat_add_attachment),
                    )
                }

                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp),
                    placeholder = { Text(stringResource(R.string.chat_composer_hint)) },
                    shape = Pill,
                    maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )

                Spacer(modifier = Modifier.width(8.dp))

                val canSend = text.isNotBlank() || pendingAttachmentUri != null
                FilledIconButton(
                    onClick = onSendClick,
                    enabled = canSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                    )
                }
            }
        }
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

private fun placeCall(context: Context, address: String) {
    val hasCallPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CALL_PHONE,
    ) == PackageManager.PERMISSION_GRANTED

    val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
    context.startActivity(Intent(action, "tel:$address".toUri()))
}

/** Creates a `content://` target for [ActivityResultContracts.TakePicture] to write into,
 * backed by the app's existing FileProvider (see `file_paths.xml`). */
private fun createCameraCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
