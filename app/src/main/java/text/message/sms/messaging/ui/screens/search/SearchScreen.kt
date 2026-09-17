package text.message.sms.messaging.ui.screens.search

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.ui.theme.Pill
import text.message.sms.messaging.util.SearchHighlight
import java.util.Date

/**
 * Full-text search across threads and message bodies, backed live by [SearchViewModel] --
 * [ConversationRepository][text.message.sms.messaging.domain.repository.ConversationRepository.search]
 * for the "People" tab and [MessageRepository][text.message.sms.messaging.domain.repository.MessageRepository.search]
 * for "Messages". See [SearchViewModel]'s class doc for why there is no "Media" filter chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onResultClick: (threadId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val peopleResults by viewModel.peopleResults.collectAsStateWithLifecycle()
    val messageResults by viewModel.messageResults.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    SearchField(
                        value = queryText,
                        onValueChange = viewModel::onQueryChanged,
                        onSearch = {
                            viewModel.commitSearch()
                            keyboardController?.hide()
                        },
                        focusRequester = focusRequester,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchFilterChipRow(
                selected = filter,
                onSelect = viewModel::onFilterSelected,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            SearchContent(
                queryText = queryText,
                filter = filter,
                recentSearches = recentSearches,
                peopleResults = peopleResults,
                messageResults = messageResults,
                onRecentSearchSelected = viewModel::onRecentSearchSelected,
                onResultClick = { threadId ->
                    viewModel.commitSearch()
                    onResultClick(threadId)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
        singleLine = true,
        shape = Pill,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun SearchFilterChipRow(
    selected: SearchFilter,
    onSelect: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = listOf(
        SearchFilter.ALL to stringResource(R.string.search_filter_all),
        SearchFilter.PEOPLE to stringResource(R.string.search_filter_people),
        SearchFilter.MESSAGES to stringResource(R.string.search_filter_messages),
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

@Composable
private fun SearchContent(
    queryText: String,
    filter: SearchFilter,
    recentSearches: List<String>,
    peopleResults: List<Conversation>,
    messageResults: List<MessageSearchRow>,
    onRecentSearchSelected: (String) -> Unit,
    onResultClick: (threadId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        queryText.isEmpty() && recentSearches.isNotEmpty() ->
            RecentSearchesList(recentSearches, onRecentSearchSelected, modifier)
        queryText.isEmpty() -> Box(modifier) // nothing typed yet and no search history either
        else -> SearchResultsList(queryText, filter, peopleResults, messageResults, onResultClick, modifier)
    }
}

@Composable
private fun RecentSearchesList(
    recentSearches: List<String>,
    onTermSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "recent_header") { SectionHeader(stringResource(R.string.search_recent_header)) }
        items(recentSearches, key = { "recent_$it" }) { term ->
            RecentSearchRow(term = term, onClick = { onTermSelected(term) })
        }
    }
}

@Composable
private fun RecentSearchRow(term: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = term,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchResultsList(
    queryText: String,
    filter: SearchFilter,
    peopleResults: List<Conversation>,
    messageResults: List<MessageSearchRow>,
    onResultClick: (threadId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showPeople = filter != SearchFilter.MESSAGES && peopleResults.isNotEmpty()
    val showMessages = filter != SearchFilter.PEOPLE && messageResults.isNotEmpty()

    if (!showPeople && !showMessages) {
        NoResults(query = queryText, modifier = modifier)
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (showPeople) {
            if (filter == SearchFilter.ALL) {
                item(key = "header_people") { SectionHeader(stringResource(R.string.search_section_people)) }
            }
            items(peopleResults, key = { "person_${it.threadId}" }) { conversation ->
                PersonResultRow(conversation = conversation, onClick = { onResultClick(conversation.threadId) })
            }
        }
        if (showMessages) {
            if (filter == SearchFilter.ALL) {
                item(key = "header_messages") { SectionHeader(stringResource(R.string.search_section_messages)) }
            }
            items(messageResults, key = { "message_${it.message.id}" }) { row ->
                MessageResultRow(row = row, query = queryText, onClick = { onResultClick(row.conversation.threadId) })
            }
        }
    }
}

@Composable
private fun NoResults(query: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.search_no_results, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun PersonResultRow(conversation: Conversation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SearchResultRow(
        title = conversation.title,
        isGroup = conversation.isGroup,
        contact = conversation.recipients.firstOrNull()?.contact,
        timestampMillis = conversation.lastMessageAtMillis,
        snippet = AnnotatedString(conversation.snippet),
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * A message hit shown as the thread it belongs to (avatar/name), since resolving the exact
 * per-message sender within a group thread would need its own address-to-contact lookup beyond
 * this pass's scope -- see [SearchViewModel].
 */
@Composable
private fun MessageResultRow(row: MessageSearchRow, query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SearchResultRow(
        title = row.conversation.title,
        isGroup = row.conversation.isGroup,
        contact = row.conversation.recipients.firstOrNull()?.contact,
        timestampMillis = row.message.receivedAtMillis,
        snippet = highlightedText(row.message.body, query),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun SearchResultRow(
    title: String,
    isGroup: Boolean,
    contact: Contact?,
    timestampMillis: Long,
    snippet: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchResultAvatar(isGroup = isGroup, contact = contact)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatMessageTime(timestampMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchResultAvatar(isGroup: Boolean, contact: Contact?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isGroup -> Icon(
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

@Composable
private fun formatMessageTime(timestampMillis: Long): String {
    val context = LocalContext.current
    return remember(timestampMillis) {
        DateFormat.getTimeFormat(context).format(Date(timestampMillis))
    }
}

/** [text] with every case-insensitive occurrence of [query] bolded in the theme's primary color,
 * per the design reference -- falls back to plain text when there is nothing to highlight. */
@Composable
private fun highlightedText(text: String, query: String): AnnotatedString {
    val ranges = remember(text, query) { SearchHighlight.matchRanges(text, query) }
    if (ranges.isEmpty()) return AnnotatedString(text)

    val highlightColor = MaterialTheme.colorScheme.primary
    return remember(text, ranges, highlightColor) {
        buildAnnotatedString {
            append(text)
            ranges.forEach { range ->
                addStyle(
                    style = SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor),
                    start = range.first,
                    end = range.last + 1,
                )
            }
        }
    }
}
