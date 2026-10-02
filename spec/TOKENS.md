# Pathland Design Tokens

**Status:** Draft
**Last Updated:** October 1, 2026

---

## Purpose

Design tokens are the application-facing vocabulary for **look and feel** —
colors, typography, spacing, radii (roundings), border widths, elevations, and
control styling. They let an application **roll a full theme** — including a
**dark variant** — that is honored consistently across every renderer, while
leaving platform-native defaults and interaction-state styling to the renderer.

This document is the companion catalog for the [Design Token
System](./OPCODE.md#design-token-system). It defines:

- the **standard token catalog** (what renderers must understand),
- the **resolution contract** (override → default → parent → fallback),
- the **color-scheme contract** (base = light, `dark.` = dark), and
- the **renderer integration contract** (how future native renderers adopt it).

The wire encoding (`PARAMETER::SET_DESIGN_TOKEN`, the `DESIGN_TOKEN` value type)
lives in [OPCODE.md](./OPCODE.md). Modifier mapping lives in
[MODIFIERS.md](./MODIFIERS.md). The DSL surface is specified in
[DSL.md](./DSL.md).

> **Implementation status** is tracked per implementing project (a `status.md`
> in each protocol crate/library), **not** in this specification. This document
> defines the protocol contract only.

---

## Core Principle

> **The application owns the theme. The renderer owns the defaults, the scheme
> detection, and the interaction states.**

- The **application** declares a theme: token overrides (base values = the
  **light** design, `dark.`-prefixed values = the **dark** design) and, in a
  style property, a **token reference** instead of a literal value.
- The **renderer** supplies platform-appropriate defaults for the standard
  catalog (both light and dark), derives the **effective color scheme** from the
  platform (OS / browser / native theme), resolves token references, and owns
  hover/pressed/focus/disabled styling.

This preserves native look-and-feel by default and gives the application
cross-renderer consistency when it overrides tokens.

### Ownership

| Aspect | Owner |
|--------|-------|
| Theme values (base + `dark.*`) | Application |
| Color-scheme detection (light/dark) | Renderer (platform-derived) |
| Default token values (light + dark) | Renderer |
| Token resolution (override → default → fallback) | Renderer |
| Interaction states (hover/pressed/focus/disabled) | Renderer |
| Literal property values | Application |

---

## Token Identification

Tokens are identified by **dot-separated string paths** — `color.primary`,
`font.body.size`, `space.2` — case-sensitive, lowercase recommended. A `dark.`
prefix selects the **dark variant** of a token (`dark.color.primary`).

The path namespace is **open**: the standard catalog below is a contract the
renderer MUST implement; any path outside it is **application-defined** and
renderer-optional (the renderer falls back rather than erroring).

---

## Value Types

A token's value uses the standard [value types](./OPCODE.md#value-types) —
`COLOR` (packed `0xAARRGGBB`, sRGB), `F32`, `U32`, `I32`, `U8`, `STRING`, `ENUM`.

A **token reference** in a property uses the `DESIGN_TOKEN` value type (`0x08`):
`C` = arenaRef to the token path. A property carries **exactly one** value —
either a literal (`COLOR`/`F32`/…) or a token reference, never both (see
[References in properties](#references-in-properties)).

---

## Standard Token Catalog

The catalog is layered. **Tier 1 (core)** tokens are mandatory: a renderer MUST
provide light and dark defaults and resolve them. **Tier 2 (extended)** tokens
are recommended: a renderer SHOULD provide defaults, and an application MAY
override them; a renderer that cannot apply a Tier 2 token ignores it (the same
"allowed and ignored" rule as unapplicable modifiers).

Default values are **renderer-owned and platform-appropriate**; the tables below
name the tokens and their *meaning*, never concrete values. The renderer MUST
resolve tokens by name and MAY map them onto native semantic resources (e.g. the
platform's system text color for `color.text.primary`).

### Colors — Tier 1

| Token | Meaning |
|-------|---------|
| `color.primary` | Primary brand / accent fill |
| `color.secondary` | Secondary brand / accent fill |
| `color.background` | Document / canvas background |
| `color.surface` | Raised surface (cards, panels, popovers) |
| `color.text.primary` | Primary text color |
| `color.text.secondary` | Secondary / muted text color |
| `color.text.onPrimary` | Text placed on a `color.primary` fill |
| `color.border` | Default border / outline color |
| `color.accent` | Accent for controls (focus, selection, toggle fill) |

Tier 2: `color.separator` (hairline dividers), and status colors
`color.success`, `color.warning`, `color.danger`, `color.info`.

### Typography — Tier 1 core

| Token | Meaning |
|-------|---------|
| `font.body.size` | Body text size |
| `font.body.weight` | Body text weight (100–900) |
| `font.body.family` | Body font family (STRING) |

Tier 2: `font.body.lineHeight`, `font.caption.size`,
`font.heading.1.size`–`font.heading.6.size` (with matching `.weight`/`.family`
paths), `font.mono.family`.

### Spacing — Tier 1

Spacing is a **generative family** (Tailwind-v4 style): one base unit
multiplied by an arbitrary number.

| Token | Meaning |
|-------|---------|
| `space.base` | The spacing **unit** (F32) — override this to change the whole density |
| `space.<N>` | `space.base` × N, where N is any positive decimal (`space.1`, `space.2`, `space.0.5`, `space.3.75`) — **computed** by the resolver |

Rules:

1. The `space.<N>` family is **generative**, not independently valued: the
   renderer resolves `space.base` and multiplies by the numeric leaf. Individual
   members MUST NOT be overridden via `SET_DESIGN_TOKEN` — override `space.base`
   to control the whole scale.
2. The color-scheme layer and parent fallback apply to `space.base`, never to an
   individual `space.<N>` member (`dark.space.2` is not a token; the dark layer
   is `dark.space.base`).
3. A spacing/padding property MAY carry a `DESIGN_TOKEN` reference to any
   `space.<N>` path — e.g. a DSL expresses `.padding(space.2)` or `Space(2)`.
   Literal F32 spacing values remain valid and unthemed.

Tier 2: `size.control.xs` / `sm` / `md` / `lg` — standard control heights.

### Radius (roundings) — Tier 1

| Token | Meaning |
|-------|---------|
| `radius.xs` / `radius.sm` / `radius.md` / `radius.lg` / `radius.xl` | Corner radius scale (F32) |
| `radius.full` | Fully round (pill/circle) — renderer resolves to its "infinite" value |

### Borders — Tier 1 core

| Token | Meaning |
|-------|---------|
| `border.width.thin` | Default hairline/border width |

Tier 2: `border.width.thick`.

### Elevation — Tier 2

Shadows are composite; each component is its own token:

| Token | Meaning |
|-------|---------|
| `elevation.low.color` / `radius` / `x` / `y` / `blur` | Low elevation shadow |
| `elevation.high.color` / `radius` / `x` / `y` / `blur` | High elevation shadow |

### Opacity — Tier 2

| Token | Meaning |
|-------|---------|
| `opacity.disabled` | The renderer MAY apply this to disabled controls. The disabled **state** itself is the `ENABLED` property; its visual handling (including this opacity) remains renderer-owned. |

### Shared control core — Tier 1

Tokens every control-style component (buttons, inputs, switches, sliders, …)
shares. Renderers MUST resolve these; an individual token a renderer cannot
apply is ignored.

| Token | Meaning |
|-------|---------|
| `control.background` | Control resting background |
| `control.foreground` | Control content (label/icon) color |
| `control.border` | Control border color |
| `control.border.width` | Control border width |
| `control.radius` | Control corner radius |
| `control.padding.horizontal` / `control.padding.vertical` | Control inner padding |
| `control.height` | Control height (F32) |
| `control.font.size` / `control.font.weight` | Control text |
| `control.accent` | Control accent (fill, selection, focus) |

### Per-component tokens

Small sets; only where a component's semantics genuinely differ from
`control.*`. All Tier 1 unless noted.

**Buttons** (Tier 1): `button.background`, `button.foreground`, `button.border`,
`button.radius`, `button.padding`, `button.height`, `button.font.size`,
`button.font.weight`, `button.accent`.

**Text fields / editors** (Tier 1): `input.background`, `input.foreground`,
`input.placeholder`, `input.border`, `input.radius`, `input.font.size`.

**Toggle / switch** (Tier 1): `toggle.accent`, `toggle.track.off`,
`toggle.track.on`, `toggle.thumb`.

**Slider** (Tier 1): `slider.accent`, `slider.track`, `slider.thumb`.

**Progress / gauge** (Tier 1): `progress.track`, `progress.fill`,
`gauge.track`, `gauge.fill`.

**Stepper** (Tier 2): `stepper.accent`, `stepper.background`, `stepper.border`,
`stepper.radius`.

**Picker** (Tier 2): `picker.accent`, `picker.background`, `picker.border`,
`picker.radius`.

**Menu** (Tier 2): `menu.background`, `menu.foreground`, `menu.border`,
`menu.radius`.

**Label** (Tier 2): `label.foreground`, `label.font.size`.

---

## Color Schemes (Light / Dark)

Base token paths define the **light** design. A `dark.`-prefixed path defines
the **dark** design for the same token.

The renderer derives the **effective color scheme** from its platform's native
light/dark signal — it is **never** carried by the protocol.

### Rules

1. **Base = light.** `color.primary` is the light value; `dark.color.primary`
   is the dark value.
2. A renderer MUST provide **both** light and dark defaults for every Tier 1
   token (platform-appropriate). Dark mode therefore works even when the
   application only overrides base tokens.
3. The application rolls a full theme by overriding base values and — where the
   dark look must differ — `dark.*` values.
4. The renderer MUST **re-resolve** every token-referencing property when the
   effective scheme changes, and MAY animate the transition.
5. The `dark.` prefix is the first scheme prefix; the namespace is open so
   custom schemes could follow. Only `light` (base) and `dark` are
   standardized.

---

## Resolution

```
FUNCTION resolveToken(tokenPath, scheme):
    # Walk most-specific → least-specific, applying the dark layer first.
    layers = []
    if scheme == dark:
        layers += [dark.path, dark.parent(path), …dark.root]   # dark.* layer
    layers += [path, parent(path), …root]                       # base layer

    for candidate in layers:
        if appOverrides.has(candidate):
            return appOverrides[candidate]
    for candidate in layers:
        if rendererDefaults.has(candidate):
            return rendererDefaults[candidate]
    return fallbackValue(path)
```

Example — `color.primary` with the **dark** scheme active and only
`dark.color.primary` overridden:

1. `dark.color.primary` — app override → **used**.
2. (Unreached) `color.primary` app override, `dark.color.primary` renderer
   default, etc.

With the **light** scheme active:

1. `color.primary` — app override, else renderer default, else parent/fallback.
   The `dark.*` layer is skipped entirely.

### Generative token families

A **generative family** is a token rule rather than an independent value. Today
the `space` family is generative: `space.<N>` resolves by resolving `space.base`
(the scheme layer and parent fallback apply there) and multiplying by the
numeric leaf parsed from the path. This lets one override (`space.base`) drive
an arbitrarily fine spacing scale without per-step tokens. Only the `space`
family is generative today; other families could follow.

---

## References in properties

Any property whose value type is `COLOR` / `F32` / `STRING` / `ENUM` MAY instead
carry the **`DESIGN_TOKEN`** value type (`0x08`), with `C` = arenaRef to the
token path. The renderer resolves the reference against the token tables and the
current scheme, and re-resolves when the scheme changes.

- This is how a DSL expresses *semantic intent*: `.foregroundStyle(.primary)`
  or `Color.token("color.primary")` emits a token reference, not a packed color.
- A literal value and a token reference are mutually exclusive on a property.
- Because overrides and the scheme are renderer state, a token-typed property
  requires **no re-emission** when a token changes — the renderer re-resolves.

---

## Renderer Responsibilities

A renderer MUST:

1. Provide platform-appropriate **light and dark defaults** for all Tier 1
   tokens.
2. Accept `PARAMETER::SET_DESIGN_TOKEN` commands and store overrides.
3. Derive the effective color scheme from the platform and re-resolve on change.
4. Resolve token references (`DESIGN_TOKEN` value type) in properties.
5. Fall back to defaults when no override exists.
6. Never transmit visual styling rules in the protocol.

A renderer MAY:

- Map tokens onto native semantic resources (system colors, platform
  typography) for its defaults.
- **Ignore** individual tokens it cannot apply (Tier 2, or Tier 1 tokens
  without a native equivalent) — the "allowed and ignored" rule modifiers use.
- Quantize / approximate token values to platform capabilities (e.g. RGB565
  color depth on embedded displays, limited font sets).

An application MAY override any token via `SET_DESIGN_TOKEN` (base and `dark.*`)
and reference any token in a property; it MUST NOT assume specific default
values.

---

## Renderer Integration Contract

A future native renderer adopts the token system by implementing five pieces:

1. **Default token table** — Tier 1 light + dark defaults from native resources.
2. **Scheme detection** — the platform's native light/dark signal.
3. **Resolution** — the override → default → parent → fallback algorithm, with
   the `dark.*` layer keyed on the effective scheme.
4. **Property resolution** — any property carrying a `DESIGN_TOKEN` reference
   resolves to the concrete native value (e.g. a dynamic system color / theme
   resource / style value).
5. **Re-resolution** — on scheme change, re-resolve and update the rendered
   output; animate if native.

The same contract powers the reference renderers (`pathland-render-html`,
`pathland-render-gtk`, the JS DOM renderer) and is checked in their `status.md`
files.