package runonce.exceptions


/**
 * Exception is thrown if the request with
 * same [key] is still being processed
 * @author pobedenniy.alexey
 * @since 02.06.2020
 */
class OperationAlreadyStartedException(
    val key: String
) : RetryableException("Request with key $key is still executing", null)