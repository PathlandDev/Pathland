import { beforeEach, describe, expect, it } from "vitest";
import { applyBatch, setNodeText, updateNavBackButtons, type DomRenderer } from "../src/apply";
import { parseBatch } from "../src/plpl";
import {
  CAT_META,
  CAT_STYLE,
  CAT_TREE,
  CMD_CREATE_NODE,
  CMD_INSERT_CHILD,
  CMD_MOVE_CHILD,
  CMD_RESET,
  CMD_SET_DATE,
  CMD_SET_DESIGN_TOKEN,
  CMD_SET_PROPERTY,
  CMD_SET_TEXT,
  COMPONENT_BUTTON,
  COMPONENT_MENU,
  COMPONENT_PROGRESS_VIEW,
  COMPONENT_TEXT,
  COMPONENT_VSTACK,
  COMPONENT_ZSTACK,
  PROP_COLOR,
  PROP_IMAGE_SOURCE,
  PROP_IS_INDETERMINATE,
  PROP_NAV_CHROME,
  PROP_NAV_DEPTH,
  PROP_PROGRESS,
  PROP_ROUTE,
  PROP_SELECTED,
  PROP_SPACING,
  PROP_TEXT,
  PROP_ROLE,
  PROP_TEXT_STYLE,
  PROP_VALUE,
  ROLE_BANNER,
  ROLE_HEADER,
  TEXT_STYLE_LARGE_TITLE,
  TEXT_STYLE_SUBHEADLINE,
  TEXT_STYLE_TITLE2,
  VAL_DESIGN_TOKEN,
  VAL_F32,
  VAL_STRING,
  VAL_U32,
  VAL_U8,
} from "../src/constants";
import { buildBatch, stringEntry } from "./plpl.test";

const f32bits = (v: number): number => new Uint32Array(new Float32Array([v]).buffer)[0] ?? 0;

beforeEach(() => {
  document.head.querySelectorAll("style[data-pathland-tokens]").forEach((s) => s.remove());
});

function renderer(): DomRenderer {
  return { byId: new Map<number, Node>() };
}

describe("applyBatch · STYLE", () => {
  it("applies SET_TEXT to a hydrated span", () => {
    const span = document.createElement("span");
    span.setAttribute("data-pathland-id", "1");
    const r = renderer();
    r.byId.set(1, span);
    const batch = parseBatch(buildBatch([[CAT_STYLE, CMD_SET_TEXT, 0, 1, 0, 0]], stringEntry("hi!")));
    applyBatch(batch, r);
    expect(span.textContent).toBe("hi!");
  });

  it("applies SET_TEXT to a text field's input", () => {
    const tf = document.createElement("label");
    tf.className = "pathland-textfield";
    const input = document.createElement("input");
    input.type = "text";
    tf.appendChild(input);
    const r = renderer();
    r.byId.set(5, tf);
    applyBatch(parseBatch(buildBatch([[CAT_STYLE, CMD_SET_TEXT, 0, 5, 0, 0]], stringEntry("ab"))), r);
    expect(input.value).toBe("ab");
  });

  it("applies SELECTED to a toggle checkbox", () => {
    const toggle = document.createElement("label");
    toggle.className = "pathland-toggle";
    const box = document.createElement("input");
    box.type = "checkbox";
    toggle.appendChild(box);
    const r = renderer();
    r.byId.set(4, toggle);
    applyBatch(parseBatch(buildBatch([[CAT_STYLE, 1, 0, 4, (0 << 16) | PROP_SELECTED, 1]])), r);
    expect(box.checked).toBe(true);
  });

  it("applies VALUE to a slider", () => {
    const slider = document.createElement("label");
    slider.className = "pathland-slider";
    const range = document.createElement("input");
    range.type = "range";
    slider.appendChild(range);
    const r = renderer();
    r.byId.set(6, slider);
    const bits = new Uint32Array(new Float32Array([0.75]).buffer)[0]!;
    applyBatch(parseBatch(buildBatch([[CAT_STYLE, 1, 0, 6, (0 << 16) | PROP_VALUE, bits]])), r);
    expect(range.value).toBe("0.75");
  });
});

