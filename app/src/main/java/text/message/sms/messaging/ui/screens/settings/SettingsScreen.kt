package text.message.sms.messaging.ui.screens.settings

import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.R
import text.message.sms.messaging.data.local.datastore.SwipeAction
import text.message.sms.messaging.data.local.datastore.ThemeMode
import text.message.sms.messaging.ui.components.labelRes
import text.message.sms.messaging.ui.screens.onboarding.currentLanguageOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App settings, laid out the way QKSMS does it: a flat scrollable list of rows grouped under
 * plain-text category headers, each row an icon + title (+ optional summary) + an optional
 * trailing widget -- a [Switch] for a toggle, a short value string, or a small color [Swatch] for
 * a setting whose current choice is worth showing inline. Most toggles below still hold their own
 * [rememberSaveable] state rather than persisting anywhere -- wiring the rest to real behavior is
 * a separate change -- except Theme and Backup & Sync, which are backed by [SettingsViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }
    var deliveryReportsEnabled by rememberSaveable { mutableStateOf(false) }
    var quickReplyEnabled by rememberSaveable { mutableStateOf(true) }
    var autoBackupEnabled by rememberSaveable { mutableStateOf(false) }
    var wifiOnlyBackupEnabled by rememberSaveable { mutableStateOf(true) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showSwipeActionPicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val lastBackupAtMillis by viewModel.lastBackupAtMillis.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val swipeActionPreference by viewModel.swipeActionPreference.collectAsStateWithLifecycle()
    val backupBusy = operation != BackupOperation.IDLE

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportBackup(it.toString()) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importBackup(it.toString()) } }

    LaunchedEffect(event) {
        val current = event ?: return@LaunchedEffect
        val message = when (current) {
            is BackupEvent.ExportSucceeded ->
                context.getString(R.string.settings_backup_export_success, current.messageCount)
            is BackupEvent.ImportSucceeded ->
                context.getString(R.string.settings_backup_import_success, current.messageCount)
            BackupEvent.NotDefaultSmsApp -> context.getString(R.string.settings_backup_not_default_app)
            BackupEvent.DestinationUnavailable -> context.getString(R.string.settings_backup_destination_unavailable)
            is BackupEvent.Error -> context.getString(R.string.settings_backup_error, current.message)
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeBackupEvent()
    }

    val sections = listOf(
        SettingsSection(
            title = stringResource(R.string.settings_section_appearance),
            rows = listOf(
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_theme),
                    title = stringResource(R.string.settings_theme_title),
                    summary = stringResource(
                        R.string.settings_theme_summary,
                        stringResource(themePreference.mode.labelRes()),
                        stringResource(
                            if (themePreference.accentColor == null) {
                                R.string.theme_picker_color_default
                            } else {
                                R.string.theme_picker_color_custom
                            },
                        ),
                    ),
                    trailing = SettingsTrailing.Swatch(MaterialTheme.colorScheme.primary),
                    onClick = { showThemePicker = true },
                ),
                SettingsRow(
                    icon = SettingsIcon.Vector(Icons.Filled.FormatSize),
                    title = stringResource(R.string.settings_text_size_title),
                    trailing = SettingsTrailing.Value(stringResource(R.string.settings_text_size_value)),
                ),
            ),
        ),
        SettingsSection(
            title = stringResource(R.string.settings_section_general),
            rows = listOf(
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_default_sms),
                    title = stringResource(R.string.settings_default_app_title),
                    summary = stringResource(R.string.settings_default_app_summary),
                ),
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_language),
                    title = stringResource(R.string.settings_language_title),
                    summary = languageSummary(),
                    onClick = onLanguageClick,
                ),
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_swipe_actions),
                    title = stringResource(R.string.settings_swipe_actions_title),
                    summary = stringResource(
                        R.string.settings_swipe_actions_summary,
                        stringResource(swipeActionPreference.startToEnd.labelRes()),
                        stringResource(swipeActionPreference.endToStart.labelRes()),
                    ),
                    onClick = { showSwipeActionPicker = true },
                ),
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_notifications),
                    title = stringResource(R.string.settings_notifications_title),
                    summary = stringResource(R.string.settings_notifications_summary),
                    trailing = SettingsTrailing.Toggle(notificationsEnabled) { notificationsEnabled = it },
                ),
                SettingsRow(
                    icon = SettingsIcon.Vector(Icons.Filled.DoneAll),
                    title = stringResource(R.string.settings_delivery_reports_title),
                    summary = stringResource(R.string.settings_delivery_reports_summary),
                    trailing = SettingsTrailing.Toggle(deliveryReportsEnabled) { deliveryReportsEnabled = it },
                ),
                SettingsRow(
                    icon = SettingsIcon.Vector(Icons.AutoMirrored.Filled.Reply),
                    title = stringResource(R.string.settings_quick_reply_title),
                    summary = stringResource(R.string.settings_quick_reply_summary),
                    trailing = SettingsTrailing.Toggle(quickReplyEnabled) { quickReplyEnabled = it },
                ),
            ),
        ),
        SettingsSection(
            title = stringResource(R.string.settings_section_backup_sync),
            rows = listOf(
                SettingsRow(
                    icon = SettingsIcon.Vector(Icons.Filled.Backup),
                    title = stringResource(R.string.settings_auto_backup_title),
                    summary = stringResource(R.string.settings_auto_backup_summary),
                    trailing = SettingsTrailing.Toggle(autoBackupEnabled) { autoBackupEnabled = it },
                ),
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_backup),
                    title = stringResource(R.string.settings_backup_now_title),
                    summary = lastBackupAtMillis?.let { stringResource(R.string.settings_last_backup_at, formatBackupTimestamp(it)) }
                        ?: stringResource(R.string.settings_last_backup_never),
                    enabled = !backupBusy,
                    onClick = { exportLauncher.launch(defaultBackupFileName()) },
                ),
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_restore),
                    title = stringResource(R.string.settings_restore_title),
                    summary = stringResource(R.string.settings_restore_summary),
                    enabled = !backupBusy,
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                ),
                SettingsRow(
                    icon = SettingsIcon.Vector(Icons.Filled.Wifi),
                    title = stringResource(R.string.settings_wifi_only_title),
                    trailing = SettingsTrailing.Toggle(wifiOnlyBackupEnabled) { wifiOnlyBackupEnabled = it },
                ),
            ),
        ),
        SettingsSection(
            title = stringResource(R.string.settings_section_about),
            rows = listOf(
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_info),
                    title = stringResource(R.string.settings_app_version_title),
                    trailing = SettingsTrailing.Value(rememberAppVersionName()),
                ),
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_rate),
                    title = stringResource(R.string.settings_rate_app_title),
                ),
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_privacy),
                    title = stringResource(R.string.settings_privacy_policy_title),
                ),
                SettingsRow(
                    icon = SettingsIcon.Vector(Icons.Filled.Description),
                    title = stringResource(R.string.settings_licenses_title),
                ),
                SettingsRow(
                    icon = SettingsIcon.Drawable(R.drawable.ic_feedback),
                    title = stringResource(R.string.settings_help_feedback_title),
                ),
            ),
        ),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
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
            if (backupBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(
                        if (operation == BackupOperation.EXPORTING) {
                            R.string.settings_backup_exporting
                        } else {
                            R.string.settings_backup_importing
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            SettingsList(sections = sections, modifier = Modifier.fillMaxSize())
        }
    }

    if (showThemePicker) {
        ThemePickerDialog(
            currentMode = themePreference.mode,
            currentAccentColor = themePreference.accentColor,
            onModeSelected = viewModel::setThemeMode,
            onAccentColorSelected = viewModel::setAccentColor,
            onDismiss = { showThemePicker = false },
        )
    }

    if (showSwipeActionPicker) {
        SwipeActionPickerDialog(
            preference = swipeActionPreference,
            onStartToEndSelected = viewModel::setSwipeStartToEnd,
            onEndToStartSelected = viewModel::setSwipeEndToStart,
            onDismiss = { showSwipeActionPicker = false },
        )
    }
}

