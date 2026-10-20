// Human-readable descriptions of opcodes/batches for the debug logs
// (`log.debug("ws", describeBatch(batch))`): turns the numeric wire surface into
// e.g. `TREE CREATE_NODE(id=2, VSTACK)` or `EVENT POINTER_UP(target=4)`.

import type { Batch, Opcode } from "./plpl";
import { readString } from "./plpl";
import {
  CAT_EVENT,
  CAT_META,
  CAT_PARAMETER,
  CAT_TREE,
  CMD_CREATE_NODE,
  CMD_DATE_CHANGED,
  CMD_DELETE_NODE,
  CMD_EDITING_CHANGED,
  CMD_ENVIRONMENT,
  CMD_FOCUS_CHANGED,
  CMD_INSERT_CHILD,
  CMD_KEY_DOWN,
  CMD_KEY_UP,
  CMD_MOVE_CHILD,
  CMD_NAVIGATE,
  CMD_POINTER_DOWN,
  CMD_POINTER_MOVE,
  CMD_POINTER_UP,
  CMD_REMOVE_CHILD,
  CMD_RESET,
  CMD_RESYNC,
  CMD_SCROLL,
  CMD_SET_DESIGN_TOKEN,
  CMD_SET_DATE,
  CMD_SET_PROPERTY,
  CMD_SET_TEXT,
  CMD_SUBMIT,
  CMD_TEXT_CHANGED,
  CMD_VALUE_CHANGED,
  CMD_WHEEL,
  COMPONENT_BUTTON,
  COMPONENT_COMMENT,
  COMPONENT_COLOR,
  COMPONENT_COLOR_PICKER,
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
  ENV_ROUTE,
  ENV_VIEWPORT_HEIGHT,
  ENV_VIEWPORT_WIDTH,
  FLAG_NAVIGATE_URL,
  PROP_BACKGROUND_COLOR,
  PROP_BINDING_ID,
  PROP_BORDER_COLOR,
  PROP_BORDER_WIDTH,
  PROP_COLOR,
  PROP_COLOR_VALUE,
  PROP_ENABLED,
  PROP_EVENT_LISTENERS,
  PROP_FONT_FAMILY,
  PROP_FONT_SIZE,
  PROP_FONT_WEIGHT,
  PROP_IMAGE_SOURCE,
  PROP_LABEL,
  PROP_OPACITY,
  PROP_PADDING,
  PROP_PADDING_BOTTOM,
  PROP_PADDING_LEFT,
  PROP_PADDING_RIGHT,
  PROP_PADDING_TOP,
  PROP_PROMPT,
  PROP_ROUTE,
  PROP_SELECTED,
  PROP_SELECTION,
  PROP_SPACING,
  PROP_TEXT,
  PROP_TINT,
  PROP_TRANSITION,
  PROP_VALUE,
  PROP_VISIBLE,
  VAL_DESIGN_TOKEN,
  VAL_STRING,
} from "./constants";

const TREE_NAME: Record<number, string> = {
  [CMD_CREATE_NODE]: "CREATE_NODE",
  [CMD_DELETE_NODE]: "DELETE_NODE",
  [CMD_INSERT_CHILD]: "INSERT_CHILD",
  [CMD_REMOVE_CHILD]: "REMOVE_CHILD",
  [CMD_MOVE_CHILD]: "MOVE_CHILD",
};

const PARAMETER_NAME: Record<number, string> = {
  [CMD_SET_PROPERTY]: "SET_PROPERTY",
  [CMD_SET_DESIGN_TOKEN]: "SET_DESIGN_TOKEN",
  [CMD_SET_TEXT]: "SET_TEXT",
  [CMD_SET_DATE]: "SET_DATE",
};

const EVENT_NAME: Record<number, string> = {
  [CMD_POINTER_DOWN]: "POINTER_DOWN",
  [CMD_POINTER_MOVE]: "POINTER_MOVE",
  [CMD_POINTER_UP]: "POINTER_UP",
  [CMD_KEY_DOWN]: "KEY_DOWN",
  [CMD_KEY_UP]: "KEY_UP",
  [CMD_VALUE_CHANGED]: "VALUE_CHANGED",
  [CMD_TEXT_CHANGED]: "TEXT_CHANGED",
  [CMD_FOCUS_CHANGED]: "FOCUS_CHANGED",
  [CMD_EDITING_CHANGED]: "EDITING_CHANGED",
  [CMD_SUBMIT]: "SUBMIT",
  [CMD_SCROLL]: "SCROLL",
  [CMD_WHEEL]: "WHEEL",
  [CMD_DATE_CHANGED]: "DATE_CHANGED",
  [CMD_NAVIGATE]: "NAVIGATE",
};

