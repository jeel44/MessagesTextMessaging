package text.message.sms.messaging.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.model.DeliveryState
import java.util.Date

/** Corner radius shared by every rounded bubble corner except the tail. */
private val BubbleCornerRadius = 20.dp

/** Corner radius for the flattened "tail" corner of the last bubble in a run. */
private val BubbleTailRadius = 4.dp

/**
 * A single chat bubble. [isLastInRun] flattens the corner nearest the sender's side (the "tail")
 * and reveals the timestamp/status caption -- both only shown once per run of consecutive
 * same-sender bubbles, matching how Google Messages groups a burst of messages.
 */
@Composable
fun MessageBubble(
    text: String,
    isOutgoing: Boolean,
    isLastInRun: Boolean,
    timestampMillis: Long,
    deliveryState: DeliveryState,
    attachments: List<Attachment>,
    onAttachmentClick: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = remember(isOutgoing, isLastInRun) { bubbleShape(isOutgoing, isLastInRun) }

    Column(modifier = modifier.widthIn(max = 280.dp)) {
        val mediaOnly = text.isBlank() && attachments.isNotEmpty()
        Column(
            modifier = Modifier
                .clip(shape)
                .background(containerColor)
                .padding(
                    horizontal = if (mediaOnly) 4.dp else 16.dp,
                    vertical = if (mediaOnly) 4.dp else 10.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            attachments.forEach { attachment ->
                if (attachment.isImage && attachment.contentUri != null) {
                    AsyncImage(
                        model = attachment.contentUri,
                        contentDescription = attachment.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .widthIn(max = 240.dp)
                            .clickable { onAttachmentClick(attachment) },
                    )
                } else if (!attachment.isTextPart) {
                    AttachmentChip(attachment, contentColor)
                }
            }

            if (text.isNotBlank()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
            }
        }

        if (isLastInRun) {
            Text(
                text = bubbleCaption(timestampMillis, isOutgoing, deliveryState),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

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

private fun bubbleShape(isOutgoing: Boolean, isLastInRun: Boolean): RoundedCornerShape {
    val tail = if (isLastInRun) BubbleTailRadius else BubbleCornerRadius
    return if (isOutgoing) {
        RoundedCornerShape(
            topStart = BubbleCornerRadius,
            topEnd = BubbleCornerRadius,
            bottomStart = BubbleCornerRadius,
            bottomEnd = tail,
        )
    } else {
        RoundedCornerShape(
            topStart = BubbleCornerRadius,
            topEnd = BubbleCornerRadius,
            bottomStart = tail,
            bottomEnd = BubbleCornerRadius,
        )
    }
}

@Composable
private fun bubbleCaption(
    timestampMillis: Long,
    isOutgoing: Boolean,
    deliveryState: DeliveryState,
): String {
    val context = LocalContext.current
    val time = remember(timestampMillis) {
        DateFormat.getTimeFormat(context).format(Date(timestampMillis))
    }
    if (!isOutgoing) return time

    val status = when (deliveryState) {
        DeliveryState.SENDING, DeliveryState.PENDING -> stringResource(R.string.chat_status_sending)
        DeliveryState.SENT -> stringResource(R.string.chat_status_sent)
        DeliveryState.DELIVERED -> stringResource(R.string.chat_status_delivered)
        DeliveryState.FAILED -> stringResource(R.string.chat_status_failed)
        DeliveryState.NONE -> null
    }
    return if (status != null) "$time • $status" else time
}
