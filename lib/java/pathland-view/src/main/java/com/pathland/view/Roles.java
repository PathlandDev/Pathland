package com.pathland.view;

/**
 * Protocol semantic-role codes for the {@code ROLE} property (mirrors
 * {@code pathland_core::constants::role} and {@code spec/OPCODE.md} §semantic
 * properties). The catalog is **semantic structure only** — interactive/control
 * roles (button, link, checkbox, …) are NOT roles: they are intrinsic to the
 * control components (a {@link Button} renders as a {@code <button>}, a
 * {@link Toggle} as an {@code <input type="checkbox">}, a {@link Menu} as
 * {@code role="menu"}), and a custom-looking button uses a {@link ButtonStyle}.
 * The web renderer maps each role onto its native element. Use with
 * {@link AccessibilityRole#of(int)}.
 */
public final class Roles {

    /** No semantic role (the default). */
    public static final int NONE = 0;
    /** A heading (ARIA `heading`; the HTML renderer uses `&lt;hN&gt;`, the level
     *  driven by a {@link TextStyle} typography when present, default
     *  `&lt;h2&gt;`). */
    public static final int HEADER = 1;
    /** A paragraph (HTML `&lt;p&gt;`). */
    public static final int PARAGRAPH = 2;
    /** A list container (HTML `&lt;ul&gt;`). */
    public static final int LIST = 3;
    /** An item within a list (HTML `&lt;li&gt;`). */
    public static final int LIST_ITEM = 4;
    /** A summarising/complementary region (HTML `&lt;section&gt;`). */
    public static final int SUMMARY = 5;
    /** Site banner (HTML `&lt;header&gt;`). */
    public static final int BANNER = 6;
    /** Site navigation (HTML `&lt;nav&gt;`). */
    public static final int NAVIGATION = 7;
    /** The page's main content (HTML `&lt;main&gt;`). */
    public static final int MAIN = 8;
    /** Site footer / content info (HTML `&lt;footer&gt;`). */
    public static final int CONTENT_INFO = 9;
    /** Complementary/aside content (HTML `&lt;aside&gt;`). */
    public static final int COMPLEMENTARY = 10;
    /** A self-contained article (HTML `&lt;article&gt;`). */
    public static final int ARTICLE = 11;
    /** A thematically grouped section (HTML `&lt;section&gt;`). */
    public static final int SECTION = 12;
    /** A search region (HTML `&lt;search&gt;`). */
    public static final int SEARCH = 13;

    /** Whether {@code code} is a defined semantic role. */
    public static boolean isDefined(int code) {
        return code >= NONE && code <= SEARCH;
    }

    private Roles() {}
}