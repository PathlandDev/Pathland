// Validate the built-in HTML base design:
//   - every Tier-1 protocol catalog token is present;
//   - base → semantic → component layer/reference rules hold;
//   - every emitted token has light, platform-dark, forced-dark-scheme and
//     forced-dark variable values;
//   - every `$type: dimension` token is recognized by the canonical
//     `isLengthToken` rules in pathland-render-html::token_spec;
//   - internal tokens are marked;
//   - no references are unresolved;
//   - the generated build/ files match the current token sources.

import { readFile } from "node:fs/promises";
import { join } from "node:path";
import {
  DESIGN_ROOT,
  baseTokens,
  cssValueFor,
  emittedTokens,
  loadTokens,
  manifestEntries,
  refTarget,
  tokenToCssVar,
  tokenToForcedDarkCssVar,
} from "./token-model.mjs";

const RUST_ROOT = join(DESIGN_ROOT, "..", "lib", "rust");

const failures = [];
function fail(message) {
  failures.push(message);
}

/** Read a `pub const NAME: &[...] = &[ ... ];` block from the Rust token spec. */
function rustBlock(source, name) {
  const re = new RegExp(
    `pub const ${name}\\s*:\\s*&\\[[^=]*=\\s*&\\[([\\s\\S]*?)\\];`,
  );
  const match = re.exec(source);
  if (!match) throw new Error(`could not find ${name} in token_spec.rs`);
  return match[1];
}

function strings(block) {
  return [...block.matchAll(/"([^"]*)"/g)].map((match) => match[1]);
}

function pairs(block) {
  return [...block.matchAll(/\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)/g)].map(
    (match) => [match[1], match[2]],
  );
}

async function lengthRules() {
  const source = await readFile(join(RUST_ROOT, "crates", "pathland-render-html", "src", "token_spec.rs"), "utf8");
  return {
    prefixes: strings(rustBlock(source, "LENGTH_PREFIXES")),
    prefixSuffix: pairs(rustBlock(source, "LENGTH_PREFIX_SUFFIX")),
    exact: strings(rustBlock(source, "LENGTH_EXACT")),
    elevationSuffixes: strings(rustBlock(source, "LENGTH_ELEVATION_SUFFIXES")),
  };
}

function isLengthToken(path, rules) {
  if (rules.prefixes.some((prefix) => path.startsWith(prefix))) return true;
  if (
    rules.prefixSuffix.some(
      ([prefix, suffix]) => path.startsWith(prefix) && path.endsWith(suffix),
    )
  ) {
    return true;
  }
  if (rules.exact.includes(path)) return true;
  return (
    path.startsWith("elevation.") &&
    rules.elevationSuffixes.some((suffix) => path.endsWith(suffix))
  );
}

async function tier1Catalog() {
  const source = await readFile(join(RUST_ROOT, "crates", "pathland-core", "src", "tokens.rs"), "utf8");
  return strings(rustBlock(source, "TIER1_TOKENS"));
}

const COMPONENT_BASE_SCALE = [
  "base.space.",
  "base.radius.",
  "base.border.width.",
  "base.size.",
  "base.font.",
  "base.duration.",
  "base.z.",
];

function count(haystack, needle) {
  return haystack.split(needle).length - 1;
}

function checkReference(owner, value, tokens) {
  const target = refTarget(value);
  if (!target) {
    fail(`${owner}: emitted tokens must reference another token (got literal ${JSON.stringify(value)})`);
    return null;
  }
  const targetToken = tokens.get(target);
  if (!targetToken) {
    fail(`${owner}: unresolved reference {${target}}`);
    return null;
  }
  return targetToken;
}

function allowedTarget(owner, targetToken, token) {
  if (targetToken.layer === "base") {
    if (token.layer === "semantic") return;
    if (token.layer === "component") {
      const scale = COMPONENT_BASE_SCALE.some((prefix) => targetToken.path.startsWith(prefix));
      const justified =
        typeof token.extensions.baseReference === "string" &&
        token.extensions.baseReference.trim().length > 0;
      if (scale || justified) return;
      fail(
        `${owner}: component → base reference {${targetToken.path}} needs a scale path or ` +
          `$extensions.pathland.baseReference justification`,
      );
      return;
    }
  }
  if (token.layer === "semantic") {
    const alias =
      typeof token.extensions.semanticAlias === "string" &&
      token.extensions.semanticAlias.trim().length > 0;
    if (targetToken.layer === "semantic" && alias) return;
    fail(
      `${owner}: semantic tokens must reference base tokens ` +
        `(or a semantic alias with $extensions.pathland.semanticAlias), not ` +
        `${targetToken.layer} ${targetToken.path}`,
    );
    return;
  }
  if (token.layer === "component" && targetToken.layer === "semantic") return;
  fail(`${owner}: component tokens must reference semantic tokens, not ${targetToken.layer} ${targetToken.path}`);
}

const tokens = loadTokens(join(DESIGN_ROOT));
const emitted = emittedTokens(tokens);
const base = baseTokens(tokens);
const byPath = new Map(emitted.map((token) => [token.path, token]));

// --- Layer / reference rules -------------------------------------------------
for (const token of base) {
  if (refTarget(token.value)) {
    fail(`${token.path}: base tokens are literals and must not reference other tokens`);
  }
  if (token.dark !== undefined) {
    fail(`${token.path}: base tokens have no dark extension (they are internal literals)`);
  }
}

