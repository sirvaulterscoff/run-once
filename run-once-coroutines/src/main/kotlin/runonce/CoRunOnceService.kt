package runonce

import runonce.model.RunOnceRequest
import kotlinx.coroutines.reactive.awaitFirst

/**
 * Coroutines bridge for [RunOnceService.runOnce]
 */
suspend fun <Request, Response, Result> RunOnceService.coRunOnce(
    key: String,
    request: RunOnceRequest<Request, Response, Result>,
    retryableExceptionHandler: (Throwable) -> Boolean = ::defaultRetryableExceptionHandler
): Result {
    val service = this
    return service.runOnce(key, request, retryableExceptionHandler)
            .awaitFirst()
}