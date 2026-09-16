package text.message.sms.messaging.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Generously rounded containers; controls are pill-shaped via [Pill]. */
internal val MessagingShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Fully rounded shape for chips, search fields and the compose button. */
val Pill = RoundedCornerShape(percent = 50)
