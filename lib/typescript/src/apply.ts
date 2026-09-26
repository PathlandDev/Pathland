// Delta application: decode each opcode and apply it to the DOM via the
// retained `id → Node` registry. STYLE deltas mutate the hydrated element in
// place; TREE deltas build the element shell and insert/remove/move it; META
// deltas reset/environment.

import {
  CAT_META,
  CAT_STYLE,
  CAT_TREE,
  CMD_CREATE_NODE,
  CMD_DELETE_NODE,
  CMD_ENVIRONMENT,
  CMD_INSERT_CHILD,
  CMD_MOVE_CHILD,
  CMD_REMOVE_CHILD,
  CMD_RESET,
  CMD_SET_DATE,
  CMD_SET_DESIGN_TOKEN,
  CMD_SET_PROPERTY,
  CMD_SET_TEXT,
  COMPONENT_COLOR,
  COMPONENT_GRID,
  COMPONENT_LAZY_HGRID,
  COMPONENT_LAZY_VGRID,
  COMPONENT_PICKER,
  COMPONENT_PROGRESS_VIEW,
  COMPONENT_SHAPE,
  COMPONENT_TEXT,
  COMPONENT_ZSTACK,
  PROP_BINDING_ID,
  PROP_AUDIO_SOURCE,
  PROP_COLOR,
  PROP_COLOR_VALUE,
  PROP_ENABLED,
  PROP_FONT_FAMILY,
  PROP_IMAGE_SOURCE,
  PROP_IS_INDETERMINATE,
  PROP_LABEL,
  PROP_NAV_CHROME,
  PROP_NAV_DEPTH,
  PROP_PROGRESS,
  PROP_PROMPT,
  PROP_ROLE,
  PROP_ROUTE,
  PROP_SELECTED,
  PROP_SELECTION,
  PROP_TEXT,
  PROP_TEXT_STYLE,
  PROP_VALUE,
  PROP_VIDEO_SOURCE,
  PROP_WIDTH,
  VAL_DESIGN_TOKEN,
  VAL_STRING,
  VAL_U8,
} from "./constants";
import type { Batch, Opcode } from "./plpl";
import { readString } from "./plpl";
import { childrenContainer, createElement } from "./elements";
import { applyEnabled, applyProperty, applyTokenRefProperty } from "./classes";
import { createTokenSink, applyDesignToken, type DesignTokenSink } from "./tokens";
import {
  GENERIC_COMPONENTS,
  NATIVE_ROLE_COMPONENTS,
  ariaRole,
  textTag,
} from "./generated/role-spec";
import { argbToHex, argbToRgba, daysToIso, f32FromBits, millisToTime } from "./format";

/** Component type per retained node, so STYLE/TREE application can special-case
 *  per component (a ZSTACK child's absolute positioning, a ProgressView's
 *  spinner/progress morph) without baking it into the DOM. */
const componentByNode = new WeakMap<Node, number>();

/** The last `ROLE` code applied per element (morphing needs both role and text
 *  style to resolve the effective tag). */
const roleByNode = new WeakMap<HTMLElement, number>();

/** The last `TEXT_STYLE` code applied per element, if any. */
const textStyleByNode = new WeakMap<HTMLElement, number>();

/** The retained `node id → DOM Node` registry the renderer works against. */
export interface DomRenderer {
  byId: Map<number, Node>;
  /**
   * Optional hook invoked when a slot's `ROUTE` property changes — the host wires it
   * to `history.pushState` so the browser URL mirrors the app's navigation (spec
   * DSL.md §4.5). Not called on hydrate (the URL is already correct).
   */
  onRoute?: (path: string) => void;
  /**
   * Optional hook invoked when the renderer's default back button (a
   * `PlatformDefault` nav slot at depth > 1) is clicked — the host wires it to a
   * `NAVIGATE` event with no URL, so the app pops its own back-stack (spec DSL.md
   * §4.5). Null/absent when the slot is `Custom` chrome or at depth 1.
   */
  onNavigateBack?: () => void;
  /** Optional design-token sink (defaults to document-root CSS variables). */
  tokenSink?: DesignTokenSink;
}

