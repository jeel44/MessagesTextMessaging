package text.message.sms.messaging.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Full Material 3 color schemes for the non-dynamic fallback (API < 31, or dynamic color turned
 * off). Seeded from Google Messages' own default accent -- #0B57D0 in light, #A8C7FA in dark --
 * with the rest of each tonal palette hand-derived from that seed following the standard M3
 * "blue" baseline scheme, the same one `dynamicLightColorScheme`/`dynamicDarkColorScheme` would
 * converge on for a blue-leaning wallpaper. Every `onX` role is chosen to sit on its `X`
 * counterpart at the M3-guaranteed-legible tone distance, so no pairing here needs a contrast
 * check beyond "did I use the matching role" -- see [AppTheme] for how that gets used on
 * Splash's full-bleed primary background.
 *
 * `internal` rather than `private`: [AccentColorScheme] reuses each swatch's lightness (borrowing
 * only the hue/saturation of a user-picked accent) to build a custom-accent scheme without
 * needing its own hand-tuned contrast pairs -- see that file.
 */
internal val SeedBlueLight = Color(0xFF0B57D0)
internal val SeedBluePrimaryContainerLight = Color(0xFFD3E3FD)
internal val SeedBlueOnPrimaryContainerLight = Color(0xFF041E49)

internal val SeedBlueDark = Color(0xFFA8C7FA)
internal val SeedBlueOnPrimaryDark = Color(0xFF062E6F)
internal val SeedBluePrimaryContainerDark = Color(0xFF0842A0)
internal val SeedBlueOnPrimaryContainerDark = Color(0xFFD3E3FD)

/** Light theme's flat neutral gray for elevated cards/inputs that still need contrast against a
 * pure-white screen (dialog swatch backdrops, media-grid placeholder tiles, attachment-sheet
 * option chips) -- [LightColors]' `surfaceContainerHigh`/`surfaceContainerHighest`, the two
 * container tones deliberately left non-white below. */
internal val SurfaceContainerGray = Color(0xFFF1F3F4)

internal val LightColors = lightColorScheme(
    primary = SeedBlueLight,
    onPrimary = Color.White,
    primaryContainer = SeedBluePrimaryContainerLight,
    onPrimaryContainer = SeedBlueOnPrimaryContainerLight,
    inversePrimary = SeedBlueDark,

    secondary = Color(0xFF565F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),

    tertiary = Color(0xFF6B5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3DAFF),
    onTertiaryContainer = Color(0xFF251431),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    background = Color.White,
    onBackground = Color(0xFF1A1B20),
    surface = Color.White,
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = SurfaceContainerGray,
    surfaceContainerHighest = SurfaceContainerGray,
    surfaceBright = Color.White,
    surfaceDim = Color.White,

    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color.Black,
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
)

/** Thin border around the Home top bar's search pill -- a plain hairline, not a `outlineVariant`
 * token, since the pill must read the same subtle gray in both themes rather than following
 * whatever the current accent/dynamic scheme derives for that role. */
internal val SearchBarBorder = Color(0xFFDADCE0)

/** Selected filter chip fill -- a light, theme-independent blue tint paired with a matching
 * outline (see `ConversationFilterRow`'s chip colors in ConversationListScreen.kt). Deliberately
 * not `primaryContainer`: the reference design's chip fill must look the same regardless of the
 * user's picked accent color or dynamic color. */
internal val FilterChipSelectedContainer = Color(0xFFE3ECFB)

/** Selected filter chip outline -- a fixed blue, same reasoning as [FilterChipSelectedContainer]:
 * must match the reference design exactly rather than following `colorScheme.primary`, which is
 * both themeable (custom accent / dynamic color) and a visibly different blue from this hue. */
internal val FilterChipSelectedBorder = Color(0xFF1A5FD0)

/** Unselected filter chip fill -- deliberately not a `surfaceContainer*` token: those default to
 * M3's baseline (lavender-tinted) values wherever this file leaves them unset, which is exactly
 * the bug the Home TopAppBar's own container-color fix worked around. */
