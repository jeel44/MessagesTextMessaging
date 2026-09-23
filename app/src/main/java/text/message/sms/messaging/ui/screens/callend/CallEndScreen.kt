package text.message.sms.messaging.ui.screens.callend

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.CallSession
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.ui.components.ContactAvatar
import text.message.sms.messaging.ui.components.sharpIconPainter
import text.message.sms.messaging.ui.screens.conversationlist.ConversationRow

/**
 * The call-end screen: header for the call just finished, quick-launch tiles into third-party chat
 * apps, a 3-tab body (List/Archive/More), and a reserved banner-ad slot. Reached from an
 * actually-ended call, detected by [text.message.sms.messaging.service.CallStateMonitor] and
 * delivered via [text.message.sms.messaging.service.CallEndTriggerService]'s full-screen-intent
 * notification.
 */
@Composable
fun CallEndScreen(
    onConversationClick: (threadId: Long) -> Unit,
    onViewContactsClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onComingSoonClick: (featureTitle: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallEndViewModel = hiltViewModel(),
) {
    val contact by viewModel.contact.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val inboxConversations by viewModel.inboxConversations.collectAsStateWithLifecycle()
    val archivedConversations by viewModel.archivedConversations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val callSession = viewModel.callSession

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            CallEndHeader(
                contact = contact,
                callSession = callSession,
                onCallBackClick = { callBack(context, callSession.phoneNumber) },
            )

            val phoneNumber = callSession.phoneNumber
            if (phoneNumber != null && viewModel.quickLaunchApps.any) {
                QuickLaunchRow(
                    phoneNumber = phoneNumber,
                    apps = viewModel.quickLaunchApps,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }

            CallEndTabBar(
                selectedTab = selectedTab,
                onTabSelected = viewModel::onTabSelected,
                modifier = Modifier.padding(top = 20.dp),
            )

            CallEndBody(
                tab = selectedTab,
                inboxConversations = inboxConversations,
                archivedConversations = archivedConversations,
                onConversationClick = onConversationClick,
                onViewContactsClick = onViewContactsClick,
                onMessagesClick = onMessagesClick,
                onComingSoonClick = onComingSoonClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            BannerAdSlot()
        }
    }
}

@Composable
private fun CallEndHeader(
    contact: Contact?,
    callSession: CallSession,
    onCallBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (contact != null) {
            ContactAvatar(initials = contact.initials, photoUri = contact.photoUri, size = 52.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = sharpIconPainter(R.drawable.ic_avatar_default),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact?.displayName ?: callSession.phoneNumber ?: stringResource(R.string.call_end_unknown_caller),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.call_end_duration_label, formatDurationMmSs(callSession.durationMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (callSession.phoneNumber != null) {
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onCallBackClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    painter = sharpIconPainter(R.drawable.ic_call_back),
                    contentDescription = stringResource(R.string.call_end_call_back),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickLaunchRow(phoneNumber: String, apps: QuickLaunchApps, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (apps.whatsApp) {
            QuickLaunchTile(
                iconRes = R.drawable.ic_whatsapp,
                contentDescription = stringResource(R.string.call_end_whatsapp),
                onClick = { openWhatsAppLike(context, PACKAGE_WHATSAPP, phoneNumber) },
                modifier = Modifier.weight(1f),
            )
        }
        if (apps.whatsAppBusiness) {
            QuickLaunchTile(
                iconRes = R.drawable.ic_whatsapp_business,
                contentDescription = stringResource(R.string.call_end_whatsapp_business),
                onClick = { openWhatsAppLike(context, PACKAGE_WHATSAPP_BUSINESS, phoneNumber) },
                modifier = Modifier.weight(1f),
            )
        }
        if (apps.telegram) {
            QuickLaunchTile(
                iconRes = R.drawable.ic_telegram,
                contentDescription = stringResource(R.string.call_end_telegram),
                onClick = { openTelegram(context, phoneNumber) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickLaunchTile(iconRes: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painter = sharpIconPainter(iconRes), contentDescription = contentDescription, modifier = Modifier.size(28.dp))
    }
}

/** Sized to a 300x250 ad unit's aspect ratio scaled to the screen's available width, rather than
 * a fixed dp height -- so the reserved space stays proportionally correct across phone widths. */
@Composable
private fun BannerAdSlot(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val height = maxWidth * (250f / 300f)
        AdSlotPlaceholder(modifier = Modifier.fillMaxWidth().height(height))
    }
}

/** DEBUG-ONLY dashed outline + "AD SLOT" label so [BannerAdSlot]'s reserved space can actually be
 * seen during review -- release builds render just the bare, borderless [Box] it had before,
 * since a real ad will fill this space in production. */
@Composable
private fun AdSlotPlaceholder(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) {
        Box(modifier = modifier)
        return
    }
    val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Box(
        modifier = modifier.drawBehind {
            drawRoundRect(
                color = outlineColor,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx())),
                ),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "AD SLOT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun CallEndTabBar(
    selectedTab: CallEndTab,
    onTabSelected: (CallEndTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CallEndTabItem(
            tab = CallEndTab.LIST,
            selected = selectedTab,
            iconRes = R.drawable.ic_tab_list,
            activeIconRes = R.drawable.ic_tab_list_active,
            contentDescription = stringResource(R.string.call_end_tab_list),
            onClick = onTabSelected,
        )
        CallEndTabItem(
            tab = CallEndTab.ARCHIVE,
            selected = selectedTab,
            iconRes = R.drawable.ic_tab_archive,
            activeIconRes = R.drawable.ic_tab_archive_active,
            contentDescription = stringResource(R.string.call_end_tab_archive),
            onClick = onTabSelected,
        )
        CallEndTabItem(
            tab = CallEndTab.MORE,
            selected = selectedTab,
            iconRes = R.drawable.ic_tab_more,
            activeIconRes = R.drawable.ic_tab_more_active,
            contentDescription = stringResource(R.string.call_end_tab_more),
            onClick = onTabSelected,
        )
    }
}

@Composable
private fun RowScope.CallEndTabItem(
    tab: CallEndTab,
    selected: CallEndTab,
    iconRes: Int,
    activeIconRes: Int,
    contentDescription: String,
    onClick: (CallEndTab) -> Unit,
) {
    val isSelected = tab == selected
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isSelected) {
            // The active asset bakes in its own fixed blue-badge/white-glyph design, unlike the
            // inactive asset below -- tinting it would flatten that two-tone badge to a single color.
            Image(
                painter = sharpIconPainter(activeIconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                painter = sharpIconPainter(iconRes),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(percent = 50))
                .let { if (isSelected) it.background(MaterialTheme.colorScheme.primary) else it },
        )
    }
}

@Composable
private fun CallEndBody(
    tab: CallEndTab,
    inboxConversations: List<Conversation>,
    archivedConversations: List<Conversation>,
    onConversationClick: (threadId: Long) -> Unit,
    onViewContactsClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onComingSoonClick: (featureTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listEmptyText = stringResource(R.string.call_end_list_empty)
    val archiveEmptyText = stringResource(R.string.call_end_archive_empty)

    Crossfade(targetState = tab, modifier = modifier, label = "callEndTabBody") { current ->
        when (current) {
            CallEndTab.LIST -> ConversationsTabList(
                conversations = inboxConversations,
                onClick = onConversationClick,
                emptyText = listEmptyText,
            )
            CallEndTab.ARCHIVE -> ConversationsTabList(
                conversations = archivedConversations,
                onClick = onConversationClick,
                emptyText = archiveEmptyText,
            )
            CallEndTab.MORE -> MoreTabBody(
                onViewContactsClick = onViewContactsClick,
                onMessagesClick = onMessagesClick,
                onComingSoonClick = onComingSoonClick,
            )
        }
    }
}

@Composable
private fun ConversationsTabList(
    conversations: List<Conversation>,
    onClick: (threadId: Long) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    if (conversations.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(conversations, key = { it.threadId }) { conversation ->
                ConversationRow(conversation = conversation, onClick = { onClick(conversation.threadId) })
            }
        }
    }
}

@Composable
private fun MoreTabBody(
    onViewContactsClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onComingSoonClick: (featureTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheduleTitle = stringResource(R.string.call_end_menu_schedule)
    val backupTitle = stringResource(R.string.call_end_menu_backup)

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        MoreMenuRow(
            iconRes = R.drawable.ic_menu_contacts,
            title = stringResource(R.string.call_end_menu_contacts),
            onClick = onViewContactsClick,
        )
        MoreMenuRow(
            iconRes = R.drawable.ic_menu_messages,
            title = stringResource(R.string.call_end_menu_messages),
            onClick = onMessagesClick,
        )
        MoreMenuRow(
            iconRes = R.drawable.ic_menu_schedule,
            title = scheduleTitle,
            onClick = { onComingSoonClick(scheduleTitle) },
        )
        MoreMenuRow(
            iconRes = R.drawable.ic_menu_backup,
            title = backupTitle,
            onClick = { onComingSoonClick(backupTitle) },
        )
    }
}

@Composable
private fun MoreMenuRow(iconRes: Int, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = sharpIconPainter(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatDurationMmSs(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/** Opens the system dialer pre-filled with [phoneNumber] rather than placing the call directly
 * ([Intent.ACTION_DIAL], not `ACTION_CALL`) -- this needs no CALL_PHONE permission check, so it
 * can't fail with a [SecurityException] regardless of whether that best-effort onboarding
 * permission was granted. No-ops if [phoneNumber] is null (button is hidden in that case anyway,
 * see [CallEndHeader]). */
private fun callBack(context: Context, phoneNumber: String?) {
    if (phoneNumber == null) return
    launchExternal(context, Intent(Intent.ACTION_DIAL, "tel:$phoneNumber".toUri()))
}

/** WhatsApp and WhatsApp Business are separate installed apps with separate package names, so
 * `setPackage` pins the deep link to whichever tile was tapped instead of letting the system show
 * a chooser (or silently pick one) when both are installed. */
private fun openWhatsAppLike(context: Context, packageName: String, phoneNumber: String) {
    val e164Digits = phoneNumber.filter { it.isDigit() || it == '+' }
    val intent = Intent(Intent.ACTION_VIEW, "https://wa.me/$e164Digits".toUri()).apply {
        setPackage(packageName)
    }
    launchExternal(context, intent)
}

private fun openTelegram(context: Context, phoneNumber: String) {
    val digits = phoneNumber.filter { it.isDigit() }
    val intent = Intent(Intent.ACTION_VIEW, "tg://resolve?phone=$digits".toUri()).apply {
        setPackage(PACKAGE_TELEGRAM)
    }
    launchExternal(context, intent)
}

private fun launchExternal(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("CallEndScreen", "No activity to handle ${intent.action} ${intent.data}", e)
    }
}