/** Apply every opcode in a batch to the DOM (TREE structure + STYLE/META deltas). */
export function applyBatch(batch: Batch, renderer: DomRenderer): void {
  for (const op of batch.opcodes) {
    switch (op.category) {
      case CAT_TREE:
        applyTree(op, renderer);
        break;
      case CAT_STYLE:
        applyStyle(op, batch.strings, renderer);
        break;
      case CAT_META:
        applyMeta(op, renderer);
        break;
      default:
        break;
    }
  }
}

function applyMeta(op: Opcode, r: DomRenderer): void {
  switch (op.command) {
    case CMD_RESET: {
      for (const el of Array.from(r.byId.values())) {
        if (el.parentNode) {
          el.parentNode.removeChild(el);
        }
      }
      r.byId.clear();
      break;
    }
    case CMD_ENVIRONMENT:
    default:
      break;
  }
}

/** The node actually inserted into a parent's container: ZStack children are
 *  wrapped in `<div style="position:absolute;inset:0">` (mirrors the Rust SSR
 *  renderer's per-child wrapper), everything else inserts directly. */
function placedChild(parent: Node, child: Node): Node {
  if (componentByNode.get(parent) === COMPONENT_ZSTACK && child instanceof HTMLElement) {
    // Set via the style attribute so `inset:0` survives verbatim (the CSSOM
    // drops the property in some engines); mirrors the Rust SSR wrapper exactly.
    const wrapper = document.createElement("div");
    wrapper.setAttribute("style", "position:absolute;inset:0");
    wrapper.appendChild(child);
    return wrapper;
  }
  return child;
}

/** Materialize a PICKER's child TEXT node as a native `<option>` (the Rust SSR
 *  renderer emits `<option data-pathland-id="{child}" value="{index}">` per
 *  child). The option keeps the child's id so its STYLE deltas resolve to it. */
function pickerOption(child: HTMLElement): HTMLElement {
  const opt = document.createElement("option");
  opt.textContent = child.textContent ?? "";
  const id = child.getAttribute("data-pathland-id");
  if (id) {
    opt.setAttribute("data-pathland-id", id);
  }
  return opt;
}

/** Keep a PICKER's option `value` attributes aligned with their index (the SSR
 *  renderer's `<option value="{i}">` scheme). */
function renumberPickerOptions(container: Node): void {
  if (!(container instanceof HTMLSelectElement)) {
    return;
  }
  Array.from(container.options).forEach((o, i) => {
    o.value = String(i);
  });
}

function applyTree(op: Opcode, r: DomRenderer): void {
  switch (op.command) {
    case CMD_CREATE_NODE: {
      // Hydration-aware: an id already present in the registry is the SSR-hydrated
      // element — reuse it (a resync/full snapshot replays CREATE for every node
      // without clobbering the visible DOM). Only create a fresh shell when absent.
      let el = r.byId.get(op.a);
      if (!el) {
        el = createElement(op.b);
        if (el.nodeType === Node.ELEMENT_NODE) {
          (el as HTMLElement).setAttribute("data-pathland-id", String(op.a));
        }
        r.byId.set(op.a, el);
      }
      if (el.nodeType === Node.ELEMENT_NODE) {
        componentByNode.set(el, op.b);
      }
      break;
    }
    case CMD_DELETE_NODE: {
      const el = r.byId.get(op.a);
      if (el && el.parentNode) {
        el.parentNode.removeChild(el);
      }
      r.byId.delete(op.a);
      break;
    }
    case CMD_INSERT_CHILD: {
      const parent = r.byId.get(op.a);
      const child = r.byId.get(op.b);
      if (parent && child) {
        const container = childrenContainer(parent);
        // Hydration/idempotent-replay guard: skip when the child is already there
        // (the SSR DOM already holds the initial tree; a resync replays it).
        if (container && !container.contains(child)) {
          let placed = placedChild(parent, child);
          if (componentByNode.get(parent) === COMPONENT_PICKER && child instanceof HTMLElement) {
            // Picker children are option labels → materialize native `<option>`s.
            placed = pickerOption(child);
            r.byId.set(op.b, placed);
            componentByNode.set(placed, COMPONENT_TEXT);
          }
          insertAt(container, placed, op.c);
          if (componentByNode.get(parent) === COMPONENT_PICKER) {
            renumberPickerOptions(container);
          }
          maybeAnimateInsert(parent, placed);
        }
      }
      break;
    }
    case CMD_REMOVE_CHILD: {
      const child = r.byId.get(op.b);
      if (child && child.parentNode) {
        child.parentNode.removeChild(child);
      }
      break;
    }
    case CMD_MOVE_CHILD: {
      const child = r.byId.get(op.b);
      if (!child) {
        break;
      }
      const parent = r.byId.get(op.a);
      if (parent) {
        const container = childrenContainer(parent);
        if (container) {
          // Detach the child (and its now-empty ZStack wrapper, if any) so the
          // wrapper-count stays in sync with the Rust SSR structure.
          const oldParent = child.parentNode;
          child.parentNode?.removeChild(child);
          if (
            oldParent instanceof HTMLElement &&
            oldParent !== container &&
            oldParent.childElementCount === 0 &&
            oldParent.style.position === "absolute"
          ) {
            oldParent.remove();
          }
          let placed = placedChild(parent, child);
          if (componentByNode.get(parent) === COMPONENT_PICKER && child instanceof HTMLElement) {
            placed = pickerOption(child);
            r.byId.set(op.b, placed);
            componentByNode.set(placed, COMPONENT_TEXT);
          }
          insertAt(container, placed, op.c);
          if (componentByNode.get(parent) === COMPONENT_PICKER) {
            renumberPickerOptions(container);
          }
        }
      }
      break;
    }
    default:
      break;
  }
}

