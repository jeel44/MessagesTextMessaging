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
 * default coloring". When set, it takes over from Material You entirely -- see
 * [accentColorScheme] -- since a color the user explicitly picked should win over whatever the
 * wallpaper-derived palette would have been. With no accent picked, color follows the device
 * wallpaper (Material You) from Android 12 (API 31) on, via
 * [dynamicLightColorScheme]/[dynamicDarkColorScheme]; older releases -- and this app, since
 * `dynamicColor` defaults to `true` here but callers may turn it off -- fall back to the
 * hand-authored blue schemes in [LightColors]/[DarkColors].
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color? = null,
    dynamicColor: Boolean = true,
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
