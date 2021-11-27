package runonce

import runonce.exceptions.OperationFailedException
import runonce.model.RunOnceRequest
import runonce.model.RunOnceSharedState
import reactor.core.publisher.Mono

/**
 * Shared state service, which is in
 * charge of storing information about
 * all idempotent requests. Its up
 * to implementation how to store the
 * information, but some considerations
 * should be took in account, when implementing
 * shared states:
 * 1. [startRequest] and [startPersistentRequest] both
 * should allow only one unique key to be stored.
 * For example, [java.util.concurrent.ConcurrentMap.computeIfAbsent]
 * is guaranteed to be invoked once for each unique key.
 * Another example is inserting a unique value in
 * DB. Be aware, that using DB for storing shared
 * state will require separate transactions for
 * each of this methods operations and main request
 * processing, as only in this case an idempotency
 * can realy be guaranteed.
 * 2. [startRequest] and [startPersistentRequest] both
 * should return previously stored record in case
 * of retry
 *
 */
interface SharedStateService {
    /**
     * Invoked each time for each incoming request.
     * Method should either return brand-new shared
     * state (if the request didn't run previously)
     * or existing one for previously run requests.
     * Generally speaking implementors should follow
     * those rules:
     * 1. Always return [RunOnceSharedState] in any case
     * 2. For previously completed requests return their
     * results in [RunOnceSharedState.response]
     * 3. For previously failed requests:
     * 3.1 in case of retryable failure return [RunOnceSharedState]
     * with [RunOnceSharedState.isRetry] set to true
     * 3.2 in case on non-retryable failure return [Mono]
     * containing [OperationFailedException]
     * 4. in case of operation still being executed
     * return [Mono] containing [runonce.exceptions.OperationAlreadyStartedException]
     * @param key idempotency key
     * @param request the request
     */
    fun <Request, Response, Result> startRequest(
        key: String,
        request: RunOnceRequest<Request, Response, Result>
    ): Mono<RunOnceSharedState<Request, Response>>

    /**
     * Method is identical to [startRequest] with one exception:
     * - result of [RunOnceRequest.request] should be evaluated
     * and stored in shared state for each new request
     * - load the result of previous invokation of [RunOnceRequest.request]
     * for each subsequent request with same key
     * @param key idempotency key
     * @param request the request
     */
    fun <Request, Response, Result> startPersistentRequest(
        key: String,
        request: RunOnceRequest<Request, Response, Result>
    ): Mono<RunOnceSharedState<Request, Response>>

    /**
     * Called for each request completed
     * successfully, no mater after retry or not.
     * This method should store [response] in such
     * a way, so that [startRequest] or [startPersistentRequest]
     * can obtain and return it
     * @param key idempotency key
     * @param response response
     */
    fun <Response> endRequest(key: String, response: Response): Mono<Void>

    /**
     * Mark this request as retryably failed
     * @param key idempotency key
     * @param request the request
     */
    fun <Request, Response, Result> markAsFailedRetryable(
        key: String,
        request: RunOnceRequest<Request, Response, Result>
    ): Mono<Void>

    /**
     * Mark this request as non-retryably failed
     * @param key idempotency key
     * @param request the request
     */
    fun <Request, Response, Result> markAsFailedNonRetryable(
        key: String,
        request: RunOnceRequest<Request, Response, Result>
    ): Mono<Void>
}
