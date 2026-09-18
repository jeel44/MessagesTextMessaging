package text.message.sms.messaging.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.ui.graphics.vector.ImageVector
import text.message.sms.messaging.R
import text.message.sms.messaging.data.local.datastore.SwipeAction

/**
 * Shared presentation for [SwipeAction] -- used by both the conversation list (to draw the
 * reveal-behind-the-row icon while swiping) and Settings' "Swipe actions" picker (to label and
 * icon each option), so the two never drift apart on what an action is called or looks like.
 */
internal fun SwipeAction.labelRes(): Int = when (this) {
    SwipeAction.NONE -> R.string.swipe_action_none
    SwipeAction.ARCHIVE -> R.string.swipe_action_archive
    SwipeAction.DELETE -> R.string.swipe_action_delete
    SwipeAction.TOGGLE_READ -> R.string.swipe_action_toggle_read
    SwipeAction.CALL -> R.string.swipe_action_call
}

internal fun SwipeAction.icon(): ImageVector? = when (this) {
    SwipeAction.NONE -> null
    SwipeAction.ARCHIVE -> Icons.Filled.Archive
    SwipeAction.DELETE -> Icons.Filled.Delete
    SwipeAction.TOGGLE_READ -> Icons.Filled.MarkChatRead
    SwipeAction.CALL -> Icons.Filled.Call
}
