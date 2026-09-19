package text.message.sms.messaging.ui.screens.conversationinfo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.Recipient
import text.message.sms.messaging.ui.components.AppTopBar
import text.message.sms.messaging.ui.components.ContactAvatar
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor
import text.message.sms.messaging.ui.theme.ConversationRowDivider

/**
 * Details for a single thread: participant(s), the per-conversation mute toggle, shared media,
 * and the archive/block/delete actions -- matching QKSMS's "conversation info" screen. Reached
 * by tapping the conversation title/avatar in [text.message.sms.messaging.ui.screens.chat.ChatScreen]'s
 * top bar.
 *
 * Laid out as a single [LazyVerticalGrid] rather than a [androidx.compose.foundation.lazy.LazyColumn]
 * with a nested grid -- every row above the media grid spans all columns via
 * `item(span = { GridItemSpan(maxLineSpan) })`, and the shared-media thumbnails are the grid's
 * actual cells. That avoids nesting one scrollable lazy layout inside another for what would
 * otherwise be an unbounded-height media grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationInfoScreen(
    onBack: () -> Unit,
    onMediaClick: (contentUri: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationInfoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val sharedMedia by viewModel.sharedMedia.collectAsStateWithLifecycle()

    var showBlockConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is ConversationInfoEvent.LeaveConversation) onBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(title = stringResource(R.string.conversation_info_title), onBack = onBack)
        },
    ) { innerPadding ->
        val current = conversation
        LazyVerticalGrid(
            columns = GridCells.Fixed(MEDIA_GRID_COLUMNS),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (current == null) return@LazyVerticalGrid

            fullWidth { ConversationHeader(current, context) }

            fullWidth {
                NotificationsRow(
                    muted = current.isMuted,
                    onMutedChange = viewModel::setMuted,
                )
            }

            if (current.isGroup) {
                fullWidth { HorizontalDivider(color = ConversationRowDivider) }
                fullWidth {
                    Text(
                        text = stringResource(R.string.conversation_info_participants_header, current.recipients.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(
                    items = current.recipients,
                    span = { GridItemSpan(MEDIA_GRID_COLUMNS) },
                    key = { "participant_${it.id}" },
                ) { recipient -> ParticipantRow(recipient, context) }
            }

            fullWidth { HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = ConversationRowDivider) }
            fullWidth {
                Text(
                    text = stringResource(R.string.conversation_info_media_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                )
            }

            if (sharedMedia.isEmpty()) {
                fullWidth {
                    Text(
                        text = stringResource(R.string.conversation_info_media_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(sharedMedia, key = { it.id }) { attachment ->
                    SharedMediaTile(attachment = attachment, onClick = onMediaClick)
                }
            }

            fullWidth { HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = ConversationRowDivider) }
            fullWidth {
                DangerActionRow(
                    icon = if (current.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    label = stringResource(
                        if (current.isArchived) R.string.conversation_info_unarchive else R.string.conversation_info_archive,
                    ),
                    onClick = viewModel::toggleArchived,
                )
            }
            fullWidth {
                DangerActionRow(
                    icon = Icons.Filled.Block,
                    label = stringResource(
                        if (current.isBlocked) R.string.conversation_info_unblock else R.string.conversation_info_block,
                    ),
                    onClick = {
                        if (current.isBlocked) viewModel.toggleBlocked() else showBlockConfirm = true
                    },
                )
            }
            fullWidth {
                DangerActionRow(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.conversation_info_delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteConfirm = true },
                )
            }
        }
    }

    if (showBlockConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.conversation_info_block_confirm_title),
            message = stringResource(R.string.conversation_info_block_confirm_message),
            confirmLabel = stringResource(R.string.conversation_info_block),
            onConfirm = {
                showBlockConfirm = false
                viewModel.toggleBlocked()
            },
            onDismiss = { showBlockConfirm = false },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.conversation_info_delete_confirm_title),
            message = stringResource(R.string.conversation_info_delete_confirm_message),
            confirmLabel = stringResource(R.string.conversation_info_delete),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/** Every row above the shared-media grid spans the whole width of the enclosing
 * [LazyVerticalGrid] -- a small helper so each call site doesn't repeat the span lambda. */
private fun LazyGridScope.fullWidth(
    key: Any? = null,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun ConversationHeader(conversation: Conversation, context: Context, modifier: Modifier = Modifier) {
    val singleContact = conversation.recipients.singleOrNull()?.contact
    Column(
        modifier = modifier
            .fillMaxWidth()
            .let { base ->
                if (!conversation.isGroup && singleContact != null) {
                    base.clickable { openContact(context, singleContact) }
                } else {
                    base
                }
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (conversation.isGroup) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
        } else {
            val contact = conversation.recipients.firstOrNull()?.contact
            ContactAvatar(
                initials = contact?.initials ?: "?",
                photoUri = contact?.photoUri,
                size = 96.dp,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = conversation.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (conversation.isGroup) {
                stringResource(R.string.conversation_info_group_subtitle, conversation.recipients.size)
            } else {
                conversation.recipients.firstOrNull()?.address.orEmpty()
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NotificationsRow(muted: Boolean, onMutedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.conversation_info_notifications_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    if (muted) {
                        R.string.conversation_info_notifications_muted_summary
                    } else {
                        R.string.conversation_info_notifications_on_summary
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = !muted, onCheckedChange = { onMutedChange(!it) })
    }
}

@Composable
private fun ParticipantRow(recipient: Recipient, context: Context, modifier: Modifier = Modifier) {
    val contact = recipient.contact
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { base -> if (contact != null) base.clickable { openContact(context, contact) } else base }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(initials = contact?.initials ?: "?", photoUri = contact?.photoUri, size = 44.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = recipient.displayName, style = MaterialTheme.typography.bodyLarge)
            if (contact != null) {
                Text(
                    text = recipient.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SharedMediaTile(attachment: Attachment, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val contentUri = attachment.contentUri ?: return
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick(contentUri) },
    ) {
        if (attachment.isImage) {
            AsyncImage(
                model = contentUri,
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // No video-thumbnail decoding in this app's Coil setup yet -- a plain play-icon tile
            // still makes it clear there's a video here and remains tappable through to the
            // media viewer, same trade-off text.message.sms.messaging.ui.components.MessageBubble
            // already makes for video attachments inside the chat itself.
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.conversation_info_video_content_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun DangerActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(24.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun openContact(context: Context, contact: Contact) {
    try {
        val uri = ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(context, R.string.conversation_info_view_contact_not_available, Toast.LENGTH_SHORT).show()
    }
}

private const val MEDIA_GRID_COLUMNS = 3
