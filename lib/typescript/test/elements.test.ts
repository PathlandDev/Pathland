import { describe, expect, it } from "vitest";
import { createElement } from "../src/elements";
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
} from "../src/constants";

// Drift guard: the runtime element shells MUST mirror the Rust renderer's SSR
// markup (crates/pathland-render-html/src/lib.rs), so nodes created at runtime
// (e.g. a destination subtree reinstated after a navigation swap) keep their
// `pathland-*` classes and structure. Encoding the canonical expectations here
// catches drift between the two renderers.
const EXPECTED: Array<[number, string, string | null, (el: HTMLElement) => boolean | undefined]> = [
  [COMPONENT_TEXT, "SPAN", null, () => undefined],
  [COMPONENT_BUTTON, "BUTTON", "pathland-button", () => undefined],
  [COMPONENT_IMAGE, "IMG", null, () => undefined],
  [COMPONENT_COLOR, "DIV", null, () => undefined],
  [COMPONENT_SHAPE, "DIV", null, () => undefined],
  [COMPONENT_DIVIDER, "DIV", null, (el) => el.style.height === "0" || el.style.height === "0px"],
  [COMPONENT_SPACER, "DIV", null, (el) => el.style.flex !== ""],
  [COMPONENT_PROGRESS_VIEW, "PROGRESS", null, (el) => (el as HTMLProgressElement).max === 1],
  [COMPONENT_GAUGE, "DIV", "pathland-gauge", (el) => el.children.length === 1],
  [COMPONENT_VSTACK, "DIV", null, (el) => el.style.flexDirection === "column"],
  [COMPONENT_HSTACK, "DIV", null, (el) => el.style.flexDirection === "row"],
  [COMPONENT_LAZY_VSTACK, "DIV", null, (el) => el.style.flexDirection === "column"],
  [COMPONENT_LAZY_HSTACK, "DIV", null, (el) => el.style.flexDirection === "row"],
  [COMPONENT_ZSTACK, "DIV", null, (el) => el.style.position === "relative"],
  [COMPONENT_GRID, "DIV", null, (el) => el.style.display === "grid"],
  [COMPONENT_LAZY_VGRID, "DIV", null, (el) => el.style.display === "grid"],
  [COMPONENT_LAZY_HGRID, "DIV", null, (el) => el.style.display === "grid"],
  [COMPONENT_SCROLLVIEW, "DIV", null, (el) => el.style.overflow === "auto"],
  [COMPONENT_TEXT_FIELD, "LABEL", "pathland-textfield", () => undefined],
  [COMPONENT_TEXT_EDITOR, "TEXTAREA", "pathland-input", () => undefined],
  [COMPONENT_TOGGLE, "LABEL", "pathland-toggle", () => undefined],
  [COMPONENT_SLIDER, "LABEL", "pathland-slider", () => undefined],
  [COMPONENT_STEPPER, "DIV", "pathland-stepper", () => undefined],
  [COMPONENT_PICKER, "SELECT", null, () => undefined],
  [COMPONENT_MENU, "DIV", "pathland-menu", () => undefined],
  [COMPONENT_COLOR_PICKER, "INPUT", null, (el) => (el as HTMLInputElement).type === "color"],
  [COMPONENT_DATE_PICKER, "INPUT", null, (el) => (el as HTMLInputElement).type === "date"],
];

describe("createElement runtime shells (drift guard vs the Rust renderer)", () => {
  for (const [component, tag, className, extra] of EXPECTED) {
    it(`component 0x${component.toString(16)} -> <${tag}${className ? " ." + className : ""}>`, () => {
      const el = createElement(component);
      expect(el.nodeType, "element node").toBe(Node.ELEMENT_NODE);
      const html = el as HTMLElement;
      expect(html.tagName).toBe(tag);
      if (className) {
        expect(html.classList.contains(className), `carries ${className}`).toBe(true);
      } else {
        expect(Array.from(html.classList).filter((c) => c.startsWith("pathland"))).toEqual([]);
      }
      const check = extra?.(html);
      if (check !== undefined) {
        expect(check).toBe(true);
      }
    });
  }

  it("COMMENT is an inert comment node", () => {
    expect(createElement(COMPONENT_COMMENT).nodeType).toBe(Node.COMMENT_NODE);
  });
});