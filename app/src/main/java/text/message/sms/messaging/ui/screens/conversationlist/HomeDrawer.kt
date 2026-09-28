package text.message.sms.messaging.ui.screens.conversationlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.R

internal const val HomeDrawerSheetTag = "home_drawer_sheet"
internal const val HomeDrawerVersionTag = "home_drawer_version"

/**
 * The inbox's side drawer: Archived, Blocked and Language (Settings' mode of it), with the app
 * name up top and the version pinned at the bottom. No ads, badges or counts.
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
                DrawerItem(Icons.Outlined.Archive, stringResource(R.string.screen_archived)) {
                    navigate(onArchivedClick)
                }
                DrawerItem(Icons.Outlined.Block, stringResource(R.string.screen_blocked)) {
                    navigate(onBlockedClick)
                }
                DrawerItem(Icons.Outlined.Language, stringResource(R.string.settings_language_title)) {
                    navigate(onLanguageClick)
                }
                Spacer(modifier = Modifier.weight(1f))
                HomeDrawerFooter()
            }
        },
    ) {
        content {
            if (enabled) scope.launch { drawerState.open() }
        }
    }
}

/** The brand mark (the same `ic_splash_logo` Splash shows -- the launcher icon's layers are still
 * the template placeholder) beside the app name. */
@Composable
private fun HomeDrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

/** Not tappable: "App version" with the value beside it, as Settings' About row shows it. Read
 * from [BuildConfig.VERSION_NAME], so it's always the version this build was stamped with. */
@Composable
private fun HomeDrawerFooter() {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HomeDrawerVersionTag)
                .padding(horizontal = 28.dp, vertical = 16.dp),
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