/** Not reactive -- [text.message.sms.messaging.ui.screens.onboarding.currentLanguageOption] is a
 * plain synchronous read of [androidx.appcompat.app.AppCompatDelegate]'s own state, not a Flow.
 * That's fine here: navigating to the Language screen and back already recomposes this screen
 * fresh, so the summary is correct by the time it's visible again. */
@Composable
private fun languageSummary(): String = currentLanguageOption().displayName

private data class SettingsSection(val title: String, val rows: List<SettingsRow>)

private data class SettingsRow(
    val icon: SettingsIcon,
    val title: String,
    val summary: String? = null,
    val trailing: SettingsTrailing = SettingsTrailing.None,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

/** Row icons are a mix of Material [ImageVector]s (rows not covered by the Flaticon set yet) and
 * raster [Drawable] icons ([DrawableRes] webp assets) -- the latter need [Icon]'s `painter`
 * overload instead of `imageVector` so the same tint logic still applies to both. */
private sealed interface SettingsIcon {
    data class Vector(val imageVector: ImageVector) : SettingsIcon
    data class Drawable(@DrawableRes val resId: Int) : SettingsIcon
}

private sealed interface SettingsTrailing {
    data object None : SettingsTrailing
    data class Value(val text: String) : SettingsTrailing
    data class Swatch(val color: Color) : SettingsTrailing
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : SettingsTrailing
}

@Composable
private fun SettingsList(sections: List<SettingsSection>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        sections.forEachIndexed { index, section ->
            item(key = "header_${section.title}") {
                SettingsSectionHeader(title = section.title)
            }
            items(section.rows, key = { "${section.title}_${it.title}" }) { row ->
                SettingsRowItem(row = row)
            }
            if (index != sections.lastIndex) {
                item(key = "divider_${section.title}") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRowItem(row: SettingsRow, modifier: Modifier = Modifier) {
    val contentAlpha = if (row.enabled) 1f else 0.38f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { rowModifier ->
                if (row.onClick != null) {
                    rowModifier.clickable(enabled = row.enabled, onClick = row.onClick)
                } else {
                    rowModifier
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
        when (val icon = row.icon) {
            is SettingsIcon.Vector -> Icon(
                imageVector = icon.imageVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            is SettingsIcon.Drawable -> Icon(
                painter = painterResource(icon.resId),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            if (row.summary != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = row.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val trailing = row.trailing) {
            is SettingsTrailing.Toggle -> {
                Spacer(modifier = Modifier.width(16.dp))
                Switch(checked = trailing.checked, onCheckedChange = trailing.onCheckedChange)
            }
            is SettingsTrailing.Value -> {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = trailing.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            is SettingsTrailing.Swatch -> {
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(trailing.color)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
            SettingsTrailing.None -> Unit
        }
    }
}

@Composable
private fun rememberAppVersionName(): String {
    val context = LocalContext.current
    return remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }
}

@Composable
private fun formatBackupTimestamp(timestampMillis: Long): String {
    val context = LocalContext.current
    return remember(timestampMillis) {
        val date = Date(timestampMillis)
        "${DateFormat.getMediumDateFormat(context).format(date)} ${DateFormat.getTimeFormat(context).format(date)}"
    }
}

/** Suggested name for [androidx.activity.result.contract.ActivityResultContracts.CreateDocument]
 * -- timestamped so repeated backups don't collide in whatever folder the user picks. */
private fun defaultBackupFileName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "messages_backup_$timestamp.json"
}
