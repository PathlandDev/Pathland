// Option A styling: the DOM renderer owns a bounded property → style/class map.
// The protocol stays renderer-agnostic; web-specific Tailwind class resolution
// is the HTML renderer's job (SSR). At runtime, STYLE deltas are applied as
// inline style (which overrides the SSR Tailwind class for the current value),
// literal colors via inline style, and semantic design tokens as CSS variables.

import * as P from "./constants";
import { argbToHex, argbToRgba, f32FromBits, fmtFloat } from "./format";
import { resolveTokenCssRef } from "./tokens";

type Handler = (el: HTMLElement, valueType: number, c: number) => void;

// --- value decoders (per value_type) ---

function f32(valueType: number, c: number): number {
  return valueType === P.VAL_F32 ? f32FromBits(c) : valueType === P.VAL_I32 ? c | 0 : c >>> 0;
}
function enumCode(valueType: number, c: number): number {
  return valueType === P.VAL_ENUM ? c & 0xff : Math.round(f32FromBits(c));
}
function isOn(valueType: number, c: number): boolean {
  return (valueType === P.VAL_U8 ? c & 0xff : c) !== 0;
}
function u32(c: number): number {
  return c >>> 0;
}

// --- accumulators (multi-part CSS that arrives across several deltas) ---

interface ShadowInfo {
  x: number;
  y: number;
  radius: number;
  color: string;
}
interface BorderInfo {
  width: number;
  color: string;
}
interface TransformInfo {
  translateX: number;
  translateY: number;
  rotate: number;
  scale: number;
}
interface DecorationInfo {
  underline: boolean;
  strikethrough: boolean;
}

const SHADOW = new WeakMap<HTMLElement, ShadowInfo>();
const BORDER = new WeakMap<HTMLElement, BorderInfo>();
const EDGES = new WeakMap<HTMLElement, number>();
const FILTERS = new WeakMap<HTMLElement, string[]>();
const TRANSFORM = new WeakMap<HTMLElement, TransformInfo>();
const DECORATION = new WeakMap<HTMLElement, DecorationInfo>();

function shadowOf(el: HTMLElement): ShadowInfo {
  let info = SHADOW.get(el);
  if (!info) {
    info = { x: 0, y: 0, radius: 0, color: "rgba(0,0,0,0.3)" };
    SHADOW.set(el, info);
  }
  return info;
}
function borderOf(el: HTMLElement): BorderInfo {
  let info = BORDER.get(el);
  if (!info) {
    info = { width: 0, color: "rgb(0,0,0)" };
    BORDER.set(el, info);
  }
  return info;
}
function transformOf(el: HTMLElement): TransformInfo {
  let info = TRANSFORM.get(el);
  if (!info) {
    info = { translateX: 0, translateY: 0, rotate: 0, scale: 1 };
    TRANSFORM.set(el, info);
  }
  return info;
}
function decorationOf(el: HTMLElement): DecorationInfo {
  let info = DECORATION.get(el);
  if (!info) {
    info = { underline: false, strikethrough: false };
    DECORATION.set(el, info);
  }
  return info;
}

function recomposeShadow(el: HTMLElement): void {
  const s = shadowOf(el);
  el.style.boxShadow = s.radius > 0 || s.x !== 0 || s.y !== 0
    ? `${fmtFloat(s.x)}px ${fmtFloat(s.y)}px ${fmtFloat(s.radius)}px ${s.color}`
    : "";
}
function recomposeFilter(el: HTMLElement): void {
  const parts = FILTERS.get(el) ?? [];
  el.style.filter = parts.join(" ");
}
function recomposeTransform(el: HTMLElement): void {
  const t = transformOf(el);
  const parts: string[] = [];
  if (t.translateX !== 0 || t.translateY !== 0) {
    parts.push(`translate(${fmtFloat(t.translateX)}px, ${fmtFloat(t.translateY)}px)`);
  }
  if (t.rotate !== 0) {
    parts.push(`rotate(${fmtFloat(t.rotate)}deg)`);
  }
  if (t.scale !== 1) {
    parts.push(`scale(${fmtFloat(t.scale)})`);
  }
  el.style.transform = parts.join(" ");
}
function recomposeDecoration(el: HTMLElement): void {
  const d = decorationOf(el);
  const parts: string[] = [];
  if (d.underline) {
    parts.push("underline");
  }
  if (d.strikethrough) {
    parts.push("line-through");
  }
  el.style.textDecoration = parts.join(" ");
}
function applyBorderEdges(el: HTMLElement): void {
  const b = borderOf(el);
  const edges = EDGES.get(el) ?? 0x0f;
  const side = (bit: number) => (edges & bit) !== 0 ? `${fmtFloat(b.width)}px solid ${b.color}` : "none";
  el.style.borderTop = side(0x01);
  el.style.borderLeft = side(0x02);
  el.style.borderBottom = side(0x04);
  el.style.borderRight = side(0x08);
}

