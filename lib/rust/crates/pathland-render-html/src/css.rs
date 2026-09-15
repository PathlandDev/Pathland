//! The renderer's built-in CSS — delivered inline in a `<style>` tag.
//!
//! This is our own design system. It includes:
//!   - a **preflight reset** (vendored from Tailwind CSS, MIT — see
//!     `THIRD_PARTY_NOTICES`),
//!   - `:root` **design tokens** as CSS custom properties, named to match the
//!     protocol's design-token paths (e.g. `color.primary` → `--pl-color-primary`)
//!     so `SET_DESIGN_TOKEN` over the wire can retheme the renderer,
//!   - `.pathland-*` component defaults written against the tokens.
//!
//! Layout and per-node styling come from inline `style` (all opcode properties);
//! this block provides the component look + interactive states (hover/focus/
//! disabled) which cannot be expressed inline.

/// The complete `<style>` block injected into every rendered document.
pub const STYLE: &str = r#"<style>
/* ===== Preflight reset (vendored from Tailwind CSS, MIT) ===== */
*, ::before, ::after { box-sizing: border-box; border-width: 0; border-style: solid; border-color: currentColor; }
::before, ::after { --tw-content: ''; }
html { line-height: 1.5; -webkit-text-size-adjust: 100%; -moz-tab-size: 4; tab-size: 4; font-family: var(--pl-font-sans); font-feature-settings: 'liga' 1, 'calt' 1; color-scheme: light dark; background-color: var(--pl-color-background); color: var(--pl-color-text-primary); }
body { margin: 0; line-height: inherit; }
hr { height: 0; color: inherit; border-top-width: 1px; }
abbr:where([title]) { text-decoration: underline dotted; }
h1, h2, h3, h4, h5, h6 { font-size: inherit; font-weight: inherit; }
a { color: inherit; text-decoration: inherit; }
b, strong { font-weight: bolder; }
code, kbd, samp, pre { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 1em; }
small { font-size: 80%; }
sub, sup { font-size: 75%; line-height: 0; position: relative; vertical-align: baseline; }
sub { bottom: -0.25em; } sup { top: -0.5em; }
table { text-indent: 0; border-color: inherit; border-collapse: collapse; }
button, input, optgroup, select, textarea { font-family: inherit; font-feature-settings: inherit; font-variation-settings: inherit; font-size: 100%; font-weight: inherit; line-height: inherit; letter-spacing: inherit; color: inherit; margin: 0; padding: 0; }
button, select { text-transform: none; }
button, [type='button'], [type='reset'], [type='submit'] { -webkit-appearance: button; background-color: transparent; background-image: none; }
:-moz-focusring { outline: auto; }
:-moz-ui-invalid { box-shadow: none; }
progress { vertical-align: baseline; }
::-webkit-inner-spin-button, ::-webkit-outer-spin-button { height: auto; }
[type='search'] { -webkit-appearance: textfield; outline-offset: -2px; }
::-webkit-search-decoration { -webkit-appearance: none; }
::-webkit-file-upload-button { -webkit-appearance: button; font: inherit; }
summary { display: list-item; }
blockquote, dl, dd, h1, h2, h3, h4, h5, h6, hr, figure, p, pre { margin: 0; }
fieldset { margin: 0; padding: 0; }
legend { padding: 0; }
ol, ul, menu { list-style: none; margin: 0; padding: 0; }
textarea { resize: vertical; }
input::placeholder, textarea::placeholder { opacity: 1; color: var(--pl-color-text-secondary); }
button, [role='button'] { cursor: pointer; }
:disabled { cursor: default; }
img, svg, video, canvas, audio, iframe, embed, object { display: block; vertical-align: middle; }
img, video { max-width: 100%; height: auto; }
[hidden] { display: none; }

/* ===== Adjust body ===== */
body  {
  display: flex;
  height: 100vh;
}

