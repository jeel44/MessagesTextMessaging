package text.message.sms.messaging.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import text.message.sms.messaging.ui.theme.ConversationFabBlue
import text.message.sms.messaging.ui.theme.ShineButtonHalo
import text.message.sms.messaging.ui.theme.ShineButtonPressedBlue
import kotlin.math.tan

private val ButtonHeight = 52.dp
private val ButtonCornerRadius = 26.dp
private val HaloExtension = 7.dp
private val HaloCornerRadius = 33.dp
private const val HALO_SCALE_MIN = 0.985f
private const val HALO_SCALE_MAX = 1.03f
private const val HALO_ALPHA_MIN = 0.55f
private const val HALO_ALPHA_MAX = 1.0f
private const val CYCLE_DURATION_MILLIS = 2600
private const val SHINE_SWEEP_DURATION_MILLIS = 1400
private const val SHINE_WIDTH_FRACTION = 0.4f
private const val SHINE_SKEW_DEGREES = 20f
private const val SHINE_ALPHA = 0.55f
private const val DISABLED_ALPHA = 0.38f

private val ButtonShape = RoundedCornerShape(ButtonCornerRadius)
private val HaloShape = RoundedCornerShape(HaloCornerRadius)

/**
 * The shared onboarding CTA: a full-width pill button with an animated pulsing halo behind it and
 * a diagonal light "shine" sweeping across its face, per the approved Welcome/Set-as-Default
 * design.
 *
 * Hand-rolled rather than wrapping [androidx.compose.material3.Button]: the shine must be drawn
 * strictly between the button's fill and its text (M3's `Button` slot API has no seam for that),
 * and the halo must sit in its own reserved layout space so it is never at risk of being clipped
 * by a caller's own clipping/padding -- see the outer [Box]'s fixed [HaloExtension]-padded height
 * below, which contains the halo entirely within this composable's own measured bounds rather than
 * drawing outside them.
 *
 * All continuously-animated values (halo scale/alpha, shine sweep offset) are read as `.value` off
 * a bare [androidx.compose.runtime.State] inside a [Modifier.graphicsLayer] or [drawBehind] lambda,
 * never via a `by` delegate in this function's body -- so every 2600ms-cycle repaint invalidates
 * only that layout node's paint, never recomposing this composable or its caller.
 *
 * @param showHalo whether the pulsing halo (and shine) should render at all; always suppressed
 * while [enabled] is false (an inert control pulsing/shining reads as broken, not decorative) or
 * while the system's reduced-motion setting is on.
 */
@Composable
fun ShineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showHalo: Boolean = true,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val animated = showHalo && enabled && !reducedMotion

    val infiniteTransition = rememberInfiniteTransition(label = "shine-button")
    val haloScale = infiniteTransition.animateFloat(
        initialValue = HALO_SCALE_MIN,
        targetValue = HALO_SCALE_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CYCLE_DURATION_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo-scale",
    )
    val haloAlpha = infiniteTransition.animateFloat(
        initialValue = HALO_ALPHA_MIN,
        targetValue = HALO_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CYCLE_DURATION_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo-alpha",
    )
    // 0f -> 1f across the first SHINE_SWEEP_DURATION_MILLIS of the cycle, then holds at 1f (the
    // streak parked fully past the button's right edge, i.e. invisible) for the remainder -- "sweep
    // during the first ~1400ms, rest for the remainder" of one 2600ms cycle.
    val shineProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CYCLE_DURATION_MILLIS
                0f at 0 using LinearEasing
                1f at SHINE_SWEEP_DURATION_MILLIS using LinearEasing
                1f at CYCLE_DURATION_MILLIS
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "shine-progress",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val containerColor = if (isPressed) ShineButtonPressedBlue else ConversationFabBlue

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight + HaloExtension * 2),
        contentAlignment = Alignment.Center,
    ) {
        if (animated) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // graphicsLayer must wrap clip (not the reverse) so the scale transforms the
                    // whole already-clipped rounded shape as one unit -- scale is a compositing
                    // transform on this layer's rendered output, so it needs to be the outer layer
                    // around the clip, or the clip boundary itself would stay fixed while only its
                    // (fully opaque, edge-to-edge) fill visibly moved.
                    .graphicsLayer {
                        scaleX = haloScale.value
                        scaleY = haloScale.value
                        alpha = haloAlpha.value
                    }
                    .clip(HaloShape)
                    .drawBehind { drawRect(ShineButtonHalo) }
                    .clearAndSetSemantics {},
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HaloExtension)
                .height(ButtonHeight)
                .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
                .clip(ButtonShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .drawBehind {
                    drawRect(containerColor)
                    if (animated) drawShineStreak(shineProgress.value)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Draws one diagonal, soft-white streak at the sweep position [progress] (0f = fully off the left
 * edge, 1f = fully off the right edge) -- a horizontal gradient (transparent -> white @
 * [SHINE_ALPHA] -> transparent) filling a parallelogram skewed [SHINE_SKEW_DEGREES] off vertical,
 * [SHINE_WIDTH_FRACTION] of the button's width wide. The caller clips this [DrawScope] to the
 * button shape already, so no clipping is needed here.
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
