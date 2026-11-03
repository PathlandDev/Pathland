package dev.pathland.view.core.transport

/**
 * The platform's environment data: viewport size and the initial route, delivered
 * as `META::ENVIRONMENT` fields.
 */
class EnvironmentData(
    route: String?,
    val viewportWidth: Float,
    val viewportHeight: Float,
) {
    val route: String = normalize(route)

    fun route(): String = route
    fun viewportWidth(): Float = viewportWidth
    fun viewportHeight(): Float = viewportHeight

    companion object {
        @JvmStatic
        fun of(route: String?): EnvironmentData = EnvironmentData(route, -1f, -1f)

        private fun normalize(route: String?): String {
            var normalized = if (route.isNullOrBlank()) "/" else route
            if (!normalized.startsWith("/")) normalized = "/$normalized"
            return normalized
        }
    }
}
