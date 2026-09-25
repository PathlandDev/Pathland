import { describe, expect, it } from "vitest";
import { applyEnabled, applyProperty, alignmentCss, sizeCss } from "../src/classes";
import {
  PROP_ACTION_ID,
  PROP_ALIGNMENT,
  PROP_BACKGROUND_COLOR,
  PROP_BLUR_RADIUS,
  PROP_BORDER_COLOR,
  PROP_BORDER_EDGES,
  PROP_BORDER_RADIUS,
  PROP_BORDER_WIDTH,
  PROP_COLOR,
  PROP_DATE_PICKER_MODE,
  PROP_EVENT_LISTENERS,
  PROP_FONT_SIZE,
  PROP_FONT_WEIGHT,
  PROP_IS_SECURE,
  PROP_LINE_LIMIT,
  PROP_OPACITY,
  PROP_PROGRESS,
  PROP_ROTATION_DEGREES,
  PROP_SHADOW_COLOR,
  PROP_SHADOW_RADIUS,
  PROP_SPACING,
  PROP_STATE,
  PROP_TEXT_ALIGNMENT,
  PROP_TEXT_CASE,
  PROP_TOGGLE_STYLE,
  PROP_VISIBLE,
  PROP_Z_INDEX,
  STATE_DISABLED,
  STATE_SELECTED,
  TOGGLE_STYLE_CHECKBOX,
  TOGGLE_STYLE_SWITCH,
  VAL_COLOR,
  VAL_F32,
  VAL_U32,
  VAL_U8,
  VAL_STRING,
} from "../src/constants";
import { bitsFromF32 } from "../src/format";

describe("Option A style appliers", () => {
  it("spacing -> gap", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_SPACING, VAL_F32, bitsFromF32(13));
    expect(el.style.gap).toBe("13px");
  });

  it("alignment (F32 enum) -> align-items", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_ALIGNMENT, VAL_F32, bitsFromF32(1)); // Center
    expect(el.style.alignItems).toBe("center");
  });

  it("opacity -> inline opacity", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_OPACITY, VAL_F32, bitsFromF32(0.5));
    expect(el.style.opacity).toBe("0.5");
  });

  it("font-size / font-weight -> inline typography", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_FONT_SIZE, VAL_F32, bitsFromF32(18));
    applyProperty(el, PROP_FONT_WEIGHT, VAL_F32, bitsFromF32(700));
    expect(el.style.fontSize).toBe("18px");
    expect(el.style.fontWeight).toBe("700");
  });

  it("text-align -> textAlign", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_TEXT_ALIGNMENT, VAL_F32, bitsFromF32(2)); // Trailing
    expect(el.style.textAlign).toBe("right");
  });

  it("text-case -> textTransform", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_TEXT_CASE, VAL_F32, bitsFromF32(1)); // Uppercase
    expect(el.style.textTransform).toBe("uppercase");
  });

  it("line-limit -> webkitLineClamp", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_LINE_LIMIT, VAL_U32, 2);
    expect(el.style.webkitLineClamp).toBe("2");
  });

  it("color -> rgba", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_COLOR, VAL_COLOR, 0xff000000);
    expect(el.style.color).toBe("rgba(0, 0, 0, 1.000)");
  });

  it("background color -> rgba", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_BACKGROUND_COLOR, VAL_COLOR, 0x80ff0000);
    expect(el.style.backgroundColor).toBe("rgba(255, 0, 0, 0.502)");
  });

  it("visible=0 -> display none, visible=1 -> restored", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_VISIBLE, VAL_U8, 0);
    expect(el.style.display).toBe("none");
    applyProperty(el, PROP_VISIBLE, VAL_U8, 1);
    expect(el.style.display).toBe("");
  });

  it("width FILL -> 100%, HUG -> fit-content, fixed -> px", () => {
    expect(sizeCss(-1)).toBe("100%");
    expect(sizeCss(-2)).toBe("fit-content");
    expect(sizeCss(320)).toBe("320px");
  });

  it("z-index -> zIndex", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_Z_INDEX, VAL_U32, 5);
    expect(el.style.zIndex).toBe("5");
  });

  it("border width + color + edges -> per-side borders", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_BORDER_WIDTH, VAL_F32, bitsFromF32(2));
    applyProperty(el, PROP_BORDER_COLOR, VAL_COLOR, 0xffff0000);
    applyProperty(el, PROP_BORDER_EDGES, VAL_U32, 0x03); // top + left
    expect(el.style.borderTop).toBe("2px solid rgba(255, 0, 0, 1.000)");
    expect(el.style.borderLeft).toBe("2px solid rgba(255, 0, 0, 1.000)");
    expect(el.style.borderRight).toBe("none none");
    expect(el.style.borderBottom).toBe("none none");
  });

  it("border-radius -> borderRadius", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_BORDER_RADIUS, VAL_F32, bitsFromF32(8));
    expect(el.style.borderRadius).toBe("8px");
  });

  it("shadow accumulates into boxShadow", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_SHADOW_RADIUS, VAL_F32, bitsFromF32(4));
    applyProperty(el, PROP_SHADOW_COLOR, VAL_COLOR, 0x80000000);
    expect(el.style.boxShadow).toContain("rgba(0,0,0,0.502)");
  });

  it("blur -> filter", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_BLUR_RADIUS, VAL_F32, bitsFromF32(3));
    expect(el.style.filter).toBe("blur(3px)");
  });

  it("rotation + scale -> transform", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_ROTATION_DEGREES, VAL_F32, bitsFromF32(90));
    applyProperty(el, 0x102e, VAL_F32, bitsFromF32(1.5)); // SCALE
    expect(el.style.transform).toBe("rotate(90deg) scale(1.5)");
  });
});

