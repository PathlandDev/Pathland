package com.pathland.view;

/**
 * Protocol accessibility-role codes for the {@code ROLE} semantic property
 * (mirrors {@code pathland_core::constants::role} and {@code spec/OPCODE.md}
 * §semantic properties). The first twenty are the classic accessibility set;
 * {@link #BANNER} and above are the landmark/structural roles a web renderer
 * maps onto native semantic HTML elements. Use with
 * {@link AccessibilityRole#of(int)}.
 */
public final class Roles {

    // ── Classic accessibility roles (0–19) ──────────────────────────────────
    /** No semantic role (the default). */
    public static final int NONE = 0;
    /** A button/pressable action. */
    public static final int BUTTON = 1;
    /** A link to another destination. */
    public static final int LINK = 2;
    /** A heading (ARIA `heading`; the HTML renderer uses `&lt;hN&gt;`). */
    public static final int HEADER = 3;
    /** Plain text content. */
    public static final int TEXT = 4;
    /** An image. */
    public static final int IMAGE = 5;
    /** A single-line text input. */
    public static final int TEXT_FIELD = 6;
    /** A continuous/stepped numeric range control. */
    public static final int SLIDER = 7;
    /** A toggle (switch/checkbox/button styles). */
    public static final int TOGGLE = 8;
    /** A checkbox. */
    public static final int CHECKBOX = 9;
    /** A radio button. */
    public static final int RADIO_BUTTON = 10;
    /** A discrete increment/decrement control. */
    public static final int STEPPER = 11;
    /** A tab within a tab bar. */
    public static final int TAB = 12;
    /** A tab bar (ARIA `tablist`). */
    public static final int TAB_BAR = 13;
    /** A list container (the HTML renderer uses `&lt;ul&gt;`). */
    public static final int LIST = 14;
    /** A 2D matrix grid. */
    public static final int GRID = 15;
    /** A scrollable content region. */
    public static final int SCROLL_VIEW = 16;
    /** A value the user can adjust. */
    public static final int ADJUSTABLE = 17;
    /** A summarising/complementary region (ARIA `region`). */
    public static final int SUMMARY = 18;
    /** A menu / pop-up button. */
    public static final int MENU = 19;

    // ── Landmark / structural roles (20+) ───────────────────────────────────
    /** Site banner (HTML `&lt;header&gt;`). */
    public static final int BANNER = 20;
    /** Site navigation (HTML `&lt;nav&gt;`). */
    public static final int NAVIGATION = 21;
    /** The page's main content (HTML `&lt;main&gt;`). */
    public static final int MAIN = 22;
    /** Site footer / content info (HTML `&lt;footer&gt;`). */
    public static final int CONTENT_INFO = 23;
    /** Complementary/aside content (HTML `&lt;aside&gt;`). */
    public static final int COMPLEMENTARY = 24;
    /** A self-contained article (HTML `&lt;article&gt;`). */
    public static final int ARTICLE = 25;
    /** A thematically grouped section (HTML `&lt;section&gt;`). */
    public static final int SECTION = 26;
    /** A search region (HTML `&lt;search&gt;`). */
    public static final int SEARCH = 27;
    /** An item within a list (HTML `&lt;li&gt;`). */
    public static final int LIST_ITEM = 28;
    /** A paragraph (HTML `&lt;p&gt;`). */
    public static final int PARAGRAPH = 29;

    private Roles() {}
}