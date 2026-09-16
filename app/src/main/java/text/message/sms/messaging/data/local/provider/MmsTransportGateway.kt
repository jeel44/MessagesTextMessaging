package text.message.sms.messaging.data.local.provider

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps `SmsManager`'s MMS transport calls (API 21+, well within this app's minSdk 26) so the
 * rest of the app never has to speak HTTP or read an APN table itself. `SmsManager` already
 * knows the carrier's MMSC address, proxy and data-connection requirements for the active SIM
 * -- that is precisely the part a hand-rolled APN/HTTP client would have to duplicate, so this
 * app never builds one. What `SmsManager` does *not* do is understand the PDU bytes themselves;
 * that stays this app's job, in `data.local.provider.mms`.
 *
 * Both transport calls hand the platform's MMS service a `content://` Uri rather than raw
 * bytes, so it is granted explicit read/write access to a private, per-call file.
 */
@Singleton
class MmsTransportGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val defaultSmsManager: SmsManager,
) {

    suspend fun download(
        contentLocation: String,
        transactionId: String,
        subscriptionId: Int,
        downloadedIntent: PendingIntent,
    ): Uri = withContext(Dispatchers.IO) {
        val file = transportFile("dl_${transactionId.hashCode()}")
        val uri = grantedUriFor(file)
        smsManagerFor(subscriptionId)
            .downloadMultimediaMessage(context, contentLocation, uri, null, downloadedIntent)
        uri
    }

    suspend fun send(
        messageId: Long,
        pduBytes: ByteArray,
        subscriptionId: Int,
        sentIntent: PendingIntent,
    ): Uri = withContext(Dispatchers.IO) {
        val file = transportFile("send_$messageId")
        file.writeBytes(pduBytes)
        val uri = grantedUriFor(file)
        smsManagerFor(subscriptionId).sendMultimediaMessage(context, uri, null, null, sentIntent)
        uri
    }

    /** The file a completed download was written to, so the caller can read the PDU bytes
     * back out once [downloadedIntent] fires. */
    fun downloadedFile(transactionId: String): File = transportFile("dl_${transactionId.hashCode()}")

    fun cleanup(file: File) {
        runCatching { file.delete() }
    }

    private fun transportFile(name: String): File {
        val dir = File(context.cacheDir, "mms_transport").apply { mkdirs() }
        return File(dir, "$name.dat")
    }

    private fun grantedUriFor(file: File): Uri {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // The MMS transport runs as a different system package depending on OEM/AOSP lineage;
        // granting to both covers every device this app is likely to run on.
        listOf("com.android.mms.service", "com.android.phone").forEach { pkg ->
            runCatching { context.grantUriPermission(pkg, uri, flags) }
        }
        return uri
    }

    /** Android 12 moved SIM selection onto the instance; older releases use the static form. */
    private fun smsManagerFor(subscriptionId: Int): SmsManager = when {
        subscriptionId < 0 -> defaultSmsManager

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            defaultSmsManager.createForSubscriptionId(subscriptionId)

        else -> @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
    }
}