function insertAt(container: Node, child: Node, index: number): void {
  const visible = Array.from(container.childNodes).filter(
    (n) =>
      (n.nodeType === Node.ELEMENT_NODE || n.nodeType === Node.COMMENT_NODE) &&
      !(n instanceof HTMLElement && n.classList.contains(NAV_BACK_CLASS)),
  );
  const target = visible[index];
  if (target) {
    container.insertBefore(child, target);
  } else {
    container.appendChild(child);
  }
}

// --- renderer-provided navigation chrome (spec DSL.md §4.5) ---
// A `PlatformDefault` nav slot at depth > 1 gets a default back button drawn by
// the DOM renderer (the web has no native navigation container). The button is
// a tracked child of the slot that the reconcile ignores (see `insertAt`), so
// TREE deltas never displace it. `Custom` chrome slots never get one.

/** The injected default back button's class (excluded from child indexing). */
export const NAV_BACK_CLASS = "pathland-nav-back";

/** Whether an element is a nav slot (carries a `data-pathland-route`). */
function isNavSlot(el: HTMLElement): boolean {
  return el.hasAttribute("data-pathland-route");
}

/** The slot's chrome mode: `true` when the developer owns all nav UI. */
function isCustomChrome(el: HTMLElement): boolean {
  return el.getAttribute("data-pathland-nav-chrome") === "custom";
}

/** The slot's current depth (defaults to 1 = root destination). */
function slotDepth(el: HTMLElement): number {
  return Math.max(1, Number(el.getAttribute("data-pathland-depth") ?? "1") || 1);
}

/** The injected back button inside a slot, if present. */
function injectedBackButton(el: HTMLElement): HTMLButtonElement | null {
  return el.querySelector<HTMLButtonElement>(`.${NAV_BACK_CLASS}`);
}

/**
 * Reconcile the renderer's default back button for a nav slot: shown for a
 * `PlatformDefault` slot at depth > 1, removed for `Custom` chrome or depth 1.
 * Called from `SET_PROPERTY` (depth/chrome changes) and once at hydrate.
 */
export function updateNavBackButton(el: HTMLElement, r: DomRenderer): void {
  if (!isNavSlot(el) || isCustomChrome(el) || slotDepth(el) <= 1) {
    injectedBackButton(el)?.remove();
    return;
  }
  let back = injectedBackButton(el);
  if (!back) {
    back = document.createElement("button");
    back.type = "button";
    back.className = NAV_BACK_CLASS;
    back.setAttribute("aria-label", "Back");
    back.textContent = "‹ Back";
    back.addEventListener("click", () => r.onNavigateBack?.());
    el.insertBefore(back, el.firstChild); // above the destination
  }
}

