package text.message.sms.messaging.ui.screens.onboarding

import androidx.annotation.DrawableRes
import text.message.sms.messaging.R

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
    /** The row's round flag, or null for System Default (which shows a globe instead). */
    @get:DrawableRes
    val flagRes: Int?
        get() = flagFor(languageTag)
}

/**
 * The one place a language tag maps to its flag drawable -- one flag per country (English uses
 * the US flag, Portuguese Brazil's, Arabic Saudi Arabia's). Null only for System Default (`null`
 * tag). Any tag without its own flag file gets [R.drawable.flag_placeholder], a neutral glyph,
 * so a missing file never breaks the build: German's `flag_de` isn't in the project yet -- once
 * `flag_de.webp` is added, point "de" at `R.drawable.flag_de` here.
 */
@DrawableRes
internal fun flagFor(languageTag: String?): Int? = when (languageTag) {
    null -> null
    "en" -> R.drawable.flag_us
    "ar" -> R.drawable.flag_sa
    "nl" -> R.drawable.flag_nl
    "fr" -> R.drawable.flag_fr
    "hi" -> R.drawable.flag_in
    "id" -> R.drawable.flag_id
    "it" -> R.drawable.flag_it
    "ko" -> R.drawable.flag_kr
    "pt" -> R.drawable.flag_br
    "ro" -> R.drawable.flag_ro
    "es" -> R.drawable.flag_es
    "sv" -> R.drawable.flag_se
    "th" -> R.drawable.flag_th
    else -> R.drawable.flag_placeholder
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