describe("semantic + control variants", () => {
  it("state -> aria attributes", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_STATE, VAL_F32, bitsFromF32(STATE_DISABLED));
    expect(el.getAttribute("aria-disabled")).toBe("true");
    applyProperty(el, PROP_STATE, VAL_F32, bitsFromF32(STATE_SELECTED));
    expect(el.getAttribute("aria-selected")).toBe("true");
  });

  it("event-listeners -> data mask", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_EVENT_LISTENERS, VAL_U32, 0x0c); // POINTER_UP + KEY_DOWN
    expect(el.getAttribute("data-event-listeners")).toBe("12");
  });

  it("action-id -> data-action-id", () => {
    const el = document.createElement("div");
    applyProperty(el, PROP_ACTION_ID, VAL_U32, 77);
    expect(el.dataset.actionId).toBe("77");
  });

  it("is-secure flips input text -> password", () => {
    const tf = document.createElement("label");
    tf.className = "pathland-textfield";
    const input = document.createElement("input");
    input.type = "text";
    tf.appendChild(input);
    applyProperty(tf, PROP_IS_SECURE, VAL_U8, 1);
    expect(input.type).toBe("password");
    applyProperty(tf, PROP_IS_SECURE, VAL_U8, 0);
    expect(input.type).toBe("text");
  });

  it("toggle-style switch/checkbox set the input role", () => {
    const toggle = document.createElement("label");
    toggle.className = "pathland-toggle";
    const box = document.createElement("input");
    box.type = "checkbox";
    toggle.appendChild(box);
    applyProperty(toggle, PROP_TOGGLE_STYLE, VAL_F32, bitsFromF32(TOGGLE_STYLE_SWITCH));
    expect(box.getAttribute("role")).toBe("switch");
    applyProperty(toggle, PROP_TOGGLE_STYLE, VAL_F32, bitsFromF32(TOGGLE_STYLE_CHECKBOX));
    expect(box.hasAttribute("role")).toBe(false);
  });

  it("date-picker-mode time -> input[type=time]", () => {
    const input = document.createElement("input");
    input.type = "date";
    applyProperty(input, PROP_DATE_PICKER_MODE, VAL_F32, bitsFromF32(1));
    expect(input.type).toBe("time");
  });

  it("progress -> progress value", () => {
    const progress = document.createElement("progress");
    applyProperty(progress, PROP_PROGRESS, VAL_F32, bitsFromF32(0.4));
    expect(progress.value).toBeCloseTo(0.4);
  });

  it("alignmentCss maps enum codes", () => {
    expect(alignmentCss(0)).toBe("flex-start");
    expect(alignmentCss(1)).toBe("center");
    expect(alignmentCss(2)).toBe("flex-end");
  });

  it("disabled toggles native controls + aria-disabled on other elements", () => {
    const button = document.createElement("button");
    applyEnabled(button, 0);
    expect(button.disabled).toBe(true);
    const div = document.createElement("div");
    applyEnabled(div, 0);
    expect(div.getAttribute("aria-disabled")).toBe("true");
    applyEnabled(div, 1);
    expect(div.hasAttribute("aria-disabled")).toBe(false);
  });
});

// Keep VAL_STRING referenced so the import documents the string path is handled in apply.ts.
void VAL_STRING;