/** Reconcile the default back button for every nav slot in the tree (hydrate). */
export function updateNavBackButtons(r: DomRenderer): void {
  for (const node of r.byId.values()) {
    if (node instanceof HTMLElement && isNavSlot(node)) {
      updateNavBackButton(node, r);
    }
  }
}

// --- swap transitions (spec DSL.md §4.5 / MODIFIERS.md TRANSITION) ---
// When a child is inserted into a slot carrying a `data-pathland-transition` hint,
// the DOM client animates the new subtree in (fade/slide/scale). This is a pure
// renderer-side animation of its own output cache — never app state.

const TRANSITION_KEYFRAMES: Record<string, string> = {
  platform: "@keyframes pl-platform { from { opacity: 0; } to { opacity: 1; } }",
  fade: "@keyframes pl-fade { from { opacity: 0; } to { opacity: 1; } }",
  slide:
    "@keyframes pl-slide { from { opacity: 0; transform: translateX(16px); } to { opacity: 1; transform: none; } }",
  scale:
    "@keyframes pl-scale { from { opacity: 0; transform: scale(0.96); } to { opacity: 1; transform: none; } }",
};

let transitionsInjected = false;

function ensureTransitionStyles(): void {
  if (transitionsInjected || typeof document === "undefined") {
    return;
  }
  transitionsInjected = true;
  const style = document.createElement("style");
  style.setAttribute("data-pathland-transitions", "");
  style.textContent = Object.values(TRANSITION_KEYFRAMES).join("\n");
  document.head.appendChild(style);
}

function maybeAnimateInsert(parent: Node, child: Node): void {
  if (!(parent instanceof HTMLElement) || !(child instanceof HTMLElement)) {
    return;
  }
  const name = parent.getAttribute("data-pathland-transition");
  if (!name) {
    return;
  }
  ensureTransitionStyles();
  const key = TRANSITION_KEYFRAMES[name] ? name : "platform";
  child.style.animation = `pl-${key} 180ms ease-out`;
  child.addEventListener("animationend", () => {
    child.style.animation = "";
  }, { once: true });
}

/** Resolve an element's role shell kind: from the component when known (fresh
 *  nodes), else from its tag (SSR-hydrated nodes have no component). */
function shellKind(el: HTMLElement): "generic" | "native" | "other" {
  const comp = componentByNode.get(el);
  if (comp !== undefined) {
    if (GENERIC_COMPONENTS.includes(comp)) {
      return "generic";
    }
    if (NATIVE_ROLE_COMPONENTS.includes(comp)) {
      return "native";
    }
    return "other";
  }
  const t = el.tagName.toLowerCase();
  if (t === "div" || t === "span") {
    return el.classList.contains("pathland-menu") || el.classList.contains("pathland-stepper")
      ? "other"
      : "generic";
  }
  if (
    t === "button" ||
    t === "input" ||
    t === "label" ||
    t === "select" ||
    t === "textarea" ||
    t === "progress" ||
    t === "img"
  ) {
    return "native";
  }
  return "other";
}

/** Replace an element with one of a different tag (a semantic role retag),
 *  preserving attributes, children, and the registry entry. */
function morphRole(el: HTMLElement, tag: string, r: DomRenderer): HTMLElement {
  const fresh = document.createElement(tag);
  for (const attr of Array.from(el.attributes)) {
    fresh.setAttribute(attr.name, attr.value);
  }
  while (el.firstChild) {
    fresh.appendChild(el.firstChild);
  }
  if (el.parentNode) {
    el.parentNode.replaceChild(fresh, el);
  }
  const id = Number(el.getAttribute("data-pathland-id"));
  if (id) {
    r.byId.set(id, fresh);
  }
  const comp = componentByNode.get(el);
  if (comp !== undefined) {
    componentByNode.set(fresh, comp);
  }
  const role = roleByNode.get(el);
  if (role !== undefined) {
    roleByNode.set(fresh, role);
  }
  const style = textStyleByNode.get(el);
  if (style !== undefined) {
    textStyleByNode.set(fresh, style);
  }
  return fresh;
}

