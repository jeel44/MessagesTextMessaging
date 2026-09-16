package text.message.sms.messaging.data.local.db.converter

import androidx.room.TypeConverter
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder

/**
 * Stores domain enums as their names rather than their ordinals, so reordering an enum
 * never silently reinterprets existing rows.
 */
class MessagingConverters {

    @TypeConverter
    fun channelToString(value: MessageChannel): String = value.name

    @TypeConverter
    fun stringToChannel(value: String): MessageChannel = MessageChannel.valueOf(value)

    @TypeConverter
    fun folderToString(value: MessageFolder): String = value.name

    @TypeConverter
    fun stringToFolder(value: String): MessageFolder = MessageFolder.valueOf(value)

    @TypeConverter
    fun deliveryStateToString(value: DeliveryState): String = value.name

    @TypeConverter
    fun stringToDeliveryState(value: String): DeliveryState = DeliveryState.valueOf(value)

    @TypeConverter
    fun blockReasonToString(value: BlockReason): String = value.name

    @TypeConverter
    fun stringToBlockReason(value: String): BlockReason = BlockReason.valueOf(value)
}
