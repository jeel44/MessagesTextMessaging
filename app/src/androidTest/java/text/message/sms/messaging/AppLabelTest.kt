package text.message.sms.messaging

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The home-screen launcher shows "Messages" (MainActivity's own label), while system screens that
 * show the application label -- the default-SMS role dialog, Default apps, App info -- show
 * "#Messages" (`<application android:label>`).
 */
@RunWith(AndroidJUnit4::class)
class AppLabelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager

    @Test
    fun launcherActivityLabelIsMessages() {
        val launcher = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)
        val entries = packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
        assertEquals("exactly one launcher entry", 1, entries.size)
        assertEquals("Messages", entries.single().loadLabel(packageManager).toString())
    }

    @Test
    fun applicationLabelIsHashMessages() {
        val appInfo = context.applicationInfo
        assertEquals(R.string.app_name, appInfo.labelRes)
        // The default (English) resource behind the label, whatever the device language is.
        val english = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        assertEquals("#Messages", context.createConfigurationContext(english).getString(appInfo.labelRes))
        // What the system actually shows -- app_name is translated, so only on an English device.
        assumeTrue("device language isn't English", Locale.getDefault().language == "en")
        assertEquals("#Messages", appInfo.loadLabel(packageManager).toString())
    }
}