// --- enum → CSS ---

export function alignmentCss(code: number): string {
  switch (code) {
    case P.ALIGN_LEADING: return "flex-start";
    case P.ALIGN_TRAILING: return "flex-end";
    case P.ALIGN_FILL: return "stretch";
    default: return "center";
  }
}
function textAlignCss(code: number): string {
  switch (code) {
    case P.TEXT_ALIGN_LEADING: return "left";
    case P.TEXT_ALIGN_TRAILING: return "right";
    default: return "center";
  }
}
function truncationCss(code: number): string {
  switch (code) {
    case P.TRUNCATION_HEAD: return "clip";
    default: return "ellipsis";
  }
}
function fontStyleCss(code: number): string {
  return code === P.FONT_STYLE_ITALIC ? "italic" : "normal";
}
function textCaseCss(code: number): string {
  switch (code) {
    case P.TEXT_CASE_UPPERCASE: return "uppercase";
    case P.TEXT_CASE_LOWERCASE: return "lowercase";
    default: return "none";
  }
}

// --- structural control variants (shell reconfiguration) ---

function applyToggleStyle(el: HTMLElement, code: number): void {
  const input = el.querySelector<HTMLInputElement>("input[type=checkbox]");
  if (!input) {
    return;
  }
  if (code === P.TOGGLE_STYLE_SWITCH) {
    input.setAttribute("role", "switch");
  } else if (code === P.TOGGLE_STYLE_CHECKBOX) {
    input.removeAttribute("role");
  } else {
    input.setAttribute("role", "checkbox");
    input.setAttribute("aria-pressed", input.checked ? "true" : "false");
  }
}

function applySecure(el: HTMLElement, on: boolean): void {
  const input = el.querySelector<HTMLInputElement>("input[type=text],input[type=password]");
  if (input) {
    input.type = on ? "password" : "text";
  }
}

function applyDatePickerMode(el: HTMLElement, code: number): void {
  const input = el.matches("input[type=date],input[type=time],input[type=datetime-local]")
    ? (el as HTMLInputElement)
    : el.querySelector<HTMLInputElement>("input[type=date],input[type=time],input[type=datetime-local]");
  if (!input) {
    return;
  }
  input.type = code === P.DATE_PICKER_MODE_TIME
    ? "time"
    : code === P.DATE_PICKER_MODE_DATE_AND_TIME
      ? "datetime-local"
      : "date";
}

function applyProgress(el: HTMLElement, value: number): void {
  const progress = el.matches("progress") ? (el as HTMLProgressElement) : el.querySelector("progress");
  if (progress) {
    progress.value = value;
  }
}

function applyIndeterminate(el: HTMLElement, on: boolean): void {
  const progress = el.matches("progress") ? (el as HTMLProgressElement) : el.querySelector("progress");
  if (progress) {
    if (on) {
      progress.removeAttribute("value");
    } else {
      progress.setAttribute("value", progress.value.toFixed(3));
    }
  }
}

function applyStepValue(el: HTMLElement, value: number): void {
  const range = el.querySelector<HTMLInputElement>("input[type=range]");
  if (range) {
    range.step = String(value);
  }
  const meta = el.querySelector<HTMLElement>(".pathland-stepper-range");
  if (meta) {
    meta.dataset.step = String(value);
  }
}

function applyRangeBounds(el: HTMLElement, property: number, value: number): void {
  const input = el.matches("input[type=range]")
    ? (el as HTMLInputElement)
    : el.querySelector<HTMLInputElement>("input[type=range]");
  if (input) {
    if (property === P.PROP_MIN_VALUE) {
      input.min = String(value);
    } else if (property === P.PROP_MAX_VALUE) {
      input.max = String(value);
    }
  }
  // Range bounds are also mirrored onto the composite shells that carry them as
  // `data-min`/`data-max` (stepper click math, gauge percentage), matching the
  // Rust SSR renderer's attributes. A gauge element IS the `.pathland-gauge`, so
  // the element itself is included alongside any descendant meta spans.
  const attr = property === P.PROP_MIN_VALUE ? "data-min" : "data-max";
  const metas: HTMLElement[] = [el];
  for (const meta of el.querySelectorAll<HTMLElement>(".pathland-stepper-range, .pathland-gauge")) {
    metas.push(meta);
  }
  for (const meta of metas) {
    if (meta.classList.contains("pathland-stepper-range") || meta.classList.contains("pathland-gauge")) {
      meta.setAttribute(attr, String(value));
    }
  }
}