const META_NAME: Record<number, string> = {
  [CMD_RESET]: "RESET",
  [CMD_ENVIRONMENT]: "ENVIRONMENT",
  [CMD_RESYNC]: "RESYNC",
};

const COMPONENT_NAME: Record<number, string> = {
  [COMPONENT_TEXT]: "TEXT",
  [COMPONENT_IMAGE]: "IMAGE",
  [COMPONENT_COLOR]: "COLOR",
  [COMPONENT_SHAPE]: "SHAPE",
  [COMPONENT_DIVIDER]: "DIVIDER",
  [COMPONENT_SPACER]: "SPACER",
  [COMPONENT_PROGRESS_VIEW]: "PROGRESS_VIEW",
  [COMPONENT_GAUGE]: "GAUGE",
  [COMPONENT_VSTACK]: "VSTACK",
  [COMPONENT_HSTACK]: "HSTACK",
  [COMPONENT_ZSTACK]: "ZSTACK",
  [COMPONENT_GRID]: "GRID",
  [COMPONENT_SCROLLVIEW]: "SCROLLVIEW",
  [COMPONENT_LAZY_VGRID]: "LAZY_VGRID",
  [COMPONENT_LAZY_HGRID]: "LAZY_HGRID",
  [COMPONENT_LAZY_VSTACK]: "LAZY_VSTACK",
  [COMPONENT_LAZY_HSTACK]: "LAZY_HSTACK",
  [COMPONENT_BUTTON]: "BUTTON",
  [COMPONENT_TEXT_FIELD]: "TEXT_FIELD",
  [COMPONENT_TEXT_EDITOR]: "TEXT_EDITOR",
  [COMPONENT_TOGGLE]: "TOGGLE",
  [COMPONENT_SLIDER]: "SLIDER",
  [COMPONENT_STEPPER]: "STEPPER",
  [COMPONENT_DATE_PICKER]: "DATE_PICKER",
  [COMPONENT_PICKER]: "PICKER",
  [COMPONENT_MENU]: "MENU",
  [COMPONENT_COLOR_PICKER]: "COLOR_PICKER",
  [COMPONENT_COMMENT]: "COMMENT",
};

const PROPERTY_NAME: Record<number, string> = {
  [PROP_SPACING]: "SPACING",
  [PROP_TEXT]: "TEXT",
  [PROP_PADDING]: "PADDING",
  [PROP_PADDING_TOP]: "PADDING_TOP",
  [PROP_PADDING_RIGHT]: "PADDING_RIGHT",
  [PROP_PADDING_BOTTOM]: "PADDING_BOTTOM",
  [PROP_PADDING_LEFT]: "PADDING_LEFT",
  [PROP_BACKGROUND_COLOR]: "BACKGROUND_COLOR",
  [PROP_FONT_SIZE]: "FONT_SIZE",
  [PROP_FONT_WEIGHT]: "FONT_WEIGHT",
  [PROP_FONT_FAMILY]: "FONT_FAMILY",
  [PROP_COLOR]: "COLOR",
  [PROP_OPACITY]: "OPACITY",
  [PROP_VISIBLE]: "VISIBLE",
  [PROP_BORDER_WIDTH]: "BORDER_WIDTH",
  [PROP_BORDER_COLOR]: "BORDER_COLOR",
  [PROP_TINT]: "TINT",
  [PROP_TRANSITION]: "TRANSITION",
  [PROP_ENABLED]: "ENABLED",
  [PROP_SELECTED]: "SELECTED",
  [PROP_EVENT_LISTENERS]: "EVENT_LISTENERS",
  [PROP_VALUE]: "VALUE",
  [PROP_LABEL]: "LABEL",
  [PROP_PROMPT]: "PROMPT",
  [PROP_IMAGE_SOURCE]: "IMAGE_SOURCE",
  [PROP_SELECTION]: "SELECTION",
  [PROP_COLOR_VALUE]: "COLOR_VALUE",
  [PROP_BINDING_ID]: "BINDING_ID",
  [PROP_ROUTE]: "ROUTE",
};

const ENV_NAME: Record<number, string> = {
  [ENV_VIEWPORT_WIDTH]: "VIEWPORT_WIDTH",
  [ENV_VIEWPORT_HEIGHT]: "VIEWPORT_HEIGHT",
  [ENV_ROUTE]: "ROUTE",
};

const hex = (v: number): string => `0x${(v >>> 0).toString(16)}`;
const f32 = (bits: number): number => new Float32Array(new Uint32Array([bits]).buffer)[0] ?? 0;
const name = (table: Record<number, string>, id: number): string => table[id] ?? hex(id);

