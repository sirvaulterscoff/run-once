package runonce

import com.fasterxml.jackson.databind.ObjectMapper
import runonce.exceptions.OperationAlreadyStartedException
import runonce.exceptions.OperationFailedException
import runonce.model.RunOnceExecutionStatus.*
import runonce.model.RunOnceRecord
import runonce.model.RunOnceRequest
import runonce.model.RunOnceSharedState
import org.slf4j.LoggerFactory.*
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.TransactionDefinition.*
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.support.DefaultTransactionDefinition
import reactor.core.publisher.Mono
import runonce.dao.RunOnceDao
import runonce.model.RunOnceExecutionStatus
import java.time.Instant
import kotlin.jvm.javaClass
import kotlin.let

/**
 * Shared state storage implementation
 * for [RunOnceService] using r2dbc and
 * jackson.
 */
internal class DatabaseSharedStateServiceImpl(
    private val dao: RunOnceDao,
    private val objectMapper: ObjectMapper,
    private val tm: R2dbcTransactionManager,
    private val logEventHandler: LogEventHandler? = null
) : SharedStateService {
    private val log = getLogger(javaClass)

    override fun <Request, Response, Result> startRequest(
        key: String,
        request: RunOnceRequest<Request, Response, Result>
    ): Mono<RunOnceSharedState<Request, Response>> {
        return newRequest(request, key, false)
    }

    override fun <Request, Response, Result> startPersistentRequest(
        key: String,
        request: RunOnceRequest<Request, Response, Result>
    ): Mono<RunOnceSharedState<Request, Response>> {
        return newRequest(request, key, true)
    }

    override fun <Response> endRequest(key: String, response: Response): Mono<Void> {
        logEventHandler?.markAsCompleted(key, response)
        val td =
            DefaultTransactionDefinition(PROPAGATION_REQUIRES_NEW)
        td.setName("RunOnce#endRequest")
        val op = TransactionalOperator.create(tm, td)
        return dao
            .finish(
                key, objectMapper.writeValueAsString(response),
                COMPLETED
            )
            .`as`(op::transactional)
            .then()
    }

    override fun <Request, Response, Result> markAsFailedRetryable(
        key: String,
        request: RunOnceRequest<Request, Response, Result>
    ): Mono<Void> {
        logEventHandler?.markAsRetryable(key, request)

        val td =
            DefaultTransactionDefinition(PROPAGATION_REQUIRES_NEW)
        td.setName("RunOnce#markAsFailed")
        val op = TransactionalOperator.create(tm, td)
        return dao
            .finish(key, null, FAILED_RETRYABLE)
            .`as`(op::transactional)
            .then()
    }

    override fun <Request, Response, Result> markAsFailedNonRetryable(
        key: String,
        request: RunOnceRequest<Request, Response, Result>
    ): Mono<Void> {
        logEventHandler?.markAsNonRetryable(key, request)
        val td =
            DefaultTransactionDefinition(PROPAGATION_REQUIRES_NEW)
        td.setName("RunOnce#markAsFailed")
        val op = TransactionalOperator.create(tm, td)
        return dao
            .finish(key, null, FAILED_NON_RETRYABLE)
            .`as`(op::transactional)
            .then()
    }

    private fun <Request, Response, Result> newRequest(
        request: RunOnceRequest<Request, Response, Result>,
        key: String,
        serializeRequest: Boolean
    ): Mono<RunOnceSharedState<Request, Response>> {
        val td =
            DefaultTransactionDefinition(PROPAGATION_REQUIRES_NEW)
        td.setName("RunOnce#newRequest")
        val op = TransactionalOperator.create(tm, td)
        val insertIfNew = Mono.just(request)
            .flatMap { it.request }
            .flatMap { req ->
                val requestJson = if (serializeRequest) {
                    objectMapper.writeValueAsString(req)
                } else null
                val record = RunOnceRecord(
                    id = key,
                    startedAt = Instant.now(),
                    status = INITIAL,
                    request = requestJson
                )
                insertNewOrLoad(record, key, request)
            }
        return dao
            .load(key)
            .flatMap { record ->
                createSharedState(key, record, request)
            }.switchIfEmpty(
                insertIfNew
            ).`as`(op::transactional)
    }

    private fun <Request, Response, Result> insertNewOrLoad(
        record: RunOnceRecord,
        key: String,
        request: RunOnceRequest<Request, Response, Result>
    ): Mono<RunOnceSharedState<Request, Response>> {
        return dao
            .insert(record)
            .thenReturn(record)
            .onErrorResume { t ->
                val mono: Mono<out RunOnceRecord>? = if (t is DataIntegrityViolationException) {
                    logEventHandler?.logRequestExists(key)
                    dao.load(key)
                } else {
                    logEventHandler?.fatalFailedToStoreRequest(key, t)
                    Mono.error(t)
                }
                mono
            }
            .flatMap {
                createSharedState(key, it, request)
            }
    }

    private fun <Request, Response, Result> createSharedState(
        key: String,
        record: RunOnceRecord,
        request: RunOnceRequest<Request, Response, Result>
    ): Mono<RunOnceSharedState<Request, Response>> {
        logEventHandler?.logSharedState(key, record, request)
        return when (record.status) {
            INITIAL -> Mono.just(
                RunOnceSharedState(
                    request = request.request,
                    isRetry = false
                )
            )
            RUNNING -> {
                val now = Instant.now()
                logEventHandler?.operationIsMarkedAsRunning(key, record)
                if (now.compareTo(record.startedAt.plusMillis(request.ttl)) >= 0) {
                    retry(key, record, request, RUNNING)
                } else {
                    Mono.error(
                        OperationAlreadyStartedException(
                            record.id
                        )
                    )
                }
            }
            FAILED_NON_RETRYABLE -> Mono.error(
                OperationFailedException(record.id)
            )
            FAILED_RETRYABLE -> {
                retry(key, record, request, FAILED_RETRYABLE)
            }
            COMPLETED -> {
                logEventHandler?.logCompletedPreviously(key, record, request)
                Mono.just(
                    RunOnceSharedState<Request, Response>(
                        request = null,
                        response = Mono.defer {
                            Mono.just(
                                objectMapper.readValue(
                                    record.response,
                                    request.responseClass
                                )
                            )
                        },
                        isRetry = false
                    )
                )
            }
        }
    }

    private fun <Request, Response, Result> retry(
        key: String,
        record: RunOnceRecord,
        request: RunOnceRequest<Request, Response, Result>,
        retryStatus: RunOnceExecutionStatus
    ): Mono<RunOnceSharedState<Request, Response>> {
        logEventHandler?.logRetry(key, record, request)
        val requestBody = record.request?.let {
            Mono.just(objectMapper.readValue(it, request.requestClass!!))
        } ?: request.request

        return dao
            .run(key, retryStatus)
            .filter { it == 1 }
            .switchIfEmpty(
                Mono.error<Int>(
                    OperationAlreadyStartedException(
                        record.id
                    )
                )
                    .doOnSubscribe {
                        logEventHandler?.logRetryAlreadyRunning(key, record, request)
                    }
            )
            .thenReturn(RunOnceSharedState(request = requestBody, isRetry = true))
    }

    interface LogEventHandler {
        fun <Response> markAsCompleted(key: String, response: Response)
        fun <Request, Response, Result1> markAsRetryable(
            key: String,
            request: RunOnceRequest<Request, Response, Result1>
        )

        fun <Request, Response, Result1> markAsNonRetryable(
            key: String,
            request: RunOnceRequest<Request, Response, Result1>
        )

        fun logRequestExists(key: String)
        fun fatalFailedToStoreRequest(key: String, t: Throwable?)
        fun <Request, Response, Result1> logSharedState(
            key: String,
            record: RunOnceRecord,
            request: RunOnceRequest<Request, Response, Result1>
        )

        fun operationIsMarkedAsRunning(key: String, record: RunOnceRecord)
        fun <Request, Response, Result1> logCompletedPreviously(
            key: String,
            record: RunOnceRecord,
            request: RunOnceRequest<Request, Response, Result1>
        )

        fun <Request, Response, Result1> logRetry(
            key: String,
            record: RunOnceRecord,
            request: RunOnceRequest<Request, Response, Result1>
        )

        fun <Request, Response, Result1> logRetryAlreadyRunning(
            key: String,
            record: RunOnceRecord,
            request: RunOnceRequest<Request, Response, Result1>
        )
    }
}