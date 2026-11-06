package dev.pathland.view.core

/**
 * Marks a Kotlin DSL receiver scope.
 *
 * When nested receiver scopes share this marker, an inner scope cannot
 * implicitly resolve members of an outer scope. This keeps view-building
 * blocks from accidentally capturing a surrounding router/config builder.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.BINARY)
annotation class PathlandDsl
