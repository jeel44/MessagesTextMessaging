package text.message.sms.messaging.ui.screens.chat

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.SchedulingRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

internal const val ScheduleDialogTestTag = "schedule_dialog"
internal const val ScheduledComposerBarTestTag = "scheduled_composer_bar"

/** The dialog's two quick picks, from [nowMillis]: an hour from now (to the minute), and 9:00
 * tomorrow. Pure, so the rules are unit-testable without a clock. */
internal data class QuickScheduleOptions(val inOneHourMillis: Long, val tomorrowMorningMillis: Long)

internal fun quickScheduleOptions(nowMillis: Long, zone: ZoneId): QuickScheduleOptions {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val inOneHour = now.plusHours(1).withSecond(0).withNano(0)
    val tomorrowMorning = now.toLocalDate().plusDays(1).atTime(TomorrowMorning).atZone(zone)
    return QuickScheduleOptions(inOneHour.toInstant().toEpochMilli(), tomorrowMorning.toInstant().toEpochMilli())
}

private val TomorrowMorning: LocalTime = LocalTime.of(9, 0)

/** "Tue, Oct 6, 9:00 AM" in the device's locale and 12/24-hour setting. */
internal fun formatScheduleTime(context: Context, millis: Long): String = DateUtils.formatDateTime(
    context,
    millis,
    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
        DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME,
)

private enum class ScheduleStep { QUICK, DATE, TIME }

/**
 * Picks a send time: quick options first ("In 1 hour", "Tomorrow, 9:00 AM", "Pick date and
 * time"), then -- for the last -- a Material 3 date picker followed by a time picker. Only calls
 * [onConfirm] with a time at least [SchedulingRules.MIN_LEAD_MILLIS] ahead of [nowMillis]; a
 * picked time too soon shows an error on the time step instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleMessageDialog(
    onDismiss: () -> Unit,
    onConfirm: (sendAtMillis: Long) -> Unit,
    nowMillis: Long = remember { System.currentTimeMillis() },
    zone: ZoneId = remember { ZoneId.systemDefault() },
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(ScheduleStep.QUICK) }
    var pickedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var tooSoon by rememberSaveable { mutableStateOf(false) }
    val options = remember(nowMillis, zone) { quickScheduleOptions(nowMillis, zone) }
    val today = remember(nowMillis, zone) { Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate() }

    when (step) {
        ScheduleStep.QUICK -> AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag(ScheduleDialogTestTag),
            title = { Text(stringResource(R.string.schedule_dialog_title)) },
            text = {
                Column {
                    QuickOption(
                        icon = Icons.Filled.Schedule,
                        label = stringResource(R.string.schedule_option_in_one_hour),
                        detail = formatScheduleTime(context, options.inOneHourMillis),
                        onClick = { onConfirm(options.inOneHourMillis) },
                    )
                    QuickOption(
                        icon = Icons.Outlined.WbSunny,
                        label = stringResource(
                            R.string.schedule_option_tomorrow,
                            DateFormat.getTimeFormat(context).format(java.util.Date(options.tomorrowMorningMillis)),
                        ),
                        detail = null,
                        onClick = { onConfirm(options.tomorrowMorningMillis) },
                    )
                    QuickOption(
                        icon = Icons.Outlined.CalendarMonth,
                        label = stringResource(R.string.schedule_option_pick),
                        detail = null,
                        onClick = { step = ScheduleStep.DATE },
                    )
                    ScheduleHint()
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )

        ScheduleStep.DATE -> {
            val todayUtcMillis = remember(today) { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            val dateState = rememberDatePickerState(
                initialSelectedDateMillis = pickedDate ?: todayUtcMillis,
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
                    override fun isSelectableYear(year: Int): Boolean = year >= today.year
                },
            )
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickedDate = dateState.selectedDateMillis
                            step = ScheduleStep.TIME
                        },
                        enabled = dateState.selectedDateMillis != null,
                    ) { Text(stringResource(R.string.schedule_dialog_next)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
            ) {
                DatePicker(state = dateState)
            }
        }

        ScheduleStep.TIME -> {
            val startTime = remember { Instant.ofEpochMilli(options.inOneHourMillis).atZone(zone) }
            val timeState = rememberTimePickerState(
                initialHour = startTime.hour,
                initialMinute = startTime.minute,
                is24Hour = DateFormat.is24HourFormat(context),
            )
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.schedule_dialog_title)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TimePicker(state = timeState)
                        if (tooSoon) {
                            Text(
                                text = stringResource(R.string.schedule_error_too_soon),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        ScheduleHint()
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val date = Instant.ofEpochMilli(pickedDate ?: return@TextButton)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            val sendAt = combine(date, timeState.hour, timeState.minute, zone)
                            if (sendAt - System.currentTimeMillis() < SchedulingRules.MIN_LEAD_MILLIS) {
                                tooSoon = true
                            } else {
                                onConfirm(sendAt)
                            }
                        },
                    ) { Text(stringResource(R.string.schedule_dialog_set)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

private fun combine(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
    ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant().toEpochMilli()

@Composable
private fun QuickOption(icon: ImageVector, label: String, detail: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = detail?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ScheduleHint() {
    Text(
        text = stringResource(R.string.schedule_dialog_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/** Above the composer once a send time is chosen: "Scheduled for <time>" with a clear button. */
@Composable
internal fun ScheduledComposerBar(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .testTag(ScheduledComposerBarTestTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.composer_clear_schedule))
            }
        }
    }
}

/** Tapping a scheduled bubble: Edit (pending only), Send now, Cancel. */
@Composable
internal fun ScheduledMessageActionsDialog(
    canEdit: Boolean,
    onEdit: () -> Unit,
    onSendNow: () -> Unit,
    onCancelMessage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scheduled_actions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canEdit) QuickOption(Icons.Outlined.Edit, stringResource(R.string.scheduled_action_edit), null, onEdit)
                QuickOption(Icons.AutoMirrored.Filled.Send, stringResource(R.string.scheduled_action_send_now), null, onSendNow)
                QuickOption(Icons.Outlined.DeleteOutline, stringResource(R.string.scheduled_action_cancel), null, onCancelMessage)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Edits a pending scheduled message's text and time; saved in place by the caller. */
@Composable
internal fun EditScheduledMessageDialog(
    initialBody: String,
    initialSendAtMillis: Long,
    onSave: (body: String, sendAtMillis: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var body by rememberSaveable { mutableStateOf(initialBody) }
    var sendAt by rememberSaveable { mutableStateOf(initialSendAtMillis) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    if (pickingTime) {
        ScheduleMessageDialog(
            onDismiss = { pickingTime = false },
            onConfirm = {
                sendAt = it
                pickingTime = false
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scheduled_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = body, onValueChange = { body = it }, maxLines = 6, modifier = Modifier.fillMaxWidth())
                ListItem(
                    headlineContent = { Text(formatScheduleTime(context, sendAt)) },
                    supportingContent = { Text(stringResource(R.string.scheduled_edit_change_time)) },
                    leadingContent = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.clickable { pickingTime = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(body.trim(), sendAt) }, enabled = body.isNotBlank()) {
                Text(stringResource(R.string.scheduled_edit_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
