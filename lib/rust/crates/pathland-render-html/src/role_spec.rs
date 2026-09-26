//! Canonical `ROLE` → semantic HTML element mapping (spec/OPCODE.md §semantic
//! properties) + typography-driven heading levels, shared with the TypeScript
//! DOM client: the Rust renderer consumes these tables directly, and
//! `pathland-ts-codegen` emits the TS equivalents into `generated/role-spec.ts`
//! — so the semantic-tag / ARIA / heading mapping cannot drift between the
//! renderers.
//!
//! Rule summary:
//! * **`ROLE` is semantic structure only.** The catalog (see
//!   `pathland_core::role`) holds landmarks, headings, paragraphs and lists —
//!   interactive/control roles (button, link, checkbox, …) are NOT roles: they
//!   are intrinsic to the control components (`BUTTON` → `<button>`, `TOGGLE` →
//!   `<input type="checkbox">`, `MENU` → `role="menu"`), and a custom-looking
//!   button uses `Button` + `ButtonStyle`.
//! * A role **retags only a generic `div`/`span` shell** (see
//!   [`GENERIC_COMPONENTS`]) to its semantic element (`<nav>`, `<main>`, …).
//! * **Control components keep their native element** and emit no ARIA role
//!   from `ROLE`.
//! * **Headings** come from a `TEXT_STYLE` typography (LargeTitle…Headline →
//!   `<h1>`–`<h5>`, always a heading) or from `ROLE_HEADER` (default `<h2>`);
//!   raw font modifiers never imply a heading. Non-heading text defaults to
//!   `<span>`; `ROLE_PARAGRAPH` gives `<p>`.

use pathland_core::{component_type, role, text_style};

/// A role that maps onto a native semantic HTML element. On a generic shell the
/// role renders as [`RoleTag::tag`]; when the role is applied to a non-generic
/// component, [`RoleTag::aria`] is the ARIA fallback.
pub struct RoleTag {
    /// The `ROLE` enum code (see `pathland_core::role`).
    pub role: u8,
    /// The semantic HTML tag the role renders as on a generic shell.
    pub tag: &'static str,
    /// The equivalent ARIA `role` value (fallback when no tag applies).
    pub aria: &'static str,
}

/// Semantic roles with a native element. `HEADER` (heading) defaults to `<h2>`
/// — its level is raised by a `TEXT_STYLE` typography (see [`HEADING_STYLES`]).
/// `SUMMARY` maps to `<section>` (a named region).
pub const ROLE_TAGS: &[RoleTag] = &[
    RoleTag { role: role::HEADER, tag: "h2", aria: "heading" },
    RoleTag { role: role::PARAGRAPH, tag: "p", aria: "paragraph" },
    RoleTag { role: role::LIST, tag: "ul", aria: "list" },
    RoleTag { role: role::LIST_ITEM, tag: "li", aria: "listitem" },
    RoleTag { role: role::SUMMARY, tag: "section", aria: "region" },
    RoleTag { role: role::BANNER, tag: "header", aria: "banner" },
    RoleTag { role: role::NAVIGATION, tag: "nav", aria: "navigation" },
    RoleTag { role: role::MAIN, tag: "main", aria: "main" },
    RoleTag { role: role::CONTENT_INFO, tag: "footer", aria: "contentinfo" },
    RoleTag { role: role::COMPLEMENTARY, tag: "aside", aria: "complementary" },
    RoleTag { role: role::ARTICLE, tag: "article", aria: "article" },
    RoleTag { role: role::SECTION, tag: "section", aria: "region" },
    RoleTag { role: role::SEARCH, tag: "search", aria: "search" },
];

/// The `TEXT_STYLE` typographies that imply a heading element (`<h1>`–`<h5>`),
/// mapping style code → heading level. A title/headline style on a `TEXT` is
/// **always** a heading, even without `ROLE_HEADER`.
pub const HEADING_STYLES: &[(u8, u8)] = &[
    (text_style::LARGE_TITLE, 1),
    (text_style::TITLE, 2),
    (text_style::TITLE2, 3),
    (text_style::TITLE3, 4),
    (text_style::HEADLINE, 5),
];

/// The heading level a `TEXT_STYLE` typography implies, or `None` for the
/// non-heading styles (Subheadline, Body, Callout, Footnote, Caption, Caption2).
#[must_use]
pub fn heading_level(text_style: u8) -> Option<u8> {
    HEADING_STYLES.iter().find(|(s, _)| *s == text_style).map(|(_, level)| *level)
}

/// The heading element tag for a level (`1` → `h1` … `5` → `h5`).
#[must_use]
pub fn heading_tag(level: u8) -> &'static str {
    match level {
        1 => "h1",
        2 => "h2",
        3 => "h3",
        4 => "h4",
        _ => "h5",
    }
}

/// The effective tag for a generic `TEXT` shell: a heading `TEXT_STYLE` wins
/// (always a heading), then the semantic role (`ROLE_HEADER` → `<h2>`,
/// `ROLE_PARAGRAPH` → `<p>`, landmarks), else `None` (→ `<span>`).
#[must_use]
pub fn text_tag(kind: ShellKind, role: u8, text_style: Option<u8>) -> Option<&'static str> {
    if kind == ShellKind::Generic {
        if let Some(level) = text_style.and_then(heading_level) {
            return Some(heading_tag(level));
        }
    }
    semantic_tag(kind, role)
}

/// Components whose shell is a generic `div`/`span` — the only shells a
/// semantic role retags. Control components keep their native element.
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
/// control element). Control roles no longer exist in the catalog, so any
/// unknown/reserved code is a no-op.
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
    None
}