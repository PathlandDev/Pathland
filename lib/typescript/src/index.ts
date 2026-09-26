// The Pathland DOM renderer bootstrap. Hydrates the server-rendered DOM,
// connects to the WebSocket, applies PLPL deltas in place, and reports raw
// inputs back as host → guest EVENT batches (gated by EVENT_LISTENERS when the
// SSR HTML carries a `data-event-listeners` mask). Bundle: dist/pathland-dom-renderer.js

import type { DomRenderer } from "./apply";
import { updateNavBackButtons } from "./apply";
import { Transport } from "./transport";
import { log } from "./log";
import {
  encodeDateChanged,
  encodeEditingChanged,
  encodeEnvironment,
  encodeFocusChanged,
  encodeKeyDown,
  encodeKeyUp,
  encodeNavigate,
  encodeNavigateBack,
  encodePointerDown,
  encodePointerMove,
  encodePointerUp,
  encodeScroll,
  encodeTextChanged,
  encodeValueBits,
  encodeValueChanged,
  encodeWheel,
} from "./events";
import {
  FLAG_HOVER_ENTER,
  FLAG_HOVER_LEAVE,
  FLAG_POINTER_SECONDARY,
  LISTEN_EDITING,
  LISTEN_FOCUS,
  LISTEN_KEY_DOWN,
  LISTEN_KEY_UP,
  LISTEN_POINTER_DOWN,
  LISTEN_POINTER_MOVE,
  LISTEN_POINTER_UP,
  LISTEN_SCROLL,
  LISTEN_WHEEL,
} from "./constants";

/** The owning Pathland node (the closest `[data-pathland-id]` ancestor). */
function nodeOf(el: Element | null): HTMLElement | null {
  return el ? el.closest<HTMLElement>("[data-pathland-id]") : null;
}

/**
 * The element an input event belongs to: the nearest element (self or ancestor)
 * that has a `data-pathland-id` AND a `data-event-listeners` mask including `bit`.
 * Unlike {@link nodeOf} (which returns the deepest id-bearing element), this walks
 * up so a gesture declared on a container (e.g. a tap on a `VStack` row whose child
 * `Text` is what's actually clicked) is attributed to the node that listens — the
 * contract the GTK renderer follows (gesture attached to any widget with the bits).
 * Elements that declare no listeners are skipped, so non-listening children never
 * steal an event from a listening ancestor.
 */
function eventNodeOf(target: Element | null, bit: number): HTMLElement | null {
  let el: Element | null = target;
  while (el && el !== document.body) {
    if (el.hasAttribute("data-pathland-id")) {
      const raw = el.getAttribute("data-event-listeners");
      if (raw !== null && (Number(raw) & bit) !== 0) {
        return el as HTMLElement;
      }
    }
    el = el.parentElement;
  }
  return null;
}

/**
 * EVENT_LISTENERS gating: emit an event only if the node's `data-event-listeners`
 * mask (from the SSR HTML, mirroring the Rust renderer's event attrs) requests it.
 * When the attribute is absent there is no gating information — be permissive.
 */
function listens(node: HTMLElement | null, bit: number): boolean {
  if (!node) {
    return false;
  }
  const raw = node.getAttribute("data-event-listeners");
  if (raw === null) {
    return true;
  }
  return (Number(raw) & bit) !== 0;
}

function modifiersOf(e: KeyboardEvent): number {
  let m = 0;
  if (e.shiftKey) m |= 0x01;
  if (e.ctrlKey) m |= 0x02;
  if (e.altKey) m |= 0x04;
  if (e.metaKey) m |= 0x08;
  return m;
}

function keyCodeOf(e: KeyboardEvent): number {
  return typeof e.keyCode === "number" && e.keyCode !== 0 ? e.keyCode : e.key.charCodeAt(0);
}