describe("applyBatch · TREE", () => {
  it("builds a stack: create nodes, insert, then set text", () => {
    const r = renderer();
    const batch = parseBatch(
      buildBatch(
        [
          [CAT_TREE, CMD_CREATE_NODE, 0, 1, COMPONENT_VSTACK],
          [CAT_TREE, CMD_CREATE_NODE, 0, 2, COMPONENT_TEXT],
          [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 2],
          [CAT_STYLE, CMD_SET_TEXT, 0, 2, 0],
        ],
        stringEntry("x"),
      ),
    );
    applyBatch(batch, r);
    const stack = r.byId.get(1) as HTMLElement;
    expect(stack).toBeInstanceOf(HTMLDivElement);
    expect(stack.style.display).toBe("flex");
    expect(stack.style.flexDirection).toBe("column");
    expect(r.byId.get(2)?.textContent).toBe("x");
    expect(stack.children).toHaveLength(1);
    expect(stack.firstElementChild?.getAttribute("data-pathland-id")).toBe("2");
  });

  it("removes a node from the DOM", () => {
    const root = document.createElement("div");
    const button = document.createElement("button");
    button.setAttribute("data-pathland-id", "9");
    root.appendChild(button);
    const r = renderer();
    r.byId.set(9, button);
    applyBatch(parseBatch(buildBatch([[CAT_TREE, 2, 0, 9, 0, 0]])), r);
    expect(root.childNodes).toHaveLength(0);
    expect(r.byId.has(9)).toBe(false);
  });

  it("inserts children at the requested index", () => {
    const r = renderer();
    const batch = parseBatch(
      buildBatch([
        [CAT_TREE, CMD_CREATE_NODE, 0, 1, COMPONENT_VSTACK],
        [CAT_TREE, CMD_CREATE_NODE, 0, 2, COMPONENT_BUTTON],
        [CAT_TREE, CMD_CREATE_NODE, 0, 3, COMPONENT_TEXT],
        [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 3],
        [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 2, 0],
      ]),
    );
    applyBatch(batch, r);
    const stack = r.byId.get(1) as HTMLElement;
    expect(stack.children[0]?.getAttribute("data-pathland-id")).toBe("2");
    expect(stack.children[1]?.getAttribute("data-pathland-id")).toBe("3");
  });

  it("moves a child to a new index", () => {
    const r = renderer();
    const batch = parseBatch(
      buildBatch([
        [CAT_TREE, CMD_CREATE_NODE, 0, 1, COMPONENT_VSTACK],
        [CAT_TREE, CMD_CREATE_NODE, 0, 2, COMPONENT_TEXT],
        [CAT_TREE, CMD_CREATE_NODE, 0, 3, COMPONENT_TEXT],
        [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 2],
        [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 3],
        [CAT_TREE, CMD_MOVE_CHILD, 0, 1, 2, 1],
      ]),
    );
    applyBatch(batch, r);
    const stack = r.byId.get(1) as HTMLElement;
    expect(stack.children[0]?.getAttribute("data-pathland-id")).toBe("3");
    expect(stack.children[1]?.getAttribute("data-pathland-id")).toBe("2");
  });
});

describe("setNodeText", () => {
  it("writes through to the composite's label span", () => {
    const slider = document.createElement("label");
    slider.className = "pathland-slider";
    const input = document.createElement("input");
    input.type = "range";
    const span = document.createElement("span");
    span.className = "pathland-text";
    slider.append(input, span);
    setNodeText(slider, "50%");
    expect(span.textContent).toBe("50%");
  });
});

