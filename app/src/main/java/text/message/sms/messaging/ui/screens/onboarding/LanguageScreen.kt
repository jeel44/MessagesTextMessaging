package text.message.sms.messaging.ui.screens.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.R
import text.message.sms.messaging.ads.NativeAdState
import text.message.sms.messaging.ui.components.AppTopBar
import text.message.sms.messaging.ui.components.ads.AdShimmerPlaceholder
import text.message.sms.messaging.ui.components.ads.NativeAdCard
import text.message.sms.messaging.ui.components.ads.NativeAdCardReservedHeight
import text.message.sms.messaging.ui.components.ads.NativeAdCardShape

/**
 * The language picker, reused for two different entry points -- [onBack] picks which:
 *
 * - Onboarding's first step, right after Splash (`onBack` null): no top bar, just this screen's
 *   own small title, with the confirm check at its end. Confirming moves on to Intro (storing it
 *   as the step to resume at), with no interstitial. [LanguageViewModel] silently syncs the
 *   message cache in the background while this is up (see its `init` block); confirming never
 *   waits on that sync -- Home's own Flow-backed repository query picks up any rows that land
 *   after navigation.
 * - Settings' "Language" row (`onBack` set): a top bar with a back arrow, its own "Language"
 *   title and the confirm check as its action -- this screen's own title is skipped here so the
 *   two don't stack.
 *
 * Both share the rest: the active language opens highlighted and scrolled into view, a tap only
 * moves the highlight. The confirm check stays hidden until 2s after the last tap (see
 * [ConfirmRevealTimer]); it applies the language, shows an interstitial (Settings only), then
 * calls [onApplied] (see [LanguageViewModel.onApplyClicked]). A native ad is pinned at the bottom.
 */
@Composable
fun LanguageScreen(
    onApplied: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val nativeAdState by viewModel.nativeAdState.collectAsStateWithLifecycle()
    val confirmVisible by viewModel.confirmVisible.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isOnboarding = onBack == null

    LaunchedEffect(Unit) { viewModel.startAds(isOnboarding) }

    LanguageScreenContent(
        selectedLanguageId = selectedLanguage?.id,
        nativeAdState = nativeAdState,
        confirmVisible = confirmVisible,
        onLanguageClick = viewModel::selectLanguage,
        onConfirmClick = { viewModel.onApplyClicked(activity, isOnboarding, onDone = onApplied) },
        onBack = onBack,
        modifier = modifier,
    )
}

/** [LanguageScreen] minus its ViewModel, so render tests can drive it directly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageScreenContent(
    selectedLanguageId: String?,
    nativeAdState: NativeAdState,
    confirmVisible: Boolean,
    onLanguageClick: (LanguageOption) -> Unit,
    onConfirmClick: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val listState = rememberLazyListState()

    // First tap wins, so a double-tap only confirms once (the ViewModel guards it too).
    var confirmed by remember { mutableStateOf(false) }
    val confirm = {
        if (!confirmed) {
            confirmed = true
            onConfirmClick()
        }
    }

    // Once per visit (saveable, so rotation doesn't re-run it): the first time a selection shows
    // up -- the ViewModel's preselection lands a moment after the first frame -- bring its row
    // into view. Later taps never scroll; the tapped row is already on screen.
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedLanguageId) {
        if (initialScrollDone || selectedLanguageId == null) return@LaunchedEffect
        initialScrollDone = true
        val index = LanguageOptions.indexOfFirst { it.id == selectedLanguageId }
        val fullyVisible = listState.layoutInfo.let { info ->
            info.visibleItemsInfo.any {
                it.index == index &&
                    it.offset >= info.viewportStartOffset &&
                    it.offset + it.size <= info.viewportEndOffset
            }
        }
        if (index >= 0 && !fullyVisible) {
            // Two rows of context above, so the selected card doesn't land flush against the top.
            listState.scrollToItem((index - 2).coerceAtLeast(0))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        topBar = {
            if (onBack != null) {
                AppTopBar(
                    title = stringResource(R.string.settings_language_title),
                    onBack = onBack,
                    actions = { LanguageConfirmButton(visible = confirmVisible, onClick = confirm) },
                )
            }
        },
        bottomBar = {
            Surface(color = backgroundColor) {
                // navigationBarsPadding: the ad is the bottom-most element, and an ad drawn
                // under the system navigation bar would be partly obscured.
                Column(modifier = Modifier.navigationBarsPadding()) {
                    LanguageNativeAdSlot(
                        state = nativeAdState,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(innerPadding),
        ) {
            // Onboarding only -- Settings already shows "Language" (and the check) in the top bar
            // above, so repeating a heading here would stack two titles.
            if (onBack == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.language_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                    )
                    LanguageConfirmButton(
                        visible = confirmVisible,
                        onClick = confirm,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .selectableGroup(),
            ) {
                items(LanguageOptions, key = { it.id }) { language ->
                    LanguageCard(
                        language = language,
                        selected = language.id == selectedLanguageId,
                        onClick = { onLanguageClick(language) },
                    )
                }
            }
        }
    }
}

internal const val LanguageConfirmTag = "language_confirm"

/**
 * The confirm check, in a fixed 48dp slot so neither the top bar's title nor the list below
 * moves when it appears. While hidden it isn't composed at all -- not just disabled -- so it's
 * absent from the semantics tree too. It fades and scales in, or just appears when the system's
 * "remove animations" setting is on; once shown it never leaves (see [ConfirmRevealTimer]).
 */
