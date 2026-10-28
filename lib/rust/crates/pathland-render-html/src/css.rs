//! The renderer's built-in CSS — delivered inline in a `<style>` tag.
//!
//! The stylesheet is the generated HTML base design:
//!   - a **preflight reset** (vendored from Tailwind CSS, MIT — see
//!     `THIRD_PARTY_NOTICES`),
//!   - `:root` **design tokens** as CSS custom properties, named to match the
//!     protocol's design-token paths (e.g. `color.primary` → `--pl-color-primary`)
//!     so `SET_DESIGN_TOKEN` over the wire can retheme the renderer,
//!   - `.pathland-*` component defaults written against the tokens.
//!
//! Layout and per-node styling come from inline `style` (all opcode properties);
//! this bundle provides the component look + interactive states (hover/focus/
//! disabled) which cannot be expressed inline.
//!
//! Source of truth: `design/` (Style Dictionary tokens + `design/html/*.css`).
//! `design/build/base.css` is synced here by `scripts/sync-design-css.sh` and
//! committed so the crate packages the built-in design without a Node toolchain.
//! Do not edit `assets/base.css` by hand.

const CSS: &str = include_str!("../assets/base.css");
const CSS_NAME: &str = include_str!("../assets/base.css.name");

/// The generated base-design stylesheet (without the wrapping `<style>` tag).
#[must_use]
pub fn builtin_css() -> &'static str {
    CSS
}

/// The content-hashed asset name of [`builtin_css`] (e.g. `base-5f86423db422.css`).
/// Hosts serve the stylesheet at this name under `/_pathland/` so a new build
/// busts the immutable asset cache via a fresh URL.
#[must_use]
pub fn builtin_css_name() -> &'static str {
    CSS_NAME.trim()
}

/// The complete `<style>` block injected into every rendered document.
pub const STYLE: &str = concat!("<style>", include_str!("../assets/base.css"), "</style>");
