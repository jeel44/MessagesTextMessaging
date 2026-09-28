package text.message.sms.messaging.ui.screens.blocked

import android.telephony.PhoneNumberUtils
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.ui.components.AppTopBar
import java.util.Locale

internal const val BlockedRowTagPrefix = "blocked_row_"

/**
 * Every blocked number, reached from the inbox's side drawer. Same shape as
 * [text.message.sms.messaging.ui.screens.archived.ArchivedScreen]: a back-arrow [AppTopBar], and
 * each row unblocks with a swipe right or its trailing icon button. Unlike unarchiving, an
 * unblock gets an undo snackbar -- it changes which messages reach the inbox, so an accidental
 * swipe shouldn't be one extra step to reverse. Tapping a row does nothing else.
 */
@Composable
fun BlockedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockedViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val unblockedLabel = stringResource(R.string.blocked_unblocked_snackbar)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel) {
        viewModel.unblocked.collect { unblocked ->
            val result = snackbarHostState.showSnackbar(
                message = unblockedLabel,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoUnblock(unblocked)
        }
    }

    BlockedScreenContent(
        rows = rows,
        onUnblock = viewModel::unblock,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** [BlockedScreen] minus its ViewModel, so render tests can drive it directly. [rows] is null
 * while the first read is in flight: neither the list nor the empty state shows yet. */
@Composable
internal fun BlockedScreenContent(
    rows: List<BlockedNumberRow>?,
    onUnblock: (BlockedNumber) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { AppTopBar(title = stringResource(R.string.screen_blocked), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            rows == null -> Unit
            rows.isEmpty() -> EmptyBlocked(modifier = Modifier.fillMaxSize().padding(innerPadding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(items = rows, key = { it.number.address }) { row ->
                    BlockedNumberRowItem(row = row, onUnblock = { onUnblock(row.number) })
                }
            }
        }
    }
}

/**
 * Swipe right, or the trailing button, unblocks. The swipe never settles on the dismissed side
 * (`confirmValueChange` returns false for it) -- the row goes away on its own once the unblock
 * lands in Room. Returning false means [SwipeToDismissBox] keeps re-invoking
 * `confirmValueChange` on every drag frame past the threshold, so [unblockFired] lets it act once
 * per swipe, reset when the drag falls back to Settled (the same guard the inbox's swipe row uses).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedNumberRowItem(
    row: BlockedNumberRow,
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var unblockFired by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd && !unblockFired) {
                unblockFired = true
                onUnblock()
            }
            value != SwipeToDismissBoxValue.StartToEnd
        },
    )
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.targetValue }.collect {
            if (it == SwipeToDismissBoxValue.Settled) unblockFired = false
        }
    }

    val colors = MaterialTheme.colorScheme
    val formattedNumber = remember(row.number.address) { formatAddress(row.number.address) }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.testTag(BlockedRowTagPrefix + row.number.address),
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.primaryContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LockOpen,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.contactName ?: formattedNumber,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.contactName != null) {
                    Text(
                        text = formattedNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onUnblock) {
                Icon(
                    imageVector = Icons.Outlined.LockOpen,
                    contentDescription = stringResource(R.string.blocked_unblock),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** The number as the device's region writes it ("(555) 010-1234"); short codes and
 * alphanumeric senders, which [PhoneNumberUtils] can't format, stay as stored. */
private fun formatAddress(address: String): String =
    PhoneNumberUtils.formatNumber(address, Locale.getDefault().country) ?: address

@Composable
private fun EmptyBlocked(modifier: Modifier = Modifier) {
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
                imageVector = Icons.Outlined.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.blocked_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.blocked_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