function describeProperty(batch: Batch, op: Opcode): string {
  const prop = op.b & 0xffff;
  const valueType = (op.b >>> 16) & 0xff;
  const pn = name(PROPERTY_NAME, prop);
  if (valueType === VAL_STRING) return `${pn}="${readString(batch.strings, op.c)}"`;
  if (valueType === VAL_DESIGN_TOKEN) return `${pn}=token("${readString(batch.strings, op.c)}")`;
  return `${pn}=${hex(op.c)}`;
}

function describeEvent(batch: Batch, op: Opcode): string {
  const en = name(EVENT_NAME, op.command);
  const t = `target=${op.a}`;
  switch (op.command) {
    case CMD_POINTER_DOWN:
    case CMD_POINTER_MOVE:
    case CMD_POINTER_UP:
      return `EVENT ${en}(${t}, x=${f32(op.b)}, y=${f32(op.c)})`;
    case CMD_KEY_DOWN:
    case CMD_KEY_UP:
      return `EVENT ${en}(${t}, key=${hex(op.b)}, mods=${hex(op.c)})`;
    case CMD_VALUE_CHANGED:
      return `EVENT ${en}(${t}, value=${f32(op.b)})`;
    case CMD_TEXT_CHANGED:
      return `EVENT ${en}(${t}, "${readString(batch.strings, op.b)}")`;
    case CMD_NAVIGATE:
      return op.flags & FLAG_NAVIGATE_URL
        ? `EVENT ${en}(${t}, url="${readString(batch.strings, op.b)}")`
        : `EVENT ${en}(back)`;
    case CMD_DATE_CHANGED:
      return `EVENT ${en}(${t}, days=${op.b >>> 0}, millis=${op.c})`;
    case CMD_SCROLL:
    case CMD_WHEEL:
      return `EVENT ${en}(${t}, x=${f32(op.b)}, y=${f32(op.c)})`;
    default:
      return `EVENT ${en}(${t})`;
  }
}

function describeOpcode(batch: Batch, op: Opcode): string {
  switch (op.category) {
    case CAT_TREE: {
      const tn = name(TREE_NAME, op.command);
      switch (op.command) {
        case CMD_CREATE_NODE:
          return `TREE ${tn}(id=${op.a}, ${name(COMPONENT_NAME, op.b & 0xffff)})`;
        case CMD_DELETE_NODE:
          return `TREE ${tn}(id=${op.a})`;
        case CMD_INSERT_CHILD:
        case CMD_MOVE_CHILD:
          return `TREE ${tn}(parent=${op.a}, child=${op.b}, index=${op.c})`;
        case CMD_REMOVE_CHILD:
          return `TREE ${tn}(parent=${op.a}, child=${op.b})`;
        default:
          return `TREE ${tn}(a=${op.a}, b=${op.b}, c=${op.c})`;
      }
    }
    case CAT_PARAMETER: {
      const sn = name(PARAMETER_NAME, op.command);
      switch (op.command) {
        case CMD_SET_PROPERTY:
          return `STYLE ${sn}(${describeProperty(batch, op)}, node=${op.a})`;
        case CMD_SET_TEXT:
          return `STYLE ${sn}("${readString(batch.strings, op.b)}", node=${op.a})`;
        case CMD_SET_DATE:
          return `STYLE ${sn}(node=${op.a}, days=${op.b >>> 0}, millis=${op.c})`;
        case CMD_SET_DESIGN_TOKEN:
          return `STYLE ${sn}("${readString(batch.strings, op.a)}", vt=${hex(op.b)})`;
        default:
          return `STYLE ${sn}(node=${op.a}, b=${op.b}, c=${op.c})`;
      }
    }
    case CAT_EVENT:
      return describeEvent(batch, op);
    case CAT_META: {
      const mn = name(META_NAME, op.command);
      if (op.command === CMD_ENVIRONMENT) {
        const field = name(ENV_NAME, op.a & 0xffff);
        return `META ${mn}(${field}=${
          (op.a & 0xffff) === ENV_ROUTE ? `"${readString(batch.strings, op.b)}"` : f32(op.b)
        })`;
      }
      return `META ${mn}(a=${op.a}, b=${op.b}, c=${op.c})`;
    }
    default:
      return `${hex(op.category)} ${hex(op.command)}(a=${op.a}, b=${op.b}, c=${op.c})`;
  }
}

/** Compact one-line batch summary: the first opcode + count (safe for `info`). */
export function describeBatch(batch: Batch): string {
  const first = batch.opcodes[0];
  const head = first ? describeOpcode(batch, first) : "(no opcodes)";
  return `seq=${batch.sequence} (${batch.opcodes.length} op${batch.opcodes.length === 1 ? "" : "s"}): ${head}`;
}

/** Full per-opcode listing (for `debug`). */
export function describeBatchDetail(batch: Batch): string {
  return `seq=${batch.sequence} [${batch.opcodes.map((op) => describeOpcode(batch, op)).join("; ")}]`;
}