package dev.pathland.view.data.coroutines

import dev.pathland.view.data.AsyncDataSource
import dev.pathland.view.data.Page
import dev.pathland.view.data.PagedDataSource
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

/** A `suspend` page loader bridged to the core `CompletionStage` paged source. */
fun <T> PagedDataSource.Companion.fromSuspend(
    scope: CoroutineScope,
    pageSize: Int,
    loader: suspend (offset: Int, limit: Int) -> Page<T>,
): PagedDataSource<T> = PagedDataSource.ofAsync(pageSize) { offset, limit ->
    scope.future { loader(offset, limit) }
}
