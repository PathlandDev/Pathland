// The DOM renderer's native element mapping: create the DOM shell for a node of
// the given component. Used when TREE deltas create nodes at runtime (the SSR
// path renders the shell server-side with Tailwind classes).

import {
  COMPONENT_BUTTON,
  COMPONENT_COLOR,
  COMPONENT_COLOR_PICKER,
  COMPONENT_COMMENT,
  COMPONENT_DATE_PICKER,
  COMPONENT_DIVIDER,
  COMPONENT_GAUGE,
  COMPONENT_GRID,
  COMPONENT_HSTACK,
  COMPONENT_IMAGE,
  COMPONENT_LAZY_HGRID,
  COMPONENT_LAZY_HSTACK,
  COMPONENT_LAZY_VGRID,
  COMPONENT_LAZY_VSTACK,
  COMPONENT_MENU,
  COMPONENT_PICKER,
  COMPONENT_PROGRESS_VIEW,
  COMPONENT_SCROLLVIEW,
  COMPONENT_SHAPE,
  COMPONENT_SLIDER,
  COMPONENT_SPACER,
  COMPONENT_STEPPER,
  COMPONENT_TEXT,
  COMPONENT_TEXT_EDITOR,
  COMPONENT_TEXT_FIELD,
  COMPONENT_TOGGLE,
  COMPONENT_VSTACK,
  COMPONENT_ZSTACK,
} from "./constants";

/** Create the DOM shell for a node of {@code component}. Returns a Node (elements or an inert comment). */
export function createElement(component: number): Node {
  switch (component) {
    case COMPONENT_VSTACK:
    case COMPONENT_LAZY_VSTACK:
      return flexBox("column");
    case COMPONENT_HSTACK:
    case COMPONENT_LAZY_HSTACK:
      return flexBox("row");
    case COMPONENT_ZSTACK: {
      const el = document.createElement("div");
      el.style.position = "relative";
      el.style.width = "100%";
      el.style.height = "100%";
      return el;
    }
    case COMPONENT_GRID:
    case COMPONENT_LAZY_VGRID:
    case COMPONENT_LAZY_HGRID: {
      const el = document.createElement("div");
      el.style.display = "grid";
      // Lazy horizontal grids flow into auto columns (mirrors the Rust SSR
      // renderer's `grid-auto-flow:column;grid-auto-columns:1fr`).
      if (component === COMPONENT_LAZY_HGRID) {
        el.style.gridAutoFlow = "column";
        el.style.gridAutoColumns = "1fr";
      }
      return el;
    }
    case COMPONENT_SCROLLVIEW: {
      const el = document.createElement("div");
      el.style.overflow = "auto";
      return el;
    }
    case COMPONENT_TEXT:
      return document.createElement("span");
    case COMPONENT_BUTTON: {
      const el = document.createElement("button");
      el.className = "pathland-button"; // mirror the Rust renderer's SSR class
      return el;
    }
    case COMPONENT_IMAGE: {
      const el = document.createElement("img");
      el.alt = "";
      return el;
    }
    case COMPONENT_COLOR: {
      // Layout-greedy (SwiftUI Color): expands to the available space unless a
      // size modifier constrains it (mirrors the Rust SSR renderer's
      // `flex:1 1 auto;align-self:stretch`).
      const el = document.createElement("div");
      el.style.flex = "1 1 auto";
      el.style.alignSelf = "stretch";
      return el;
    }
    case COMPONENT_SHAPE:
      return document.createElement("div");
    case COMPONENT_GAUGE: {
      // `<div class="pathland-gauge"><div></div></div>` — the bar width is set from
      // the VALUE property at apply time (mirrors the Rust renderer's SSR markup).
      const el = document.createElement("div");
      el.className = "pathland-gauge";
      el.append(document.createElement("div"));
      return el;
    }
    case COMPONENT_DIVIDER: {
      // `<div style="height:0;border-top:1px solid rgba(0,0,0,0.2)">` — mirrors
      // the Rust SSR renderer's default divider (a div with only a top border, so
      // it is free of the `<hr>` element's UA margins/styles). BORDER_WIDTH /
      // COLOR deltas override border-top at apply time.
      const el = document.createElement("div");
      el.style.height = "0";
      el.style.borderTop = "1px solid rgba(0,0,0,0.2)";
      return el;
    }
    case COMPONENT_SPACER: {
      const el = document.createElement("div");
      el.style.flex = "1";
      return el;
    }
    case COMPONENT_PROGRESS_VIEW: {
      const el = document.createElement("progress");
      el.max = 1;
      return el;
    }
    case COMPONENT_TEXT_EDITOR: {
      const el = document.createElement("textarea");
      el.className = "pathland-input";
      el.setAttribute("rows", "4"); // mirrors the Rust SSR renderer
      return el;
    }
    case COMPONENT_TEXT_FIELD:
      return textFieldShell();
    case COMPONENT_TOGGLE:
      return toggleShell();
    case COMPONENT_SLIDER:
      return sliderShell();
    case COMPONENT_STEPPER:
      return stepperShell();
    case COMPONENT_PICKER:
      return document.createElement("select");
    case COMPONENT_MENU:
      return menuShell();
    case COMPONENT_COLOR_PICKER: {
      const el = document.createElement("input");
      el.type = "color";
      return el;
    }
    case COMPONENT_DATE_PICKER: {
      const el = document.createElement("input");
      el.type = "date";
      return el;
    }
    case COMPONENT_COMMENT:
      return document.createComment("");
    default:
      return document.createElement("div");
  }
}

