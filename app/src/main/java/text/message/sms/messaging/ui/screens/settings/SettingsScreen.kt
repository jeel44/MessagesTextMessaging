package text.message.sms.messaging.ui.screens.settings

import android.content.pm.PackageManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import text.message.sms.messaging.R

/**
 * App settings, laid out the way QKSMS does it: a flat scrollable list of rows grouped under
 * plain-text category headers, each row an icon + title (+ optional summary) + an optional
 * trailing widget -- a [Switch] for a toggle, or a short value string for a setting whose current
 * choice is worth showing inline. There is no backing preferences store yet, so the toggles below
 * hold their own [rememberSaveable] state rather than persisting anywhere; wiring them to real
 * behavior is a separate change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var darkModeEnabled by rememberSaveable { mutableStateOf(false) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }
    var deliveryReportsEnabled by rememberSaveable { mutableStateOf(false) }
    var quickReplyEnabled by rememberSaveable { mutableStateOf(true) }
    var autoBackupEnabled by rememberSaveable { mutableStateOf(false) }
    var wifiOnlyBackupEnabled by rememberSaveable { mutableStateOf(true) }

    val sections = listOf(
        SettingsSection(
            title = stringResource(R.string.settings_section_appearance),
            rows = listOf(
                SettingsRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.settings_theme_title),
                    summary = stringResource(R.string.settings_theme_summary),
                    trailing = SettingsTrailing.Value(stringResource(R.string.settings_theme_value)),
                ),
                SettingsRow(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(R.string.settings_dark_mode_title),
                    summary = stringResource(R.string.settings_dark_mode_summary),
                    trailing = SettingsTrailing.Toggle(darkModeEnabled) { darkModeEnabled = it },
                ),
                SettingsRow(
                    icon = Icons.Filled.FormatSize,
                    title = stringResource(R.string.settings_text_size_title),
                    trailing = SettingsTrailing.Value(stringResource(R.string.settings_text_size_value)),
                ),
            ),
        ),
        SettingsSection(
            title = stringResource(R.string.settings_section_general),
            rows = listOf(
                SettingsRow(
                    icon = Icons.Filled.Sms,
                    title = stringResource(R.string.settings_default_app_title),
                    summary = stringResource(R.string.settings_default_app_summary),
                ),
                SettingsRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_notifications_title),
                    summary = stringResource(R.string.settings_notifications_summary),
                    trailing = SettingsTrailing.Toggle(notificationsEnabled) { notificationsEnabled = it },
                ),
                SettingsRow(
                    icon = Icons.Filled.DoneAll,
                    title = stringResource(R.string.settings_delivery_reports_title),
                    summary = stringResource(R.string.settings_delivery_reports_summary),
                    trailing = SettingsTrailing.Toggle(deliveryReportsEnabled) { deliveryReportsEnabled = it },
                ),
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Reply,
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
                    icon = Icons.Filled.Backup,
                    title = stringResource(R.string.settings_auto_backup_title),
                    summary = stringResource(R.string.settings_auto_backup_summary),
                    trailing = SettingsTrailing.Toggle(autoBackupEnabled) { autoBackupEnabled = it },
                ),
                SettingsRow(
                    icon = Icons.Filled.CloudUpload,
                    title = stringResource(R.string.settings_backup_now_title),
                    summary = stringResource(R.string.settings_backup_now_summary),
                ),
                SettingsRow(
                    icon = Icons.Filled.CloudDownload,
                    title = stringResource(R.string.settings_restore_title),
                    summary = stringResource(R.string.settings_restore_summary),
                ),
                SettingsRow(
                    icon = Icons.Filled.Wifi,
                    title = stringResource(R.string.settings_wifi_only_title),
                    trailing = SettingsTrailing.Toggle(wifiOnlyBackupEnabled) { wifiOnlyBackupEnabled = it },
                ),
            ),
        ),
        SettingsSection(
            title = stringResource(R.string.settings_section_about),
            rows = listOf(
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.settings_app_version_title),
                    trailing = SettingsTrailing.Value(rememberAppVersionName()),
                ),
                SettingsRow(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.settings_rate_app_title),
                ),
                SettingsRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = stringResource(R.string.settings_privacy_policy_title),
                ),
                SettingsRow(
                    icon = Icons.Filled.Description,
                    title = stringResource(R.string.settings_licenses_title),
                ),
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SettingsList(
            sections = sections,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

private data class SettingsSection(val title: String, val rows: List<SettingsRow>)

private data class SettingsRow(
    val icon: ImageVector,
    val title: String,
    val summary: String? = null,
    val trailing: SettingsTrailing = SettingsTrailing.None,
)

private sealed interface SettingsTrailing {
    data object None : SettingsTrailing
    data class Value(val text: String) : SettingsTrailing
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = row.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(24.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
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
