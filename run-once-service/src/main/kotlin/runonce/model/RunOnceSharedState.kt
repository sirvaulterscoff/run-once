package runonce.model

import reactor.core.publisher.Mono

/**
 * Shared state for [runonce.SharedStateService]
 *
 * @property request request
 * @property response response
 * @property isRetry should be true in case of retry
 * @author pobedenniy.alexey
 * @since 02.06.2020
 */
data class RunOnceSharedState<Request, Response>(
    val request: Mono<Request>? = null,
    val response: Mono<Response>? = null,
    val isRetry: Boolean = false
) {
    /**
     * Returns true is this request completed previously (
     * in that case [response] should be not-null, meaning
     * that we can't actually handle null result's normally.
     * But, anyway, [Mono] containing null value is not what
     * you normally willing to have)
     */
    fun hasResponse() = response != null
}