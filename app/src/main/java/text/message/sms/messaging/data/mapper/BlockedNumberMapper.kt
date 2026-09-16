package text.message.sms.messaging.data.mapper

import text.message.sms.messaging.data.local.db.entity.BlockedNumberEntity
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.util.PhoneNumbers

fun BlockedNumberEntity.toDomain(): BlockedNumber = BlockedNumber(
    id = id,
    address = address,
    reason = reason,
    blockedAtMillis = blockedAtMillis,
)

fun BlockedNumber.toEntity(): BlockedNumberEntity = BlockedNumberEntity(
    id = id,
    address = address,
    normalizedAddress = PhoneNumbers.normalize(address),
    reason = reason,
    blockedAtMillis = blockedAtMillis,
)
