package text.message.sms.messaging.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Opens [url] in the browser -- a tapped link inside a message body, never a real call. Adds a
 * scheme when [url] is a bare `www.`-style match (the only shape [android.util.Patterns.WEB_URL]
 * accepts without one), since [Intent.ACTION_VIEW] needs a URI it can resolve to an app. */
fun openMessageLinkUrl(context: Context, url: String) {
    val withScheme = if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
    context.startActivity(Intent(Intent.ACTION_VIEW, withScheme.toUri()))
}

/** Opens the mail app to compose to [email] -- a tapped email link inside a message body. */
fun openMessageLinkEmail(context: Context, email: String) {
    context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri()))
}

/** Opens the dialer pre-filled with [phone] via [Intent.ACTION_DIAL] -- deliberately never
 * [Intent.ACTION_CALL] (which needs the `CALL_PHONE` permission and places the call immediately):
 * a tapped phone number inside a message body should let the user confirm before dialing, unlike
 * [placeCall] (the Chat top bar's dedicated call button). */
fun openMessageLinkDialer(context: Context, phone: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
}