// --- the full property table ---

const HANDLERS: Record<number, Handler> = {
  // Layout
  [P.PROP_SPACING]: (el, vt, c) => (el.style.gap = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_ALIGNMENT]: (el, vt, c) => (el.style.alignItems = alignmentCss(enumCode(vt, c))),
  [P.PROP_CONTENT_MARGINS]: (el, vt, c) => (el.style.padding = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_SHAPE_KIND]: (el, vt, c) => {
    const code = enumCode(vt, c);
    if (code === P.SHAPE_CIRCLE) {
      el.style.borderRadius = "50%";
    } else if (code === P.SHAPE_CAPSULE) {
      el.style.borderRadius = "9999px";
    } else if (code === P.SHAPE_ELLIPSE) {
      el.style.borderRadius = "50%";
    }
  },
  [P.PROP_TEXT_ALIGNMENT]: (el, vt, c) => (el.style.textAlign = textAlignCss(enumCode(vt, c))),
  [P.PROP_LINE_LIMIT]: (el, _vt, c) => {
    const n = u32(c);
    if (n > 0) {
      el.style.display = "-webkit-box";
      el.style.webkitLineClamp = String(n);
      (el.style as unknown as Record<string, string>).boxOrient = "vertical";
    }
  },
  [P.PROP_TRUNCATION_MODE]: (el, vt, c) => (el.style.textOverflow = truncationCss(enumCode(vt, c))),
  [P.PROP_OFFSET_X]: (el, vt, c) => {
    transformOf(el).translateX = f32(vt, c);
    recomposeTransform(el);
  },
  [P.PROP_OFFSET_Y]: (el, vt, c) => {
    transformOf(el).translateY = f32(vt, c);
    recomposeTransform(el);
  },
  [P.PROP_POSITION_X]: (el, vt, c) => {
    el.style.position = "absolute";
    el.style.left = fmtFloat(f32(vt, c)) + "px";
  },
  [P.PROP_POSITION_Y]: (el, vt, c) => {
    el.style.position = "absolute";
    el.style.top = fmtFloat(f32(vt, c)) + "px";
  },
  [P.PROP_MIN_WIDTH]: (el, vt, c) => (el.style.minWidth = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_IDEAL_WIDTH]: (el, vt, c) => (el.style.width = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_MAX_WIDTH]: (el, vt, c) => (el.style.maxWidth = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_MIN_HEIGHT]: (el, vt, c) => (el.style.minHeight = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_IDEAL_HEIGHT]: (el, vt, c) => (el.style.height = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_MAX_HEIGHT]: (el, vt, c) => (el.style.maxHeight = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_FIXED_SIZE_HORIZONTAL]: (el, vt, c) => {
    el.style.width = isOn(vt, c) ? "fit-content" : "";
  },
  [P.PROP_FIXED_SIZE_VERTICAL]: (el, vt, c) => {
    el.style.height = isOn(vt, c) ? "fit-content" : "";
  },
  [P.PROP_LAYOUT_PRIORITY]: (el, vt, c) => (el.style.flexGrow = fmtFloat(f32(vt, c))),
  [P.PROP_ASPECT_RATIO]: (el, vt, c) => (el.style.aspectRatio = fmtFloat(f32(vt, c))),
  [P.PROP_CONTENT_MODE]: (el, vt, c) => {
    el.style.objectFit = enumCode(vt, c) === P.CONTENT_MODE_FILL ? "fill" : "contain";
  },

  // Text
  [P.PROP_FONT_SIZE]: (el, vt, c) => (el.style.fontSize = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_FONT_WEIGHT]: (el, vt, c) => (el.style.fontWeight = fmtFloat(f32(vt, c))),
  [P.PROP_FONT_STYLE]: (el, vt, c) => (el.style.fontStyle = fontStyleCss(enumCode(vt, c))),
  [P.PROP_FONT_DESIGN]: (el, vt, c) => {
    const stack =
      enumCode(vt, c) === P.FONT_DESIGN_SERIF
        ? "Georgia, 'Times New Roman', serif"
        : enumCode(vt, c) === P.FONT_DESIGN_MONOSPACED
          ? "ui-monospace, monospace"
          : enumCode(vt, c) === P.FONT_DESIGN_ROUNDED
            ? "ui-rounded, system-ui, sans-serif"
            : "inherit";
    el.style.fontFamily = stack;
  },
  [P.PROP_FONT_WIDTH]: (el, vt, c) => (el.style.fontStretch = fmtFloat(f32(vt, c)) + "%"),
  [P.PROP_KERNING]: (el, vt, c) => (el.style.letterSpacing = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_TRACKING]: (el, vt, c) => (el.style.letterSpacing = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_BASELINE_OFFSET]: (el, vt, c) => (el.style.verticalAlign = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_LINE_SPACING]: (el, vt, c) => (el.style.lineHeight = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_TEXT_CASE]: (el, vt, c) => (el.style.textTransform = textCaseCss(enumCode(vt, c))),
  [P.PROP_UNDERLINE]: (el, vt, c) => {
    decorationOf(el).underline = isOn(vt, c);
    recomposeDecoration(el);
  },
  [P.PROP_STRIKETHROUGH]: (el, vt, c) => {
    decorationOf(el).strikethrough = isOn(vt, c);
    recomposeDecoration(el);
  },

  // Styling
  [P.PROP_Z_INDEX]: (el, _vt, c) => (el.style.zIndex = String(u32(c))),
  [P.PROP_CLIPS_TO_BOUNDS]: (el, vt, c) => (el.style.overflow = isOn(vt, c) ? "hidden" : "visible"),
  [P.PROP_TINT]: (el, _vt, c) => (el.style.accentColor = argbToRgba(c)),
  [P.PROP_BORDER_WIDTH]: (el, vt, c) => {
    borderOf(el).width = f32(vt, c);
    applyBorderEdges(el);
  },
  [P.PROP_BORDER_COLOR]: (el, _vt, c) => {
    borderOf(el).color = argbToRgba(c);
    applyBorderEdges(el);
  },
  [P.PROP_BORDER_EDGES]: (el, _vt, c) => {
    EDGES.set(el, u32(c));
    applyBorderEdges(el);
  },
  [P.PROP_BORDER_RADIUS]: (el, vt, c) => (el.style.borderRadius = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_SHADOW_COLOR]: (el, _vt, c) => {
    shadowOf(el).color = argbToRgba(c);
    recomposeShadow(el);
  },
  [P.PROP_SHADOW_RADIUS]: (el, vt, c) => {
    shadowOf(el).radius = f32(vt, c);
    recomposeShadow(el);
  },
  [P.PROP_SHADOW_X]: (el, vt, c) => {
    shadowOf(el).x = f32(vt, c);
    recomposeShadow(el);
  },
  [P.PROP_SHADOW_Y]: (el, vt, c) => {
    shadowOf(el).y = f32(vt, c);
    recomposeShadow(el);
  },
  [P.PROP_BLUR_RADIUS]: (el, vt, c) => {
    const parts = FILTERS.get(el) ?? [];
    const i = parts.findIndex((p) => p.startsWith("blur("));
    const value = `blur(${fmtFloat(f32(vt, c))}px)`;
    if (i >= 0) {
      parts[i] = value;
    } else {
      parts.push(value);
    }
    FILTERS.set(el, parts);
    recomposeFilter(el);
  },
  [P.PROP_SATURATION]: (el, vt, c) => {
    setFilterPart(el, "saturate(", f32(vt, c));
  },
  [P.PROP_CONTRAST]: (el, vt, c) => {
    setFilterPart(el, "contrast(", f32(vt, c));
  },
  [P.PROP_BRIGHTNESS]: (el, vt, c) => {
    setFilterPart(el, "brightness(", f32(vt, c));
  },
  [P.PROP_GRAYSCALE]: (el, vt, c) => {
    setFilterPart(el, "grayscale(", f32(vt, c));
  },
  [P.PROP_HUE_ROTATION]: (el, vt, c) => {
    setFilterPart(el, "hue-rotate(", f32(vt, c) + "deg");
  },
  [P.PROP_COLOR_INVERT]: (el, vt, c) => {
    setFilterPart(el, "invert(", isOn(vt, c) ? 1 : 0);
  },
  [P.PROP_ROTATION_DEGREES]: (el, vt, c) => {
    transformOf(el).rotate = f32(vt, c);
    recomposeTransform(el);
  },
  [P.PROP_SCALE]: (el, vt, c) => {
    transformOf(el).scale = f32(vt, c);
    recomposeTransform(el);
  },
  [P.PROP_ALLOWS_HIT_TESTING]: (el, vt, c) => (el.style.pointerEvents = isOn(vt, c) ? "auto" : "none"),

  // Semantic
  // PROP_ROLE is handled component-aware in apply.ts (semantic-tag retag via
  // the generated role-spec); the STATE handler below sets the ARIA state attrs.
  [P.PROP_STATE]: (el, vt, c) => {
    const state = enumCode(vt, c);
    el.setAttribute("aria-disabled", String(state === P.STATE_DISABLED));
    el.setAttribute("aria-pressed", String(state === P.STATE_PRESSED));
    el.setAttribute("aria-selected", String(state === P.STATE_SELECTED));
    el.setAttribute("aria-expanded", String(state === P.STATE_EXPANDED));
    el.setAttribute("aria-busy", String(state === P.STATE_BUSY));
  },
  [P.PROP_EVENT_LISTENERS]: (el, _vt, c) => {
    const mask = u32(c);
    if (mask === 0) {
      el.removeAttribute("data-event-listeners");
    } else {
      el.setAttribute("data-event-listeners", String(mask));
    }
  },
  [P.PROP_MIN_VALUE]: (el, vt, c) => applyRangeBounds(el, P.PROP_MIN_VALUE, f32(vt, c)),
  [P.PROP_MAX_VALUE]: (el, vt, c) => applyRangeBounds(el, P.PROP_MAX_VALUE, f32(vt, c)),

  // Control / structural variants
  [P.PROP_STEP_VALUE]: (el, vt, c) => applyStepValue(el, f32(vt, c)),
  [P.PROP_CONTROL_SIZE]: (el, vt, c) => (el.dataset.controlSize = String(enumCode(vt, c))),
  [P.PROP_IS_SECURE]: (el, vt, c) => applySecure(el, isOn(vt, c)),
  [P.PROP_PROGRESS]: (el, vt, c) => applyProgress(el, f32(vt, c)),
  [P.PROP_IS_INDETERMINATE]: (el, vt, c) => applyIndeterminate(el, isOn(vt, c)),
  [P.PROP_DATE_PICKER_MODE]: (el, vt, c) => applyDatePickerMode(el, enumCode(vt, c)),
  [P.PROP_PICKER_STYLE]: (el, vt, c) => (el.dataset.pickerStyle = String(enumCode(vt, c))),
  [P.PROP_TOGGLE_STYLE]: (el, vt, c) => applyToggleStyle(el, enumCode(vt, c)),
  [P.PROP_ACTION_ID]: (el, _vt, c) => (el.dataset.actionId = String(u32(c))),
  [P.PROP_BINDING_ID]: (el, _vt, c) => (el.dataset.bindingId = String(u32(c))),

  // Bare sizing / decorators already handled below (kept here for completeness).
  [P.PROP_COLOR]: (el, _vt, c) => (el.style.color = argbToRgba(c)),
  [P.PROP_BACKGROUND_COLOR]: (el, _vt, c) => (el.style.backgroundColor = argbToRgba(c)),
  [P.PROP_OPACITY]: (el, vt, c) => (el.style.opacity = fmtFloat(f32(vt, c))),
  [P.PROP_VISIBLE]: (el, vt, c) => (el.style.display = isOn(vt, c) ? "" : "none"),
  [P.PROP_WIDTH]: (el, vt, c) => {
    // HUG_CONTENT (-2) is left to the intrinsic size (the Rust SSR renderer
    // omits it); FILL/fixed values are applied.
    const v = f32(vt, c);
    if (v !== P.WIDTH_HUG) {
      el.style.width = sizeCss(v);
    }
  },
  [P.PROP_HEIGHT]: (el, vt, c) => {
    const v = f32(vt, c);
    if (v !== P.WIDTH_HUG) {
      el.style.height = sizeCss(v);
    }
  },
  [P.PROP_PADDING]: (el, vt, c) => (el.style.padding = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_PADDING_TOP]: (el, vt, c) => (el.style.paddingTop = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_PADDING_RIGHT]: (el, vt, c) => (el.style.paddingRight = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_PADDING_BOTTOM]: (el, vt, c) => (el.style.paddingBottom = fmtFloat(f32(vt, c)) + "px"),
  [P.PROP_PADDING_LEFT]: (el, vt, c) => (el.style.paddingLeft = fmtFloat(f32(vt, c)) + "px"),
};

