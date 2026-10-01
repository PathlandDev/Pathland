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
  COMPONENT_AUDIO,
  COMPONENT_COLOR,
  COMPONENT_DIVIDER,
  COMPONENT_GRID,
  COMPONENT_HSTACK,
  COMPONENT_LAZY_HGRID,
  COMPONENT_LAZY_HSTACK,
  COMPONENT_LAZY_VGRID,
  COMPONENT_LAZY_VSTACK,
  COMPONENT_PICKER,
  COMPONENT_PROGRESS_VIEW,
  COMPONENT_SCROLLVIEW,
  COMPONENT_SHAPE,
  COMPONENT_SPACER,
  COMPONENT_TEXT,
  COMPONENT_VSTACK,
  COMPONENT_VIDEO,
  COMPONENT_ZSTACK,
  PROP_ALIGNMENT,
  PROP_BINDING_ID,
  PROP_AUDIO_SOURCE,
  PROP_COLOR,
  PROP_COLOR_VALUE,
  PROP_ENABLED,
  PROP_FONT_FAMILY,
  PROP_GRID_COLUMNS,
  PROP_GRID_ROWS,
  PROP_IMAGE_SOURCE,
  PROP_IS_INDETERMINATE,
  PROP_LABEL,
  PROP_MEDIA_POSITION,
  PROP_MEDIA_VOLUME,
  PROP_NAV_CHROME,
  PROP_NAV_DEPTH,
  PROP_PLAYBACK_STATE,
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
  VAL_DESIGN_TOKEN,
  VAL_ENUM,
  VAL_STRING,
  VAL_U8,
} from "./constants";
import type { Batch, Opcode } from "./plpl";
import { readString } from "./plpl";
import { childrenContainer, createElement } from "./elements";
import { applyEnabled, applyProperty, applyTokenRefProperty, gridAlignmentCss } from "./classes";
import { createTokenSink, applyDesignToken, type DesignTokenSink } from "./tokens";
import {
  encodeMediaEnded,
  encodeMediaPlayStateChanged,
  encodeMediaTimeUpdated,
  encodeMediaVolumeChanged,
} from "./events";
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

/** Flex stacks (main axis horizontal). */
const STACK_MAIN_HORIZONTAL = new Set([COMPONENT_HSTACK, COMPONENT_LAZY_HSTACK]);
/** Flex stacks (main axis vertical). */
const STACK_MAIN_VERTICAL = new Set([COMPONENT_VSTACK, COMPONENT_LAZY_VSTACK]);
/** Hug-to-content containers that propagate a FILL descendant up (LAYOUT.md). */
const PROPAGATING = new Set([
  COMPONENT_VSTACK,
  COMPONENT_HSTACK,
  COMPONENT_ZSTACK,
  COMPONENT_LAZY_VSTACK,
  COMPONENT_LAZY_HSTACK,
]);

/** Whether `el` is effectively FILL-sized on `horizontal` (LAYOUT.md §fill
 *  propagation, SwiftUI/Compose parity): FILL itself (`width/height:100%`), a
 *  layout-greedy primitive, or a Hug container whose subtree carries one. A
 *  Fixed px box bounds its subtree. `mainAxis` = whether the axis is the
 *  element's parent's main axis (governs SPACER/DIVIDER greediness). A
 *  component-less shell (a ZStack child wrapper) is transparent. */
function fillsAxis(el: Element, horizontal: boolean, mainAxis: boolean): boolean {
  const comp = componentByNode.get(el) ?? 0;
  if (comp === 0) {
    return Array.from(el.children).some(
      (c) => c instanceof HTMLElement && fillsAxis(c, horizontal, mainAxis),
    );
  }
  const style = (el as HTMLElement).style;
  const hint = horizontal ? style.width : style.height;
  if (hint === "100%") {
    return true;
  }
  if (hint !== "") {
    return false; // a Fixed px box bounds its subtree
  }
  const greedy =
    comp === COMPONENT_COLOR ||
    comp === COMPONENT_SCROLLVIEW ||
    (comp === COMPONENT_SPACER && mainAxis) ||
    (comp === COMPONENT_DIVIDER && !mainAxis);
  if (greedy) {
    return true;
  }
  if (!PROPAGATING.has(comp)) {
    return false;
  }
  const childMain = STACK_MAIN_HORIZONTAL.has(comp)
    ? horizontal
    : STACK_MAIN_VERTICAL.has(comp)
      ? !horizontal
      : false;
  return Array.from(el.children).some(
    (c) => c instanceof HTMLElement && fillsAxis(c, horizontal, childMain),
  );
}

