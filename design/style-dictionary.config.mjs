// Style Dictionary v5 configuration for the built-in HTML base design.
//
// The canonical token maps live in `tokens/{base,semantic,component}` as flat
// DTCG files. They are read by the custom formats in `scripts/formats.mjs`
// rather than Style Dictionary's nested source parser: the Pathland catalog has
// paths that are both a leaf and a group (`control.border` is a color while
// `control.border.width` is a dimension), which nested DTCG trees cannot
// represent. The custom formats still run through Style Dictionary's build, and
// `scripts/check.mjs` enforces layer/reference/catalog/type rules.

import { pathlandFormats } from "./scripts/formats.mjs";

export default {
  // The custom formats own token loading/resolution (see the comment above).
  source: [],
  tokens: {},
  platforms: {
    css: {
      buildPath: "build/",
      files: [
        { destination: "tokens.css", format: "pathland/css-tokens" },
        { destination: "manifest.json", format: "pathland/manifest" },
      ],
    },
  },
  hooks: {
    formats: pathlandFormats,
  },
};
