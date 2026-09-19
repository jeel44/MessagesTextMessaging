package text.message.sms.messaging.ui.screens.search

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.ui.components.AppBackButton
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor
import text.message.sms.messaging.ui.theme.ConversationRowDivider
import text.message.sms.messaging.ui.theme.FilterChipContentGray
import text.message.sms.messaging.ui.theme.FilterChipSelectedBorder
import text.message.sms.messaging.ui.theme.FilterChipSelectedContainer
import text.message.sms.messaging.ui.theme.FilterChipUnselectedContainer
import text.message.sms.messaging.ui.theme.SearchBarBorder
import text.message.sms.messaging.util.RelativeDateFormatter
import text.message.sms.messaging.util.SearchHighlight

/**
 * Full-text search across threads and message bodies, backed live by [SearchViewModel] --
 * [ConversationRepository][text.message.sms.messaging.domain.repository.ConversationRepository.search]
 * for the "People" tab and [MessageRepository][text.message.sms.messaging.domain.repository.MessageRepository.search]
 * for "Messages". See [SearchViewModel]'s class doc for why there is no "Media" filter chip.
 */
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
        containerColor = screenSurfaceColor(),
        topBar = {
            SearchBar(
                value = queryText,
                onValueChange = viewModel::onQueryChanged,
                onSearch = {
                    viewModel.commitSearch()
                    keyboardController?.hide()
                },
                onBack = onBack,
                focusRequester = focusRequester,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            if (queryText.isNotEmpty()) {
                SearchFilterChipRow(
                    selected = filter,
                    onSelect = viewModel::onFilterSelected,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                )
            }

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

/**
 * White, rounded-pill search bar matching the Home top bar's own pill styling (see
 * [screenSurfaceColor] and [SearchBarBorder]) -- deliberately not a [androidx.compose.material3.TopAppBar],
 * whose own tonal-elevation Surface is what caused this screen's lavender cast in the first place.
 * [statusBarsPadding] here (rather than relying on Scaffold's own inset handling) mirrors exactly
 * how `ConversationListTopBar` clears the status bar on Home.
 */
@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // Same luminance-gated light/dark split as screenSurfaceColor(): the reference's fixed black
    // icon text and #5F6368 hint/clear gray are only correct against a genuinely light background,
    // so dark theme falls back to theme-aware onSurface/onSurfaceVariant instead.
    val isLightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val hintColor = if (isLightSurface) FilterChipContentGray else MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = if (isLightSurface) Color.Black else MaterialTheme.colorScheme.onSurface

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
        AppBackButton(onClick = onBack)

        Spacer(modifier = Modifier.width(12.dp))

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = hintColor,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = textColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
        }

        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.search_clear),
                    tint = hintColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Restyled to match Home's [text.message.sms.messaging.ui.screens.conversationlist.ConversationFilterRow]
 * chip look exactly (same color tokens, 40dp height, 1.5dp selected border) -- these chips are
 * functional (they gate [SearchResultsList]'s `showPeople`/`showMessages`), so per the design brief
 * they're kept, just restyled, and only shown once there's a query to filter. */
@OptIn(ExperimentalMaterial3Api::class)
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
            val isSelected = chipFilter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(chipFilter) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    )
                },
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = FilterChipUnselectedContainer,
                    labelColor = FilterChipContentGray,
                    selectedContainerColor = FilterChipSelectedContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
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
        queryText.isEmpty() -> EmptyQueryState(modifier)
        else -> SearchResultsList(queryText, filter, peopleResults, messageResults, onResultClick, modifier)
    }
}

/** Shown for an empty query with no search history yet -- matches the reference design's clean
 * empty state instead of a blank screen. */
@Composable
private fun EmptyQueryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.search_empty_state),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
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

/** Left inset for the divider between result rows -- lines up with the start of the title/snippet
 * text (16dp row padding + 48dp avatar + 16dp spacer), same math as Home's own `RowDividerInset`. */
private val SearchResultRowDividerInset = 80.dp

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(screenSurfaceColor()),
    ) {
        if (showPeople) {
            if (filter == SearchFilter.ALL) {
                item(key = "header_people") { SectionHeader(stringResource(R.string.search_section_people)) }
            }
            items(peopleResults, key = { "person_${it.threadId}" }) { conversation ->
                Column {
                    PersonResultRow(conversation = conversation, onClick = { onResultClick(conversation.threadId) })
                    ResultRowDivider()
                }
            }
        }
        if (showMessages) {
            if (filter == SearchFilter.ALL) {
                item(key = "header_messages") { SectionHeader(stringResource(R.string.search_section_messages)) }
            }
            items(messageResults, key = { "message_${it.message.id}" }) { row ->
                Column {
                    MessageResultRow(row = row, query = queryText, onClick = { onResultClick(row.conversation.threadId) })
                    ResultRowDivider()
                }
            }
        }
    }
}

@Composable
private fun ResultRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = SearchResultRowDividerInset),
        thickness = 0.75.dp,
        color = ConversationRowDivider,
    )
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
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatResultDate(timestampMillis),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = snippet,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
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
            .size(48.dp)
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

/** Short-form date label, matching Home's own row format exactly -- see
 * [text.message.sms.messaging.util.RelativeDateFormatter.listLabel]. */
@Composable
private fun formatResultDate(timestampMillis: Long): String {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    return remember(timestampMillis, is24Hour) {
        RelativeDateFormatter.listLabel(timestampMillis, is24Hour)
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
