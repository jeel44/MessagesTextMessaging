package text.message.sms.messaging.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import text.message.sms.messaging.R
import text.message.sms.messaging.data.local.datastore.SwipeAction
import text.message.sms.messaging.data.local.datastore.SwipeActionPreference
import text.message.sms.messaging.ui.components.icon
import text.message.sms.messaging.ui.components.labelRes
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor

/**
 * Settings' "Swipe actions" row opens this: lets the user rebind what a right-swipe and a
 * left-swipe do on the conversation list, instead of the hard-coded archive/delete this app
 * shipped with first -- see [SwipeActionPreference] for the defaults.
 */
@Composable
internal fun SwipeActionPickerDialog(
    preference: SwipeActionPreference,
    onStartToEndSelected: (SwipeAction) -> Unit,
    onEndToStartSelected: (SwipeAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.settings_swipe_actions_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SwipeActionSection(
                    header = stringResource(R.string.settings_swipe_right_header),
                    selected = preference.startToEnd,
                    onSelected = onStartToEndSelected,
                )
                Spacer(modifier = Modifier.height(16.dp))
                SwipeActionSection(
                    header = stringResource(R.string.settings_swipe_left_header),
                    selected = preference.endToStart,
                    onSelected = onEndToStartSelected,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun SwipeActionSection(
    header: String,
    selected: SwipeAction,
    onSelected: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = remember { SwipeAction.entries }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = header,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        actions.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected == action, onClick = { onSelected(action) }, role = Role.RadioButton)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == action, onClick = null)
                Spacer(modifier = Modifier.width(12.dp))
                action.icon()?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(text = stringResource(action.labelRes()))
            }
        }
    }
}