/** Resolve and apply the semantic tag + ARIA role for an element from its
 *  current `ROLE` / `TEXT_STYLE` state (a heading `TEXT_STYLE` always wins).
 *  Retags a generic shell in place when the effective tag differs. */
function applySemantic(el: HTMLElement, r: DomRenderer): void {
  const kind = shellKind(el);
  const role = roleByNode.get(el) ?? 0;
  const style = textStyleByNode.get(el);
  const tag = textTag(kind, role, style ?? null);
  let current = el;
  if (tag && current.tagName.toLowerCase() !== tag) {
    current = morphRole(current, tag, r);
  }
  const aria = ariaRole(kind, role);
  if (aria) {
    current.setAttribute("role", aria);
  } else {
    current.removeAttribute("role");
  }
}

function applyStyle(op: Opcode, strings: Uint8Array, r: DomRenderer): void {
  if (op.command === CMD_SET_DESIGN_TOKEN) {
    applyDesignToken(op, strings, r.tokenSink ?? createTokenSink());
    return;
  }
  const node = r.byId.get(op.a);
  if (!node || node.nodeType !== Node.ELEMENT_NODE) {
    return;
  }
  const el = node as HTMLElement;
  switch (op.command) {
    case CMD_SET_TEXT:
      setNodeText(el, readString(strings, op.b));
      break;
    case CMD_SET_DATE: {
      const input = el.matches("input[type=date],input[type=time],input[type=datetime-local]")
        ? (el as HTMLInputElement)
        : el.querySelector<HTMLInputElement>("input[type=date],input[type=time],input[type=datetime-local]");
      if (input) {
        if (input.type === "time") {
          input.value = millisToTime(op.c);
        } else if (input.type === "datetime-local") {
          const date = daysToIso(op.b);
          const time = millisToTime(op.c);
          input.value = date ? `${date}T${time}` : time;
        } else {
          input.value = daysToIso(op.b);
        }
      }
      break;
    }
    case CMD_SET_PROPERTY: {
      const propId = op.b & 0xffff;
      const valueType = (op.b >>> 16) & 0xff;
      if (valueType === VAL_STRING) {
        const text = readString(strings, op.c);
        if (propId === PROP_ROUTE) {
          el.setAttribute("data-pathland-route", text);
          r.onRoute?.(text); // host mirrors the URL (history.pushState)
        } else {
          applyStringProperty(el, propId, text);
        }
      } else if (valueType === VAL_DESIGN_TOKEN) {
        applyTokenRefProperty(el, propId, readString(strings, op.c));
      } else if (componentByNode.get(el) === COMPONENT_PROGRESS_VIEW
              && (propId === PROP_IS_INDETERMINATE || propId === PROP_PROGRESS)) {
        applyProgress(el, r, propId, valueType, op.c);
      } else if (propId === PROP_ROLE) {
        // Semantic role → native element where one exists (generated role-spec):
        // a generic shell is retagged to its semantic element; the ARIA `role`
        // attribute is set only when the element does not already convey it.
        roleByNode.set(el, Math.round(f32FromBits(op.c)) & 0xff);
        applySemantic(el, r);
      } else if (propId === PROP_TEXT_STYLE) {
        // A heading `TEXT_STYLE` (LargeTitle…Headline) makes a TEXT a heading
        // (`<h1>`–`<h5>`) — always, even without `ROLE_HEADER`; raw font
        // modifiers never imply a heading. Absence is "no style" (the enum has
        // no NONE — LARGE_TITLE is code 0, a real heading style).
        textStyleByNode.set(el, Math.round(f32FromBits(op.c)) & 0xff);
        applySemantic(el, r);
      } else if (propId === PROP_NAV_DEPTH) {
        // Back-stack depth on a nav slot → the renderer's default back button.
        el.setAttribute("data-pathland-depth", String(op.c));
        updateNavBackButton(el, r);
      } else if (propId === PROP_NAV_CHROME) {
        // Chrome mode: PlatformDefault=0 / Custom=1 (F32 enum code).
        const custom = Math.round(f32FromBits(op.c)) === 1;
        if (custom) {
          el.setAttribute("data-pathland-nav-chrome", "custom");
        } else {
          el.removeAttribute("data-pathland-nav-chrome");
        }
        updateNavBackButton(el, r);
      } else {
        applyNumericProperty(el, propId, valueType, op.c);
      }
      break;
    }
    default:
      break;
  }
}

