package text.message.sms.messaging.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import text.message.sms.messaging.R

class LanguageOptionTest {

    @Test
    fun `list is System Default then the 14 languages with unchanged tags`() {
        assertEquals(
            listOf(null, "en", "ar", "nl", "fr", "de", "hi", "id", "it", "ko", "pt", "ro", "es", "sv", "th"),
            LanguageOptions.map { it.languageTag },
        )
    }

    /** Pinned by code point, in logical (storage) order, so a reversed or visually-ordered copy
     * of a right-to-left or complex-script name can't slip in unnoticed. */
    @Test
    fun `non-Latin native names are stored in logical order`() {
        val expected = mapOf(
            "ar" to "العربية", // العربية
            "hi" to "हिन्दी", // हिन्दी
            "ko" to "한국어", // 한국어
            "th" to "ไทย", // ไทย
        )
        expected.forEach { (id, name) ->
            assertEquals(id, name, LanguageOptions.single { it.id == id }.nativeName)
        }
    }

    @Test
    fun `flag mapping uses one flag per country`() {
        val expected = mapOf(
            "en" to R.drawable.flag_us,
            "ar" to R.drawable.flag_sa,
            "nl" to R.drawable.flag_nl,
            "fr" to R.drawable.flag_fr,
            "hi" to R.drawable.flag_in,
            "id" to R.drawable.flag_id,
            "it" to R.drawable.flag_it,
            "ko" to R.drawable.flag_kr,
            "pt" to R.drawable.flag_br,
            "ro" to R.drawable.flag_ro,
            "es" to R.drawable.flag_es,
            "sv" to R.drawable.flag_se,
            "th" to R.drawable.flag_th,
        )
        expected.forEach { (tag, res) -> assertEquals(tag, res, flagFor(tag)) }
    }

    @Test
    fun `System Default has no flag and unknown tags fall back to the placeholder`() {
        assertNull(LanguageOptions.first().flagRes)
        assertEquals(R.drawable.flag_placeholder, flagFor("de"))
        assertEquals(R.drawable.flag_placeholder, flagFor("xx"))
        LanguageOptions.drop(1).forEach { assertNotEquals(it.id, null, it.flagRes) }
    }
}
