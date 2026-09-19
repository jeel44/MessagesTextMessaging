package text.message.sms.messaging.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import text.message.sms.messaging.R
import text.message.sms.messaging.data.local.datastore.ThemeMode
import text.message.sms.messaging.ui.screens.conversationlist.screenSurfaceColor
import text.message.sms.messaging.ui.theme.AccentColorPresets
import kotlin.math.roundToInt

/**
 * Everything the "Theme" row in Settings opens: a light/dark/system mode selector plus an accent
 * color picker (a curated preset grid, falling back to a custom HSV picker), matching QKSMS's own
 * theme screen. Both choices apply immediately -- there's no separate "Apply" step, since every
 * call here already writes straight through to
 * [text.message.sms.messaging.data.local.datastore.ThemePreferences] via the callbacks the caller
 * supplies, the same immediate-write pattern every other toggle in Settings already uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemePickerDialog(
    currentMode: ThemeMode,
    currentAccentColor: Color?,
    onModeSelected: (ThemeMode) -> Unit,
    onAccentColorSelected: (Color?) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCustomPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.settings_theme_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.theme_picker_mode_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = ThemeMode.entries
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = currentMode == mode,
                            onClick = { onModeSelected(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        ) {
                            Text(stringResource(mode.labelRes()))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.theme_picker_color_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AccentColorGrid(
                    selectedColor = currentAccentColor,
                    onDefaultClick = { onAccentColorSelected(null) },
                    onPresetClick = onAccentColorSelected,
                    onCustomClick = { showCustomPicker = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )

    if (showCustomPicker) {
        HsvColorPickerDialog(
            initialColor = currentAccentColor ?: AccentColorPresets.first(),
            onColorSelected = { color ->
                onAccentColorSelected(color)
                showCustomPicker = false
            },
            onDismiss = { showCustomPicker = false },
        )
    }
}

/** Shared with [SettingsScreen]'s Theme row summary, so both read the same label. */
internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
}

@Composable
private fun AccentColorGrid(
    selectedColor: Color?,
    onDefaultClick: () -> Unit,
    onPresetClick: (Color) -> Unit,
    onCustomClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCustom = selectedColor != null && selectedColor !in AccentColorPresets
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.heightIn(max = 220.dp),
    ) {
        item(key = "default") {
            DefaultColorSwatch(selected = selectedColor == null, onClick = onDefaultClick)
        }
        items(AccentColorPresets, key = { it.toArgb() }) { color ->
            ColorSwatch(
                color = color,
                selected = selectedColor == color,
                contentDescription = "#%06X".format(color.toArgb() and 0xFFFFFF),
                onClick = { onPresetClick(color) },
            )
        }
        item(key = "custom") {
            CustomColorSwatch(selected = isCustom, onClick = onCustomClick)
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DefaultColorSwatch(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.theme_picker_default_swatch_description)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.RestartAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CustomColorSwatch(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.theme_picker_custom_swatch_description)
    val rainbow = remember {
        Brush.sweepGradient((0..360 step 60).map { Color.hsv(it.toFloat().coerceAtMost(359.999f), 0.85f, 0.95f) })
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(rainbow)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Colorize,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HsvColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val selectedColor = Color.hsv(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenSurfaceColor(),
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.theme_picker_custom_color_title)) },
        text = {
            Column {
                SaturationValueBox(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { newSaturation, newValue ->
                        saturation = newSaturation
                        value = newValue
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(selectedColor),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(selectedColor) }) {
                Text(stringResource(R.string.action_select))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** A saturation/value square for the fixed [hue] -- x is saturation (white to full hue color),
 * y is value/brightness (full color to black), the classic HSV picker layout. */
@Composable
private fun SaturationValueBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val hueColor = remember(hue) { Color.hsv(hue, 1f, 1f) }

    fun updateFrom(offset: Offset) {
        if (boxSize.width == 0 || boxSize.height == 0) return
        onChange(
            (offset.x / boxSize.width).coerceIn(0f, 1f),
            (1f - offset.y / boxSize.height).coerceIn(0f, 1f),
        )
    }

    val thumbRadiusPx = with(LocalDensity.current) { (THUMB_SIZE / 2).roundToPx() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(hueColor)
            .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .onSizeChanged { boxSize = it }
            .pointerInputTapAndDrag { offset -> updateFrom(offset) },
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (saturation * boxSize.width).roundToInt() - thumbRadiusPx,
                        y = ((1f - value) * boxSize.height).roundToInt() - thumbRadiusPx,
                    )
                }
                .size(THUMB_SIZE)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
        )
    }
}

/** A rainbow strip picking [hue] alone -- saturation/value are handled by [SaturationValueBox]. */
@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var width by remember { mutableStateOf(0) }
    val hueGradient = remember {
        Brush.horizontalGradient((0..360 step 60).map { Color.hsv(it.toFloat().coerceAtMost(359.999f), 1f, 1f) })
    }

    fun updateFrom(offset: Offset) {
        if (width == 0) return
        onHueChange((offset.x / width * 360f).coerceIn(0f, 359.999f))
    }

    val thumbRadiusPx = with(LocalDensity.current) { (HUE_THUMB_SIZE / 2).roundToPx() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(hueGradient)
            .onSizeChanged { width = it.width }
            .pointerInputTapAndDrag { offset -> updateFrom(offset) },
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x = (hue / 360f * width).roundToInt() - thumbRadiusPx, y = 0) }
                .size(HUE_THUMB_SIZE)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
        )
    }
}

/**
 * Combines tap-to-jump and drag-to-follow into one modifier -- [detectDragGestures] alone needs
 * the pointer to move past touch slop before `onDragStart` fires, so a plain tap without any
 * movement would otherwise do nothing.
 */
private fun Modifier.pointerInputTapAndDrag(onOffset: (Offset) -> Unit): Modifier =
    this
        .pointerInput(Unit) { detectTapGestures { offset -> onOffset(offset) } }
        .pointerInput(Unit) {
            detectDragGestures(onDragStart = { offset -> onOffset(offset) }) { change, _ ->
                change.consume()
                onOffset(change.position)
            }
        }

private val THUMB_SIZE = 24.dp
private val HUE_THUMB_SIZE = 28.dp
