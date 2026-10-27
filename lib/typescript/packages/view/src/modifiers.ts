import {
  CONTENT_MODE_FILL,
  CONTENT_MODE_FIT,
  PROP_BACKGROUND_COLOR,
  PROP_BORDER_RADIUS,
  PROP_COLOR,
  PROP_CONTENT_MODE,
  PROP_FONT_SIZE,
  PROP_FONT_WEIGHT,
  PROP_HEIGHT,
  PROP_LINE_LIMIT,
  PROP_MAX_HEIGHT,
  PROP_MAX_WIDTH,
  PROP_MIN_HEIGHT,
  PROP_PADDING,
  PROP_WIDTH,
  VAL_COLOR,
  VAL_F32,
  VAL_U32,
} from "@pathland/protocol";
import { computed, isSignal, type MaybeSignal, type ReadonlySignal } from "@pathland/signals";
import type { PropertyValue, ViewModifier } from "./element";

/** The protocol's FILL/HUG size hints (F32). */
export const FILL = -1;
export const HUG = -2;

export function padding(value: number): ViewModifier {
  return { property: PROP_PADDING, valueType: VAL_F32, value };
}

export function cornerRadius(value: number): ViewModifier {
  return { property: PROP_BORDER_RADIUS, valueType: VAL_F32, value };
}

export function fontSize(value: number): ViewModifier {
  return { property: PROP_FONT_SIZE, valueType: VAL_F32, value };
}

export function fontWeight(value: number): ViewModifier {
  return { property: PROP_FONT_WEIGHT, valueType: VAL_F32, value };
}

export function lineLimit(value: number): ViewModifier {
  return { property: PROP_LINE_LIMIT, valueType: VAL_U32, value };
}

export function width(value: PropertyValue): ViewModifier {
  return { property: PROP_WIDTH, valueType: VAL_F32, value };
}

export function height(value: PropertyValue): ViewModifier {
  return { property: PROP_HEIGHT, valueType: VAL_F32, value };
}

export function maxWidth(value: PropertyValue): ViewModifier {
  return { property: PROP_MAX_WIDTH, valueType: VAL_F32, value };
}

export function minHeight(value: PropertyValue): ViewModifier {
  return { property: PROP_MIN_HEIGHT, valueType: VAL_F32, value };
}

export function maxHeight(value: PropertyValue): ViewModifier {
  return { property: PROP_MAX_HEIGHT, valueType: VAL_F32, value };
}

export function fillWidth(): ViewModifier {
  return width(FILL);
}

export function fillHeight(): ViewModifier {
  return height(FILL);
}

export function scaledToFill(): ViewModifier {
  return { property: PROP_CONTENT_MODE, valueType: VAL_F32, value: CONTENT_MODE_FILL };
}

export function scaledToFit(): ViewModifier {
  return { property: PROP_CONTENT_MODE, valueType: VAL_F32, value: CONTENT_MODE_FIT };
}

export function background(color: MaybeSignal<string | number>): ViewModifier {
  return { property: PROP_BACKGROUND_COLOR, valueType: VAL_COLOR, value: colorValue(color) };
}

export function foregroundStyle(color: MaybeSignal<string | number>): ViewModifier {
  return { property: PROP_COLOR, valueType: VAL_COLOR, value: colorValue(color) };
}

function colorValue(color: MaybeSignal<string | number>): PropertyValue {
  if (isSignal(color)) {
    const signal = color as ReadonlySignal<string | number>;
    return computed(() => colorBits(signal()));
  }
  return colorBits(color);
}

/** Pack a CSS-like color into the wire's sRGB `0xAARRGGBB`. */
export function colorBits(color: string | number): number {
  if (typeof color === "number") return color >>> 0;
  const text = color.trim();
  if (!text.startsWith("#")) throw new Error(`unsupported color "${color}"`);
  const hex = text.slice(1);
  if (hex.length === 3) {
    const r = parseInt(hex[0]! + hex[0]!, 16);
    const g = parseInt(hex[1]! + hex[1]!, 16);
    const b = parseInt(hex[2]! + hex[2]!, 16);
    return (0xff000000 | (r << 16) | (g << 8) | b) >>> 0;
  }
  if (hex.length === 6) return (0xff000000 | parseInt(hex, 16)) >>> 0;
  if (hex.length === 8) return parseInt(hex, 16) >>> 0;
  throw new Error(`unsupported color "${color}"`);
}
