package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Same luminance check [onboardingPrimaryTextColor] and friends use: the reference design's
 * fixed dark palette below only applies while the active scheme is actually dark -- light theme
 * (or a light custom accent/dynamic color) instead builds its equivalents from `MaterialTheme`
 * tokens, so this screen never looks wrong under either. */
@Composable
private fun isLightScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

/** The reference design's own near-black, not [text.message.sms.messaging.ui.theme.DarkColors]'
 * bluish `background` (`#111318`) -- this screen intentionally overrides the app's own dark
 * background rather than reusing it. */
@Composable
internal fun languageScreenBackground(): Color =
    if (isLightScheme()) MaterialTheme.colorScheme.background else Color(0xFF1A1A1A)

@Composable
internal fun languageTitleColor(): Color =
    if (isLightScheme()) MaterialTheme.colorScheme.onBackground else Color.White

@Composable
internal fun languageDisplayNameColor(): Color =
    if (isLightScheme()) MaterialTheme.colorScheme.onBackground else Color.White

@Composable
internal fun languageNativeNameColor(): Color =
    if (isLightScheme()) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF9A9A9A)

@Composable
internal fun languageRadioOutlineColor(): Color =
    if (isLightScheme()) MaterialTheme.colorScheme.outline else Color(0xFFC8C8C8)

/** Cycled by row index -- (background, glyph color) pairs. Four muted tones with a plain white
 * glyph in dark theme (the reference design's own palette); four `MaterialTheme` container tones
 * paired with their own contrasting `on*` color in light theme, since a flat white glyph would be
 * unreadable against light pastel containers. */
@Composable
internal fun languageAvatarTones(): List<Pair<Color, Color>> =
    if (isLightScheme()) {
        listOf(
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        listOf(
            Color(0xFF2E3B2E) to Color.White,
            Color(0xFF4A3A38) to Color.White,
            Color(0xFF2A3142) to Color.White,
            Color(0xFF4A4432) to Color.White,
        )
    }