describe("applyBatch · META + design tokens + string props", () => {
  it("META::RESET clears the DOM and the registry", () => {
    const root = document.createElement("div");
    const span = document.createElement("span");
    span.setAttribute("data-pathland-id", "1");
    root.appendChild(span);
    const r = renderer();
    r.byId.set(1, span);
    applyBatch(parseBatch(buildBatch([[CAT_META, CMD_RESET, 0, 0, 0, 0]])), r);
    expect(root.childNodes).toHaveLength(0);
    expect(r.byId.size).toBe(0);
  });

  it("SET_DESIGN_TOKEN applies a CSS variable via the default token sink", () => {
    // A = token-path string offset (0), B low byte = valueType COLOR (0x07), C = 0xAARRGGBB.
    const batch = parseBatch(
      buildBatch([[CAT_STYLE, CMD_SET_DESIGN_TOKEN, 0, 0, 0x07, 0xff0000ff]], stringEntry("color.primary")),
    );
    applyBatch(batch, renderer());
    const style = document.head.querySelector("style[data-pathland-tokens]");
    expect(style?.textContent).toContain(":root{--pl-color-primary:rgba(0,0,255,1.000);}");
  });

  it("SET_DESIGN_TOKEN scopes a dark.* override inside the dark media query", () => {
    const batch = parseBatch(
      buildBatch([[CAT_STYLE, CMD_SET_DESIGN_TOKEN, 0, 0, 0x07, 0xff60a5fa]], stringEntry("dark.color.primary")),
    );
    applyBatch(batch, renderer());
    const style = document.head.querySelector("style[data-pathland-tokens]");
    expect(style?.textContent).toContain(
      "@media (prefers-color-scheme: dark){:root{--pl-color-primary:rgba(96,165,250,1.000);}}",
    );
  });

  it("SET_DESIGN_TOKEN registers a length token with px", () => {
    const batch = parseBatch(
      buildBatch([[CAT_STYLE, CMD_SET_DESIGN_TOKEN, 0, 0, 0x04, 0x40800000]], stringEntry("space.base")),
    );
    applyBatch(batch, renderer());
    const style = document.head.querySelector("style[data-pathland-tokens]");
    expect(style?.textContent).toContain(":root{--pl-space-base:4px;}");
  });

  it("SET_DESIGN_TOKEN resolves a STRING-valued override (font.body.family)", () => {
    // Path "font.body.family" (16 chars → entry 20 B) at offset 0, value
    // "Inter" at offset 20 — the conformance vector 20 wire shape.
    const path = stringEntry("font.body.family");
    const value = stringEntry("Inter");
    const strings = new Uint8Array(path.length + value.length);
    strings.set(path, 0);
    strings.set(value, path.length);
    const batch = parseBatch(
      buildBatch([[CAT_STYLE, CMD_SET_DESIGN_TOKEN, 0, 0, VAL_STRING, 20]], strings),
    );
    applyBatch(batch, renderer());
    const style = document.head.querySelector("style[data-pathland-tokens]");
    expect(style?.textContent).toContain(":root{--pl-font-body-family:'Inter';}");
  });

  it("resolves a DESIGN_TOKEN property reference to var()", () => {
    const span = document.createElement("span");
    const r = renderer();
    r.byId.set(11, span);
    const batch = parseBatch(
      buildBatch(
        [[CAT_STYLE, CMD_SET_PROPERTY, 0, 11, (VAL_DESIGN_TOKEN << 16) | PROP_COLOR, 0]],
        stringEntry("color.primary"),
      ),
    );
    applyBatch(batch, r);
    expect(span.style.color).toBe("var(--pl-color-primary)");
  });

  it("resolves a generative space.<N> property reference to calc()", () => {
    const vstack = document.createElement("div");
    const r = renderer();
    r.byId.set(12, vstack);
    const batch = parseBatch(
      buildBatch(
        [[CAT_STYLE, CMD_SET_PROPERTY, 0, 12, (VAL_DESIGN_TOKEN << 16) | PROP_SPACING, 0]],
        stringEntry("space.2"),
      ),
    );
    applyBatch(batch, r);
    expect(vstack.style.gap).toBe("calc(var(--pl-space-base) * 2)");
  });

  it("applies IMAGE_SOURCE to an img", () => {
    const img = document.createElement("img");
    const r = renderer();
    r.byId.set(8, img);
    applyBatch(
      parseBatch(
        buildBatch([[CAT_STYLE, CMD_SET_PROPERTY, 0, 8, (VAL_STRING << 16) | PROP_IMAGE_SOURCE, 0]], stringEntry("/logo.png")),
      ),
      r,
    );
    expect(img.getAttribute("src")).toBe("/logo.png");
  });

  it("applies the TEXT property like SET_TEXT", () => {
    const span = document.createElement("span");
    const r = renderer();
    r.byId.set(9, span);
    applyBatch(
      parseBatch(
        buildBatch([[CAT_STYLE, CMD_SET_PROPERTY, 0, 9, (VAL_STRING << 16) | PROP_TEXT, 0]], stringEntry("hello")),
      ),
      r,
    );
    expect(span.textContent).toBe("hello");
  });

  it("SET_DATE on a time input writes millis-of-day as HH:MM", () => {
    const input = document.createElement("input");
    input.type = "time";
    const r = renderer();
    r.byId.set(10, input);
    applyBatch(parseBatch(buildBatch([[CAT_STYLE, CMD_SET_DATE, 0, 10, 0, 3600000]])), r);
    expect(input.value).toBe("01:00");
  });
});

