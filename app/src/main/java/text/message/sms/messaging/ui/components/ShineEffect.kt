package text.message.sms.messaging.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import kotlin.math.tan

private const val SHINE_WIDTH_FRACTION = 0.4f
private const val SHINE_SKEW_DEGREES = 20f
private const val SHINE_ALPHA = 0.55f

/** How long one sweep takes to cross the surface, regardless of [Modifier.shineEffect]'s
 * `periodMillis` -- the streak then parks off-screen for the remainder of each period, which is
 * what makes the effect read as periodic rather than continuous. */
private const val SHINE_SWEEP_DURATION_MILLIS = 1400

/**
 * Draws a diagonal, skewed light streak sweeping once left-to-right across this composable's
 * content every [periodMillis] -- the sweep itself always takes [SHINE_SWEEP_DURATION_MILLIS], the
 * rest of each period is a pause with the streak parked fully off the right edge, so the effect
 * reads as periodic, not continuous. Suppressed entirely when the system's reduced-motion setting
 * is on, or when [enabled] is false.
 *
 * Extracted from [ShineButton]'s own inline shine-sweep drawing so both it and any other shined
 * surface (see [text.message.sms.messaging.ui.screens.callend.CallEndNativeAd]'s native-ad CTA)
 * share one implementation; [ShineButton] still owns its separate pulsing-halo animation, which
 * this doesn't touch.
 *
 * Must be chained after whatever draws this element's own fill/background (e.g.
 * `.background(color).shineEffect(...)`), since this relies on being drawn -- via [drawBehind] --
 * after that fill but before this element's children, the same "shine strictly between fill and
 * text" placement [ShineButton] always required. Relies on an enclosing `.clip(shape)` to bound
 * the streak to the surface's shape; this draws unclipped on its own.
 */
fun Modifier.shineEffect(periodMillis: Int, enabled: Boolean = true): Modifier = composed {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // rememberInfiniteTransition/animateFloat must run unconditionally on every recomposition of
    // this modifier -- gating them behind `enabled`/`reducedMotion` would change how many
    // composables this call makes between recompositions if either ever flips at runtime,
    // corrupting Compose's slot table. Only the *drawing* below is conditional, matching
    // ShineButton's own established pattern for the same reason.
    val active = enabled && !reducedMotion
    val infiniteTransition = rememberInfiniteTransition(label = "shine-effect")
    val progress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = periodMillis
                0f at 0 using LinearEasing
                1f at SHINE_SWEEP_DURATION_MILLIS.coerceAtMost(periodMillis) using LinearEasing
                1f at periodMillis
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "shine-progress",
    )

    this.drawBehind { if (active) drawShineStreak(progress.value) }
}

/**
 * Draws one diagonal, soft-white streak at the sweep position [progress] (0f = fully off the left
 * edge, 1f = fully off the right edge) -- a horizontal gradient (transparent -> white @
 * [SHINE_ALPHA] -> transparent) filling a parallelogram skewed [SHINE_SKEW_DEGREES] off vertical,
 * [SHINE_WIDTH_FRACTION] of the surface's width wide.
 */
private fun DrawScope.drawShineStreak(progress: Float) {
    val streakWidth = size.width * SHINE_WIDTH_FRACTION
    val totalTravel = size.width + streakWidth
    val centerX = -streakWidth / 2f + progress * totalTravel
    val skewPx = (size.height * tan(Math.toRadians(SHINE_SKEW_DEGREES.toDouble()))).toFloat()

    val path = Path().apply {
        moveTo(centerX - streakWidth / 2f + skewPx / 2f, 0f)
        lineTo(centerX + streakWidth / 2f + skewPx / 2f, 0f)
        lineTo(centerX + streakWidth / 2f - skewPx / 2f, size.height)
        lineTo(centerX - streakWidth / 2f - skewPx / 2f, size.height)
        close()
    }

    val brush = Brush.linearGradient(
        colors = listOf(Color.Transparent, Color.White.copy(alpha = SHINE_ALPHA), Color.Transparent),
        start = Offset(centerX - streakWidth / 2f, 0f),
        end = Offset(centerX + streakWidth / 2f, 0f),
    )

    drawPath(path = path, brush = brush)
}
