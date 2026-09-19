package text.message.sms.messaging.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import text.message.sms.messaging.R
import text.message.sms.messaging.data.local.datastore.SimSendPreference
import text.message.sms.messaging.domain.model.SimInfo
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor

/**
 * Settings' "SIM for sending messages" row opens this: the three ways a dual-SIM device can pick
 * which SIM sends -- always SIM 1, always SIM 2, or ask every time. Only ever shown on a device
 * [sims] reports 2+ active SIMs for; see [SettingsScreen]'s gating.
 */
@Composable
internal fun SimSendPreferenceDialog(
    sims: List<SimInfo>,
    selected: SimSendPreference,
    onSelected: (SimSendPreference) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.settings_sim_for_sending_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                sims.sortedBy { it.slotIndex }.forEach { sim ->
                    val preference = when (sim.slotIndex) {
                        0 -> SimSendPreference.SLOT_0
                        1 -> SimSendPreference.SLOT_1
                        else -> return@forEach
                    }
                    SimPreferenceRow(
                        label = stringResource(
                            R.string.settings_sim_slot_summary,
                            sim.slotNumber,
                            sim.displayName.ifBlank { sim.carrierName },
                        ),
                        selected = selected == preference,
                        onClick = { onSelected(preference) },
                    )
                }
                SimPreferenceRow(
                    label = stringResource(R.string.settings_sim_ask_every_time),
                    selected = selected == SimSendPreference.ASK,
                    onClick = { onSelected(SimSendPreference.ASK) },
                    icon = null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun SimPreferenceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Filled.SimCard,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(text = label)
    }
}