/** Set a node's text, honoring text fields/editors and label spans (SSR structure). */
export function setNodeText(el: HTMLElement, text: string): void {
  const input = el.querySelector("input[type=text],input[type=password]");
  if (input instanceof HTMLInputElement) {
    input.value = text;
    return;
  }
  const textarea = el.querySelector("textarea");
  if (textarea instanceof HTMLTextAreaElement) {
    textarea.value = text;
    return;
  }
  const trigger = el.querySelector(".pathland-menu-trigger");
  if (trigger) {
    trigger.textContent = text;
    return;
  }
  const span = el.querySelector(".pathland-text");
  if (span) {
    span.textContent = text;
    return;
  }
  el.textContent = text;
}

function applyStringProperty(el: HTMLElement, propId: number, text: string): void {
  switch (propId) {
    case PROP_TEXT:
    case PROP_LABEL: {
      // On a media element the accessibility label is the `alt` text (mirrors
      // the Rust SSR renderer); elsewhere it's the caption/label span.
      const media = el.matches("img,audio,video") ? el : null;
      if (media) {
        media.setAttribute("alt", text);
      } else {
        const span = el.querySelector(".pathland-label");
        if (span) {
          span.textContent = text;
        } else {
          setNodeText(el, text);
        }
      }
      break;
    }
    case PROP_PROMPT: {
      const input = el.querySelector("input[type=text]");
      if (input instanceof HTMLInputElement) {
        input.placeholder = text;
      }
      break;
    }
    case PROP_IMAGE_SOURCE: {
      const img = el.matches("img") ? el : el.querySelector("img");
      if (img instanceof HTMLImageElement) {
        img.src = text;
      }
      break;
    }
    case PROP_AUDIO_SOURCE: {
      const audio = el.matches("audio") ? el : el.querySelector("audio");
      if (audio instanceof HTMLAudioElement) {
        audio.src = text;
      }
      break;
    }
    case PROP_VIDEO_SOURCE: {
      const video = el.matches("video") ? el : el.querySelector("video");
      if (video instanceof HTMLVideoElement) {
        video.src = text;
      }
      break;
    }
    case PROP_FONT_FAMILY: {
      el.style.fontFamily = text;
      break;
    }
    default:
      break;
  }
}

