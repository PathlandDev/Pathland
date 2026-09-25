// Design tokens: SET_DESIGN_TOKEN deltas apply token overrides as CSS custom
// properties (spec/TOKENS.md). Base (light) tokens are emitted as `:root`
// rules; `dark.*` overrides are emitted inside
// `@media (prefers-color-scheme: dark)` so the browser resolves the scheme
// natively. A managed <style> element is used instead of inline
// `documentElement.style` — an inline dark override would beat the media query
// and break light mode.
//
// The token *conventions* (`tokenToCssVar`, `isDarkToken`, `isLengthToken`,
// `resolveTokenCssRef` and the length-token tables) are generated from
// `pathland-render-html::token_spec` by `pathland-ts-codegen` — see
// `generated/tokens-core.ts`.

import type { Batch, Opcode } from "./plpl";
import { readString } from "./plpl";
import { VAL_COLOR, VAL_F32, VAL_I32, VAL_STRING, VAL_U8, VAL_U32 } from "./constants";
import { argbToRgba, f32FromBits } from "./format";
import {
  isDarkToken,
  isLengthToken,
  tokenToCssVar,
} from "./generated/tokens-core";

// Re-exported for callers that use the token conventions directly.
export { isDarkToken, resolveTokenCssRef, tokenToCssVar } from "./generated/tokens-core";

/** Render a token value as a CSS value string per the value type. */
export function tokenCssValue(valueType: number, c: number): string {
  switch (valueType) {
    case VAL_COLOR:
      return argbToRgba(c);
    case VAL_F32:
      return String(f32FromBits(c));
    case VAL_I32:
      return String(c | 0);
    case VAL_U32:
      return String(c >>> 0);
    case VAL_U8:
      return String(c & 0xff);
    default:
      return String(c >>> 0);
  }
}

/** Render a token override value as CSS, appending `px` to F32 length tokens.
 *  `STRING` values (e.g. `font.body.family`) are single-quoted and escaped. */
function tokenCssOverride(path: string, valueType: number, value: number | string): string {
  if (typeof value === "string") {
    return `'${value.replace(/\\/g, "\\\\").replace(/'/g, "\\'")}'`;
  }
  const v = tokenCssValue(valueType, value);
  return isLengthToken(path) && valueType === VAL_F32 ? v + "px" : v;
}

export interface DesignTokenSink {
  setToken(path: string, valueType: number, value: number | string): void;
}

/**
 * Applies token overrides to a managed `<style data-pathland-tokens>` element:
 * base tokens as `:root` rules, `dark.*` tokens inside
 * `@media (prefers-color-scheme: dark)`. The element is appended to `<head>`
 * after the SSR built-in style block, so equal-specificity rules win in order.
 */
export function createTokenSink(): DesignTokenSink {
  const base = new Map<string, string>();
  const dark = new Map<string, string>();
  let styleEl: HTMLStyleElement | null = null;

  function render(): void {
    const rules: string[] = [];
    for (const [name, value] of base) {
      rules.push(`:root{${name}:${value};}`);
    }
    if (dark.size > 0) {
      const darkRules: string[] = [];
      for (const [name, value] of dark) {
        darkRules.push(`:root{${name}:${value};}`);
      }
      rules.push(`@media (prefers-color-scheme: dark){${darkRules.join("")}}`);
    }
    if (!styleEl) {
      styleEl = document.createElement("style");
      styleEl.setAttribute("data-pathland-tokens", "");
      document.head.appendChild(styleEl);
    }
    styleEl.textContent = rules.join("");
  }

  return {
    setToken(path, valueType, value) {
      const name = tokenToCssVar(path);
      const cssValue = tokenCssOverride(path, valueType, value);
      (isDarkToken(path) ? dark : base).set(name, cssValue);
      render();
    },
  };
}

/**
 * Handle a STYLE `SET_DESIGN_TOKEN` opcode (spec/OPCODE.md design-token system):
 * `A = arenaRef (token path)`, `B = valueType (u8, low byte)`, `C = value`. For
 * a `STRING` value type, `C` is an arenaRef to the value string, resolved here.
 */
export function applyDesignToken(op: Opcode, strings: Uint8Array, sink: DesignTokenSink): void {
  const path = readString(strings, op.a);
  const valueType = op.b & 0xff;
  const value: number | string = valueType === VAL_STRING ? readString(strings, op.c) : op.c;
  sink.setToken(path, valueType, value);
}

/** Convenience: apply every SET_DESIGN_TOKEN opcode in a batch. */
export function applyDesignTokens(batch: Batch, sink: DesignTokenSink): void {
  for (const op of batch.opcodes) {
    if (op.category === 0x02 /* STYLE */ && op.command === 0x02 /* SET_DESIGN_TOKEN */) {
      applyDesignToken(op, batch.strings, sink);
    }
  }
}