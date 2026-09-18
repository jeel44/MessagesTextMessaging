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

    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474E),

    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color.Black,
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
)

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
