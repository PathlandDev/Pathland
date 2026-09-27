package com.pathland.view;

/**
 * A reusable view modifier (SwiftUI {@code ViewModifier}). External developers wrap
 * any view with custom styling logic:
 *
 * <pre>{@code
 * View card = content.with(new CardStyle());
 * }</pre>
 */
@FunctionalInterface
public interface ViewModifier {

    /** Transform {@code content} into a new view (typically the styled body). */
    View body(View content);
}