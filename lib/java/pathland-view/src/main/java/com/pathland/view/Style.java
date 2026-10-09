package com.pathland.view;

/**
 * Marker for the DSL's style values (style protocols). A style supplies a
 * control's or view's content via {@code makeBody(Configuration)} — it never owns the
 * control's component or its interaction (spec DSL.md §5.7). A style **is** a
 * {@link ViewModifier} value ({@code .modifiers(MyButtonStyle)}): applying it scopes
 * the matching environment key down the wrapped subtree, nearest-wins.
 */
public interface Style extends ViewModifier {
}
