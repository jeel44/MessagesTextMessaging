package text.message.sms.messaging.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Starts a call to [address] -- a real call via [Intent.ACTION_CALL] if `CALL_PHONE` is granted,
 * otherwise falls back to the dialer via [Intent.ACTION_DIAL] so the action still does something
 * useful rather than silently failing. Shared between
 * [text.message.sms.messaging.ui.screens.chat.ChatScreen]'s call button and the conversation
 * list's configurable swipe action.
 */
fun placeCall(context: Context, address: String) {
    val hasCallPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CALL_PHONE,
    ) == PackageManager.PERMISSION_GRANTED

    val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
    context.startActivity(Intent(action, "tel:$address".toUri()))
}
