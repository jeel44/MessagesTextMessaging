package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import text.message.sms.messaging.ui.theme.OnboardingHeadlineDark
import text.message.sms.messaging.ui.theme.OnboardingPrivacyLinkBlue

/**
 * Shared light/dark gating for [WelcomeScreen]'s and [SetDefaultSmsScreen]'s fixed reference-design
 * text colors -- same luminance check [text.message.sms.messaging.ui.screens.conversationlist
 * .screenSurfaceColor] uses: the fixed hex only applies while the active scheme's background is
 * light enough to read as "white" (light theme, or a light custom accent/dynamic color); a dark
 * background falls back to the matching `MaterialTheme.colorScheme` token instead, so dark theme
 * keeps working regardless of accent or dynamic color.
 */
@Composable
internal fun onboardingPrimaryTextColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        OnboardingHeadlineDark
    } else {
        MaterialTheme.colorScheme.onSurface
    }

/** [lightColor] is one of the design's fixed grays (eyebrow label, subtitle, privacy line -- each
 * screen picks its own shade); dark theme always falls back to `onSurfaceVariant` regardless of
 * which shade was requested, since M3 doesn't distinguish tones of secondary text the way the
 * light design does. */
@Composable
internal fun onboardingSecondaryTextColor(lightColor: Color): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        lightColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
internal fun onboardingPrivacyLinkColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        OnboardingPrivacyLinkBlue
    } else {
        MaterialTheme.colorScheme.primary
    }
