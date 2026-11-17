package dev.pathland.view.core

import java.util.Objects

/**
 * The immutable configuration of a `PROXY_VIEW`.
 *
 * Holds the origin server's base endpoint, the origin route to render, and the
 * name of the credential the proxy presents. All three are app-configured; the
 * origin endpoint is never taken from client input.
 */
data class ProxyConfig(
    val endpoint: String,
    val route: String?,
    val credential: String?,
) {
    init {
        Objects.requireNonNull(endpoint, "endpoint")
        require(endpoint.isNotBlank()) { "proxy endpoint must not be blank" }
    }

    fun endpoint(): String = endpoint
    fun route(): String = route ?: "/"
    fun credential(): String? = credential

    companion object {
        fun of(endpoint: String, route: String?, credential: String?): ProxyConfig =
            ProxyConfig(endpoint, if (route.isNullOrBlank()) "/" else route, credential)
    }
}