/* ===== Design tokens (protocol retheme via SET_DESIGN_TOKEN) ===== */
:root {
  /* Core colors (Tier 1, spec/TOKENS.md) */
  --pl-color-background: #ffffff;
  --pl-color-primary: #2563eb;
  --pl-color-secondary: #4f46e5;
  --pl-color-surface: #ffffff;
  --pl-color-text-primary: #111827;
  --pl-color-text-secondary: #6b7280;
  --pl-color-text-on-primary: #ffffff;
  --pl-color-border: #e5e7eb;
  --pl-color-accent: #2563eb;

  /* Spacing — generative family: space.<N> = space.base × N */
  --pl-space-base: 4px;

  /* Typography (Tier 1) */
  --pl-font-body-size: 16px;
  --pl-font-body-weight: 400;
  --pl-font-body-family: 'Inter', ui-sans-serif, system-ui, -apple-system, sans-serif;
  --pl-font-sans: var(--pl-font-body-family);

  /* Radius (Tier 1) */
  --pl-radius-xs: 0.125rem;
  --pl-radius-sm: 0.375rem;
  --pl-radius-md: 0.375rem;
  --pl-radius-lg: 0.5rem;
  --pl-radius-xl: 0.75rem;
  --pl-radius-full: 9999px;

  /* Borders (Tier 1) */
  --pl-border-width-thin: 1px;

  /* Shared control core (Tier 1) */
  --pl-control-background: #ffffff;
  --pl-control-foreground: #111827;
  --pl-control-border: #d1d5db;
  --pl-control-border-width: 1px;
  --pl-control-radius: 0.375rem;
  --pl-control-height: 2.25rem;
  --pl-control-padding-horizontal: 0.75rem;
  --pl-control-padding-vertical: 0.375rem;
  --pl-control-font-size: 0.875rem;
  --pl-control-font-weight: 600;
  --pl-control-accent: #2563eb;

  /* Button (Tier 1) */
  --pl-button-background: #ffffff;
  --pl-button-background-hover: #f9fafb;
  --pl-button-foreground: #111827;
  --pl-button-border: #d1d5db;
  --pl-button-border-width: 1px;
  --pl-button-radius: 0.375rem;
  --pl-button-padding: 0.375rem 0.75rem;
  --pl-button-font-size: 0.875rem;
  --pl-button-font-weight: 600;
  --pl-button-accent: #2563eb;

  /* Input / text field (Tier 1) */
  --pl-input-background: #ffffff;
  --pl-input-foreground: #111827;
  --pl-input-placeholder: #9ca3af;
  --pl-input-border: #d1d5db;
  --pl-input-radius: 0.375rem;
  --pl-input-font-size: 1rem;
  --pl-input-focus: #4f46e5;

  /* Elevation (Tier 2, composite shadows) */
  --pl-elevation-low-color: rgb(0 0 0 / 0.05);
  --pl-elevation-low-radius: 0px;
  --pl-elevation-low-x: 0px;
  --pl-elevation-low-y: 1px;
  --pl-elevation-low-blur: 2px;
  --pl-elevation-high-color: rgb(0 0 0 / 0.15);
  --pl-elevation-high-radius: 0px;
  --pl-elevation-high-x: 0px;
  --pl-elevation-high-y: 4px;
  --pl-elevation-high-blur: 12px;
}

/* InterVariable (variable font, loaded from the rsms.me CDN) for browsers that
   support it: one file covers every weight. Others fall back to static Inter. */
@supports (font-variation-settings: normal) {
  :root { --pl-font-body-family: 'InterVariable', 'Inter', ui-sans-serif, system-ui, -apple-system, sans-serif; }
}

/* ===== Buttons ===== */
.pathland-button {
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: var(--pl-button-radius);
  background-color: var(--pl-button-background);
  padding: var(--pl-button-padding);
  font-size: var(--pl-button-font-size); font-weight: var(--pl-button-font-weight); line-height: 1.25rem;
  color: var(--pl-button-foreground);
  box-shadow: inset 0 0 0 var(--pl-button-border-width) var(--pl-button-border),
    var(--pl-elevation-low-x) var(--pl-elevation-low-y) var(--pl-elevation-low-blur) var(--pl-elevation-low-radius) var(--pl-elevation-low-color);
  transition: background-color 120ms, box-shadow 120ms;
}
.pathland-button:hover { background-color: var(--pl-button-background-hover); }
.pathland-button:focus-visible { outline: 2px solid var(--pl-control-accent); outline-offset: 2px; }
.pathland-button:disabled { opacity: 0.5; pointer-events: none; }
@media (prefers-color-scheme: dark) {
  :root {
    --pl-color-background: #0f172a;
    --pl-color-primary: #60a5fa;
    --pl-color-secondary: #818cf8;
    --pl-color-surface: #111827;
    --pl-color-text-primary: #f9fafb;
    --pl-color-text-secondary: #9ca3af;
    --pl-color-text-on-primary: #0f172a;
    --pl-color-border: #374151;
    --pl-color-accent: #60a5fa;
    --pl-space-base: 4px;
    --pl-control-background: rgb(255 255 255 / 0.1);
    --pl-control-foreground: #f9fafb;
    --pl-control-border: rgb(255 255 255 / 0.15);
    --pl-control-accent: #60a5fa;
    --pl-button-background: rgb(255 255 255 / 0.1);
    --pl-button-background-hover: rgb(255 255 255 / 0.2);
    --pl-button-foreground: #ffffff;
    --pl-button-border: rgb(255 255 255 / 0.1);
    --pl-button-accent: #60a5fa;
    --pl-input-background: rgb(255 255 255 / 0.05);
    --pl-input-foreground: #ffffff;
    --pl-input-placeholder: #6b7280;
    --pl-input-border: rgb(255 255 255 / 0.1);
    --pl-input-focus: #6366f1;
    --pl-elevation-low-color: rgb(0 0 0 / 0.35);
    --pl-elevation-high-color: rgb(0 0 0 / 0.5);
  }
}

