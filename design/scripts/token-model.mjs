// The canonical token model for the built-in HTML base design.
//
// Tokens are authored as flat DTCG maps (`<dot.path>.tokens.json`) because the
// Pathland catalog contains paths that are simultaneously a leaf and a group —
// e.g. `control.border` (a color) and `control.border.width` (a dimension).
// Nested DTCG groups cannot represent both, so keys carry their full dotted
// path and Style Dictionary's custom formats consume this module directly.
//
// Layers:
//   base      internal literals (palette, scales) — compiled into literals, never emitted
//   semantic  the protocol catalog's semantic tokens (spec/TOKENS.md) — emitted
//   component per-component tokens — emitted
//
// Dark values live on `$extensions.pathland.dark`. An emitted token references
// another emitted token by CSS variable so that target's own dark layer wins;
// a base reference resolves to its literal. The forced-dark family
// (`--pl-dark-*`) mirrors the dark layer for explicit `dark.` token references.

import { readFileSync, readdirSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const DESIGN_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const VAR_PREFIX = "--pl-";
const FORCED_DARK_VAR_PREFIX = "--pl-dark-";
const REF_RE = /^\{([^{}]+)\}$/;

/** The CSS custom property for a token path (`.`/invalid → `-`; case preserved). */
export function tokenToCssVar(path) {
  return VAR_PREFIX + path.replace(/[^A-Za-z0-9_-]/g, "-");
}

/** The forced-dark CSS custom property for a token path (`--pl-dark-<path>`). */
export function tokenToForcedDarkCssVar(path) {
  return FORCED_DARK_VAR_PREFIX + path.replace(/[^A-Za-z0-9_-]/g, "-");
}

/** The reference target inside `{…}`, or `null` when the value is a literal. */
export function refTarget(value) {
  if (typeof value !== "string") return null;
  const match = REF_RE.exec(value.trim());
  return match ? match[1] : null;
}

function layerForFile(relPath) {
  const parts = relPath.split(/[\\/]/);
  const dir = parts[parts.indexOf("tokens") + 1];
  if (dir === "base" || dir === "semantic" || dir === "component") return dir;
  throw new Error(`token file outside tokens/{base,semantic,component}: ${relPath}`);
}

function walkJsonFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walkJsonFiles(full));
    else if (entry.isFile() && entry.name.endsWith(".tokens.json")) out.push(full);
  }
  return out.sort();
}

function flatten(node, prefix, inheritedType, file, out) {
  for (const [key, value] of Object.entries(node)) {
    if (key.startsWith("$")) continue;
    if (value === null || typeof value !== "object") continue;
    const path = prefix ? `${prefix}.${key}` : key;
    if (Object.hasOwn(value, "$value")) {
      const type = value.$type ?? inheritedType;
      if (!type) throw new Error(`token ${path} (${file}) has no $type`);
      out.set(path, {
        path,
        layer: layerForFile(file),
        type,
        value: value.$value,
        dark: value.$extensions?.pathland?.dark,
        extensions: value.$extensions?.pathland ?? {},
        file,
      });
    } else {
      flatten(value, path, value.$type ?? inheritedType, file, out);
    }
  }
}

/**
 * Load every token into a path → token map. A duplicate path is an error.
 */
export function loadTokens(root = DESIGN_ROOT) {
  const tokens = new Map();
  for (const file of walkJsonFiles(join(root, "tokens"))) {
    const json = JSON.parse(readFileSync(file, "utf8"));
    const relFile = relative(root, file);
    const found = new Map();
    flatten(json, "", undefined, relFile, found);
    for (const [path, token] of found) {
      if (tokens.has(path)) {
        throw new Error(
          `duplicate token ${path}: ${tokens.get(path).file} and ${token.file}`,
        );
      }
      tokens.set(path, token);
    }
  }
  return tokens;
}

/** Tokens that are emitted into CSS/manifest (semantic + component). */
export function emittedTokens(tokens = loadTokens()) {
  return [...tokens.values()]
    .filter((token) => token.layer !== "base")
    .sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0));
}

/** All base tokens (internal literals). */
export function baseTokens(tokens = loadTokens()) {
  return [...tokens.values()]
    .filter((token) => token.layer === "base")
    .sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0));
}

/** Resolve a base token to its literal, following any base → base references. */
export function resolveBaseLiteral(path, tokens, stack = []) {
  const token = tokens.get(path);
  if (!token) throw new Error(`unresolved reference {${path}}`);
  if (token.layer !== "base") {
    throw new Error(`expected a base token, got ${token.layer} token ${path}`);
  }
  if (stack.includes(path)) {
    throw new Error(`base reference cycle: ${[...stack, path].join(" → ")}`);
  }
  const target = refTarget(token.value);
  if (target) return resolveBaseLiteral(target, tokens, [...stack, path]);
  return String(token.value);
}

/**
 * The CSS value for one layer of an emitted token.
 * `layer` is `light`, `dark`, or `forced-dark`.
 */
export function cssValueFor(token, layer, tokens) {
  const raw = layer === "light" ? token.value : (token.dark ?? token.value);
  const target = refTarget(raw);
  if (!target) return String(raw);

  const targetToken = tokens.get(target);
  if (!targetToken) throw new Error(`${token.path}: unresolved reference {${target}}`);

  if (targetToken.layer === "base") return resolveBaseLiteral(target, tokens);

  const bare = target.startsWith("dark.") ? target.slice("dark.".length) : target;
  return layer === "forced-dark"
    ? `var(${tokenToForcedDarkCssVar(bare)})`
    : `var(${tokenToCssVar(bare)})`;
}

/** The generated `tokens.css` body (all four token layers). */
export function renderTokensCss(tokens = loadTokens()) {
  const emitted = emittedTokens(tokens);
  const declarations = (layer, nameFor = tokenToCssVar) =>
    emitted.map((token) => `  ${nameFor(token.path)}: ${cssValueFor(token, layer, tokens)};`);

  const blocks = [
    "/* Generated by Style Dictionary from design/tokens. Do not edit. */",
    "",
    ":root {",
    ...declarations("light"),
    "}",
    "",
    "@media (prefers-color-scheme: dark) {",
    "  :root:not([data-pathland-scheme]) {",
    ...declarations("dark").map((line) => `  ${line}`),
    "  }",
    "}",
    "",
    ":root[data-pathland-scheme='dark'] {",
    "  color-scheme: dark;",
    ...declarations("dark"),
    "}",
    "",
    ":root[data-pathland-scheme='light'] {",
    "  color-scheme: light;",
    "}",
    "",
    "/* Forced-dark values: explicit `dark.` references resolve these regardless of scheme. */",
    ":root {",
    ...declarations("forced-dark", tokenToForcedDarkCssVar),
    "}",
    "",
  ];
  return blocks.join("\n");
}

/** Manifest entries for validation and future codegen. */
export function manifestEntries(tokens = loadTokens()) {
  return emittedTokens(tokens).map((token) => ({
    path: token.path,
    layer: token.layer,
    type: token.type,
    cssVar: tokenToCssVar(token.path),
    darkCssVar: tokenToForcedDarkCssVar(token.path),
    internal: token.extensions.internal === true,
  }));
}

/** `manifest.json` body. */
export function renderManifest(tokens = loadTokens()) {
  return `${JSON.stringify(manifestEntries(tokens), null, 2)}\n`;
}