describe("applyBatch · hydration reconciliation (no-replay-on-connect)", () => {
  it("reconciles a full snapshot against the SSR-hydrated DOM without clobbering or duplicating", () => {
    // Simulate SSR: a visible stack with a text child, hydrated into byId.
    const root = document.createElement("div");
    const stack = document.createElement("div");
    stack.setAttribute("data-pathland-id", "1");
    const span = document.createElement("span");
    span.setAttribute("data-pathland-id", "2");
    stack.appendChild(span);
    root.appendChild(stack);
    document.body.appendChild(root);

    const r = renderer();
    r.byId.set(1, stack);
    r.byId.set(2, span);

    // The full mount frame the server used to replay over WS (TREE + STYLE).
    const batch = parseBatch(
      buildBatch(
        [
          [CAT_TREE, CMD_CREATE_NODE, 0, 1, COMPONENT_VSTACK],
          [CAT_TREE, CMD_CREATE_NODE, 0, 2, COMPONENT_TEXT],
          [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 2],
          [CAT_STYLE, CMD_SET_TEXT, 0, 2, 0],
        ],
        stringEntry("Hello"),
      ),
    );
    applyBatch(batch, r);

    // No duplicates: the hydrated stack still has exactly one element child.
    expect(stack.children).toHaveLength(1);
    // The visible hydrated element (not a detached clone) got the text.
    expect(span.textContent).toBe("Hello");
    // byId still points at the visible elements.
    expect(r.byId.get(1)).toBe(stack);
    expect(r.byId.get(2)).toBe(span);

    document.body.removeChild(root);
  });

  it("still inserts genuinely new runtime children", () => {
    const stack = document.createElement("div");
    stack.setAttribute("data-pathland-id", "1");
    document.body.appendChild(stack);
    const r = renderer();
    r.byId.set(1, stack);

    const batch = parseBatch(
      buildBatch([
        [CAT_TREE, CMD_CREATE_NODE, 0, 7, COMPONENT_BUTTON],
        [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 7],
      ]),
    );
    applyBatch(batch, r);
    expect(stack.children).toHaveLength(1);
    expect(r.byId.get(7)?.textContent).toBe("");
    document.body.removeChild(stack);
  });
});

