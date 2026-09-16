package text.message.sms.messaging.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Root theme every screen in the app is wrapped in (applied once, in `MainActivity`).
 *
 * Dark/light follows the system setting by default; [darkTheme] is exposed as a parameter
 * rather than hard-coded to [isSystemInDarkTheme] so a future Settings screen can force either
 * mode regardless of the system setting.
 *
 * Color follows the device wallpaper (Material You) from Android 12 (API 31) on, via
 * [dynamicLightColorScheme]/[dynamicDarkColorScheme]; older releases -- and this app, since
 * `dynamicColor` defaults to `true` here but callers may turn it off -- fall back to the
 * hand-authored blue schemes in [LightColors]/[DarkColors].
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
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
