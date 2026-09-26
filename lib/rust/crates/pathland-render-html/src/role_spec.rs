//! Canonical `ROLE` → semantic HTML element mapping (spec/OPCODE.md §semantic
//! properties), shared with the TypeScript DOM client: the Rust renderer
//! consumes these tables directly, and `pathland-ts-codegen` emits the TS
//! equivalents into `generated/role-spec.ts` — so the semantic-tag / ARIA
//! mapping cannot drift between the renderers.
//!
//! Rule summary:
//! * A role **retags only a generic `div`/`span` shell** (see
//!   [`GENERIC_COMPONENTS`]) to its semantic element (`<nav>`, `<main>`, …).
//! * **Control components keep their native element** (`<button>`, `<input>`,
//!   `<label>`, `<select>`, `<textarea>`, …) and emit no ARIA role from `ROLE`
//!   (their native semantics already convey it; the toggle switch/checkbox
//!   roles are set by the component itself).
//! * The ARIA `role` attribute is emitted only when no semantic element applies
//!   and the component does not convey the role natively.

use pathland_core::{component_type, role};

/// A role that maps onto a native semantic HTML element. On a generic shell the
/// role renders as [`RoleTag::tag`]; when the role is applied to a non-generic
/// component (or no tag can be used), [`RoleTag::aria`] is the ARIA fallback.
pub struct RoleTag {
    /// The `ROLE` enum code (see `pathland_core::role`).
    pub role: u8,
    /// The semantic HTML tag the role renders as on a generic shell.
    pub tag: &'static str,
    /// The equivalent ARIA `role` value (fallback when no tag applies).
    pub aria: &'static str,
}

/// Roles with a native semantic element. `HEADER` (heading) renders as `<h2>`
/// for now — its level will be driven by the design system's typography tokens
/// once those land (the spec explicitly defers heading levels to the design
/// system). `SUMMARY` maps to `<section>` (a named region).
pub const ROLE_TAGS: &[RoleTag] = &[
    // Landmarks / structural.
    RoleTag { role: role::BANNER, tag: "header", aria: "banner" },
    RoleTag { role: role::NAVIGATION, tag: "nav", aria: "navigation" },
    RoleTag { role: role::MAIN, tag: "main", aria: "main" },
    RoleTag { role: role::CONTENT_INFO, tag: "footer", aria: "contentinfo" },
    RoleTag { role: role::COMPLEMENTARY, tag: "aside", aria: "complementary" },
    RoleTag { role: role::ARTICLE, tag: "article", aria: "article" },
    RoleTag { role: role::SECTION, tag: "section", aria: "region" },
    RoleTag { role: role::SEARCH, tag: "search", aria: "search" },
    RoleTag { role: role::LIST, tag: "ul", aria: "list" },
    RoleTag { role: role::LIST_ITEM, tag: "li", aria: "listitem" },
    RoleTag { role: role::PARAGRAPH, tag: "p", aria: "paragraph" },
    // Header (heading) → <hN>; interim default level.
    RoleTag { role: role::HEADER, tag: "h2", aria: "heading" },
    // Summary → a region landmark.
    RoleTag { role: role::SUMMARY, tag: "section", aria: "region" },
];

/// Roles with no dedicated element, mapped to an ARIA `role` attribute.
pub const ROLE_ARIA: &[(u8, &str)] = &[
    (role::BUTTON, "button"),
    (role::LINK, "link"),
    (role::TEXT, "text"),
    (role::IMAGE, "img"),
    (role::TEXT_FIELD, "textbox"),
    (role::SLIDER, "slider"),
    (role::TOGGLE, "switch"),
    (role::CHECKBOX, "checkbox"),
    (role::RADIO_BUTTON, "radio"),
    (role::STEPPER, "spinbutton"),
    (role::TAB, "tab"),
    (role::TAB_BAR, "tablist"),
    (role::GRID, "grid"),
    (role::SCROLL_VIEW, "scrollbar"),
    (role::ADJUSTABLE, "slider"),
    (role::MENU, "menu"),
];

/// Components whose shell is a generic `div`/`span` — the only shells a
/// semantic role may retag. Control components keep their native element.
pub const GENERIC_COMPONENTS: &[u16] = &[
    component_type::TEXT,
    component_type::VSTACK,
    component_type::HSTACK,
    component_type::LAZY_VSTACK,
    component_type::LAZY_HSTACK,
    component_type::ZSTACK,
    component_type::GRID,
    component_type::LAZY_VGRID,
    component_type::LAZY_HGRID,
    component_type::SCROLLVIEW,
    component_type::COLOR,
    component_type::SHAPE,
    component_type::SPACER,
];

/// Components whose native element conveys their role — they never emit an
/// ARIA role from the `ROLE` property.
pub const NATIVE_ROLE_COMPONENTS: &[u16] = &[
    component_type::BUTTON,
    component_type::TEXT_FIELD,
    component_type::TEXT_EDITOR,
    component_type::SLIDER,
    component_type::TOGGLE,
    component_type::IMAGE,
    component_type::PROGRESS_VIEW,
    component_type::GAUGE,
    component_type::DATE_PICKER,
    component_type::COLOR_PICKER,
    component_type::PICKER,
];

/// How a role interacts with an element's shell. Decided from the component
/// when known; the DOM client derives it from the element's tag when hydrating
/// (it has no component for SSR-provided nodes).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ShellKind {
    /// A generic `div`/`span` shell — the only shells a semantic role retags.
    Generic,
    /// A native control element (`<button>`, `<input>`, …) that conveys its own
    /// role — never retagged, never given an ARIA role from `ROLE`.
    Native,
    /// Any other element (composite shells, unknown) — never retagged, but an
    /// ARIA `role` attribute is emitted.
    Other,
}

/// Resolve a component to its shell kind.
#[must_use]
pub fn shell_kind(component: u16) -> ShellKind {
    if GENERIC_COMPONENTS.contains(&component) {
        ShellKind::Generic
    } else if NATIVE_ROLE_COMPONENTS.contains(&component) {
        ShellKind::Native
    } else {
        ShellKind::Other
    }
}

/// The semantic tag a `(shell kind, role)` pair resolves to, or `None` when the
/// element keeps its default shell tag.
#[must_use]
pub fn semantic_tag(kind: ShellKind, role: u8) -> Option<&'static str> {
    if kind != ShellKind::Generic {
        return None;
    }
    ROLE_TAGS.iter().find(|r| r.role == role).map(|r| r.tag)
}

/// The ARIA `role` attribute value to emit for `(shell kind, role)`, or `None`
/// when the element already conveys the role (a semantic tag or a native
/// control element).
#[must_use]
pub fn aria_role(kind: ShellKind, role: u8) -> Option<&'static str> {
    if kind == ShellKind::Native {
        return None;
    }
    if let Some(tag) = ROLE_TAGS.iter().find(|r| r.role == role) {
        // A generic shell is retagged to `tag` (conveying the role); any other
        // element falls back to the ARIA string.
        return if kind == ShellKind::Generic { None } else { Some(tag.aria) };
    }
    ROLE_ARIA.iter().find(|(r, _)| *r == role).map(|(_, aria)| *aria)
}