package text.message.sms.messaging.ui.screens.newmessage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import text.message.sms.messaging.R
import text.message.sms.messaging.ui.components.ScreenPlaceholder

/** Recipient picker and first-message composer. Both arrive in a later pass. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    onBack: () -> Unit,
    onConversationStarted: (threadId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_new_message)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        ScreenPlaceholder(
            title = stringResource(R.string.screen_new_message),
            detail = stringResource(R.string.placeholder_new_message),
            modifier = Modifier.padding(innerPadding),
        )
    }
}
