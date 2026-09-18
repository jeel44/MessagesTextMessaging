package text.message.sms.messaging.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Builds a full M3 [ColorScheme] from an arbitrary user-picked accent [seed], for
 * [AppTheme]'s custom-color path.
 *
 * There's no Material Color Utilities dependency here to compute a real HCT tonal palette, so
 * this takes a simpler route: contrast between an "X" role and its "onX" partner is governed
 * almost entirely by the *lightness* gap between them, not their hue -- so reusing the exact
 * lightness of each already-verified pairing in [SeedBlueLight]/[SeedBlueDark] and friends (see
 * that file's doc comment) while swapping in the seed's own hue/saturation keeps every pairing
 * legible for whatever hue the user picks. Only the primary-family roles are recolored, the same
 * scope QKSMS's own theme picker has (it recolors the app's single accent, not the full neutral
 * palette) -- [background]/[surface]/[outline]/etc. stay whatever [LightColors]/[DarkColors]
 * already set.
 */
internal fun accentColorScheme(seed: Color, darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        DarkColors.copy(
            primary = seed.withLightnessOf(SeedBlueDark),
            onPrimary = seed.withLightnessOf(SeedBlueOnPrimaryDark),
            primaryContainer = seed.withLightnessOf(SeedBluePrimaryContainerDark),
            onPrimaryContainer = seed.withLightnessOf(SeedBlueOnPrimaryContainerDark),
            inversePrimary = seed.withLightnessOf(SeedBlueLight),
        )
    } else {
        LightColors.copy(
            primary = seed.withLightnessOf(SeedBlueLight),
            onPrimary = Color.White,
            primaryContainer = seed.withLightnessOf(SeedBluePrimaryContainerLight),
            onPrimaryContainer = seed.withLightnessOf(SeedBlueOnPrimaryContainerLight),
            inversePrimary = seed.withLightnessOf(SeedBlueDark),
        )
    }

/** This color's hue and saturation, combined with [reference]'s own HSL lightness. */
private fun Color.withLightnessOf(reference: Color): Color {
    val seedHsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), seedHsl)
    val referenceHsl = FloatArray(3)
    ColorUtils.colorToHSL(reference.toArgb(), referenceHsl)
    return Color(ColorUtils.HSLToColor(floatArrayOf(seedHsl[0], seedHsl[1], referenceHsl[2])))
}
