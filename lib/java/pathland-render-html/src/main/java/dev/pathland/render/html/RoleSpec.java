package dev.pathland.render.html;

import dev.pathland.view.core.protocol.Components;

/**
 * Canonical {@code ROLE} → semantic HTML element mapping and
 * typography-driven heading levels, ported from the Rust {@code role_spec}
 * module.
 */
final class RoleSpec {

    enum ShellKind { GENERIC, NATIVE, OTHER }

    private static final class RoleTag {
        final int role;
        final String tag;
        final String aria;

        RoleTag(int role, String tag, String aria) {
            this.role = role;
            this.tag = tag;
            this.aria = aria;
        }
    }

    private static final RoleTag[] ROLE_TAGS = {
        new RoleTag(1, "h2", "heading"),
        new RoleTag(2, "p", "paragraph"),
        new RoleTag(3, "ul", "list"),
        new RoleTag(4, "li", "listitem"),
        new RoleTag(5, "section", "region"),
        new RoleTag(6, "header", "banner"),
        new RoleTag(7, "nav", "navigation"),
        new RoleTag(8, "main", "main"),
        new RoleTag(9, "footer", "contentinfo"),
        new RoleTag(10, "aside", "complementary"),
        new RoleTag(11, "article", "article"),
        new RoleTag(12, "section", "region"),
        new RoleTag(13, "search", "search"),
    };

    private static final int[] GENERIC_COMPONENTS = {
        Components.TEXT,
        Components.VSTACK,
        Components.HSTACK,
        Components.LAZY_VSTACK,
        Components.LAZY_HSTACK,
        Components.ZSTACK,
        Components.GRID,
        Components.LAZY_VGRID,
        Components.LAZY_HGRID,
        Components.SCROLLVIEW,
        Components.COLOR,
        Components.SHAPE,
        Components.SPACER,
    };

    private static final int[] NATIVE_ROLE_COMPONENTS = {
        Components.BUTTON,
        Components.TEXT_FIELD,
        Components.TEXT_EDITOR,
        Components.SLIDER,
        Components.TOGGLE,
        Components.IMAGE,
        Components.VIDEO,
        Components.PROGRESS_VIEW,
        Components.GAUGE,
        Components.DATE_PICKER,
        Components.COLOR_PICKER,
        Components.PICKER,
    };

    private RoleSpec() {}

    static ShellKind shellKind(int component) {
        if (contains(GENERIC_COMPONENTS, component)) {
            return ShellKind.GENERIC;
        }
        if (contains(NATIVE_ROLE_COMPONENTS, component)) {
            return ShellKind.NATIVE;
        }
        return ShellKind.OTHER;
    }

    static String semanticTag(ShellKind kind, int role) {
        if (kind != ShellKind.GENERIC) {
            return null;
        }
        for (RoleTag tag : ROLE_TAGS) {
            if (tag.role == role) {
                return tag.tag;
            }
        }
        return null;
    }

    static String ariaRole(ShellKind kind, int role) {
        if (kind == ShellKind.NATIVE) {
            return null;
        }
        for (RoleTag tag : ROLE_TAGS) {
            if (tag.role == role) {
                return kind == ShellKind.GENERIC ? null : tag.aria;
            }
        }
        return null;
    }

    static String textTag(ShellKind kind, int role, Integer textStyle) {
        if (kind == ShellKind.GENERIC && textStyle != null) {
            String heading = headingTag(textStyle);
            if (heading != null) {
                return heading;
            }
        }
        return semanticTag(kind, role);
    }

    private static String headingTag(int textStyle) {
        return switch (textStyle) {
            case 0 -> "h1";
            case 1 -> "h2";
            case 2 -> "h3";
            case 3 -> "h4";
            case 4 -> "h5";
            default -> null;
        };
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }
}
