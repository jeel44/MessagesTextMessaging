package text.message.sms.messaging.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.ui.theme.ChatBubbleContentDark
import text.message.sms.messaging.ui.theme.ChatBubbleSelectedBlue
import text.message.sms.messaging.ui.theme.ChatBubbleSentContainer
import text.message.sms.messaging.ui.theme.ChatDateSeparatorGray
import text.message.sms.messaging.ui.theme.ChatLinkColor
import text.message.sms.messaging.ui.theme.ChatReceivedBubble
import text.message.sms.messaging.ui.theme.ChatSelectedImageOverlay
import text.message.sms.messaging.util.openMessageLinkDialer
import text.message.sms.messaging.util.openMessageLinkEmail
import text.message.sms.messaging.util.openMessageLinkUrl

/** Corner radius shared by every rounded bubble corner except the tail. */
private val BubbleCornerRadius = 20.dp

/** Corner radius for the flattened "tail" corner of the last bubble in a run. */
private val BubbleTailRadius = 4.dp

/** Corner radius on all four corners of a received bubble -- unlike [bubbleShape]'s sent-bubble
 * "tail", the reference design never flattens a received bubble's corner regardless of its
 * position in a run. */
private val ReceivedBubbleCornerRadius = 16.dp

/** Fraction of the screen's width an outgoing bubble may grow to before wrapping. */
private const val MaxBubbleWidthFraction = 0.80f

/** Fraction of the screen's width a received bubble may grow to before wrapping -- wider than
 * [MaxBubbleWidthFraction] to match the reference design. */
private const val MaxReceivedBubbleWidthFraction = 0.85f

/**
 * A single chat bubble. [isLastInRun] flattens the corner nearest the sender's side (the "tail")
 * and, for an outgoing bubble, reveals the status caption below it ("Sending...", "Failed. Tap to
 * retry") -- both only shown once per run of consecutive same-sender bubbles, matching how Google
 * Messages groups a burst of messages. There is no per-bubble timestamp: times now live only in
 * the chat's date separators (see [text.message.sms.messaging.ui.screens.chat.ChatScreen]'s
 * `ChatDateSeparator`).
 *
 * A selected bubble (sent or received alike) shows as a solid [ChatBubbleSelectedBlue] fill with
 * white text/links -- no separate badge or outline -- animated in over 120ms so selection doesn't
 * pop.
 */