describe("applyBatch · navigation (spec DSL.md §4.5)", () => {
  it("mirrors a ROUTE change into the slot attribute and the onRoute hook", () => {
    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "1");
    const routes: string[] = [];
    const r = renderer();
    r.onRoute = (path) => routes.push(path);
    r.byId.set(1, slot);

    const batch = parseBatch(
      buildBatch([[CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_STRING << 16) | PROP_ROUTE, 0]], stringEntry("/users/7")),
    );
    applyBatch(batch, r);
    expect(slot.getAttribute("data-pathland-route")).toBe("/users/7");
    expect(routes).toEqual(["/users/7"]);
  });

  it("animates a child inserted into a transition-hinted slot", () => {
    const r = renderer();
    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "1");
    slot.setAttribute("data-pathland-transition", "fade");
    r.byId.set(1, slot);

    const batch = parseBatch(
      buildBatch([
        [CAT_TREE, CMD_CREATE_NODE, 0, 2, COMPONENT_TEXT],
        [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 2],
      ]),
    );
    applyBatch(batch, r);
    const child = r.byId.get(2) as HTMLElement;
    expect(child.style.animation).toContain("pl-fade");
    expect(document.head.querySelector("style[data-pathland-transitions]")).not.toBeNull();
  });

  it("does not animate children of a slot without a transition hint", () => {
    const r = renderer();
    const stack = document.createElement("div");
    stack.setAttribute("data-pathland-id", "1");
    r.byId.set(1, stack);

    const batch = parseBatch(
      buildBatch([
        [CAT_TREE, CMD_CREATE_NODE, 0, 2, COMPONENT_TEXT],
        [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 2],
      ]),
    );
    applyBatch(batch, r);
    expect((r.byId.get(2) as HTMLElement).style.animation).toBe("");
  });

  it("draws a default back button on a PlatformDefault slot at depth > 1", () => {
    const r = renderer();
    const backs: string[] = [];
    r.onNavigateBack = () => backs.push("back");
    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "1");
    slot.setAttribute("data-pathland-route", "/users");
    r.byId.set(1, slot);

    const batch = parseBatch(
      buildBatch([
        [CAT_TREE, CMD_CREATE_NODE, 0, 2, COMPONENT_TEXT],
        [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 2],
        [CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_U32 << 16) | PROP_NAV_DEPTH, 2],
      ]),
    );
    applyBatch(batch, r);

    const back = slot.querySelector(".pathland-nav-back");
    expect(back).not.toBeNull();
    // The injected button is excluded from reconcile indexing: a child inserted
    // after it still lands in the right slot position.
    expect(slot.children[0]).toBe(back);
    expect(slot.children[1]).toBe(r.byId.get(2));

    (back as HTMLButtonElement).click();
    expect(backs).toEqual(["back"]);
  });

  it("removes the default back button when depth returns to 1", () => {
    const r = renderer();
    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "1");
    slot.setAttribute("data-pathland-route", "/");
    r.byId.set(1, slot);

    applyBatch(
      parseBatch(
        buildBatch([[CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_U32 << 16) | PROP_NAV_DEPTH, 2]]),
      ),
      r,
    );
    expect(slot.querySelector(".pathland-nav-back")).not.toBeNull();

    applyBatch(
      parseBatch(
        buildBatch([[CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_U32 << 16) | PROP_NAV_DEPTH, 1]]),
      ),
      r,
    );
    expect(slot.querySelector(".pathland-nav-back")).toBeNull();
  });

  it("never draws a back button on a Custom-chrome slot", () => {
    const r = renderer();
    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "1");
    slot.setAttribute("data-pathland-route", "/users");
    r.byId.set(1, slot);

    applyBatch(
      parseBatch(
        buildBatch([
          [CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_F32 << 16) | PROP_NAV_CHROME, f32bits(1)],
          [CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_U32 << 16) | PROP_NAV_DEPTH, 3],
        ]),
      ),
      r,
    );
    expect(slot.querySelector(".pathland-nav-back")).toBeNull();
    expect(slot.getAttribute("data-pathland-nav-chrome")).toBe("custom");
  });

  it("hydrates a default back button from the SSR HTML (data-pathland-depth)", () => {
    const r = renderer();
    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "1");
    slot.setAttribute("data-pathland-route", "/users");
    slot.setAttribute("data-pathland-depth", "2");
    r.byId.set(1, slot);

    updateNavBackButtons(r);
    expect(slot.querySelector(".pathland-nav-back")).not.toBeNull();
  });

  it("wraps children inserted into a ZStack in an absolute-positioned div (runtime overlap)", () => {
    const r = renderer();
    const batch = parseBatch(
      buildBatch([
        [CAT_TREE, CMD_CREATE_NODE, 0, 1, COMPONENT_ZSTACK],
        [CAT_TREE, CMD_CREATE_NODE, 0, 2, COMPONENT_TEXT],
        [CAT_TREE, CMD_CREATE_NODE, 0, 3, COMPONENT_BUTTON],
        [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 2],
        [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 3],
      ]),
    );
    applyBatch(batch, r);
    const zstack = r.byId.get(1) as HTMLElement;
    const first = r.byId.get(2) as HTMLElement;
    const second = r.byId.get(3) as HTMLElement;
    // Mirrors the Rust SSR renderer's per-child `<div style="position:absolute;inset:0">` wrapper.
    const wrappers = Array.from(zstack.children).filter((el) => el instanceof HTMLElement);
    expect(wrappers).toHaveLength(2);
    for (const wrapper of wrappers) {
      const el = wrapper as HTMLElement;
      expect(el.getAttribute("style")).toBe("position:absolute;inset:0");
      expect(el.tagName).toBe("DIV");
    }
    expect(zstack.contains(first)).toBe(true);
    expect(zstack.contains(second)).toBe(true);
    expect(first.parentElement).not.toBe(zstack);
  });

  it("morphs a ProgressView between spinner and determinate progress", () => {
    const r = renderer();
    applyBatch(
      parseBatch(
        buildBatch([
          [CAT_TREE, CMD_CREATE_NODE, 0, 1, COMPONENT_PROGRESS_VIEW],
          [CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_U8 << 16) | PROP_IS_INDETERMINATE, 1],
        ]),
      ),
      r,
    );
    const spinner = r.byId.get(1) as HTMLElement;
    expect(spinner.classList.contains("pathland-spinner")).toBe(true);
    expect(spinner.tagName).toBe("DIV");

    applyBatch(
      parseBatch(
        buildBatch([
          [CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_F32 << 16) | PROP_PROGRESS, f32bits(0.5)],
        ]),
      ),
      r,
    );
    const progress = r.byId.get(1);
    expect(progress).toBeInstanceOf(HTMLProgressElement);
    expect((progress as HTMLProgressElement).value).toBeCloseTo(0.5);
    expect((progress as HTMLProgressElement).max).toBe(1);
  });
});

