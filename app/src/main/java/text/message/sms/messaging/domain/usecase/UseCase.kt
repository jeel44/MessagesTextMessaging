package text.message.sms.messaging.domain.usecase

/**
 * Marker for a single-responsibility domain action.
 *
 * Every use case in this package exposes exactly one `operator fun invoke`, so call sites read
 * as `markRead(threadIds)` rather than `markRead.execute(threadIds)`.
 */
interface UseCase
