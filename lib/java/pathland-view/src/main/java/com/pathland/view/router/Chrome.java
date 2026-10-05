package com.pathland.view.router;

/**
 * The navigation chrome mode of a {@code NavigationContainer} (spec DSL.md §4.5,
 * the {@code NAV_CHROME} 0x201B enum).
 *
 * <p>{@link #PLATFORM_DEFAULT} is the default: the <b>renderer</b> supplies the
 * navigation chrome — the platform's native navigation container where one exists
 * (GTK {@code AdwNavigationView}, {@code NavigationStack}, Compose
 * {@code NavHost}) and a renderer-drawn back affordance where none exists (the
 * DOM renderer shows a back button once {@code NAV_DEPTH &gt; 1}).
 *
 * <p>{@link #CUSTOM} means the <b>developer owns all navigation UI</b>: they draw
 * their own back buttons/bars in the destinations and call
 * {@code router.back()}/{@code navigate(...)} directly; the renderer adds no
 * chrome (no native header-bar back button, no DOM back button).
 */
public enum Chrome {

    PLATFORM_DEFAULT(0),
    CUSTOM(1);

    private final int wire;

    Chrome(int wire) {
        this.wire = wire;
    }

    /** The wire enum code for {@code NAV_CHROME} (F32). */
    public int wire() {
        return wire;
    }
}