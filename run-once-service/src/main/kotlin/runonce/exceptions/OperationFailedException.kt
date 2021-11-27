package runonce.exceptions

/**
 * Exception thrown in case if previous request
 * with same [key] has generated non-retryable
 * exception
 * @author pobedenniy.alexey
 * @since 02.06.2020
 */
class OperationFailedException(val key: String) :
    RuntimeException("Request with $key previously completed with an error and can't be retried")