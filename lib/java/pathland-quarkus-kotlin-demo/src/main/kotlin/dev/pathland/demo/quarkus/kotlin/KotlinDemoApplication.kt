package dev.pathland.demo.quarkus.kotlin

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.server.PathlandApp
import dev.pathland.view.core.View
import jakarta.enterprise.context.ApplicationScoped

/**
 * The entire app: the DSL-neutral Quarkus extension provides SSR, the per-app
 * WebSocket endpoint, sessions and state; this bean supplies a fresh Kotlin root.
 */
@ApplicationScoped
class KotlinDemoApplication : PathlandApp {

    override fun newRoot(): View = MusicPlayerView()// KotlinDemoView()
}