function setFilterPart(el: HTMLElement, prefix: string, value: number | string): void {
  const parts = FILTERS.get(el) ?? [];
  const i = parts.findIndex((p) => p.startsWith(prefix));
  const part = `${prefix}${value})`;
  if (i >= 0) {
    parts[i] = part;
  } else {
    parts.push(part);
  }
  FILTERS.set(el, parts);
  recomposeFilter(el);
}

// --- DESIGN_TOKEN property references ---

// A property carrying the DESIGN_TOKEN value type (spec/OPCODE.md) resolves to a
// CSS expression (`var(--pl-…)` / `calc(var(--pl-space-base) * N)`) instead of
// a literal. Only properties that map to a single CSS value are tokenizable
// here; compound accumulators that need concrete numbers (e.g. BORDER_WIDTH's
// px suffix, shadow/transform components) are left literal. The value is
// written into the style *attribute* verbatim (not via the CSSOM) so CSS
// expressions survive exactly as the SSR renderer emits them.
const TOKEN_STYLE_PROPS: Record<number, string> = {
  [P.PROP_COLOR]: "color",
  [P.PROP_BACKGROUND_COLOR]: "background-color",
  [P.PROP_TINT]: "accent-color",
  [P.PROP_FONT_SIZE]: "font-size",
  [P.PROP_FONT_FAMILY]: "font-family",
  [P.PROP_FONT_WEIGHT]: "font-weight",
  [P.PROP_SPACING]: "gap",
  [P.PROP_PADDING]: "padding",
  [P.PROP_PADDING_TOP]: "padding-top",
  [P.PROP_PADDING_RIGHT]: "padding-right",
  [P.PROP_PADDING_BOTTOM]: "padding-bottom",
  [P.PROP_PADDING_LEFT]: "padding-left",
  [P.PROP_BORDER_RADIUS]: "border-radius",
  [P.PROP_OPACITY]: "opacity",
  [P.PROP_WIDTH]: "width",
  [P.PROP_HEIGHT]: "height",
};