@Composable
private fun LanguageConfirmButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = if (reducedMotion) {
                EnterTransition.None
            } else {
                fadeIn(tween(CONFIRM_REVEAL_ANIM_MILLIS)) +
                    scaleIn(tween(CONFIRM_REVEAL_ANIM_MILLIS), initialScale = 0.6f)
            },
            exit = ExitTransition.None,
        ) {
            IconButton(onClick = onClick, modifier = Modifier.testTag(LanguageConfirmTag)) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.language_apply_content_description),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private const val CONFIRM_REVEAL_ANIM_MILLIS = 200

/** Shimmer while the first load is in flight, then the card; nothing if it failed. Both reserve
 * [NativeAdCardReservedHeight], so the slot doesn't jump when the ad arrives or is swapped
 * by the first-selection refresh (a refresh keeps the current card up, see
 * [text.message.sms.messaging.ads.NativeAdLoader.refresh]). */
@Composable
private fun LanguageNativeAdSlot(state: NativeAdState, modifier: Modifier = Modifier) {
    when (state) {
        NativeAdState.Loading -> AdShimmerPlaceholder(
            height = NativeAdCardReservedHeight,
            shape = NativeAdCardShape,
            modifier = modifier,
        )
        is NativeAdState.Loaded -> NativeAdCard(
            nativeAd = state.nativeAd,
            modifier = modifier.heightIn(min = NativeAdCardReservedHeight),
        )
        NativeAdState.Failed -> Unit
    }
}

private val LanguageCardShape = RoundedCornerShape(12.dp)

internal const val LanguageCardTagPrefix = "language_card_"

/**
 * One language: flag (or System Default's globe), English name over native name, radio.
 * Unselected sits on `surface` with an `outlineVariant` hairline; selected fills with `primary`
 * and flips its text and radio to `onPrimary`, the same accent the confirm check uses.
 *
 * The card's own layout is pinned left-to-right in every locale -- flag left, radio right, text
 * left-aligned -- so it looks the same with the app in Arabic as in English (the English names
 * aren't localized, so an RTL-mirrored row would put LTR text in a right-aligned column). Each
 * name still lays out by its own script ([TextDirection.Content]), so العربية shapes and orders
 * as Arabic inside that left-aligned slot. The screen around the cards (top bar, title) still
 * mirrors normally.
 */
@Composable
private fun LanguageCard(
    language: LanguageOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (selected) colors.primary else colors.surface
    val borderColor = if (selected) colors.primary else colors.outlineVariant
    val nameColor = if (selected) colors.onPrimary else colors.onSurface
    val nativeNameColor = if (selected) colors.onPrimary.copy(alpha = 0.8f) else colors.onSurfaceVariant

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clip(LanguageCardShape)
                .background(containerColor)
                .border(width = 1.dp, color = borderColor, shape = LanguageCardShape)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .testTag(LanguageCardTagPrefix + language.id)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageFlag(flagRes = language.flagRes, selected = selected)

            Spacer(modifier = Modifier.width(14.dp))

            val textStyle = TextStyle(textAlign = TextAlign.Left, textDirection = TextDirection.Content)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = language.displayName,
                    color = nameColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = textStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                // System Default has no native name of its own -- it's whatever the device uses.
                if (language.languageTag != null) {
                    Text(
                        text = language.nativeName,
                        color = nativeNameColor,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = textStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            LanguageRadioIndicator(selected = selected)
        }
    }
}

/** A 44dp round flag, cropped to fill, with a hairline ring so white-heavy flags (Indonesia, Korea)
 * don't bleed into a light card. Null [flagRes] is System Default: a globe instead. The neutral
 * placeholder (a flag glyph, see [flagFor]) is tinted like the globe rather than cropped. */
@Composable
private fun LanguageFlag(@DrawableRes flagRes: Int?, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val glyphContainer = if (selected) colors.onPrimary.copy(alpha = 0.16f) else colors.surfaceVariant
    val glyphTint = if (selected) colors.onPrimary else colors.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(glyphContainer)
            .border(width = 1.dp, color = colors.outlineVariant, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (flagRes) {
            null -> Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = glyphTint,
                modifier = Modifier.size(24.dp),
            )
            R.drawable.flag_placeholder -> Image(
                painter = painterResource(flagRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(glyphTint),
                modifier = Modifier.size(22.dp),
            )
            else -> Image(
                painter = painterResource(flagRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A custom indicator rather than the stock M3 [androidx.compose.material3.RadioButton]: the
 * selected card is already `primary`-filled, so its radio inverts -- an `onPrimary` disc with a
 * `primary` dot punched through -- which M3's ring-and-dot rendering can't produce. */
@Composable
private fun LanguageRadioIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(colors.onPrimary),
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 1.5.dp, color = colors.outline, shape = CircleShape),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
