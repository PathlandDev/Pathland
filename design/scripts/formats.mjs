// Style Dictionary custom formats for the built-in HTML base design.
//
// The token maps are flat DTCG files (see `token-model.mjs`); these formats
// resolve the base → semantic → component references and emit:
//   - `build/tokens.css`   the four token layers,
//   - `build/manifest.json` the emitted token surface.

import { loadTokens, renderManifest, renderTokensCss } from "./token-model.mjs";

export const pathlandFormats = {
  "pathland/css-tokens": () => renderTokensCss(loadTokens()),
  "pathland/manifest": () => renderManifest(loadTokens()),
};
