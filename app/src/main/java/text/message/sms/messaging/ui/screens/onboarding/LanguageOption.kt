package text.message.sms.messaging.ui.screens.onboarding

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
    val avatarLabel: String,
    val languageTag: String?,
)

internal val LanguageOptions = listOf(
    LanguageOption("system", "System Default", "English", "S", null),
    LanguageOption("en", "English", "English", "E", "en"),
    LanguageOption("es", "Spanish", "Español", "E", "es"),
    LanguageOption("fr", "French", "Français", "F", "fr"),
    LanguageOption("pt", "Portuguese", "Português", "P", "pt"),
    LanguageOption("de", "German", "Deutsch", "D", "de"),
    LanguageOption("id", "Indonesian", "Bahasa Indonesia", "I", "id"),
    LanguageOption("it", "Italian", "Italiano", "I", "it"),
    LanguageOption("hi", "Hindi", "हिन्दी", "ह", "hi"),
    LanguageOption("ar", "Arabic", "العربية", "ع", "ar"),
    LanguageOption("th", "Thai", "ไทย", "ท", "th"),
    LanguageOption("ro", "Romanian", "Română", "R", "ro"),
    LanguageOption("nl", "Dutch", "Nederlands", "N", "nl"),
    LanguageOption("ko", "Korean", "한국어", "한", "ko"),
    LanguageOption("sv", "Swedish", "Svenska", "S", "sv"),
)