function flexBox(direction: "column" | "row"): HTMLElement {
  const el = document.createElement("div");
  el.style.display = "flex";
  el.style.flexDirection = direction;
  // Cross-axis default is stretch (the Rust SSR renderer's `align-items` when no
  // ALIGNMENT property is set); an ALIGNMENT delta overrides it.
  el.style.alignItems = "stretch";
  return el;
}

/** `<label class="pathland-textfield"><span class="pathland-label"></span><input type="text"></label>` */
function textFieldShell(): HTMLElement {
  const label = document.createElement("label");
  label.className = "pathland-textfield";
  const span = document.createElement("span");
  span.className = "pathland-label";
  const input = document.createElement("input");
  input.type = "text";
  input.className = "pathland-input";
  label.append(span, input);
  return label;
}

/** `<label class="pathland-toggle"><input type="checkbox"><span class="pathland-text"></span></label>` */
function toggleShell(): HTMLElement {
  const label = document.createElement("label");
  label.className = "pathland-toggle";
  const input = document.createElement("input");
  input.type = "checkbox";
  const span = document.createElement("span");
  span.className = "pathland-text";
  label.append(input, span);
  return label;
}

/** `<label class="pathland-slider"><input type="range" step="any"><span class="pathland-text"></span></label>` */
function sliderShell(): HTMLElement {
  const label = document.createElement("label");
  label.className = "pathland-slider";
  const input = document.createElement("input");
  input.type = "range";
  input.step = "any";
  const span = document.createElement("span");
  span.className = "pathland-text";
  label.append(input, span);
  return label;
}

/** `<div class="pathland-stepper"><button data-step="-1">−</button><span></span><button data-step="1">+</button><span class="pathland-stepper-range" hidden></span></div>` */
function stepperShell(): HTMLElement {
  const el = document.createElement("div");
  el.className = "pathland-stepper";
  const minus = document.createElement("button");
  minus.type = "button";
  minus.dataset.step = "-1";
  minus.textContent = "−";
  const value = document.createElement("span");
  const plus = document.createElement("button");
  plus.type = "button";
  plus.dataset.step = "1";
  plus.textContent = "+";
  const range = document.createElement("span");
  range.className = "pathland-stepper-range";
  range.hidden = true;
  el.append(minus, value, plus, range);
  return el;
}

/** `<div class="pathland-menu"><div class="pathland-menu-trigger"></div><div class="pathland-menu-items"></div></div>` */
function menuShell(): HTMLElement {
  const el = document.createElement("div");
  el.className = "pathland-menu";
  const trigger = document.createElement("div");
  trigger.className = "pathland-menu-trigger";
  const items = document.createElement("div");
  items.className = "pathland-menu-items";
  el.append(trigger, items);
  return el;
}

/**
 * The container a node's TREE children are inserted into. Most nodes append
 * children directly; composite shells route them into their label/trigger span.
 */
export function childrenContainer(node: Node): Node | null {
  if (node instanceof HTMLElement) {
    if (node.classList.contains("pathland-menu")) {
      return node.querySelector(".pathland-menu-items");
    }
    if (node.classList.contains("pathland-toggle") || node.classList.contains("pathland-slider")) {
      return node.querySelector(".pathland-text");
    }
    if (node.classList.contains("pathland-textfield")) {
      return node.querySelector(".pathland-label");
    }
  }
  return node;
}