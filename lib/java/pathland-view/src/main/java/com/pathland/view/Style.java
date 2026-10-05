package com.pathland.view;

/**
 * Marker for the DSL's style values (style protocols). A style supplies a
 * control's or view's content via {@code makeBody(Configuration)} — it never owns the
 * control's component or its interaction (spec DSL.md §5.7). A style is scoped down a
 * subtree with the matching {@code <Style>Mod} value through the generic environment
 * ({@link Environment}), nearest-wins.
 */
public interface Style {
}