function boot(): void {
  const byId = new Map<number, Node>();
  for (const el of document.querySelectorAll<HTMLElement>("[data-pathland-id]")) {
    byId.set(Number(el.getAttribute("data-pathland-id")), el);
  }
  log.info(undefined, `dom-renderer boot — hydrated ${byId.size} nodes`);
  // The reserved framework path prefix (spec — host system endpoints live under
  // `/_pathland/**`). The SSR page carries it as `data-pathland-base` so a host
  // can relocate it (e.g. behind a proxy); defaults to `/_pathland`.
  const base = document.documentElement.dataset.pathlandBase ?? "/_pathland";
  const renderer: DomRenderer = { byId };
  const transport = new Transport({
    url: `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}${base}/ws`,
    renderer,
    // The platform environment (viewport + current route) is the FIRST message:
    // the server session seeds its router from the ROUTE field before mount, so a
    // deep-linked URL renders the right destination (spec DSL.md §4.5).
    onOpen: (t) => {
      const { innerWidth: w, innerHeight: h } = window;
      log.info("route", `environment: viewport ${w}x${h}, route "${location.pathname}"`);
      t.send(encodeEnvironment(w, h, location.pathname));
    },
  });
  transport.start();

  // Enrich the environment after connect: a window resize re-emits the viewport
  // fields (the mechanism future platform fields ride too).
  window.addEventListener("resize", () => {
    if (transport.open) {
      log.debug("route", `viewport resize -> ${window.innerWidth}x${window.innerHeight}`);
      transport.send(encodeEnvironment(window.innerWidth, window.innerHeight, location.pathname));
    }
  });

  // URL mirroring (spec DSL.md §4.5): the server emits the route as ROUTE; we push
  // it into the history so the browser URL follows the app. pushState never fires
  // popstate, so server-originated navigation can't loop back into NAVIGATE events.
  renderer.onRoute = (path) => {
    log.info("route", `server navigated -> pushState("${path}")`);
    history.pushState(null, "", path);
  };

  // Renderer-provided navigation chrome (spec DSL.md §4.5): a PlatformDefault nav
  // slot at depth > 1 shows the renderer's own back button (the web has no native
  // navigation container); clicking it is a NAVIGATE back request — the app pops.
  renderer.onNavigateBack = () => {
    log.info("route", "default back button -> NAVIGATE(back)");
    if (transport.open) {
      transport.send(encodeNavigateBack());
    }
  };
  // Hydrate the default back button from the SSR HTML (it carries data-pathland-depth).
  updateNavBackButtons(renderer);

  // Browser back/forward: popstate has already moved the URL; report it to the
  // server as a NAVIGATE event so the app routes (and re-emits the destination).
  window.addEventListener("popstate", () => {
    log.info("route", `popstate -> NAVIGATE("${location.href}")`);
    if (transport.open) {
      transport.send(encodeNavigate(location.href));
    }
  });

  // Hover tracking for POINTER_MOVE enter/leave.
  const hovered = new WeakSet<Element>();

  // --- pointer: down / move (hover) / up (tap) ---
  document.addEventListener("pointerdown", (event) => {
    const node = eventNodeOf(event.target as Element, LISTEN_POINTER_DOWN);
    if (!transport.open || !node) {
      return;
    }
    const id = Number(node.getAttribute("data-pathland-id"));
    const secondary = event.button !== 0 ? FLAG_POINTER_SECONDARY : 0;
    transport.send(encodePointerDown(id, event.clientX, event.clientY, secondary));
  });

  document.addEventListener("pointermove", (event) => {
    const target = event.target as Element;
    const node = eventNodeOf(target, LISTEN_POINTER_MOVE);
    if (!transport.open || !node) {
      return;
    }
    const id = Number(node.getAttribute("data-pathland-id"));
    if (!hovered.has(target)) {
      hovered.add(target);
      transport.send(encodePointerMove(id, event.clientX, event.clientY, FLAG_HOVER_ENTER));
    }
  });
  document.addEventListener("pointerout", (event) => {
    const target = event.target as Element;
    const node = eventNodeOf(target, LISTEN_POINTER_MOVE);
    if (hovered.has(target)) {
      hovered.delete(target);
      if (transport.open && node) {
        const id = Number(node.getAttribute("data-pathland-id"));
        transport.send(encodePointerMove(id, event.clientX, event.clientY, FLAG_HOVER_LEAVE));
      }
    }
  });

  document.addEventListener("click", (event) => {
    const target = event.target as Element | null;
    if (!target) {
      return;
    }
    // Tap: the nearest listening node (any element, not just <button>) — a tap
    // gesture on a non-button container resolves to the container that declared it.
    const node = eventNodeOf(target, LISTEN_POINTER_UP);
    if (node && transport.open) {
      const id = Number(node.getAttribute("data-pathland-id"));
      const secondary = (event as MouseEvent).button !== 0 ? FLAG_POINTER_SECONDARY : 0;
      transport.send(encodePointerUp(id, (event as MouseEvent).clientX, (event as MouseEvent).clientY, secondary));
      return;
    }
    const stepButton = target.closest<HTMLElement>(".pathland-stepper button[data-step]");
    if (stepButton && transport.open) {
      const stepper = stepButton.closest<HTMLElement>(".pathland-stepper[data-pathland-id]");
      if (stepper) {
        const id = Number(stepper.getAttribute("data-pathland-id"));
        const valueEl = stepper.querySelector("span");
        const rangeEl = stepper.querySelector<HTMLElement>(".pathland-stepper-range");
        const current = Number(valueEl ? valueEl.textContent : 0);
        const dir = Number(stepButton.getAttribute("data-step"));
        const min = Number(rangeEl?.getAttribute("data-min"));
        const max = Number(rangeEl?.getAttribute("data-max"));
        const step = Number(rangeEl?.getAttribute("data-step"));
        const next = Math.min(max, Math.max(min, current + dir * step));
        transport.send(encodeValueChanged(id, next));
      }
    }
  });

  // --- keyboard (reported to the focused node) ---
  document.addEventListener("keydown", (event) => {
    const node = nodeOf(document.activeElement);
    if (!transport.open || !listens(node, LISTEN_KEY_DOWN)) {
      return;
    }
    transport.send(
      encodeKeyDown(Number(node!.getAttribute("data-pathland-id")), keyCodeOf(event), modifiersOf(event), event.repeat),
    );
  });
  document.addEventListener("keyup", (event) => {
    const node = nodeOf(document.activeElement);
    if (!transport.open || !listens(node, LISTEN_KEY_UP)) {
      return;
    }
    transport.send(
      encodeKeyUp(Number(node!.getAttribute("data-pathland-id")), keyCodeOf(event), modifiersOf(event)),
    );
  });

  // --- focus / editing (text inputs + editors) ---
  function focusTarget(event: FocusEvent): HTMLElement | null {
    const input = event.target as Element | null;
    if (!input || (!input.matches("input[type=text],input[type=password],textarea"))) {
      return null;
    }
    return nodeOf(input);
  }
  document.addEventListener("focusin", (event) => {
    const node = focusTarget(event);
    if (!transport.open || !node) {
      return;
    }
    const id = Number(node.getAttribute("data-pathland-id"));
    if (listens(node, LISTEN_FOCUS)) {
      transport.send(encodeFocusChanged(id, true));
    }
    if (listens(node, LISTEN_EDITING)) {
      transport.send(encodeEditingChanged(id, true));
    }
  });
  document.addEventListener("focusout", (event) => {
    const node = focusTarget(event);
    if (!transport.open || !node) {
      return;
    }
    const id = Number(node.getAttribute("data-pathland-id"));
    if (listens(node, LISTEN_FOCUS)) {
      transport.send(encodeFocusChanged(id, false));
    }
    if (listens(node, LISTEN_EDITING)) {
      transport.send(encodeEditingChanged(id, false));
    }
  });

  // --- scroll / wheel (scroll containers) ---
  document.addEventListener("scroll", (event) => {
    const scroller = event.target as Element | null;
    const node = nodeOf(scroller);
    if (!transport.open || !listens(node, LISTEN_SCROLL)) {
      return;
    }
    const el = scroller instanceof Element ? scroller : document.documentElement;
    transport.send(
      encodeScroll(Number(node!.getAttribute("data-pathland-id")), el.scrollLeft, el.scrollTop),
    );
  }, true);
  document.addEventListener(
    "wheel",
    (event) => {
      const node = nodeOf(event.target as Element);
      if (!transport.open || !listens(node, LISTEN_WHEEL)) {
        return;
      }
      transport.send(encodeWheel(Number(node!.getAttribute("data-pathland-id")), event.deltaX, event.deltaY));
    },
    { passive: true },
  );

  // --- value-bearing controls (toggles/selects/color/date) ---
  document.addEventListener("change", (event) => {
    if (!transport.open) {
      return;
    }
    const target = event.target as Element | null;
    if (!target) {
      return;
    }
    const toggle = target.closest<HTMLElement>("label.pathland-toggle[data-pathland-id]");
    if (toggle) {
      const box = toggle.querySelector<HTMLInputElement>("input[type=checkbox]");
      transport.send(
        encodeValueChanged(Number(toggle.getAttribute("data-pathland-id")), box && box.checked ? 1 : 0),
      );
      return;
    }
    const select = target.closest<HTMLSelectElement>("select[data-pathland-id]");
    if (select) {
      transport.send(encodeValueChanged(Number(select.getAttribute("data-pathland-id")), Number(select.value)));
      return;
    }
    const color = target.closest<HTMLInputElement>("input[type=color][data-pathland-id]");
    if (color) {
      const rgb = parseInt(color.value.slice(1), 16);
      transport.send(encodeValueBits(Number(color.getAttribute("data-pathland-id")), 0xff000000 | rgb));
      return;
    }
    const date = target.closest<HTMLInputElement>(
      "input[type=date][data-pathland-id],input[type=time][data-pathland-id],input[type=datetime-local][data-pathland-id]",
    );
    if (date) {
      const id = Number(date.getAttribute("data-pathland-id"));
      if (date.value) {
        if (date.type === "time") {
          const [h, m] = date.value.split(":").map(Number);
          transport.send(encodeDateChanged(id, 0, ((h ?? 0) * 3600 + (m ?? 0) * 60) * 1000));
        } else if (date.type === "datetime-local") {
          const [ymd, t] = date.value.split("T");
          const [y, mo, d] = (ymd ?? "").split("-").map(Number);
          const [h, mi] = (t ?? "").split(":").map(Number);
          const days = Math.round(Date.UTC(y ?? 0, (mo ?? 1) - 1, d ?? 1) / 86400000);
          transport.send(encodeDateChanged(id, days, ((h ?? 0) * 3600 + (mi ?? 0) * 60) * 1000));
        } else {
          const [y, m, d] = date.value.split("-").map(Number);
          const days = Math.round(Date.UTC(y ?? 0, (m ?? 1) - 1, d ?? 1) / 86400000);
          transport.send(encodeDateChanged(id, days, 0));
        }
      }
    }
  });

  // Sliders report live while dragging; text fields/editors report live while typing.
  document.addEventListener("input", (event) => {
    if (!transport.open) {
      return;
    }
    const target = event.target as Element | null;
    if (!target) {
      return;
    }
    const slider = target.closest<HTMLInputElement>("input[type=range]");
    if (slider) {
      const label = slider.closest<HTMLElement>("label.pathland-slider[data-pathland-id]");
      if (label) {
        transport.send(encodeValueChanged(Number(label.getAttribute("data-pathland-id")), Number(slider.value)));
      }
      return;
    }
    const textarea = target.closest<HTMLTextAreaElement>("textarea[data-pathland-id]");
    if (textarea) {
      transport.send(encodeTextChanged(Number(textarea.getAttribute("data-pathland-id")), textarea.value));
      return;
    }
    const input = target.closest<HTMLInputElement>("input[type=text],input[type=password]");
    if (!input) {
      return;
    }
    const label = input.closest<HTMLElement>("label.pathland-textfield[data-pathland-id]");
    if (label) {
      transport.send(encodeTextChanged(Number(label.getAttribute("data-pathland-id")), input.value));
    }
  });
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", boot);
} else {
  boot();
}