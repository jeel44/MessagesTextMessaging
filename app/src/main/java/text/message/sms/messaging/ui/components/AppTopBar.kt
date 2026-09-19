package text.message.sms.messaging.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import text.message.sms.messaging.R
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor
import text.message.sms.messaging.ui.theme.ChatTopBarDivider

/** Whether the current theme's background is light enough that fixed near-black accents (icon
 * tint, divider) should follow the reference design's light-mode values rather than
 * theme-derived ones -- the same luminance gate Chat's and Search's own top bars already use
 * ([text.message.sms.messaging.ui.screens.chat.ChatScreen]'s `isLightChatTheme`, Search's
 * `isLightSurface`), generalized here so every top bar shares one check. */
@Composable
internal fun isLightAppBar(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

/**
 * The one back button every top bar in the app uses: [Icons.AutoMirrored.Filled.ArrowBack]
 * (RTL-safe), 24dp, default [IconButton] 48dp tap target, tint following [isLightAppBar] --
 * exactly what Chat's own top bar already drew inline before this got pulled out so every other
 * screen could match it pixel-for-pixel instead of drifting (Settings' raster icon with no tint,
 * Archived/ConversationInfo/Language's default M3 tint).
 */
@Composable
fun AppBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (isLightAppBar()) Color.Black else MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Shared top bar for every "sub-screen" hung off Settings/Home (Settings itself and its pickers,
 * Archived, ConversationInfo, New Message, Language) -- fixed 56dp height, [AppBackButton] by
 * default (New Message overrides [navigationIcon] to keep its Close/X, which signals "dismiss"
 * rather than "back"), title 18sp medium starting 4dp after the back button's tap target, and the
 * same 1dp divider Chat's own top bar draws underneath. Chat, Search and Home keep their own
 * special bars instead of this one.
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = { AppBackButton(onClick = onBack) },
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isLight = isLightAppBar()
    val contentColor = if (isLight) Color.Black else MaterialTheme.colorScheme.onSurface
    val dividerColor = if (isLight) ChatTopBarDivider else MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.background(screenSurfaceColor())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon()
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
    }
}
