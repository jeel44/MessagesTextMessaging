package text.message.sms.messaging.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import text.message.sms.messaging.R
import text.message.sms.messaging.ads.AppOpenAdManager
import javax.inject.Inject

/** Where the Settings screen's About rows point. */
object AppLinks {
    const val PRIVACY_POLICY_URL: String = "https://sites.google.com/view/textmessagesapp/home"
    const val SUPPORT_EMAIL: String = "tarangsutariya3440@gmail.com"

    /** The published listing's package -- a literal, not `BuildConfig.APPLICATION_ID`, so a build
     * variant with an id suffix would still link to the real listing. */
    const val PACKAGE_NAME: String = "text.message.sms.messaging"
    const val PLAY_STORE_APP_URI: String = "market://details?id=$PACKAGE_NAME"
    const val PLAY_STORE_WEB_URL: String = "https://play.google.com/store/apps/details?id=$PACKAGE_NAME"
}

/**
 * Hands the user off to another app for the Settings screen's About rows: the privacy policy in
 * the browser, the Play Store listing, a feedback email.
 *
 * Each one tells [AppOpenAdManager] first that the trip out is self-initiated, so coming back
 * doesn't show a warm-resume App Open ad, and shows a toast instead of crashing when nothing on
 * the device can handle the intent. Pass the screen's own (Activity-backed) context, so the
 * target opens on top of this app's task.
 */
class ExternalLinks(private val markSelfInitiatedNavigation: () -> Unit) {

    @Inject
    constructor(appOpenAdManager: AppOpenAdManager) : this(appOpenAdManager::markSelfInitiatedNavigation)

    fun openPrivacyPolicy(context: Context) {
        launch(context, viewIntent(AppLinks.PRIVACY_POLICY_URL))
    }

    /** The Play Store app's listing page, or the listing's web page where there is no Play Store
     * app to take the `market:` link. */
    fun openPlayStoreListing(context: Context) {
        launch(context, viewIntent(AppLinks.PLAY_STORE_APP_URI), viewIntent(AppLinks.PLAY_STORE_WEB_URL))
    }

    /** `mailto:` with [Intent.ACTION_SENDTO] so only mail apps answer. The subject is both in the
     * URI and an extra -- mail apps differ on which one they read. */
    fun composeFeedbackEmail(context: Context) {
        val subject = context.getString(R.string.settings_feedback_email_subject)
        val intent = Intent(
            Intent.ACTION_SENDTO,
            "mailto:${AppLinks.SUPPORT_EMAIL}?subject=${Uri.encode(subject)}".toUri(),
        ).putExtra(Intent.EXTRA_SUBJECT, subject)
        launch(context, intent)
    }

    private fun viewIntent(uri: String) = Intent(Intent.ACTION_VIEW, uri.toUri())

    private fun launch(context: Context, vararg intents: Intent) {
        launchFirstAvailable(
            candidates = intents.asList(),
            markSelfInitiatedNavigation = markSelfInitiatedNavigation,
            start = context::startActivity,
            onNoAppFound = {
                Toast.makeText(context, R.string.external_link_no_app_found, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/**
 * Starts the first of [candidates] the device has an app for, in order -- [start] throwing
 * [ActivityNotFoundException] moves on to the next -- and calls [onNoAppFound] if none did.
 * [markSelfInitiatedNavigation] runs once, before the first attempt. Generic over the candidate
 * type so plain-JVM tests can drive it without building a real [Intent].
 */
internal fun <T> launchFirstAvailable(
    candidates: List<T>,
    markSelfInitiatedNavigation: () -> Unit,
    start: (T) -> Unit,
    onNoAppFound: () -> Unit,
) {
    markSelfInitiatedNavigation()
    for (candidate in candidates) {
        try {
            start(candidate)
            return
        } catch (e: ActivityNotFoundException) {
            // Nothing handles this one; try the next.
        }
    }
    onNoAppFound()
}