@Composable
fun MessageBubble(
    text: String,
    isOutgoing: Boolean,
    isLastInRun: Boolean,
    deliveryState: DeliveryState,
    attachments: List<Attachment>,
    onAttachmentClick: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onStartSelection: () -> Unit = {},
) {
    val isLight = isLightChatTheme()
    val targetContainerColor = when {
        isSelected -> ChatBubbleSelectedBlue
        isOutgoing && isLight -> ChatBubbleSentContainer
        isOutgoing -> MaterialTheme.colorScheme.primary
        isLight -> ChatReceivedBubble
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 120),
        label = "bubbleContainerColor",
    )
    val contentColor = when {
        isSelected -> Color.White
        isLight -> ChatBubbleContentDark
        isOutgoing -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = remember(isOutgoing, isLastInRun) {
        if (isOutgoing) sentBubbleShape(isLastInRun) else RoundedCornerShape(ReceivedBubbleCornerRadius)
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val maxBubbleWidth = remember(screenWidthDp, isOutgoing) {
        val fraction = if (isOutgoing) MaxBubbleWidthFraction else MaxReceivedBubbleWidthFraction
        (screenWidthDp * fraction).dp
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.widthIn(max = maxBubbleWidth)) {
        val mediaOnly = text.isBlank() && attachments.isNotEmpty()
        Column(
            modifier = Modifier
                .clip(shape)
                .background(containerColor)
                // Long-press always starts (or extends) selection, with haptic feedback --
                // regardless of whether it lands on a link, since LinkAnnotation.Clickable
                // (used by buildLinkedMessageText below) only wires up a *click* listener, so
                // a long-press here is never intercepted by the link layer. A plain tap only
                // toggles selection while already in selection mode; outside it, onClick is a
                // no-op and taps on a link are handled entirely by that link's own listener
                // (gated on isSelectionMode below), leaving normal-mode link taps unchanged.
                .combinedClickable(
                    onClick = { if (isSelectionMode) onToggleSelection() },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isSelectionMode) onToggleSelection() else onStartSelection()
                    },
                )
                .padding(
                    horizontal = if (mediaOnly) 4.dp else if (isOutgoing) 12.dp else 12.dp,
                    vertical = if (mediaOnly) 4.dp else if (isOutgoing) 8.dp else 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            attachments.forEach { attachment ->
                if (attachment.isImage && attachment.contentUri != null) {
                    Box {
                        // aspectRatio reserves the bubble's height up front -- [Attachment] carries
                        // no known width/height, so this is a fixed placeholder ratio rather than
                        // the image's real one, but it still means a late decode (Coil resolving
                        // the content URI) never changes this item's height after first layout and
                        // pushes every item below it in the list.
                        AsyncImage(
                            model = attachment.contentUri,
                            contentDescription = attachment.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .widthIn(max = 240.dp)
                                .aspectRatio(4f / 3f)
                                .clickable(enabled = !isSelectionMode) { onAttachmentClick(attachment) },
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ChatSelectedImageOverlay),
                            )
                        }
                    }
                } else if (!attachment.isTextPart) {
                    AttachmentChip(attachment, contentColor)
                }
            }

            if (text.isNotBlank()) {
                val linkedText = remember(text, isSelectionMode, isSelected) {
                    buildLinkedMessageText(
                        text = text,
                        linkColor = if (isSelected) Color.White else ChatLinkColor,
                        onUrlClick = { url ->
                            if (isSelectionMode) onToggleSelection() else openMessageLinkUrl(context, url)
                        },
                        onPhoneClick = { phone ->
                            if (isSelectionMode) onToggleSelection() else openMessageLinkDialer(context, phone)
                        },
                        onEmailClick = { email ->
                            if (isSelectionMode) onToggleSelection() else openMessageLinkEmail(context, email)
                        },
                    )
                }
                Text(
                    text = linkedText,
                    style = if (isOutgoing) {
                        MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp)
                    } else {
                        MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp)
                    },
                    color = contentColor,
                )
            }
        }

        if (isLastInRun && isOutgoing) {
            val status = outgoingStatusCaption(deliveryState)
            if (status != null) {
                val captionColor = if (isLight) ChatDateSeparatorGray else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                    color = captionColor,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

/** `true` whenever the active [MaterialTheme.colorScheme] reads as a light theme -- same
 * luminance check [text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor]
 * uses, so the Chat screen's fixed reference-design colors only apply in light theme and dark
 * theme keeps following [MaterialTheme.colorScheme] as usual. */
@Composable
private fun isLightChatTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

@Composable
private fun AttachmentChip(attachment: Attachment, contentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(contentColor.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = attachment.fileName ?: attachment.mimeType,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
    }
}

/** Shape for a sent (outgoing) bubble, with a flattened "tail" on the last bubble in a run -- a
 * received bubble always uses the uniform [ReceivedBubbleCornerRadius] instead, with no tail (see
 * the caller in [MessageBubble]). */
private fun sentBubbleShape(isLastInRun: Boolean): RoundedCornerShape {
    val tail = if (isLastInRun) BubbleTailRadius else BubbleCornerRadius
    return RoundedCornerShape(
        topStart = BubbleCornerRadius,
        topEnd = BubbleCornerRadius,
        bottomStart = BubbleCornerRadius,
        bottomEnd = tail,
    )
}

/** Status caption for the last bubble in an outgoing run -- `null` (nothing shown) for
 * [DeliveryState.SENT]/[DeliveryState.DELIVERED]/[DeliveryState.NONE], since the reference design
 * only calls out a message that needs the user's attention (still sending, or failed), never a
 * "Delivered" label. */
@Composable
private fun outgoingStatusCaption(deliveryState: DeliveryState): String? = when (deliveryState) {
    DeliveryState.SENDING, DeliveryState.PENDING -> stringResource(R.string.chat_status_sending)
    DeliveryState.FAILED -> stringResource(R.string.chat_status_failed_retry)
    DeliveryState.SENT, DeliveryState.DELIVERED, DeliveryState.NONE -> null
}