for (const token of emitted) {
  const target = checkReference(token.path, token.value, tokens);
  if (target) allowedTarget(token.path, target, token);
  if (token.dark !== undefined) {
    const darkTarget = checkReference(`${token.path} (dark)`, token.dark, tokens);
    if (darkTarget) allowedTarget(`${token.path} (dark)`, darkTarget, token);
  }
}

// --- Catalog coverage --------------------------------------------------------
const catalog = await tier1Catalog();
for (const path of catalog) {
  if (!byPath.has(path)) fail(`missing Tier-1 catalog token ${path}`);
}

// --- Dark / forced-dark values ----------------------------------------------
for (const token of emitted) {
  for (const layer of ["light", "dark", "forced-dark"]) {
    try {
      const value = cssValueFor(token, layer, tokens);
      if (!value) fail(`${token.path}: empty ${layer} value`);
    } catch (error) {
      fail(`${token.path}: ${error.message}`);
    }
  }
}

// --- CSS variable references -------------------------------------------------
const emittedCssVars = new Set(emitted.map((token) => tokenToCssVar(token.path)));
const localCssVars = new Set(["--pl-slider-fill"]);
for (const file of ["html/reset.css", "html/components.css"]) {
  const css = await readFile(join(DESIGN_ROOT, file), "utf8");
  for (const match of css.matchAll(/var\((--pl-[A-Za-z0-9_-]+)/g)) {
    const name = match[1];
    if (!emittedCssVars.has(name) && !localCssVars.has(name)) {
      fail(`${file}: unresolved CSS variable ${name}`);
    }
  }
}

// --- Dimension / length conventions -----------------------------------------
const rules = await lengthRules();
for (const token of emitted) {
  if (token.type === "dimension" && !isLengthToken(token.path, rules)) {
    fail(
      `${token.path}: $type dimension is not recognized by token_spec::isLengthToken ` +
        `(add a length rule to lib/rust/crates/pathland-render-html/src/token_spec.rs)`,
    );
  }
}

// --- Internal tokens ---------------------------------------------------------
for (const token of emitted) {
  if (token.extensions.internal !== undefined && typeof token.extensions.internal !== "boolean") {
    fail(`${token.path}: $extensions.pathland.internal must be a boolean`);
  }
  if (token.type === "duration" && token.extensions.internal !== true) {
    fail(`${token.path}: duration tokens are renderer-internal and must be marked internal`);
  }
  if (
    ["menu.z-index", "icon.size", "icon.offset"].includes(token.path) &&
    token.extensions.internal !== true
  ) {
    fail(`${token.path}: internal token must be marked $extensions.pathland.internal`);
  }
}

// --- Generated build consistency --------------------------------------------
let manifest;
try {
  manifest = JSON.parse(await readFile(join(DESIGN_ROOT, "build", "manifest.json"), "utf8"));
} catch {
  fail("build/manifest.json is missing — run `npm --prefix design run build` first");
}

let tokensCss = "";
try {
  tokensCss = await readFile(join(DESIGN_ROOT, "build", "tokens.css"), "utf8");
} catch {
  fail("build/tokens.css is missing — run `npm --prefix design run build` first");
}

if (manifest) {
  const expected = manifestEntries(tokens);
  if (JSON.stringify(manifest) !== JSON.stringify(expected)) {
    fail("build/manifest.json is stale — run `npm --prefix design run build`");
  }
  const emittedPaths = new Set(manifest.map((entry) => entry.path));
  for (const token of emitted) {
    if (!emittedPaths.has(token.path)) fail(`${token.path}: missing from the manifest`);
  }
  for (const entry of manifest) {
    if (!entry.darkCssVar) fail(`${entry.path}: manifest has no darkCssVar`);
    if (!entry.cssVar.startsWith("--pl-")) fail(`${entry.path}: manifest cssVar must start with --pl-`);
  }
}

if (tokensCss) {
  for (const needle of [
    ":root {",
    "@media (prefers-color-scheme: dark) {",
    ":root:not([data-pathland-scheme]) {",
    ":root[data-pathland-scheme='dark'] {",
    "  color-scheme: dark;",
    ":root[data-pathland-scheme='light'] {",
    "  color-scheme: light;",
    "Forced-dark values",
  ]) {
    if (!tokensCss.includes(needle)) fail(`build/tokens.css is missing the ${needle} layer`);
  }
  for (const token of emitted) {
    const cssVar = tokenToCssVar(token.path);
    const darkVar = tokenToForcedDarkCssVar(token.path);
    if (count(tokensCss, `${cssVar}:`) !== 3) {
      fail(
        `${token.path}: expected 3 light/dark declarations for ${cssVar} ` +
          `(root + platform-dark + forced-dark scheme), found ${count(tokensCss, `${cssVar}:`)}`,
      );
    }
    if (count(tokensCss, `${darkVar}:`) !== 1) {
      fail(`${token.path}: expected exactly one forced-dark declaration for ${darkVar}`);
    }
  }
  for (const token of base) {
    const cssVar = tokenToCssVar(token.path);
    if (tokensCss.includes(`${cssVar}:`)) {
      fail(`${token.path}: base tokens must not be emitted into tokens.css`);
    }
  }
}

if (failures.length > 0) {
  for (const message of failures) console.error(`design check: ${message}`);
  console.error(`design check: ${failures.length} problem(s)`);
  process.exit(1);
}

console.log(
  `design check: ok — ${emitted.length} emitted tokens, ${catalog.length} Tier-1 catalog tokens, ${base.length} base tokens`,
);