/* ===== Toggle switch / checkbox ===== */
.pathland-toggle { display: inline-flex; align-items: center; gap: 0.5rem; }
.pathland-toggle input[type='checkbox'] { accent-color: var(--pl-control-accent); width: 1rem; height: 1rem; }
.pathland-toggle input[role='switch'] {
  appearance: none; width: 2.75rem; height: 1.5rem; border-radius: 9999px;
  background-color: #d1d5db; cursor: pointer; position: relative;
  transition: background-color 150ms; flex: none;
}
.pathland-toggle input[role='switch']:checked { background-color: var(--pl-control-accent); }
.pathland-toggle input[role='switch']::after {
  content: ''; position: absolute; top: 0.125rem; left: 0.125rem;
  width: 1.25rem; height: 1.25rem; border-radius: 9999px; background: #fff;
  transition: left 150ms;
}
.pathland-toggle input[role='switch']:checked::after { left: 1.375rem; }
.pathland-toggle input[role='switch']:focus-visible { outline: 2px solid var(--pl-control-accent); outline-offset: 2px; }

/* ===== Slider ===== */
.pathland-slider { display: inline-flex; align-items: center; gap: 0.625rem; }
.pathland-slider input[type='range'] { flex: 1; accent-color: var(--pl-control-accent); }
.pathland-slider input[type='range']:focus-visible { outline: 2px solid var(--pl-control-accent); outline-offset: 2px; }

/* ===== Text field / editor ===== */
.pathland-textfield { display: inline-flex; align-items: center; gap: 0.5rem; }
.pathland-textfield .pathland-label { flex: none; }
.pathland-input {
  display: block;
  width: 100%;
  flex: 1 1 auto;
  min-width: 0;
  border-radius: var(--pl-input-radius);
  background-color: var(--pl-input-background);
  padding: 0.375rem 0.75rem;
  font-size: var(--pl-input-font-size);
  line-height: 1.5rem;
  color: var(--pl-input-foreground);
  outline: 1px solid var(--pl-input-border);
  outline-offset: -1px;
  transition: outline-color 120ms, box-shadow 120ms;
}
.pathland-input::placeholder { color: var(--pl-input-placeholder); }
.pathland-input:focus {
  outline: 2px solid var(--pl-input-focus);
  outline-offset: -2px;
}

/* ===== Stepper ===== */
.pathland-stepper { display: inline-flex; align-items: center; gap: 0.5rem; }
.pathland-stepper button { width: 1.75rem; height: 1.75rem; border-radius: var(--pl-radius-sm); border: 1px solid var(--pl-color-border); background: var(--pl-color-surface); }

/* ===== Progress / gauge / spinner ===== */
.pathland-spinner {
  width: 1.25rem; height: 1.25rem; border: 3px solid var(--pl-color-border);
  border-top-color: var(--pl-color-primary); border-radius: 9999px;
  animation: pathland-spin 0.8s linear infinite;
}
@keyframes pathland-spin { to { transform: rotate(360deg); } }
.pathland-gauge { display: block; height: 0.625rem; width: 100%; background: var(--pl-color-border); border-radius: 9999px; overflow: hidden; }
.pathland-gauge > div { height: 100%; background: var(--pl-color-primary); transition: width 150ms; }

/* ===== Menu (popover) ===== */
.pathland-menu { position: relative; display: inline-block; }
.pathland-menu-items {
  display: none; position: absolute; min-width: 10rem; background: var(--pl-color-surface);
  border: 1px solid var(--pl-color-border); border-radius: var(--pl-radius-md);
  box-shadow: var(--pl-elevation-high-x) var(--pl-elevation-high-y) var(--pl-elevation-high-blur) var(--pl-elevation-high-radius) var(--pl-elevation-high-color);
  z-index: 20;
}
.pathland-menu:hover .pathland-menu-items { display: block; }
</style>"#;
