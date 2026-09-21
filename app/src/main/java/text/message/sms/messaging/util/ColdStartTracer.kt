package text.message.sms.messaging.util

import android.os.Process
import android.os.SystemClock
import android.util.Log
import text.message.sms.messaging.BuildConfig

/**
 * TEMPORARY instrumentation for the "why does `adb shell am start -W` stay ~2.3s even after the
 * splash's artificial delay was removed" investigation -- every [mark] logs how long the process
 * has been alive ([Process.getStartElapsedRealtime]) at that point, so a logcat filter on tag
 * "ColdStart" across a handful of cold starts gives a millisecond-level timeline of exactly where
 * pre-first-frame time is actually going, instead of guessing. Meant to be deleted (or at minimum
 * have every call site removed) once that investigation concludes -- gated behind
 * [BuildConfig.DEBUG] in the meantime so it costs nothing in a release build even if left in.
 */
internal object ColdStartTracer {
    private const val TAG = "ColdStart"

    fun mark(label: String) {
        if (!BuildConfig.DEBUG) return
        val sinceStartMillis = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        Log.d(TAG, "sinceStart=${sinceStartMillis}ms $label")
    }
}