internal val FilterChipUnselectedContainer = Color(0xFFF1F3F4)

/** Neutral label/icon color for an unselected filter chip -- same reasoning as
 * [FilterChipUnselectedContainer]: must read the same regardless of the current accent. */
internal val FilterChipContentGray = Color(0xFF5F6368)

/** Fixed, theme-independent blue for the Home screen's compose FAB -- matches the reference
 * design exactly rather than following the user's picked accent, the same reasoning as the swipe
 * action colors in ConversationListScreen.kt. */
internal val ConversationFabBlue = Color(0xFF2F6BFF)

/** Divider between Home's conversation rows -- a hairline, deliberately not `outlineVariant`
 * (`0xFFC4C6D0` in [LightColors]): that token reads noticeably darker and cooler/blue-tinted than
 * the reference design's very light gray divider. */
internal val ConversationRowDivider = Color(0xFFE6E6EA)

/** Centered date/time separator text on the Chat screen, and the small status caption under the
 * last sent bubble ("Sending...", "Failed. Tap to retry") -- a flat gray, not `onSurfaceVariant`,
 * to match the reference design exactly regardless of accent/dynamic color. Light theme only; dark
 * theme uses `colorScheme.onSurfaceVariant` instead -- see ChatScreen.kt/MessageBubble.kt's own
 * light/dark gating. */
internal val ChatDateSeparatorGray = Color(0xFF6B6F76)

/** Sent message bubble fill on the Chat screen, light theme only. */
internal val ChatBubbleSentContainer = Color(0xFFE8EEFB)

/** Bubble text color for both sent and received bubbles on the Chat screen, light theme only. */
internal val ChatBubbleContentDark = Color(0xFF1B1B1F)

/** Flat light-gray fill shared by the Chat screen's received bubble, the composer's "+" button,
 * and the composer's rounded text field -- light theme only. */
internal val ChatNeutralFill = Color(0xFFF1F3F4)

/** Composer placeholder/hint text and leading-icon tint on the Chat screen, light theme only. */
internal val ChatHintGray = Color(0xFF9AA0A6)

/** Send button fill once the composer has text -- light theme only. */
internal val ChatSendButtonEnabled = Color(0xFF1A5FD0)

/** Send button fill while the composer is empty (still shows blue, just muted) -- light theme
 * only. */
internal val ChatSendButtonDisabled = Color(0xFFA9C4F5)

/** Chat top bar avatar background for a saved contact with no synced photo -- a generic
 * silhouette on gray, matching [text.message.sms.messaging.ui.screens.conversationlist
 * .ConversationAvatar]'s same reasoning for the inbox row avatar. Light theme only. */
internal val ChatAvatarPlaceholderGray = Color(0xFFBDBDBD)

/** Chat top bar avatar background for an unresolved sender (bank/OTP/business sender ID) -- shown
 * with its first letter/digit rather than a silhouette, so it reads as "not a saved contact" at a
 * glance. Light theme only. */
internal val ChatAvatarAccentBlue = Color(0xFF3B7DED)

/** Hairline divider along the Chat top bar's bottom edge -- light theme only. */
internal val ChatTopBarDivider = Color(0xFFE6E6EA)

/** Received message bubble fill on the Chat screen, light theme only -- deliberately a distinct,
 * slightly darker gray from [ChatNeutralFill] (the composer's "+"/text-field fill), matching the
 * reference design's received bubble exactly rather than reusing that neutral tone. */
internal val ChatReceivedBubble = Color(0xFFEFEDEE)

/** Tappable link color (URL/phone/email) inside a message bubble, both sent and received, in
 * light theme -- paired with [androidx.compose.ui.text.style.TextDecoration.Underline] rather than
 * `colorScheme.primary`, since the reference design's link blue is a fixed hue independent of the
 * user's picked accent or dynamic color. */
