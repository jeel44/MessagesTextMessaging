package text.message.sms.messaging.di

import javax.inject.Qualifier

/** The IO dispatcher, injected rather than referenced directly so tests can substitute it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** The default dispatcher, for CPU-bound work such as parsing. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * A scope that lives as long as the process. Broadcast receivers use it to finish their work
 * after `onReceive` returns.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
