package dev.pathland.view.data

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Shared daemon executor for the library data sources (apps may inject their own). */
internal object DataExecutors {
    val SHARED_IO: Executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "pathland-data-io").apply { isDaemon = true }
    }
}
