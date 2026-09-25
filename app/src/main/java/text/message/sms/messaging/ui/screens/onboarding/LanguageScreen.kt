package text.message.sms.messaging.ui.screens.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import text.message.sms.messaging.ui.theme.Pill

/**
 * The language picker, reused for two different entry points -- [onBack] picks which:
 *
 * - Onboarding's third step (`onBack` null): no top bar, just this screen's own small title.
 *   Apply also marks onboarding complete. [LanguageViewModel] silently syncs the message cache in
 *   the background while this is up (see its `init` block); Apply never waits on that sync --
 *   Home's own Flow-backed repository query picks up any rows that land after navigation.
 * - Settings' "Language" row (`onBack` set): a top bar with a back arrow and its own "Language"
 *   title -- this screen's own title is skipped here so the two don't stack.
 *
 * Both share the rest: rows start unselected and a tap only highlights one; the bottom "Apply"
 * bar (disabled until a row is picked) applies it, shows an interstitial, then calls [onApplied]
 * (see [LanguageViewModel.onApplyClicked]). A native ad sits below Apply, not above, so its own
 * CTA is never stacked right against the button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    onApplied: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val nativeAdState by viewModel.nativeAdState.collectAsStateWithLifecycle()
    val backgroundColor = languageScreenBackground()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isOnboarding = onBack == null

    LaunchedEffect(Unit) { viewModel.startAds() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        topBar = {
            if (onBack != null) {
                AppTopBar(title = stringResource(R.string.settings_language_title), onBack = onBack)
            }
        },
        bottomBar = {
            Surface(color = backgroundColor) {
                // navigationBarsPadding: the ad is the bottom-most element, and an ad drawn
                // under the system navigation bar would be partly obscured.
                Column(modifier = Modifier.navigationBarsPadding()) {
                    Button(
                        onClick = { viewModel.onApplyClicked(activity, isOnboarding, onDone = onApplied) },
                        enabled = selectedLanguage != null,
                        shape = Pill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.language_apply))
                    }
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
            // Onboarding only -- Settings already shows "Language" in the top bar above, so
            // repeating a heading here would stack two titles.
            if (isOnboarding) {
                Text(
                    text = stringResource(R.string.language_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = languageTitleColor(),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }

            val avatarTones = languageAvatarTones()
            val displayNameColor = languageDisplayNameColor()
            val nativeNameColor = languageNativeNameColor()
            val radioOutlineColor = languageRadioOutlineColor()

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(LanguageOptions, key = { _, language -> language.id }) { index, language ->
                    val (avatarContainer, avatarContent) = avatarTones[index % avatarTones.size]
                    LanguageRow(
                        language = language,
                        selected = language.id == selectedLanguage?.id,
                        avatarContainerColor = avatarContainer,
                        avatarContentColor = avatarContent,
                        displayNameColor = displayNameColor,
                        nativeNameColor = nativeNameColor,
                        radioOutlineColor = radioOutlineColor,
                        onClick = { viewModel.selectLanguage(language) },
                    )
                }
            }
        }
    }
}

/** Shimmer while the first load is in flight, then the card; nothing if it failed. Both reserve
 * [NativeAdCardReservedHeight], so Apply above doesn't move when the ad arrives or is swapped
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

@Composable
private fun LanguageRow(
    language: LanguageOption,
    selected: Boolean,
    avatarContainerColor: Color,
    avatarContentColor: Color,
    displayNameColor: Color,
    nativeNameColor: Color,
    radioOutlineColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(avatarContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = language.avatarLabel,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = avatarContentColor,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // One line, ellipsized rather than wrapped -- the row height is fixed, and a long native
        // name (e.g. "Bahasa Indonesia") wrapping to a second line would clip against it and
        // overlap LanguageRadioIndicator instead of just truncating.
        val nameText = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = displayNameColor)) {
                append(language.displayName)
            }
            append(" ")
            withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = nativeNameColor)) {
                append("(${language.nativeName})")
            }
        }
        Text(
            text = nameText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        LanguageRadioIndicator(selected = selected, outlineColor = radioOutlineColor)
    }
}

/** A custom indicator rather than the stock M3 [androidx.compose.material3.RadioButton] -- the
 * reference design's selected state (a fully accent-filled circle with a small white dot punched
 * through its center) isn't a look M3's own ring-and-dot rendering can produce. */
@Composable
private fun LanguageRadioIndicator(selected: Boolean, outlineColor: Color, modifier: Modifier = Modifier) {
    val accentColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.size(29.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(accentColor),
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 1.2.dp, color = outlineColor, shape = CircleShape),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