/** The SSR's alignment token for a ZSTACK child position from the element's
 *  `align-items` value (`flex-start`/`center`/`flex-end`, default start). */
function zstackAlignToken(alignItems: string): string {
  if (alignItems === "center") {
    return "center";
  }
  if (alignItems === "flex-end") {
    return "end";
  }
  return "start";
}

/** Derived layout pass (LAYOUT.md): fill propagation (a Hug stack/ZStack with a
 *  FILL descendant becomes FILL on that axis), ZStack hug-to-largest-child
 *  sizing, cross-axis `align-self:stretch` for FILL children, and ZStack child
 *  positioning. Mirrors the Rust SSR renderer's whole-tree layout decisions;
 *  runs after every batch so a delta leaves the derived styles consistent. */
function applyLayout(r: DomRenderer): void {
  const els = Array.from(r.byId.values()).filter(
    (n): n is HTMLElement => n instanceof HTMLElement,
  );
  // Phase 1: container fill propagation + ZStack sizing + grid cell alignment.
  for (const el of els) {
    const comp = componentByNode.get(el) ?? 0;
    if (!PROPAGATING.has(comp) && !isGridComponent(comp)) {
      continue;
    }
    if (STACK_MAIN_HORIZONTAL.has(comp) || STACK_MAIN_VERTICAL.has(comp)) {
      const mainH = STACK_MAIN_HORIZONTAL.has(comp);
      if (el.style.width === "" && fillsAxis(el, true, mainH)) {
        el.style.width = "100%";
      }
      if (el.style.height === "" && fillsAxis(el, false, !mainH)) {
        el.style.height = "100%";
      }
    } else if (comp === COMPONENT_ZSTACK) {
      if (el.style.width === "") {
        el.style.width = fillsAxis(el, true, false) ? "100%" : "max-content";
      }
      if (el.style.height === "") {
        el.style.height = fillsAxis(el, false, false) ? "100%" : "max-content";
      }
      // Position each child shell per the ZSTACK ALIGNMENT (both axes).
      const token = zstackAlignToken(el.style.alignItems);
      for (const w of Array.from(el.children)) {
        if (!(w instanceof HTMLElement)) {
          continue;
        }
        w.style.justifySelf = fillsAxis(w, true, false) ? "stretch" : token;
        w.style.alignSelf = fillsAxis(w, false, false) ? "stretch" : token;
      }
    } else if (isGridComponent(comp)) {
      // Position each cell shell per the grid ALIGNMENT on both axes (spec
      // §grid model): a FILL-sized / greedy cell stretches to fill its track.
      const token = el.style.alignItems || "start";
      for (const w of Array.from(el.children)) {
        if (!(w instanceof HTMLElement)) {
          continue;
        }
        w.style.justifySelf = fillsAxis(w, true, false) ? "stretch" : token;
        w.style.alignSelf = fillsAxis(w, false, false) ? "stretch" : token;
      }
    }
  }
  // Phase 2: cross-axis FILL children in a stack stretch via align-self.
  for (const el of els) {
    const parent = el.parentElement;
    if (!parent) {
      continue;
    }
    const parentComp = componentByNode.get(parent) ?? 0;
    const mainH = STACK_MAIN_HORIZONTAL.has(parentComp);
    if (!STACK_MAIN_HORIZONTAL.has(parentComp) && !STACK_MAIN_VERTICAL.has(parentComp)) {
      continue;
    }
    if (fillsAxis(el, !mainH, false)) {
      el.style.alignSelf = "stretch";
    }
  }
}

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
  /**
   * Optional media-event sink: the host wires it to `transport.send` so a bound
   * `AUDIO`/`VIDEO` node can report play/pause/time/ended/volume (host → guest).
   */
  onMediaEvent?: (batch: Uint8Array) => void;
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
  // Derived layout (fill propagation, ZStack sizing/positioning, cross-axis
  // align-self) — mirrors the Rust SSR renderer's whole-tree decisions.
  applyLayout(renderer);
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
 *  wrapped in a `grid-area:1/1` cell shell and grid cells in an auto-placed
 *  cell shell (the layout pass sets their `justify-self`/`align-self` from the
 *  container's ALIGNMENT once the child's size styles exist — mirrors the Rust
 *  SSR renderer), everything else inserts directly. */
