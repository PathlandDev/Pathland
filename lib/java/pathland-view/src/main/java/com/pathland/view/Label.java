package com.pathland.view;

import com.pathland.view.signal.Signal;

import java.util.ArrayList;
import java.util.List;

/**
 * A text title with an optional icon (SwiftUI {@code Label}), composed from the
 * existing primitives — an {@link HStack} of an {@link Image} and a {@link Text} —
 * so it is reusable anywhere a view is (buttons, menus, pickers, sidebar rows).
 *
 * <p>The {@link LabelStyle} scoped in the environment decides which parts render
 * (spec DSL.md §5.5): the default {@code TITLE_AND_ICON} shows both,
 * {@code TITLE_ONLY} drops the icon, {@code ICON_ONLY} drops the title. The title
 * is <em>always</em> the label's accessibility label, even when only the icon is
 * shown — so an icon-only label stays announced by screen readers. A blank title
 * or icon is suppressed.
 *
 * <p>The title/icon may be static or bound to a signal; bound text/source/label
 * re-emit only that node's delta when the signal changes. Whether a part is
 * <em>present</em> is decided at mount (label style is a mount-time scope); issue
 * #88 tracks re-shaping a subtree when a signal read during render changes.
 */
public final class Label implements View {

    private final String title;
    private final Signal<String> titleSignal;
    private final String icon;
    private final Signal<String> iconSignal;

    private Label(String title, Signal<String> titleSignal, String icon, Signal<String> iconSignal) {
        this.title = title;
        this.titleSignal = titleSignal;
        this.icon = icon;
        this.iconSignal = iconSignal;
    }

    /** A title-only label (no icon). */
    public static Label of(String title) {
        return new Label(title, null, null, null);
    }

    /** A label with a title and an icon. */
    public static Label of(String title, String icon) {
        return new Label(title, null, icon, null);
    }

    /** A title-only label whose title is bound to a reactive signal. */
    public static Label of(Signal<String> title) {
        return new Label(null, title, null, null);
    }

    /** A label with a reactive title and a static icon. */
    public static Label of(Signal<String> title, String icon) {
        return new Label(null, title, icon, null);
    }

    /** A label with a static title and a reactive icon. */
    public static Label of(String title, Signal<String> icon) {
        return new Label(title, null, null, icon);
    }

    /** A label whose title and icon are both bound to reactive signals. */
    public static Label of(Signal<String> title, Signal<String> icon) {
        return new Label(null, title, null, icon);
    }

    @Override
    public View body() {
        LabelStyle style = Environment.value(Environment.LABEL_STYLE).get();
        if (style == null) {
            style = LabelStyle.TITLE_AND_ICON;
        }
        String title = value(this.title, this.titleSignal);
        String icon = value(this.icon, this.iconSignal);

        List<View> children = new ArrayList<>(2);
        if (nonBlank(icon) && style.showsIcon()) {
            children.add(iconSignal != null ? Image.of(iconSignal) : Image.of(icon));
        }
        if (nonBlank(title) && style.showsTitle()) {
            View text = titleSignal != null ? Text.of(titleSignal) : Text.of(title);
            children.add(text.modifier(LineLimit.of(1)));
        }

        View stack = HStack.of(Alignment.CENTER, 2f, children);
        if (nonBlank(title)) {
            stack = stack.modifier(titleSignal != null
                    ? AccessibilityLabel.of(titleSignal)
                    : AccessibilityLabel.of(title));
        }
        return stack;
    }

    /** The current value: a static one, or the signal's current value. */
    private static String value(String staticValue, Signal<String> signal) {
        return signal != null ? signal.get() : staticValue;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}