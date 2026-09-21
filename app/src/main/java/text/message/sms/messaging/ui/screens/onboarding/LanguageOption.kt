package text.message.sms.messaging.ui.screens.onboarding

import androidx.appcompat.app.AppCompatDelegate
import text.message.sms.messaging.util.firstLetterOrDigitOrNull

/**
 * One row in the language picker.
 *
 * [languageTag] is the BCP-47 tag passed to `AppCompatDelegate.setApplicationLocales`; `null`
 * means "System Default", which clears the per-app override instead of setting one.
 */
internal data class LanguageOption(
    val id: String,
    val displayName: String,
    val nativeName: String,
    val languageTag: String?,
) {
    /** The avatar glyph -- [nativeName]'s own first letter/digit, codepoint-safe (see
     * [firstLetterOrDigitOrNull]) so a script using a surrogate-pair code point never splits, and
     * left as-is (no forced uppercasing effect) for scripts with no case, e.g. Arabic/Thai/
     * Devanagari/Hangul. Derived rather than hand-typed so it can never drift from [nativeName]
     * the way the old hardcoded label did for Indonesian ("Bahasa Indonesia" showed "I", the
     * English name's initial, instead of "B"). */
    val avatarLabel: String
        get() = nativeName.firstLetterOrDigitOrNull().orEmpty()
}

// System Default, then English, then the remaining 13 alphabetical by English display name
// (Collator order): Arabic, Dutch, French, German, Hindi, Indonesian, Italian, Korean,
// Portuguese, Romanian, Spanish, Swedish, Thai.
internal val LanguageOptions = listOf(
    LanguageOption("system", "System Default", "English", null),
    LanguageOption("en", "English", "English", "en"),
    LanguageOption("ar", "Arabic", "العربية", "ar"),
    LanguageOption("nl", "Dutch", "Nederlands", "nl"),
    LanguageOption("fr", "French", "Français", "fr"),
    LanguageOption("de", "German", "Deutsch", "de"),
    LanguageOption("hi", "Hindi", "हिन्दी", "hi"),
    LanguageOption("id", "Indonesian", "Bahasa Indonesia", "id"),
    LanguageOption("it", "Italian", "Italiano", "it"),
    LanguageOption("ko", "Korean", "한국어", "ko"),
    LanguageOption("pt", "Portuguese", "Português", "pt"),
    LanguageOption("ro", "Romanian", "Română", "ro"),
    LanguageOption("es", "Spanish", "Español", "es"),
    LanguageOption("sv", "Swedish", "Svenska", "sv"),
    LanguageOption("th", "Thai", "ไทย", "th"),
)

/**
 * The [LanguageOption] actually in effect right now, read from
 * [AppCompatDelegate.getApplicationLocales] -- the same call [LanguageScreen]'s selection writes
 * to, and the authoritative source regardless of which screen last set it (onboarding's, or
 * Settings'). Falls back to [LanguageOptions.first] ("System Default") when nothing has been set.
 */
internal fun currentLanguageOption(): LanguageOption {
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotBlank() }
    return LanguageOptions.firstOrNull { it.languageTag == currentTag } ?: LanguageOptions.first()
}
