//! Canonical design-token conventions (spec/TOKENS.md), shared with the
//! TypeScript DOM client: the Rust renderer consumes these tables directly, and
//! `pathland-ts-codegen` emits the TS equivalents from them — so the
//! `--pl-*` naming, the `dark.` scheme prefix, the generative `space.<N>`
//! family, and the length-token rules cannot drift between the renderers.

/// The scheme prefix stripped when naming a CSS variable (`dark.color.primary`
/// overrides the same `--pl-color-primary` inside a dark media query).
pub const DARK_PREFIX: &str = "dark.";

/// The CSS custom-property prefix for every token variable.
pub const VAR_PREFIX: &str = "--pl-";

/// Prefix for the **explicit dark-value** variable family. Every canonical token
/// also exposes its dark-layer value at `--pl-dark-<path>` regardless of the
/// active scheme, so a `dark.`-prefixed reference can force the dark value
/// (spec/TOKENS.md).
pub const FORCED_DARK_VAR_PREFIX: &str = "--pl-dark-";

/// The generative spacing family: `space.<N>` resolves to
/// `calc(var(--pl-space-base) * N)`.
pub const SPACE_FAMILY: &str = "space.";

/// Token paths whose F32 values are CSS lengths (emitted with `px`): the value
/// renders with a `px` suffix when it matches any of these leading prefixes.
pub const LENGTH_PREFIXES: &[&str] = &[
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
];

/// Prefix/suffix length rules: a path matching `prefix…suffix` is a length.
pub const LENGTH_PREFIX_SUFFIX: &[(&str, &str)] = &[
    ("control.font.", ".size"),
    ("button.font.", ".size"),
    ("input.font.", ".size"),
    ("label.font.", ".size"),
    ("font.heading.", ".size"),
];

/// Exact-length token paths.
pub const LENGTH_EXACT: &[&str] = &["font.body.size", "font.caption.size"];

/// `elevation.<low|high>.<component>`: these suffixes are lengths.
pub const LENGTH_ELEVATION_SUFFIXES: &[&str] = &[".radius", ".x", ".y", ".blur"];

/// Characters preserved verbatim in a CSS variable name; every other character
/// maps to `-`. (ASCII alphanumerics plus `-` and `_`.)
pub const VAR_NAME_PRESERVED: &str = "alphanumeric, '-', '_'";