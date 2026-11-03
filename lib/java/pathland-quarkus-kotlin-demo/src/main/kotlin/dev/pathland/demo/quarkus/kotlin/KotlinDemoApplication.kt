package dev.pathland.demo.quarkus.kotlin

import dev.pathland.demo.ad.AdView
import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.server.MountedApp
import dev.pathland.server.PathlandApp
import dev.pathland.view.core.View
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * The entire app: the DSL-neutral Quarkus extension provides SSR, the per-app
 * WebSocket endpoint, sessions and state; this bean supplies a fresh Kotlin root.
 *
 * Two apps share the server: the [MusicPlayerView] at `/` and the [AdView] at `/ad`.
 * The player's Now Playing pane embeds the ad app via a `ProxyView`
 * (spec/PROXY.md) — a same-server proxy on a loopback WebSocket.
 */
@ApplicationScoped
class KotlinDemoApplication : PathlandApp {

    /** The `/ad` app's base URL — the proxy origin. */
    @field:ConfigProperty(name = "pathland.demo.ad-url", defaultValue = "http://localhost:8080/ad")
    lateinit var adUrl: String

    override fun newRoot(): View = MusicPlayerView.make(adUrl)

    /** The origin app mounted at `/ad`: the small sponsored-proxy ad app. */
    @Produces
    @Singleton
    fun adApp(): MountedApp = MountedApp.of("/ad", PathlandApp { AdView() })
}
