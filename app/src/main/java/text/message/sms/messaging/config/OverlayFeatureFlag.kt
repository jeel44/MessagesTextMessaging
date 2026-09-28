package text.message.sms.messaging.config

/**
 * The overlay-permission + call-end feature flag.
 *
 * - true: current behavior -- onboarding asks for "draw over other apps"
 *   (OverlayPermissionScreen) and the call-end screen opens after each call.
 * - false: overlay + call-end disabled -- onboarding skips OverlayPermission (Welcome goes
 *   straight to SetDefaultSms), the overlay permission is never requested, and
 *   PhoneStateReceiver/CallEndTriggerService/CallEndActivity never show the call-end screen.
 *
 * Hardcoded for now: edit and rebuild. Private so nothing reads it except [OverlayFeatureFlag].
 */
private const val OVERLAY_AND_CALL_END_ENABLED = true

/**
 * The single read every call site goes through. A plain object rather than a Hilt singleton, so
 * the manifest receiver, the call-end service, CallEndActivity's static launcher and Compose all
 * read it the same way, with nothing to inject.
 *
 * When this moves to Remote Config, only [isEnabled] changes -- and it should resolve the value
 * once and cache it for the process, so onboarding can't switch paths mid-flow on a late fetch.
 */
object OverlayFeatureFlag {
    fun isEnabled(): Boolean = OVERLAY_AND_CALL_END_ENABLED
}
