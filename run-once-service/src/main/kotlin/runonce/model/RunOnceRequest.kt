package runonce.model

import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.just
import runonce.exceptions.RetryableException

/**
 * Default timeout
 */
const val DEFAULT_RUNONCE_TTL = 30_000L

@Suppress("LongParameterList")
/**
 * Each request consists of three invokable parts:
 * - request preprocessing [RunOnceRequest.request].
 * This step is optional
 * and is used to calculated some data needed
 * by handling idempotent process.
 * - request handling [RunOnceRequest.invoke].
 * This is the main step
 * in which all processing should take place.
 * Will be run only for first-time requests
 * or previously failed with [RetryableException]
 * - response postprocessing [RunOnceRequest.postProcess].
 * This is invoked
 * each time request handling finishes, even
 * for requests that ended previously and has
 * not triggered request handling.
 *
 * @property request request preprocess handler. In case of [newPersistentRequest] will
 * be invoked only once per each request with same key
 * @property invoke request processing handler. For each subsequent run (in
 * case of retry), true will be passed as second argument
 * @property postProcess response post processing handler. Invoked each
 * time for every request
 * @property requestClass request class (should be used for serializing/
 * deserializing process
 * @property responseClass response class (should be used for serializing/
 * deserializing process
 * @property ttl operation timeout (ms). Value -1 means no timeout. Every subsequent
 * run for each request marked as running will return [runonce.exceptions.OperationAlreadyStartedException]
 * until [ttl] passes. After [ttl] passed the operation will be considered failed
 * and retry will be atempted
 * @property automaticTimeout if [ttl] is greater than 0 then  [Mono.timeout]
 * operator will be applied to reactive chain
 *
 */
sealed class RunOnceRequest<Request, Response, Result>(
    val request: Mono<Request>,
    val invoke: (Request, retry: Boolean) -> Mono<Response>,
    val postProcess: (Response, alreadyCompleted: Boolean) -> Mono<Result>,
    val requestClass: Class<Request>?,
    val responseClass: Class<Response>,
    val ttl: Long = DEFAULT_RUNONCE_TTL,
    val automaticTimeout: Boolean = true
) {
    companion object {
        /**
         * New persistent request [request]
         * @param request request prepocessing
         * @param invoke request processing
         * @param postProcess response postprocessing
         * @param ttl request timeout (-1 means no timeout)
         * @param automaticTimeout whether to apply [Mono.timeout] to reactive chain
         */
        inline fun <reified Request, reified Response, Result> newPersistentRequest(
            request: Mono<Request>,
            noinline invoke: (Request, Boolean) -> Mono<Response>,
            noinline postProcess: (Response, alreadyCompleted: Boolean) -> Mono<Result>,
            ttl: Long = DEFAULT_RUNONCE_TTL,
            automaticTimeout: Boolean = true
        ):
                PersistentRunOnceRequest<Request, Response, Result> {
            return PersistentRunOnceRequest(
                request, invoke, postProcess,
                Request::class.java, Response::class.java,
                ttl, automaticTimeout
            )
        }

        /**
         * New request [request]
         * @param request request prepocessing
         * @param invoke request processing
         * @param ttl request timeout (-1 means no timeout)
         * @param automaticTimeout whether to apply [Mono.timeout] to reactive chain
         * */
        inline fun <reified Request, reified Result> newPersistentRequest(
            request: Mono<Request>,
            noinline invoke: (Request, Boolean) -> Mono<Result>,
            ttl: Long = DEFAULT_RUNONCE_TTL,
            automaticTimeout: Boolean = true
        ):
                PersistentRunOnceRequest<Request, Result, Result> {
            return PersistentRunOnceRequest(
                request, invoke,
                { res, _ ->
                    just(res)
                }, Request::class.java,
                Result::class.java, ttl, automaticTimeout
            )
        }

        /**
         * Creates new request
         * @param request request prepocessing
         * @param invoke request processing
         * @param postProcess response postprocessing
         * @param ttl request timeout (-1 means no timeout)
         * @param automaticTimeout whether to apply [Mono.timeout] to reactive chain
         */
        inline fun <Request, reified Response, Result> newRequestWithPostProcess(
            request: Mono<Request>,
            noinline invoke: (Request, Boolean) -> Mono<Response>,
            noinline postProcess: (Response, alreadyCompleted: Boolean) -> Mono<Result>,
            ttl: Long = DEFAULT_RUNONCE_TTL,
            automaticTimeout: Boolean = true
        ):
                GenericRunOnceRequest<Request, Response, Result> {
            return GenericRunOnceRequest(
                request, invoke, postProcess, Response::class.java,
                ttl, automaticTimeout
            )
        }

        /**
         * @param request request prepocessing
         * @param invoke request processing
         * @param postProcess response postprocessing
         * @param ttl request timeout (-1 means no timeout)
         * @param automaticTimeout whether to apply [Mono.timeout] to reactive chain
         */
        inline fun <Request, reified Response> newRequest(
            request: Mono<Request>,
            noinline invoke: (Request, Boolean) -> Mono<Response>,
            noinline postProcess:
                (Response, alreadyCompleted: Boolean) -> Mono<Response> = { res, _ -> just(res) },
            ttl: Long = DEFAULT_RUNONCE_TTL,
            automaticTimeout: Boolean = true
        ): GenericRunOnceRequest<Request, Response, Response> {
            return GenericRunOnceRequest(
                request, invoke, postProcess, Response::class.java,
                ttl, automaticTimeout
            )
        }
    }
}

@Suppress("LongParameterList")
class PersistentRunOnceRequest<Request, Response, Result>(
    request: Mono<Request>,
    invoke: (Request, Boolean) -> Mono<Response>,
    postProcess: (Response, alreadyCompleted: Boolean) -> Mono<Result>,
    requestClass: Class<Request>?,
    responseClass: Class<Response>,
    ttl: Long = DEFAULT_RUNONCE_TTL,
    automaticTimeout: Boolean = true
) : RunOnceRequest<Request, Response, Result>(
    request,
    invoke,
    postProcess,
    requestClass,
    responseClass,
    ttl,
    automaticTimeout
)

@Suppress("LongParameterList")
class GenericRunOnceRequest<Request, Response, Result>(
    request: Mono<Request>,
    invoke: (Request, Boolean) -> Mono<Response>,
    postProcess: (Response, alreadyCompleted: Boolean) -> Mono<Result>,
    responseClass: Class<Response>,
    ttl: Long = DEFAULT_RUNONCE_TTL,
    automaticTimeout: Boolean = true
) : RunOnceRequest<Request, Response, Result>(request, invoke, postProcess, null, responseClass, ttl, automaticTimeout)
