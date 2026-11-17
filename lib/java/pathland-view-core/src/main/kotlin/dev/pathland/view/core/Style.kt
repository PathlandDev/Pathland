package dev.pathland.view.core

/**
 * Marker for the DSL's style values (`ButtonStyle`/`LabelStyle`/`VideoStyle`).
 *
 * A style is a `ViewModifier` that installs itself in the environment and
 * receives the styled content through a configuration object.
 */
interface Style : ViewModifier
