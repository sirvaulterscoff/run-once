package runonce

import reactor.core.publisher.Mono
import reactor.util.context.ContextView
import runonce.model.RunOnceRequest

/**
 * Use runOnce as reactive operator.
 * This is actualy a context sugar,
 * to wrap mono inside runonce block.
 * Needs a function to extract request
 * id, which receives [ContextView] as
 * an arg
 */
inline fun <reified T> Mono<T>.runOnce(runOnceService: RunOnceService,
                                       crossinline idExtractor: (ContextView) -> String) : Mono<T> {
    val that = this
    return Mono.deferContextual {
        cv ->
        val id = idExtractor(cv)
        runOnceService.runOnce(
            id,
            RunOnceRequest.newRequest<String, T>(
                Mono.just(id),
                {_, retry -> that }
            )
        )
    }
}