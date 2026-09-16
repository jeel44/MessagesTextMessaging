package text.message.sms.messaging.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint

/**
 * Handles `RESPOND_VIA_MESSAGE`, which the dialer fires when the user declines a call with a
 * canned reply. Declaring it is one of the requirements for holding the default SMS role.
 *
 * Sending the reply arrives with the send pipeline; the declaration has to exist first, or the
 * app is not offered as a default SMS handler at all.
 */
@AndroidEntryPoint
class QuickResponseService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
