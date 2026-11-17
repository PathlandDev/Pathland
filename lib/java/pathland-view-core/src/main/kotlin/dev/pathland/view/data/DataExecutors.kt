package dev.pathland.view.data

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The shared blocking-I/O executor used by the library data sources.
 *
 * [AsyncDataSource]'s `ofBlocking` factory and [PagedDataSource]'s
 * `ofBlocking` factory run their blocking loaders (JDBC/JPA, a blocking REST
 * client, …) here via `CompletableFuture.supplyAsync`, so the work never blocks
 * the session actor. The pool is internal, shared by every data source in the
 * process, and uses daemon threads (`pathland-data-io`) so it never prevents
 * JVM shutdown.
 *
 * This executor is only for **blocking** loaders: the `ofAsync` /
 * `CompletionStage` variants run on their own completion threads. Results are
 * always applied back on the session actor via `SessionContext.submit`; the
 * executor never touches signals. Applications can pass their own executor
 * (for example a Quarkus managed executor or a virtual-thread executor)
 * instead of the default.
 */
internal object DataExecutors {
    val SHARED_IO: Executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "pathland-data-io").apply { isDaemon = true }
    }
}
