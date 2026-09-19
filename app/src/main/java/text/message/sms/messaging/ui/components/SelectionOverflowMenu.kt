package text.message.sms.messaging.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor
import text.message.sms.messaging.ui.theme.ConversationRowDivider
import text.message.sms.messaging.ui.theme.SelectionMenuIconDark
import text.message.sms.messaging.ui.theme.SelectionMenuText

/** One row of [SelectionOverflowMenu]. */
data class SelectionMenuItem(
    val icon: Painter,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** Fixed width matching the reference design -- wide enough that a long label never crowds the
 * end edge. */
private val MenuWidth = 220.dp
private val RowHeight = 52.dp

/**
 * The one overflow popup shared by Chat's and Home's selection top bars: a solid, flat surface
 * with no tonal tint (unlike a bare [DropdownMenu], which defaults to a lavender-tinted
 * `surfaceContainer`), fixed 220dp width, 52dp rows, and every icon/label the same near-black
 * tone -- built from plain rows rather than [androidx.compose.material3.DropdownMenuItem] so the
 * row height and icon/label gap match the reference design exactly rather than that component's
 * own tighter defaults. [items] renders in order, with a hairline divider between rows (never
 * after the last one).
 */
@Composable
fun SelectionOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<SelectionMenuItem>,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val containerColor = if (isLight) screenSurfaceColor() else MaterialTheme.colorScheme.surface
    val dividerColor = if (isLight) ConversationRowDivider else MaterialTheme.colorScheme.outlineVariant

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 4.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .width(MenuWidth)
            .background(containerColor)
            .padding(vertical = 6.dp),
    ) {
        items.forEachIndexed { index, item ->
            SelectionOverflowMenuRow(item = item, contentColor = if (isLight) SelectionMenuText else MaterialTheme.colorScheme.onSurface)
            if (index != items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    thickness = 0.5.dp,
                    color = dividerColor,
                )
            }
        }
    }
}

@Composable
private fun SelectionOverflowMenuRow(
    item: SelectionMenuItem,
    contentColor: Color,
) {
    val alpha = if (item.enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(enabled = item.enabled, onClick = item.onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = item.icon,
            contentDescription = null,
            tint = contentColor.copy(alpha = alpha),
            modifier = Modifier.height(20.dp).width(20.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = item.label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = contentColor.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