describe("applyBatch · semantic roles (generated role-spec)", () => {
  const roleBatch = (id: number, component: number, role: number) =>
    buildBatch([
      [CAT_TREE, CMD_CREATE_NODE, 0, id, component],
      [CAT_STYLE, CMD_SET_PROPERTY, 0, id, (VAL_F32 << 16) | PROP_ROLE, f32bits(role)],
    ]);

  const styleBatch = (id: number, component: number, style: number) =>
    buildBatch([
      [CAT_TREE, CMD_CREATE_NODE, 0, id, component],
      [CAT_STYLE, CMD_SET_PROPERTY, 0, id, (VAL_F32 << 16) | PROP_TEXT_STYLE, f32bits(style)],
    ]);

  it("retags a generic Text with role=HEADER to an <h2> (no redundant role attr)", () => {
    const r = renderer();
    applyBatch(parseBatch(roleBatch(1, COMPONENT_TEXT, ROLE_HEADER)), r);
    const el = r.byId.get(1) as HTMLElement;
    expect(el.tagName.toLowerCase()).toBe("h2");
    expect(el.getAttribute("role")).toBeNull();
  });

  it("retags a generic stack with role=BANNER to a <header>", () => {
    const r = renderer();
    applyBatch(parseBatch(roleBatch(1, COMPONENT_VSTACK, ROLE_BANNER)), r);
    const el = r.byId.get(1) as HTMLElement;
    expect(el.tagName.toLowerCase()).toBe("header");
    expect(el.getAttribute("role")).toBeNull();
  });

  it("a heading TEXT_STYLE makes a Text a heading even without a role (TITLE2 -> <h3>)", () => {
    const r = renderer();
    applyBatch(parseBatch(styleBatch(1, COMPONENT_TEXT, TEXT_STYLE_TITLE2)), r);
    const el = r.byId.get(1) as HTMLElement;
    expect(el.tagName.toLowerCase()).toBe("h3");
    expect(el.getAttribute("role")).toBeNull();
  });

  it("a non-heading TEXT_STYLE (SUBHEADLINE) stays a <span>", () => {
    const r = renderer();
    applyBatch(parseBatch(styleBatch(1, COMPONENT_TEXT, TEXT_STYLE_SUBHEADLINE)), r);
    const el = r.byId.get(1) as HTMLElement;
    expect(el.tagName.toLowerCase()).toBe("span");
  });

  it("a heading TEXT_STYLE wins over the role default (LARGE_TITLE + HEADER -> <h1>)", () => {
    const r = renderer();
    applyBatch(
      parseBatch(
        buildBatch([
          [CAT_TREE, CMD_CREATE_NODE, 0, 1, COMPONENT_TEXT],
          [CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_F32 << 16) | PROP_TEXT_STYLE, f32bits(TEXT_STYLE_LARGE_TITLE)],
          [CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_F32 << 16) | PROP_ROLE, f32bits(ROLE_HEADER)],
        ]),
      ),
      r,
    );
    const el = r.byId.get(1) as HTMLElement;
    expect(el.tagName.toLowerCase()).toBe("h1");
  });

  it("a control component keeps its native element and no role attribute", () => {
    const r = renderer();
    applyBatch(parseBatch(roleBatch(1, COMPONENT_BUTTON, ROLE_HEADER)), r);
    const el = r.byId.get(1) as HTMLElement;
    expect(el.tagName.toLowerCase()).toBe("button");
    expect(el.getAttribute("role")).toBeNull();
  });

  it("a reserved/unknown role code is a no-op on a generic shell", () => {
    const r = renderer();
    applyBatch(parseBatch(roleBatch(1, COMPONENT_TEXT, 14)), r); // reserved (was a control role)
    const el = r.byId.get(1) as HTMLElement;
    expect(el.tagName.toLowerCase()).toBe("span");
    expect(el.getAttribute("role")).toBeNull();
  });

  it("a MENU renders with its intrinsic role=\"menu\"", () => {
    const r = renderer();
    applyBatch(
      parseBatch(
        buildBatch([[CAT_TREE, CMD_CREATE_NODE, 0, 1, COMPONENT_MENU]]),
      ),
      r,
    );
    const el = r.byId.get(1) as HTMLElement;
    expect(el.getAttribute("role")).toBe("menu");
  });
});