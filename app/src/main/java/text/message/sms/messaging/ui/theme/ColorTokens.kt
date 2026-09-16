package text.message.sms.messaging.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Placeholder Material 3 palette. Solid surfaces, no gradients -- the final expressive tokens
 * land in a later pass.
 */
private val SeedBlue = Color(0xFF3F5BD9)
private val SeedBlueLight = Color(0xFFB6C4FF)
private val AccentTeal = Color(0xFF00696E)
private val AccentTealLight = Color(0xFF6FF6FF)

internal val LightColors = lightColorScheme(
    primary = SeedBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = AccentTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF1F6),
    onSecondaryContainer = Color(0xFF002022),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF757680),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

internal val DarkColors = darkColorScheme(
    primary = SeedBlueLight,
    onPrimary = Color(0xFF001C92),
    primaryContainer = Color(0xFF2439B4),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = AccentTealLight,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004F53),
    onSecondaryContainer = Color(0xFF9CF1F6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    outline = Color(0xFF90909A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)
