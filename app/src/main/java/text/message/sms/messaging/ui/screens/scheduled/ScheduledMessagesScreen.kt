package text.message.sms.messaging.ui.screens.scheduled

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.ScheduledStatus
import text.message.sms.messaging.ui.components.AppTopBar
import text.message.sms.messaging.ui.screens.chat.formatScheduleTime

internal const val ScheduledMessagesListTestTag = "scheduled_messages_list"

/** Every scheduled message across threads -- reached from the call-end screen's "Schedule
 * message" entry. Each row has Send now and Delete; tapping a row opens its conversation. */
@Composable
fun ScheduledMessagesScreen(
    onBack: () -> Unit,
    onConversationClick: (threadId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ScheduledMessagesViewModel = hiltViewModel()
    val items by viewModel.items.collectAsStateWithLifecycle()
    ScheduledMessagesContent(
        items = items,
        onBack = onBack,
        onOpen = { onConversationClick(it.threadId) },
        onSendNow = { viewModel.sendNow(it.messageId) },
        onDelete = { viewModel.delete(it.messageId) },
        modifier = modifier,
    )
}

/** Stateless body of [ScheduledMessagesScreen] -- `internal` for its render test. [items] null =
 * still loading. Theme colors only. */
@Composable
internal fun ScheduledMessagesContent(
    items: List<ScheduledListItem>?,
    onBack: () -> Unit,
    onOpen: (ScheduledListItem) -> Unit,
    onSendNow: (ScheduledListItem) -> Unit,
    onDelete: (ScheduledListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(title = stringResource(R.string.scheduled_list_title), onBack = onBack) },
    ) { padding ->
        when {
            items == null -> Unit
            items.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.scheduled_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(ScheduledMessagesListTestTag),
            ) {
                items(items, key = { it.messageId }) { item ->
                    val time = formatScheduleTime(context, item.sendAtMillis)
                    ListItem(
                        overlineContent = {
                            Text(
                                text = when (item.status) {
                                    ScheduledStatus.PENDING -> time
                                    ScheduledStatus.SENDING -> stringResource(R.string.chat_status_sending)
                                    ScheduledStatus.FAILED -> "${stringResource(R.string.scheduled_list_status_failed)} · $time"
                                },
                                color = if (item.status == ScheduledStatus.FAILED) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        },
                        headlineContent = { Text(item.recipient, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(item.body, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = if (item.status == ScheduledStatus.SENDING) {
                            null
                        } else {
                            {
                                Row {
                                    IconButton(onClick = { onSendNow(item) }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = stringResource(R.string.scheduled_action_send_now),
                                        )
                                    }
                                    IconButton(onClick = { onDelete(item) }) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = stringResource(R.string.scheduled_list_delete),
                                        )
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable { onOpen(item) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
