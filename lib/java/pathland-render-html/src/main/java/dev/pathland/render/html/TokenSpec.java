package dev.pathland.render.html;

/**
 * Canonical design-token conventions (spec/TOKENS.md), ported from the Rust
 * {@code token_spec} module and shared with the DOM client.
 */
final class TokenSpec {
    static final String DARK_PREFIX = "dark.";
    static final String VAR_PREFIX = "--pl-";
    static final String FORCED_DARK_VAR_PREFIX = "--pl-dark-";
    static final String SPACE_FAMILY = "space.";

    static final String[] LENGTH_PREFIXES = {
        "space.",
        "radius.",
        "border.width.",
        "size.control.",
        "control.padding.",
        "control.height",
        "control.border.",
        "control.radius",
        "control.line-height",
        "control.focus.ring.",
        "control.icon.",
        "control.swatch.inset",
        "button.padding.",
        "button.border.",
        "button.radius",
        "button.height",
        "button.line-height",
        "button.focus.ring.",
        "input.padding.",
        "input.focus.ring.",
        "input.swatch.inset",
        "input.radius",
        "icon.",
        "toggle.gap",
        "toggle.checkbox.size",
        "toggle.switch.",
        "toggle.thumb.",
        "toggle.focus.ring.",
        "slider.gap",
        "slider.height",
        "slider.track.",
        "slider.thumb.",
        "slider.focus.ring.",
        "progress.height",
        "progress.radius",
        "gauge.height",
        "gauge.radius",
        "stepper.gap",
        "stepper.button.size",
        "stepper.border.width",
        "stepper.radius",
        "spinner.size",
        "spinner.border.width",
        "menu.min-width",
        "menu.border.width",
        "menu.radius",
        "picker.radius",
        "color.swatch.inset",
    };

    static final String[][] LENGTH_PREFIX_SUFFIX = {
        {"control.font.", ".size"},
        {"button.font.", ".size"},
        {"input.font.", ".size"},
        {"label.font.", ".size"},
        {"font.heading.", ".size"},
    };

    static final String[] LENGTH_EXACT = {"font.body.size", "font.caption.size"};

    static final String[] LENGTH_ELEVATION_SUFFIXES = {".radius", ".x", ".y", ".blur"};

    private TokenSpec() {}
}