function placedChild(parent: Node, child: Node): Node {
  if (child instanceof HTMLElement) {
    const parentComp = componentByNode.get(parent);
    if (parentComp === COMPONENT_ZSTACK) {
      const wrapper = document.createElement("div");
      wrapper.style.gridArea = "1/1";
      wrapper.style.width = "max-content";
      wrapper.style.height = "max-content";
      wrapper.appendChild(child);
      return wrapper;
    }
    if (isGridComponent(parentComp)) {
      const wrapper = document.createElement("div");
      wrapper.appendChild(child);
      return wrapper;
    }
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
      const child = r.byId.get(op.b);
      const parentNode = r.byId.get(op.a);
      if (!child || !parentNode) {
        break;
      }
      let parent: Node = parentNode;
      // A media node gaining custom control children morphs its bare media
      // element into a `.pathland-media` wrapper (the Rust SSR emits the same
      // structure); the children then attach to the wrapper.
      const comp = componentByNode.get(parentNode);
      if ((comp === COMPONENT_AUDIO || comp === COMPONENT_VIDEO)
          && parentNode instanceof HTMLElement && parentNode.matches("audio,video")) {
        parent = mediaToWrapper(parentNode, r);
      }
      const container = childrenContainer(parent);
      // Hydration/idempotent-replay guard: skip when the child is already there
      // (the SSR DOM already holds the initial tree; a resync replays it).
      if (container && !container.contains(child)) {
        let placed = placedChild(parent, child);
        if (comp === COMPONENT_PICKER && child instanceof HTMLElement) {
          // Picker children are option labels → materialize native `<option>`s.
          placed = pickerOption(child);
          r.byId.set(op.b, placed);
          componentByNode.set(placed, COMPONENT_TEXT);
        }
        insertAt(container, placed, op.c);
        if (comp === COMPONENT_PICKER) {
          renumberPickerOptions(container);
        }
        maybeAnimateInsert(parent, placed);
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
          if (propId === PROP_AUDIO_SOURCE || propId === PROP_VIDEO_SOURCE) {
            // Wire a media node's element + listeners, finalize native controls
            // (a control-less media node renders native controls), and resume
            // playback if the app was playing (a src change pauses the element).
            ensureMediaElement(el, propId);
            applyStringProperty(el, propId, text);
            setupMediaElement(el, r);
            finalizeNativeMedia(el);
            resumeIfPlaying(el);
          }
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
      } else if (propId === PROP_PLAYBACK_STATE || propId === PROP_MEDIA_POSITION
              || propId === PROP_MEDIA_VOLUME) {
        applyMediaProperty(el, r, propId, valueType, op.c);
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
  // Only a leaf (no element children) falls through to textContent: re-applying a
  // composite's LABEL/TEXT property must never wipe its children (SSR renders no
  // label on composites — a composite's children are its body).
  if (el.childElementCount === 0) {
    el.textContent = text;
  }
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
    case PROP_ALIGNMENT: {
      // Grids position cells within their tracks on BOTH axes (spec §grid
      // model); stacks/others use the cross-axis `align-items` token.
      const comp = componentByNode.get(el);
      if (isGridComponent(comp)) {
        const code = valueType === VAL_ENUM ? bits & 0xff : Math.round(f32FromBits(bits));
        const g = gridAlignmentCss(code);
        el.style.justifyItems = g;
        el.style.alignItems = g;
      } else {
        applyProperty(el, propId, valueType, bits);
      }
      break;
    }
    case PROP_GRID_COLUMNS: {
      // A grid's column count (the fixed track of vertical grids): a positive
      // count mirrors into `grid-template-columns` and NEVER into a pixel width
      // (spec §grid model); FILL/absent = auto-fit (no template).
      const comp = componentByNode.get(el);
      const n = f32FromBits(bits);
      if (comp !== COMPONENT_LAZY_HGRID && isGridComponent(comp)) {
        el.style.gridTemplateColumns = n > 0 ? `repeat(${Math.round(n)},1fr)` : "";
      }
      break;
    }
    case PROP_GRID_ROWS: {
      // A grid's row count (the `LAZY_HGRID` fixed track): a positive count
      // mirrors into `grid-template-rows`; FILL/absent = auto-fit.
      const comp = componentByNode.get(el);
      const n = f32FromBits(bits);
      if ((comp === COMPONENT_GRID || comp === COMPONENT_LAZY_HGRID) && isGridComponent(comp)) {
        el.style.gridTemplateRows = n > 0 ? `repeat(${Math.round(n)},1fr)` : "";
      }
      break;
    }
    default:
      applyProperty(el, propId, valueType, bits);
      break;
  }
}

/** Whether `comp` is a grid container (GRID / lazy grids). */
function isGridComponent(comp: number | undefined): boolean {
  return comp === COMPONENT_GRID || comp === COMPONENT_LAZY_VGRID || comp === COMPONENT_LAZY_HGRID;
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

// --- media (app-driven AUDIO/VIDEO) ---

/** Per-element media state: echo suppression windows + the last app-requested
 *  playing state (so a source change can resume playback). */
const mediaState = new WeakMap<HTMLMediaElement, {
  suppressUntil: number;
  suppressTimeUntil: number;
  playing: boolean;
  lastReported: number;
}>();

/** The media element of a node: the element itself when it IS the media, else the
 *  inner `<audio>`/`<video>` of a `.pathland-media` wrapper. */
function mediaElementOf(el: HTMLElement): HTMLMediaElement | null {
  return el.matches("audio,video")
    ? (el as HTMLMediaElement)
    : el.querySelector<HTMLMediaElement>("audio,video");
}

/** Ensure a media node's container holds an inner (hidden) media element,
 *  creating it for a live-created custom-controls node that lacks one yet. */
function ensureMediaElement(el: HTMLElement, propId: number): HTMLMediaElement | null {
  if (el.matches("audio,video")) {
    return el as HTMLMediaElement;
  }
  let media = el.querySelector<HTMLMediaElement>("audio,video");
  if (!media) {
    media = document.createElement(propId === PROP_VIDEO_SOURCE ? "video" : "audio");
    media.setAttribute("data-pathland-media", "");
    el.prepend(media);
  }
  return media;
}

/** Attach a media node's element once: wire the app-driven media element to the
 *  host's media-event sink (play/pause/time/ended/volume, spec/EVENTS.md). */
export function setupMediaElement(el: HTMLElement, r: DomRenderer): void {
  const media = mediaElementOf(el);
  if (!media || mediaState.has(media)) {
    return;
  }
  const state = { suppressUntil: 0, suppressTimeUntil: 0, playing: false, lastReported: 0 };
  mediaState.set(media, state);
  const id = Number(el.getAttribute("data-pathland-id"));
  const send = r.onMediaEvent;
  if (!send) {
    return;
  }
  // Play/pause/volume are reported only when the media has NATIVE controls —
  // the only case a user drives them directly. App-driven custom-control media
  // (a hidden element under app controls) never echoes them: every such change
  // is app-initiated, so reporting it back would lock the player state.
  if (media.controls) {
    media.addEventListener("play", () => {
      if (performance.now() >= state.suppressUntil) {
        send(encodeMediaPlayStateChanged(id, true));
      }
    });
    media.addEventListener("pause", () => {
      if (performance.now() >= state.suppressUntil) {
        send(encodeMediaPlayStateChanged(id, false));
      }
    });
    media.addEventListener("volumechange", () => {
      if (performance.now() >= state.suppressUntil) {
        send(encodeMediaVolumeChanged(id, media.volume));
      }
    });
  }
  // Time + ended are always reported: the app displays progress and advances on
  // end. Time updates are throttled to ~1/second of playback progress (a
  // `timeupdate` within a second of the last report is dropped), matching the
  // GTK renderer's cadence.
  media.addEventListener("timeupdate", () => {
    if (!media.paused
        && performance.now() >= state.suppressTimeUntil
        && Math.abs(media.currentTime - state.lastReported) >= 1) {
      state.lastReported = media.currentTime;
      send(encodeMediaTimeUpdated(id, media.currentTime));
    }
  });
  media.addEventListener("ended", () => {
    send(encodeMediaEnded(id));
  });
}

/** Apply a media control property: drive the media element, suppress the echo,
 *  and wire its listeners. */
function applyMediaProperty(el: HTMLElement, r: DomRenderer, propId: number, valueType: number, bits: number): void {
  const media = ensureMediaElement(el, propId);
  if (!media) {
    return;
  }
  setupMediaElement(el, r);
  const state = mediaState.get(media);
  if (propId === PROP_PLAYBACK_STATE) {
    const playing = (valueType === VAL_U8 ? bits & 0xff : bits) !== 0;
    if (state) {
      state.playing = playing;
    }
    if (playing) {
      void media.play();
    } else {
      media.pause();
    }
    if (state) {
      state.suppressUntil = performance.now() + 100;
    }
  } else if (propId === PROP_MEDIA_POSITION) {
    // A seek only when it is meaningful: the app's position echoes every
    // timeupdate, so a near-identical write must NOT seek — it would interrupt
    // the just-started playback (and echo a pause back, locking the player).
    const target = f32FromBits(bits);
    if (Math.abs(media.currentTime - target) > 0.25) {
      media.currentTime = target;
      if (state) {
        state.suppressTimeUntil = performance.now() + 250;
      }
    }
  } else if (propId === PROP_MEDIA_VOLUME) {
    media.volume = Math.min(1, Math.max(0, f32FromBits(bits)));
    if (state) {
      state.suppressUntil = performance.now() + 100;
    }
  }
}

/** A source change resets the media element to a paused, loading state — even
 *  when it was playing. If the app last requested "playing", resume playback on
 *  the new source (a skip/auto-advance must keep playing, not require a manual
 *  pause+play). Transient rejections (not yet buffered / autoplay policy) are
 *  non-fatal. */
function resumeIfPlaying(el: HTMLElement): void {
  const media = mediaElementOf(el);
  const state = media ? mediaState.get(media) : undefined;
  if (media && state && state.playing) {
    const p = media.play();
    if (p && typeof p.catch === "function") {
      p.catch(() => undefined);
    }
  }
}

/** A live-created media node with no control children renders with native
 *  `controls` (the default AudioStyle/VideoStyle), mirroring the Rust SSR. */
function finalizeNativeMedia(el: HTMLElement): void {
  const media = el.matches("audio,video")
    ? (el as HTMLMediaElement)
    : el.querySelector<HTMLMediaElement>("audio,video");
  if (!media) {
    return;
  }
  const hasCustomChildren = el.matches("audio,video")
    ? el.childElementCount > 0
    : el.childElementCount > 1;
  if (!hasCustomChildren) {
    media.controls = true;
  }
}

/** Morph a bare media element into a `.pathland-media` wrapper (a node gaining
 *  custom control children), preserving the node's identity + style and moving
 *  the media element inside — the structure the Rust SSR emits. */
function mediaToWrapper(el: HTMLElement, r: DomRenderer): HTMLElement {
  const wrapper = document.createElement("div");
  wrapper.className = "pathland-media";
  wrapper.setAttribute("data-pathland-media", "");
  for (const attr of Array.from(el.attributes)) {
    if (attr.name === "style") {
      wrapper.style.cssText = el.style.cssText;
    } else {
      wrapper.setAttribute(attr.name, attr.value);
    }
  }
  el.removeAttribute("data-pathland-id");
  el.style.cssText = "";
  wrapper.append(el);
  if (el.parentNode) {
    el.parentNode.replaceChild(wrapper, el);
  }
  const id = Number(wrapper.getAttribute("data-pathland-id") ?? 0);
  if (id) {
    r.byId.set(id, wrapper);
  }
  const c = componentByNode.get(el);
  if (c !== undefined) {
    componentByNode.set(wrapper, c);
  }
  return wrapper;
}