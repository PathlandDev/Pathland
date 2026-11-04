package dev.pathland.view.core

import java.util.Objects

/** The configuration of a ProxyView. */
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
