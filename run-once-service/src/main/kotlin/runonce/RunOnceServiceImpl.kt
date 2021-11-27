package runonce

import runonce.exceptions.OperationAlreadyStartedException
import runonce.exceptions.RetryableException
import runonce.exceptions.RetryableExceptionWrapper
import runonce.log.LogEventHandler
import runonce.model.GenericRunOnceRequest
import runonce.model.PersistentRunOnceRequest
import runonce.model.RunOnceRequest
import runonce.model.RunOnceSharedState
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeoutException

class RunOnceServiceImpl(
    private val sharedStateService: SharedStateService,
    private val logEventhandler: LogEventHandler? = null
) : RunOnceService {

    override fun <Request, Response, Result> runOnce(
        key: String,
        request: RunOnceRequest<Request, Response, Result>,
        retryableExceptionHandler: (Throwable) -> Boolean
    ): Mono<Result> {
        val sharedState = obtainSharedState(request, key)
        return sharedState
            .flatMap { ss ->
                val processResult = processInvocation(ss, key, request)
                val postProcessResult = postProcessResult(processResult, request, ss)
                postProcessResult
            }
            .onErrorResume {
                handleError(it, key, request, retryableExceptionHandler)
            }
    }

    private fun <Request, Response, Result> postProcessResult(
        processResult: Mono<Response>?,
        request: RunOnceRequest<Request, Response, Result>,
        ss: RunOnceSharedState<Request, Response>
    ) = processResult
        ?.flatMap { resp ->
            request.postProcess.invoke(resp, ss.hasResponse())
        }

    private fun <Request, Response, Result> processInvocation(
        sharedState: RunOnceSharedState<Request, Response>,
        key: String,
        request: RunOnceRequest<Request, Response, Result>,
    ) = if (sharedState.hasResponse()) {
        logEventhandler?.logCompletedEarlier(key)
        sharedState.response
    } else {
        if (sharedState.isRetry) {
            logEventhandler?.logRetry(key)
        } else {
            logEventhandler?.logStart(key)
        }
        sharedState
            .request
            ?.flatMap {
                request
                    .invoke(it, sharedState.isRetry)
                    .flatMap { resp ->
                        logEventhandler?.finish(key)
                        sharedStateService
                            .endRequest(key, resp)
                            .thenReturn(resp)
                    }
            }
            ?.transform {
                if (request.ttl > 0 && request.automaticTimeout) {
                    it.timeout(Duration.ofMillis(request.ttl))
                } else it
            }
    }

    private fun <Request, Response, Result> obtainSharedState(
        request: RunOnceRequest<Request, Response, Result>,
        key: String
    ): Mono<RunOnceSharedState<Request, Response>> {
        return when (request) {
            is GenericRunOnceRequest -> sharedStateService
                .startRequest(key, request)
            is PersistentRunOnceRequest -> sharedStateService
                .startPersistentRequest(key, request)
        }
    }

    private fun <Request, Response, Result> handleError(
        err: Throwable,
        key: String,
        request: RunOnceRequest<Request, Response, Result>,
        retryableExceptionHandler: (Throwable) -> Boolean
    ): Mono<Result>? {
        return when (err) {
            is OperationAlreadyStartedException -> Mono.error(err)
            is TimeoutException -> {
                logEventhandler?.timeout(key, err)
                markRetryable(key, request, err)
            }
            else -> {
                logEventhandler?.error(key, err)
                if (retryableExceptionHandler(err)) {
                    markRetryable(key, request, err)
                } else {
                    markNonRetryable(key, request, err)
                }
            }
        }
    }

    private fun <Request, Response, Result> markNonRetryable(
        key: String,
        request: RunOnceRequest<Request, Response, Result>,
        it: Throwable
    ): Mono<Result> {
        return sharedStateService
            .markAsFailedNonRetryable(key, request)
            .then(Mono.error(it))
    }

    private fun <Request, Response, Result> markRetryable(
        key: String,
        request: RunOnceRequest<Request, Response, Result>,
        ex: Throwable
    ): Mono<Result> {
        //We rethrow  [RetryableException]
        val targetEx = if (ex is RetryableException) {
            ex
        } else {
            RetryableExceptionWrapper(
                "Failed to process request with key $key",
                ex
            )
        }
        return sharedStateService
            .markAsFailedRetryable(key, request)
            .then(Mono.error(targetEx))
    }
}
