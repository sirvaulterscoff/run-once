package runonce.exceptions

/**
 * Base class for any exception,
 * which allows to retry execution
 */
open class RetryableException(msg: String?, cause: Throwable?) : RuntimeException(msg, cause)
