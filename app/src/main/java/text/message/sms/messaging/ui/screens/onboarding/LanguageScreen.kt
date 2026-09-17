package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import text.message.sms.messaging.R
import text.message.sms.messaging.ui.theme.Pill

/**
 * Third onboarding screen: the user picks a language while [LanguageViewModel] silently syncs
 * the message cache in the background (see its `init` block). Continue never waits on that sync
 * -- Home's own Flow-backed repository query picks up any rows that land after navigation.
 */
@Composable
fun LanguageScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = {
                        viewModel.completeOnboarding()
                        onContinue()
                    },
                    shape = Pill,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .height(52.dp),
                ) {
                    Text(stringResource(R.string.language_continue))
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )

            val avatarTones = listOf(
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(LanguageOptions, key = { _, language -> language.id }) { index, language ->
                    val (avatarContainer, avatarContent) = avatarTones[index % avatarTones.size]
                    LanguageRow(
                        language = language,
                        selected = language.id == selectedLanguage.id,
                        avatarContainerColor = avatarContainer,
                        avatarContentColor = avatarContent,
                        onClick = { viewModel.selectLanguage(language) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    language: LanguageOption,
    selected: Boolean,
    avatarContainerColor: Color,
    avatarContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = language.avatarLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = avatarContentColor,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
        val nameText = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(language.displayName)
            }
            append(" ")
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = onSurfaceVariant)) {
                append("(${language.nativeName})")
            }
        }
        Text(
            text = nameText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        RadioButton(selected = selected, onClick = null)
    }
}
