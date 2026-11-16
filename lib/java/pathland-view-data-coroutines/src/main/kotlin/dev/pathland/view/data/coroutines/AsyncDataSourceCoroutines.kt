package dev.pathland.view.data.coroutines

import dev.pathland.view.data.AsyncDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future

/**
 * A `suspend` load-all loader bridged to the core `CompletionStage` API. The
 * loader runs in [scope]; the result is applied on the session actor by the
 * data source.
 */
fun <T> AsyncDataSource.Companion.fromSuspend(
    scope: CoroutineScope,
    loader: suspend () -> List<T>,
): AsyncDataSource<T> = AsyncDataSource.ofAsync { scope.future { loader() } }
