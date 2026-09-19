package text.message.sms.messaging.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.imageResource

/**
 * Like [androidx.compose.ui.res.painterResource] for a raster (webp/png) drawable, but decodes
 * with [FilterQuality.High] instead of the default [FilterQuality.Low] -- the default leaves a
 * flat, faintly blurred edge when a large source bitmap is downsampled to a small icon size (the
 * case for every drawable in res/drawable-nodpi, which are all exported at 512x512 and drawn at
 * 20-24dp).
 */
@Composable
fun sharpIconPainter(@DrawableRes id: Int): Painter {
    val bitmap = ImageBitmap.imageResource(id)
    return remember(bitmap) { BitmapPainter(bitmap, filterQuality = FilterQuality.High) }
}
