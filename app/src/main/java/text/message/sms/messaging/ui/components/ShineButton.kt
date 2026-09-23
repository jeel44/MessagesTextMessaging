package text.message.sms.messaging.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.ui.graphics.Color
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

private val ButtonHeight = 52.dp
private val ButtonCornerRadius = 26.dp
private val HaloExtension = 7.dp
private val HaloCornerRadius = 33.dp
private const val HALO_SCALE_MIN = 0.985f
private const val HALO_SCALE_MAX = 1.03f
private const val HALO_ALPHA_MIN = 0.55f
private const val HALO_ALPHA_MAX = 1.0f
private const val CYCLE_DURATION_MILLIS = 2600
private const val DISABLED_ALPHA = 0.38f

private val ButtonShape = RoundedCornerShape(ButtonCornerRadius)
private val HaloShape = RoundedCornerShape(HaloCornerRadius)

/**
 * The shared onboarding CTA: a full-width pill button with an animated pulsing halo behind it and
 * a diagonal light "shine" sweeping across its face (see [Modifier.shineEffect]), per the approved
 * Welcome/Set-as-Default design.
 *
 * Hand-rolled rather than wrapping [androidx.compose.material3.Button]: the shine must be drawn
 * strictly between the button's fill and its text (M3's `Button` slot API has no seam for that),
 * and the halo must sit in its own reserved layout space so it is never at risk of being clipped
 * by a caller's own clipping/padding -- see the outer [Box]'s fixed [HaloExtension]-padded height
 * below, which contains the halo entirely within this composable's own measured bounds rather than
 * drawing outside them.
 *
 * The halo's continuously-animated scale/alpha are read as `.value` off a bare
 * [androidx.compose.runtime.State] inside a [Modifier.graphicsLayer] lambda, never via a `by`
 * delegate in this function's body -- so every 2600ms-cycle repaint invalidates only that layout
 * node's paint, never recomposing this composable or its caller; [shineEffect] follows the same
 * rule internally for the shine streak.
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
                .drawBehind { drawRect(containerColor) }
                .shineEffect(periodMillis = CYCLE_DURATION_MILLIS, enabled = animated),
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
