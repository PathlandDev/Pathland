// Predefined-typography resolution for the DOM renderer.
//
// A `TEXT_STYLE` opcode selects a whole typography from the renderer's design
// system; the renderer owns the concrete size/weight (spec/MODIFIERS.md §2).
// Raw `FONT_SIZE`/`FONT_WEIGHT` modifiers layer on top, so this module tracks
// which elements have an explicit override and never clobbers it when the
// `TEXT_STYLE` value (re)arrives.

/** The last `TEXT_STYLE` code applied per element, if any (also used to resolve
 *  the heading element in `applySemantic`). */
export const textStyleByNode = new WeakMap<HTMLElement, number>();

/** Elements that received an explicit `FONT_SIZE` / `FONT_WEIGHT`. */
const fontSizeExplicit = new WeakSet<HTMLElement>();
const fontWeightExplicit = new WeakSet<HTMLElement>();

/** The renderer-owned default scale: `TEXT_STYLE` code → `[size_px, weight]`. */
const SCALE: ReadonlyArray<readonly [number, number]> = [
  [34, 700], // 0  LargeTitle
  [28, 700], // 1  Title
  [22, 700], // 2  Title2
  [20, 600], // 3  Title3
  [17, 600], // 4  Headline
  [15, 400], // 5  Subheadline
  [15, 400], // 6  Body
  [14, 400], // 7  Callout
  [13, 400], // 8  Footnote
  [12, 400], // 9  Caption
  [11, 400], // 10 Caption2
];

/** The size/weight for a `TEXT_STYLE` code, or null for an unknown code. */
export function textStyleScale(code: number): readonly [number, number] | null {
  return SCALE[code] ?? null;
}

/** Record the style and apply its size/weight unless the element already has an
 *  explicit `FONT_SIZE`/`FONT_WEIGHT` override. */
export function applyTextStyle(el: HTMLElement, code: number): void {
  textStyleByNode.set(el, code);
  const scale = SCALE[code];
  if (!scale) {
    return;
  }
  if (!fontSizeExplicit.has(el)) {
    el.style.fontSize = scale[0] + "px";
  }
  if (!fontWeightExplicit.has(el)) {
    el.style.fontWeight = String(scale[1]);
  }
}

/** Mark that an explicit `FONT_SIZE` was applied (it wins over `TEXT_STYLE`). */
export function markFontSizeExplicit(el: HTMLElement): void {
  fontSizeExplicit.add(el);
}

/** Mark that an explicit `FONT_WEIGHT` was applied (it wins over `TEXT_STYLE`). */
export function markFontWeightExplicit(el: HTMLElement): void {
  fontWeightExplicit.add(el);
}

/** Carry typography state across an element morph (tag retag). */
export function copyTypography(from: HTMLElement, to: HTMLElement): void {
  const style = textStyleByNode.get(from);
  if (style !== undefined) {
    textStyleByNode.set(to, style);
  }
  if (fontSizeExplicit.has(from)) {
    fontSizeExplicit.add(to);
  }
  if (fontWeightExplicit.has(from)) {
    fontWeightExplicit.add(to);
  }
}
