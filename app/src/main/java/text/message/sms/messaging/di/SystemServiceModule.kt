package text.message.sms.messaging.di

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationManagerCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Platform services the data layer talks to, provided so they can be faked in tests. */
@Module
@InstallIn(SingletonComponent::class)
object SystemServiceModule {

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    /** `getSystemService(SmsManager)` only exists from Android 12; older releases use the
     * process-wide default instance. */
    @Provides
    @Singleton
    fun provideSmsManager(@ApplicationContext context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    @Provides
    @Singleton
    fun provideSubscriptionManager(@ApplicationContext context: Context): SubscriptionManager =
        context.getSystemService(SubscriptionManager::class.java)

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
    ): NotificationManagerCompat = NotificationManagerCompat.from(context)
}
