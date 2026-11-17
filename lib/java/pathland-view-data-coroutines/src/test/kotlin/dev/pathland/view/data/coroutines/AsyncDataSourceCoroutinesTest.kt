package dev.pathland.view.data.coroutines

import dev.pathland.view.data.AsyncDataSource
import dev.pathland.view.data.Page
import dev.pathland.view.data.PagedDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AsyncDataSourceCoroutinesTest {

    @Test
    fun suspendLoaderFeedsTheSource() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val source = AsyncDataSource.fromSuspend(scope) { listOf("a", "b") }
        withTimeout(2_000) {
            while (source.loading.get()) {
                delay(1)
            }
        }
        assertEquals(listOf("a", "b"), source.items.get())
        assertFalse(source.loading.get())
        source.close()
    }

    @Test
    fun suspendPagedLoaderFeedsTheSource() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val source = PagedDataSource.fromSuspend(scope, 2) { offset, limit ->
            val end = minOf(offset + limit, 5)
            Page((offset until end).map { "i$it" }, total = 5)
        }
        source.ensureLoaded(0, 1)
        assertEquals(5, source.total.get())
        assertEquals("i0", source.row(0))
        assertEquals("i1", source.row(1))
        source.close()
    }
}
