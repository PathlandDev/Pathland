// Copy the built DOM client into the examples' static asset dirs,
// using a content-hashed filename plus a dom-renderer.current pointer manifest.
import { createHash } from "node:crypto";
import { copyFileSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = resolve(root, "dist/pathland-dom-renderer.js");
const targets = [
  resolve(root, "examples/profile-card/public/_pathland"),
  resolve(root, "examples/music-player/public/_pathland"),
];
const manifest = "dom-renderer.current";

const bundle = readFileSync(source);
const hash = createHash("sha256").update(bundle).digest("hex").slice(0, 12);
const name = `dom-renderer-${hash}.js`;

for (const target of targets) {
  mkdirSync(target, { recursive: true });
  for (const entry of readdirSync(target)) {
    if (entry === name || entry === manifest) continue;
    if (!entry.startsWith("dom-renderer-") && entry !== "dom-renderer.js") continue;
    rmSync(join(target, entry));
    console.log(`pruned stale ${entry}`);
  }
  copyFileSync(source, resolve(target, name));
  writeFileSync(resolve(target, manifest), name + "\n");
  console.log(`copied ${name} -> ${target.replace(root + "/", "")}`);
}
