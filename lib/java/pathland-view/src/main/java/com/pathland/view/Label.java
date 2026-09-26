package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

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
 * <p>The title/icon are each a single {@link Signal<String>} — bound parts
 * re-emit only that node's delta when the signal changes; static parts are sugar
 * for constant signals. Whether a part is <em>present</em> is decided at mount
 * (label style is a mount-time scope); issue #88 tracks re-shaping a subtree
 * when a signal read during render changes.
 */
public final class Label implements View {

    private final Signal<String> titleSignal;
    private final Signal<String> iconSignal;

    private Label(Signal<String> titleSignal, Signal<String> iconSignal) {
        this.titleSignal = titleSignal;
        this.iconSignal = iconSignal;
    }

    /** A title-only label (no icon). */
    public static Label of(String title) {
        return new Label(Signals.constant(title), null);
    }

    /** A label with a title and an icon. */
    public static Label of(String title, String icon) {
        return new Label(Signals.constant(title), Signals.constant(icon));
    }

    /** A title-only label whose title is bound to a reactive signal. */
    public static Label of(Signal<String> title) {
        return new Label(title, null);
    }

    /** A label with a reactive title and a static icon. */
    public static Label of(Signal<String> title, String icon) {
        return new Label(title, Signals.constant(icon));
    }

    /** A label with a static title and a reactive icon. */
    public static Label of(String title, Signal<String> icon) {
        return new Label(Signals.constant(title), icon);
    }

    /** A label whose title and icon are both bound to reactive signals. */
    public static Label of(Signal<String> title, Signal<String> icon) {
        return new Label(title, icon);
    }

    @Override
    public View body() {
        LabelStyle style = Environment.value(Environment.LABEL_STYLE).get();
        if (style == null) {
            style = LabelStyle.TITLE_AND_ICON;
        }
        String title = titleSignal != null ? titleSignal.get() : null;
        String icon = iconSignal != null ? iconSignal.get() : null;

        List<View> children = new ArrayList<>(2);
        if (nonBlank(icon) && style.showsIcon()) {
            children.add(Image.of(iconSignal));
        }
        if (nonBlank(title) && style.showsTitle()) {
            View text = Text.of(titleSignal).modifier(LineLimit.of(1));
            children.add(text);
        }

        View stack = HStack.of(Alignment.CENTER, 2f, children);
        if (nonBlank(title)) {
            stack = stack.modifier(AccessibilityLabel.of(titleSignal));
        }
        return stack;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}