internal val ChatLinkColor = Color(0xFF3A6FD8)

/** Fill for the non-personal chat's "this SMS is secure" notice card -- light theme only. */
internal val ChatSecurityCardBackground = Color(0xFFE4EAF6)

/** Body text color inside the non-personal chat's security notice card -- light theme only. */
internal val ChatSecurityCardText = Color(0xFF3C4043)

/** "Can't reply to this short code" label color in non-personal chat mode -- light theme only. */
internal val ChatCantReplyGray = Color(0xFF5F6368)

/** Outline for the non-personal chat's "Copy OTP" outlined button -- light theme only. */
internal val ChatOtpCopyBorderGray = Color(0xFF7A7F87)

/** Selection-mode accent blue for Chat's selection top bar (close icon, count, action icons) --
 * an alias of [ChatSendButtonEnabled] rather than a duplicate hex, so a future change to the send
 * button's color never silently changes selection styling too. Light theme only. */
internal val SelectionAccentBlue = ChatSendButtonEnabled

/** Dark-theme counterpart of [SelectionAccentBlue] -- a lighter blue for legibility on a dark
 * surface, matching the reference design's dark-mode accent. */
internal val SelectionAccentBlueDark = Color(0xFF8AB4F8)

/** Solid fill for a selected bubble (sent or received) during Chat's multi-select mode -- an
 * alias of [ConversationFabBlue] rather than a duplicate hex, so a future change to the Home FAB
 * color never silently changes selection styling too. Same fill in both themes. */
internal val ChatBubbleSelectedBlue = ConversationFabBlue

/** Overlay drawn over a selected image attachment during Chat's multi-select mode -- a solid
 * fill can't be used over an image, so this is [ChatBubbleSelectedBlue] at reduced alpha instead. */
internal val ChatSelectedImageOverlay = ConversationFabBlue.copy(alpha = 0.4f)

/** Icon tint for [text.message.sms.messaging.ui.components.SelectionOverflowMenu]'s rows, shared
 * by Chat's and Home's selection top bars -- a near-black, deliberately not a gray, so every icon
 * in the popup reads the same weight as its label. Light theme only; dark theme uses
 * `colorScheme.onSurface` instead. */
internal val SelectionMenuIconDark = Color(0xFF1B1B1F)

/** Label color for [text.message.sms.messaging.ui.components.SelectionOverflowMenu]'s rows -- an
 * alias of [SelectionMenuIconDark] (same hex) rather than a duplicate constant, so icon and text
 * can never silently drift apart in tone. Light theme only; dark theme uses
 * `colorScheme.onSurface` instead. */
internal val SelectionMenuText = SelectionMenuIconDark

/** Selected-row tint for Home's multi-select mode ([text.message.sms.messaging.ui.screens
 * .conversationlist.ConversationListScreen]) -- a light, theme-independent blue, matching the
 * reference design's selection highlight. Light theme only; dark theme uses a
 * `primaryContainer`-based tint instead, see that screen's own light/dark gating. */
internal val ConversationRowSelected = Color(0xFFE9EEFA)

/** Solid fill for a selected row's avatar-replacement circle in Home's multi-select mode -- an
 * alias of [text.message.sms.messaging.ui.theme.ChatAvatarAccentBlue]'s sibling blue rather than a
 * duplicate hex family; matches the reference design's checkmark-circle blue exactly. Same fill in
 * both themes. */
internal val ConversationSelectedAvatar = Color(0xFF1E9BF0)

internal val DarkColors = darkColorScheme(
    primary = SeedBlueDark,
    onPrimary = SeedBlueOnPrimaryDark,
    primaryContainer = SeedBluePrimaryContainerDark,
    onPrimaryContainer = SeedBlueOnPrimaryContainerDark,
    inversePrimary = SeedBlueLight,

    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),

    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF3DAFF),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),

    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),

    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    scrim = Color.Black,
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3033),
)
