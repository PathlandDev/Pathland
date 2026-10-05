package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

/**
 * A text title with an optional icon ({@code Label}). The label's parts are
 * handed to the active {@link LabelStyle}, which decides which render and how they are
 * arranged (spec DSL.md §5.7) — the default {@link DefaultLabelStyle} shows an
 * {@link HStack} of an optional {@link Image} and an optional {@link Text}. The title
 * is <em>always</em> the label's accessibility label, even when only the icon is shown
 * (so an icon-only label stays announced by screen readers). A blank title or icon is
 * suppressed (the part is passed as {@code null}).
 *
 * <p>The title/icon are each a single {@link Signal<String>} — bound parts re-emit only
 * that node's delta when the signal changes; static parts are sugar for constant
 * signals. Whether a part is <em>present</em> is decided at mount (label style is a
 * mount-time scope); issue #88 tracks re-shaping a subtree when a signal read during
 * render changes.
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
            style = DefaultLabelStyle.INSTANCE;
        }
        String title = titleSignal != null ? titleSignal.get() : null;
        String icon = iconSignal != null ? iconSignal.get() : null;

        View titleView = nonBlank(title) ? Text.of(titleSignal).with(LineLimit.of(1)) : null;
        View iconView = nonBlank(icon) ? Image.of(iconSignal) : null;

        View content = style.makeBody(new LabelStyle.Configuration(titleView, iconView));
        if (content == null) {
            content = Group.of();
        }
        if (nonBlank(title)) {
            content = content.with(AccessibilityLabel.of(titleSignal));
        }
        return content;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
