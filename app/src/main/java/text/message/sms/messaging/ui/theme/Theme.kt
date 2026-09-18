package text.message.sms.messaging.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Root theme every screen in the app is wrapped in (applied once, in `MainActivity`, fed live
 * from [text.message.sms.messaging.data.local.datastore.ThemePreferences] so a change in
 * Settings' theme picker recolors every screen immediately, no restart).
 *
 * Dark/light follows the system setting by default; [darkTheme] is exposed as a parameter
 * rather than hard-coded to [isSystemInDarkTheme] so the theme picker's light/dark/system choice
 * can force either mode regardless of the system setting.
 *
 * [accentColor] is the user's picked accent from the theme picker, `null` meaning "use the
 * default coloring" -- which is the hand-authored blue schemes in [LightColors]/[DarkColors],
 * matching Google Messages' own default accent.
 * [dynamicColor] (Material-You, wallpaper-derived colors via [dynamicLightColorScheme]/
 * [dynamicDarkColorScheme] on Android 12+) defaults to `false` since there is no theme-picker
 * option that opts into it yet -- turning it on unconditionally made every screen's background
 * silently follow the device wallpaper's tint (e.g. a lavender cast from a purple-leaning
 * wallpaper) instead of the app's own designed near-white background, with no way for the user
 * to tell why. Pass `true` explicitly once a picker option exists for it.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamicColor =
        accentColor == null && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        accentColor != null -> accentColorScheme(accentColor, darkTheme)
        supportsDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        supportsDynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MessagingTypography,
        shapes = MessagingShapes,
        content = content,
    )
}
