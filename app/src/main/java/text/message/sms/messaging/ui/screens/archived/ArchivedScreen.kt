package text.message.sms.messaging.ui.screens.archived

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.ui.components.AppTopBar
import text.message.sms.messaging.ui.screens.conversationlist.ConversationListItem
import text.message.sms.messaging.ui.screens.conversationlist.ConversationRow
import text.message.sms.messaging.ui.screens.conversationlist.DateSectionHeader
import text.message.sms.messaging.ui.screens.conversationlist.groupedByDate
import java.time.ZoneId

/**
 * Every archived thread, reached from the "Archived" row atop the inbox
 * ([text.message.sms.messaging.ui.screens.conversationlist.ConversationListScreen]). Reuses that
 * screen's row/date-header rendering ([ConversationRow], [DateSectionHeader], [groupedByDate])
 * rather than duplicating it -- this list only adds a swipe-to-unarchive affordance (plus a
 * trailing icon button doing the same, for anyone who'd rather tap than swipe) on top of it.
 * Unarchiving is trivially reversible (re-archiving is one swipe away in the inbox), so unlike
 * the inbox's archive/delete swipes, this doesn't need an undo snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onConversationClick: (threadId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchivedViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(title = stringResource(R.string.screen_archived), onBack = onBack)
        },
    ) { innerPadding ->
        if (conversations.isEmpty()) {
            EmptyArchived(modifier = Modifier.fillMaxSize().padding(innerPadding))
        } else {
            val zone = remember { ZoneId.systemDefault() }
            val items = remember(conversations, zone) { groupedByDate(conversations, zone) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp),
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
                        is ConversationListItem.Row -> ArchivedConversationRow(
                            conversation = item.conversation,
                            onClick = { onConversationClick(item.conversation.threadId) },
                            onUnarchive = { viewModel.unarchive(item.conversation.threadId) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onUnarchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) onUnarchive()
            value != SwipeToDismissBoxValue.StartToEnd
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = Icons.Filled.Unarchive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConversationRow(conversation = conversation, onClick = onClick, modifier = Modifier.weight(1f))
            IconButton(onClick = onUnarchive) {
                Icon(
                    imageVector = Icons.Filled.Unarchive,
                    contentDescription = stringResource(R.string.archived_unarchive),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyArchived(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
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
                imageVector = Icons.Filled.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.archived_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.archived_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
