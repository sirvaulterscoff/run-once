package runonce

import runonce.exceptions.OperationAlreadyStartedException
import runonce.exceptions.OperationFailedException
import runonce.exceptions.RetryableException
import runonce.model.RunOnceRequest
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.spring.SpringListener
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random.Default.nextInt

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [TestSharedStateConfiguration::class]
)
open class RunOnceServiceImplTest : StringSpec() {

    @Autowired
    lateinit var service: RunOnceService

    init {
        "runs separate operations normaly" {
            val firstExpectedItem = 2
            val secondExpectedItem = 11
            val firstOp = Mono.just(firstExpectedItem)
            val secondOp = Mono.just(secondExpectedItem)
            val firstOpKey = UUID.randomUUID().toString()
            val secondOpKey = UUID.randomUUID().toString()

            val firstResult = service
                .runOnce<Int, Int, Any>(
                     firstOpKey,
                    RunOnceRequest.newRequestWithPostProcess(
                        Mono.just(1),
                        { _, _ -> firstOp },
                        { x, _ -> Mono.just(x) },
                        10000,
                        false
                    )
                )
            val secondResult = service
                .runOnce(
                    secondOpKey,
                    RunOnceRequest.newRequestWithPostProcess<Int, Int, Int>(
                        Mono.just(1),
                        { _, _ -> secondOp },
                        { x, _ -> Mono.just(x) },
                        10000,
                        false
                    )
                )

            StepVerifier
                .create(firstResult)
                .expectNext(firstExpectedItem)
                .verifyComplete()

            StepVerifier
                .create(secondResult)
                .expectNext(secondExpectedItem)
                .verifyComplete()
        }

        "forbids second run with same key" {
            val proofOfWork = AtomicBoolean(false)
            val latch = CountDownLatch(1)
            val firstOp = Mono
                .never<Int>()
                .doOnSubscribe {
                    proofOfWork.compareAndSet(false, true)
                    latch.countDown()
                }
            val opKey = "some-fixed-key"

            val firstResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, _ -> firstOp }
                    ))
            val secondResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, _ -> firstOp }
                    ))
                .timeout(Duration.ofMillis(5000))

            firstResult
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()

            latch.await()
            proofOfWork.get() shouldBe true

            StepVerifier
                .create(secondResult)
                .expectError(OperationAlreadyStartedException::class.java)
                .verify()
        }

        "returns error for failed operation" {
            val handlerMock = mockk<Mono<Int>>()
            val firstOp = Mono
                .error<Int>(FailedTestOperationException())
            val opKey = "failed-op-key"
            val latch = CountDownLatch(1)

            val firstResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, _ -> firstOp }
                    ))
                .doOnTerminate { latch.countDown() }
            val secondResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, _ -> handlerMock }
                    ))

            StepVerifier
                .create(firstResult)
                .expectError(FailedTestOperationException::class.java)
                .verify()

            latch.await()
            StepVerifier
                .create(secondResult)
                .expectError()
                .verify()
        }

        "retries if prior operation failed with retryable exception" {
            val firstOp = Mono
                .error<Int>(FailedRetryableTestOperationException())
            val opKey = "failed-retryable-op-key"
            val latch = CountDownLatch(1)
            val expectedValue = 5432
            val secondOp = Mono.just(expectedValue)

            val firstResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, retry ->
                            retry shouldBe false
                            firstOp
                        }
                    ))
                .doOnTerminate { latch.countDown() }
            val secondResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, retry ->
                            retry shouldBe true
                            secondOp
                        }
                    ))

            StepVerifier
                .create(firstResult)
                .expectError(FailedRetryableTestOperationException::class.java)
                .verify()

            latch.await()
            StepVerifier
                .create(secondResult)
                .expectNext(expectedValue)
                .verifyComplete()
        }

        "retries allow only one retry process" {
            val firstOp = Mono
                .error<Int>(FailedRetryableTestOperationException())
            val opKey = "failed-retryable-only-once-key"
            val latch = CountDownLatch(1)
            val secondLatch = CountDownLatch(1)
            val secondOp = Mono.never<Int>()

            val firstResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, retry ->
                            retry shouldBe false
                            firstOp
                        }
                    ))
                .doOnTerminate { latch.countDown() }
            val secondResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, retry ->
                            retry shouldBe true
                            secondLatch.countDown()
                            secondOp
                        }
                    ))

            val thirdResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, retry ->
                            retry shouldBe true
                            secondOp
                        }
                    ))

            StepVerifier
                .create(firstResult)
                .expectError(FailedRetryableTestOperationException::class.java)
                .verify()

            latch.await()
            secondResult
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()
            secondLatch.await()

            StepVerifier
                .create(thirdResult)
                .expectError(OperationAlreadyStartedException::class.java)
                .verify()
        }

        "allows to retry running operation if its lease time expired" {
            val firstOp = Mono
                .never<Int>()
            val opKey = "timeout-op-key"
            val latch = CountDownLatch(1)
            val expectedValue = nextInt()

            val firstResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, _ ->
                            latch.countDown()
                            firstOp
                        }
                    ))
            val secondResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, _ -> Mono.just(expectedValue) },
                        ttl = 1, automaticTimeout = false
                    )
                )

            firstResult
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()

            latch.await(3_000, TimeUnit.MILLISECONDS)
            StepVerifier
                .create(secondResult)
                .expectNext(expectedValue)
                .verifyComplete()
        }
        "fails fast if prior operation failed with non-retryable exception" {
            val handlerMock = mockk<Mono<Int>>()
            val firstOp = Mono
                .error<Int>(FailedTestOperationException())
            val opKey = "fail-fast-op-key"
            val latch = CountDownLatch(1)

            val firstResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, _ -> firstOp }
                    ))
                .doOnTerminate { latch.countDown() }
            val secondResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<Int, Int>(
                        Mono.just(1),
                        { _, _ -> handlerMock }
                    ))

            StepVerifier
                .create(firstResult)
                .expectError(FailedTestOperationException::class.java)
                .verify()

            latch.await()
            StepVerifier
                .create(secondResult)
                .expectError(OperationFailedException::class.java)
                .verify()
            verify { handlerMock wasNot Called }
        }
        "returns response if prior operation completed" {
            val handlerMock = mockk<Mono<String>>()
            val requestData = UUID.randomUUID().toString()
            val expectedResponseData = UUID.randomUUID().toString()
            val opKey = "returns-response-op-key"
            val latch = CountDownLatch(1)

            val firstResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest<String, String>(
                        Mono.just(requestData),
                        { request, _ ->
                            request shouldBe requestData
                            Mono.just(expectedResponseData)
                        }
                    ))
                .doOnTerminate { latch.countDown() }
            val secondResult = service
                .runOnce(
                    opKey,
                    RunOnceRequest.newRequest(
                        Mono.just(requestData),
                        { request, _ ->
                            Mono.just(request)
                        },
                        { _, alreadyCompleted ->
                            if (!alreadyCompleted) handlerMock else Mono.just(expectedResponseData)
                        }
                    ))

            StepVerifier
                .create(firstResult)
                .expectNext(expectedResponseData)
                .verifyComplete()

            latch.await()
            StepVerifier
                .create(secondResult)
                .expectNext(expectedResponseData)
                .verifyComplete()
            verify { handlerMock wasNot Called }
        }
    }

    override fun listeners() = listOf(SpringListener)
    internal class FailedTestOperationException : RuntimeException()
    internal class FailedRetryableTestOperationException : RetryableException("Retryable exception", null)
}