function applyNumericProperty(el: HTMLElement, propId: number, valueType: number, bits: number): void {
  switch (propId) {
    case PROP_SELECTED: {
      const input = el.querySelector<HTMLInputElement>("input[type=checkbox]");
      if (input) {
        const on = (valueType === 0x01 ? bits & 0xff : bits) !== 0;
        input.checked = on;
        // Button-style toggles are rendered with `role=checkbox` + `aria-pressed`
        // (the Rust SSR renderer's TOGGLE_STYLE=Button); switch/checkbox styles
        // carry only the native `checked` state.
        if (input.getAttribute("role") === "checkbox") {
          input.setAttribute("aria-pressed", on ? "true" : "false");
        }
      }
      break;
    }
    case PROP_VALUE: {
      const range = el.querySelector<HTMLInputElement>("input[type=range]");
      if (range) {
        range.value = String(f32FromBits(bits));
      }
      const stepText = el.querySelector(".pathland-stepper span");
      if (stepText) {
        stepText.textContent = String(Math.round(f32FromBits(bits) * 100) / 100);
      }
      const gauge = el.matches(".pathland-gauge")
        ? el
        : el.querySelector<HTMLElement>(".pathland-gauge");
      if (gauge) {
        const max = Number(gauge.dataset.max ?? 1);
        const min = Number(gauge.dataset.min ?? 0);
        const span = max - min;
        const pct = span <= 0 ? 0 : ((f32FromBits(bits) - min) / span) * 100;
        const bar = gauge.firstElementChild;
        if (bar instanceof HTMLElement) {
          bar.style.width = pct + "%";
        }
      }
      break;
    }
    case PROP_SELECTION: {
      const select = el.matches("select")
        ? (el as HTMLSelectElement)
        : el.querySelector<HTMLSelectElement>("select");
      if (select) {
        // Index-based, matching the SSR `<option value="{index}">` scheme.
        select.selectedIndex = bits;
      }
      break;
    }
    case PROP_COLOR_VALUE: {
      const input = el.matches("input[type=color]")
        ? el
        : el.querySelector<HTMLInputElement>("input[type=color]");
      if (input instanceof HTMLInputElement) {
        input.value = argbToHex(bits);
      }
      break;
    }
    case PROP_BINDING_ID: {
      el.dataset.bindingId = String(bits >>> 0);
      break;
    }
    case PROP_COLOR: {
      // A COLOR / SHAPE node's COLOR property is its fill: it renders as a
      // background (the generic handler below sets the text color, which the
      // Rust SSR renderer also emits alongside the fill).
      const comp = componentByNode.get(el);
      if (comp === COMPONENT_COLOR || comp === COMPONENT_SHAPE) {
        el.style.backgroundColor = argbToRgba(bits);
      }
      applyProperty(el, propId, valueType, bits);
      break;
    }
    case PROP_ENABLED:
      applyEnabled(el, bits);
      break;
    case PROP_WIDTH: {
      // A GRID's WIDTH property is the cell-axis count, mirrored into
      // `grid-template-columns` (the Rust SSR renderer's `grid_style`); the
      // width is still applied literally by the generic handler below.
      const comp = componentByNode.get(el);
      if (comp === COMPONENT_GRID || comp === COMPONENT_LAZY_VGRID || comp === COMPONENT_LAZY_HGRID) {
        const n = f32FromBits(bits);
        el.style.gridTemplateColumns = n > 0 ? `repeat(${Math.round(n)},1fr)` : "";
      }
      applyProperty(el, propId, valueType, bits);
      break;
    }
    default:
      applyProperty(el, propId, valueType, bits);
      break;
  }
}

/**
 * A ProgressView's indicator properties: morphs the element between the
 * determinate `<progress>` and the indeterminate `pathland-spinner` div (the
 * Rust renderer's SSR shapes) and applies the determinate value. Mirrors the
 * Rust indeterminate rule: `IS_INDETERMINATE != 0 || PROGRESS < 0`.
 */
function applyProgress(el: HTMLElement, r: DomRenderer, propId: number, valueType: number, bits: number): void {
  const on = valueType === VAL_U8 ? bits & 0xff : bits;
  const indeterminate = propId === PROP_IS_INDETERMINATE ? on !== 0 : f32FromBits(bits) < 0;
  const el2 = morphProgress(el, r, indeterminate);
  if (propId === PROP_PROGRESS && el2 instanceof HTMLProgressElement) {
    const value = Math.min(1, Math.max(0, f32FromBits(bits)));
    el2.value = value;
    el2.max = 1;
  }
}

/** Replace a ProgressView element between its two shapes, preserving attributes and the byId entry. */
function morphProgress(el: HTMLElement, r: DomRenderer, wantSpinner: boolean): HTMLElement {
  const isSpinner = el.classList.contains("pathland-spinner");
  if (wantSpinner === isSpinner) {
    return el;
  }
  const fresh = wantSpinner
    ? (() => {
        const s = document.createElement("div");
        s.className = "pathland-spinner";
        return s;
      })()
    : (() => {
        const p = document.createElement("progress");
        p.max = 1;
        return p;
      })();
  for (const attr of Array.from(el.attributes)) {
    // A spinner is a plain div — it carries no `max`/`value` (the Rust SSR
    // renderer's `<div class="pathland-spinner">`).
    if (wantSpinner && (attr.name === "max" || attr.name === "value")) {
      continue;
    }
    fresh.setAttribute(attr.name, attr.value);
  }
  if (el.parentNode) {
    el.parentNode.replaceChild(fresh, el);
  }
  const id = Number(el.getAttribute("data-pathland-id"));
  if (id) {
    r.byId.set(id, fresh);
  }
  componentByNode.set(fresh, COMPONENT_PROGRESS_VIEW);
  return fresh;
}