/** Set a single CSS declaration verbatim in the element's style attribute,
 *  replacing any existing declaration for the same property. */
function setStyleCss(el: HTMLElement, cssProp: string, value: string): void {
  const decls = (el.getAttribute("style") ?? "")
    .split(";")
    .map((s) => s.trim())
    .filter((s) => s.length > 0 && !s.toLowerCase().startsWith(`${cssProp}:`));
  decls.push(`${cssProp}:${value}`);
  el.setAttribute("style", decls.join(";"));
}

/** Apply a property whose value is a `DESIGN_TOKEN` reference (a token path). */
export function applyTokenRefProperty(el: HTMLElement, property: number, tokenPath: string): void {
  const value = resolveTokenCssRef(tokenPath);
  if (property === P.PROP_BORDER_COLOR) {
    borderOf(el).color = value;
    applyBorderEdges(el);
    return;
  }
  if (property === P.PROP_SHADOW_COLOR) {
    shadowOf(el).color = value;
    recomposeShadow(el);
    return;
  }
  const cssProp = TOKEN_STYLE_PROPS[property];
  if (cssProp) {
    setStyleCss(el, cssProp, value);
  }
}

/** Apply a numeric style/control property to an element via inline style / ARIA / shell reconfiguration. */
export function applyProperty(el: HTMLElement, property: number, valueType: number, c: number): void {
  HANDLERS[property]?.(el, valueType, c);
}

/** Apply a {@link PROP_ENABLED} delta (native disabled on controls, aria-disabled elsewhere). */
export function applyEnabled(el: HTMLElement, bits: number): void {
  const disabled = bits === 0;
  if (
    el instanceof HTMLInputElement ||
    el instanceof HTMLButtonElement ||
    el instanceof HTMLSelectElement ||
    el instanceof HTMLTextAreaElement
  ) {
    el.disabled = disabled;
  } else if (disabled) {
    el.setAttribute("aria-disabled", "true");
  } else {
    el.removeAttribute("aria-disabled");
  }
}

/** Map a {@link WIDTH}/{@link HEIGHT} value (FILL/HUG/fixed px) to CSS. */
export function sizeCss(value: number): string {
  if (value === P.WIDTH_FILL) {
    return "100%";
  }
  if (value === P.WIDTH_HUG) {
    return "fit-content";
  }
  return fmtFloat(value) + "px";
}

export { argbToHex as hexFromArgb };