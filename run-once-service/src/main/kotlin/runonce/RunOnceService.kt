package runonce

import runonce.exceptions.OperationAlreadyStartedException
import runonce.exceptions.OperationFailedException
import runonce.exceptions.RetryableException
import runonce.model.RunOnceRequest
import reactor.core.publisher.Mono

/**
 * Allows controlling simultaneous execution
 * of an idempotent process by fixating it's progress
 * using a shared state. In a microservice world
 * you often can't reliably guarantee exactly-once
 * behaviour, that's when this service come handy.
 * Imagine web-user clicks on a button, invoking
 * some back-end processing activity. Generally
 * speaking, there a multiple points of failure:
 * - user's request was not able to reach back-end
 * due to network failure
 * - user's request was received by back-end but
 * some server-side error happened and the user
 * was not notified due to network failure
 * - user's request was received and handled
 * successfully but user was not notified
 * due to network failure.
 *
 * Such cases leave client in uncertanity
 * about his request. From client perspective
 * neither success nor failure of request processing
 * can be guaranteed. In such cases it is vague
 * to handle such responses idempotently. Imagine
 * that process in question is money transfer, so
 * client performing request can't be sure he won't
 * be charged twice (if he repeats same request) neither
 * he can be sure that the payment got processed
 * successfully.
 *
 * In such cases client needs to create ands store
 * idempotent key for each request. If the request
 * fails due to some network error or client crash
 * client need to repeat the request using exactly
 * same:
 * - request data
 * - operation key (idempotency id)
 *
 * This service itself for each unique request
 * (using unique idempotent id) guarantees:
 * - only one request handling process will happen
 * - if the process was run previously service
 * returns the result of previous execution without
 * invoking processing logic
 * - if the process ended with error previously
 * service will either rerun it (if previously
 * caught expception was instance of [RetryableException]
 * or if [retryableExceptionHandler] param
 * returns true for exception
 * - if the process ended with non-retryable
 * error the error will be propaged to client
 *
 * In case of retry launched process will
 * receive information regarding the process
 * state (firs invocation or retry).
 *
 * Request handling is divided into three
 * steps:
 * - request preprocessing. This step is optional
 * and is used to calculated some data needed
 * by handling idempotent process. In some
 * cases we need to store some additional
 * data for each request, but calculate this
 * data only once for each request with same
 * key. In such cases we can use [RunOnceRequest.newPersistentRequest]
 * which stores the result of request processing
 * in shared state. For example this is
 * useful if you need to generation another
 * idempotent key for another outgoing request
 * performing during request handling
 * - request handling. This is the main step
 * in which all processing should take place.
 * Will be run only for first-time requests
 * or previously failed with [RetryableException]
 * - response postprocessing. This is invoked
 * each time request handling finishes, even
 * for requests that ended previously and has
 * not triggered request handling.
 *
 * @author pobedenniy.alexey
 * @since 29.05.2020
 */
interface RunOnceService {
    /**
     * This method returns [Mono], which contains
     * the result of the operation for the given
     * [key] if operation didn't run previously.
     * If the operation did run previously there
     * are several possible outcomes:
     * 1. request with same [key] is still market as running.
     * Method will throw [OperationAlreadyStartedException]
     * 2. request with same [key] failed with an error:
     * 2.a if error was not instance of [RetryableException], then
     * reactive stream will contain [OperationFailedException]
     * 2.b if error was instance of [RetryableException], then
     * request processing will take place, and invoke
     * [RunOnceRequest.request]
     * 3. request with same [key]  completed previously. In
     * that case the resulting [Mono] will contain result
     * of previous opertaion
     * @param key  idempotency key
     * @param request idempotent request
     * @param retryableExceptionHandler exception handler used
     * to calculate which exceptions should be considered as
     * retryable
     */
    fun <Request, Response, Result> runOnce(
        key: String,
        request: RunOnceRequest<Request, Response, Result>,
        retryableExceptionHandler: (Throwable) -> Boolean = this::defaultRetryableExceptionHandler
    ): Mono<Result>

    /**
     * Default exception processing which
     * considers all [RetryableException] inheritors
     * as exceptions that allows client to retry
     * request
     */
    fun defaultRetryableExceptionHandler(err: Throwable): Boolean {
        return err is RetryableException
    }
}
