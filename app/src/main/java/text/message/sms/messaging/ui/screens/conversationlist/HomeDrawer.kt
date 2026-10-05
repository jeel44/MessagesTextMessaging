package text.message.sms.messaging.ui.screens.conversationlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.R
import text.message.sms.messaging.analytics.Analytics
import text.message.sms.messaging.analytics.DrawerItem

internal const val HomeDrawerSheetTag = "home_drawer_sheet"
internal const val HomeDrawerVersionTag = "home_drawer_version"
internal const val HomeDrawerHeaderTag = "home_drawer_header"
internal const val HomeDrawerChevronTag = "home_drawer_chevron"

/**
 * The inbox's side drawer: Archived, Blocked, Scheduled and Language (Settings' mode of it), with an
 * app-name card up top and the version pinned at the bottom. No ads, badges or counts.
 *
 * [enabled] false (the inbox's multi-select mode) makes [content]'s `openDrawer` a no-op and
 * closes the drawer if it's somehow open, so selection and the drawer never overlap.
 *
 * It only opens from the hamburger, never an edge swipe: dragging open would compete with the
 * inbox rows' own horizontal swipe actions (archive-right by default) and, on gesture
 * navigation, with the system back gesture along that same left edge. Once open, a drag can still
 * close it. System back closes it too.
 *
 * Tapping an item snaps the drawer shut before navigating, so the inbox is back to its plain state
 * when the user returns.
 */
@Composable
internal fun HomeDrawer(
    drawerState: DrawerState,
    enabled: Boolean,
    onArchivedClick: () -> Unit,
    onBlockedClick: () -> Unit,
    onScheduledClick: () -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (openDrawer: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(enabled) {
        if (!enabled) drawerState.snapTo(DrawerValue.Closed)
    }
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    fun navigate(action: () -> Unit) {
        scope.launch { drawerState.snapTo(DrawerValue.Closed) }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        gesturesEnabled = enabled && drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.testTag(HomeDrawerSheetTag)) {
                HomeDrawerHeader()
                // Scrolls only when it must (huge font scale, short screen), so the footer stays put.
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    DrawerItem(Icons.Outlined.Archive, stringResource(R.string.screen_archived)) {
                        Analytics.drawerItemOpened(DrawerItem.ARCHIVED)
                        navigate(onArchivedClick)
                    }
                    DrawerItemDivider()
                    DrawerItem(Icons.Outlined.Block, stringResource(R.string.screen_blocked)) {
                        Analytics.drawerItemOpened(DrawerItem.BLOCKED)
                        navigate(onBlockedClick)
                    }
                    DrawerItemDivider()
                    DrawerItem(Icons.Outlined.Schedule, stringResource(R.string.drawer_scheduled)) {
                        Analytics.drawerItemOpened(DrawerItem.SCHEDULED)
                        navigate(onScheduledClick)
                    }
                    DrawerItemDivider()
                    DrawerItem(Icons.Outlined.Language, stringResource(R.string.settings_language_title)) {
                        Analytics.drawerItemOpened(DrawerItem.LANGUAGE)
                        navigate(onLanguageClick)
                    }
                }
                HomeDrawerFooter()
            }
        },
    ) {
        content {
            if (enabled) scope.launch { drawerState.open() }
        }
    }
}

/**
 * A soft card in the accent container color: the brand mark (the same `ic_splash_logo` Splash
 * shows -- the launcher icon's layers are still the template placeholder) beside the app name and
 * a subtitle. The name is `app_launcher_name`, untranslated "Messages", not `app_name`, which is
 * "#Messages" and translated per locale.
 *
 * The sheet's default window insets already clear the status bar; the extra top margin is on top of
 * that.
 */
@Composable
private fun HomeDrawerHeader() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 12.dp)
            .testTag(HomeDrawerHeaderTag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_launcher_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                )
                Text(
                    text = stringResource(R.string.drawer_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private val DrawerItemHorizontalPadding = 16.dp
private val DrawerItemIconContainerSize = 44.dp
private val DrawerItemIconGap = 16.dp

/** A full-width row: the icon in an accent circle, the label, and a chevron that flips in RTL.
 * Never selected, no badges. */
@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(horizontal = DrawerItemHorizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(DrawerItemIconContainerSize)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(DrawerItemIconGap))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .testTag(HomeDrawerChevronTag),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

/** A hairline between rows, starting where the label does so it reads as one list. */
@Composable
private fun DrawerItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = DrawerItemHorizontalPadding + DrawerItemIconContainerSize + DrawerItemIconGap),
        thickness = Dp.Hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Not tappable: "App version" with the value beside it, as Settings' About row shows it. Read
 * from [BuildConfig.VERSION_NAME], so it's always the version this build was stamped with. The sheet's
 * default window insets keep it above the navigation bar. */
@Composable
private fun HomeDrawerFooter() {
    Column {
        HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HomeDrawerVersionTag)
                .padding(horizontal = DrawerItemHorizontalPadding, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_app_version_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
