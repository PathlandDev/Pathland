// Copy the built bundle into both demo modules' static resource dirs so the
// Maven jars stay self-contained and both demos serve the SAME artifact. The
// bundle lives under the reserved framework prefix `/_pathland/` (spec).
//
// The served filename carries a sha-256 content hash (`dom-renderer-<hash>.js`)
// so a new bundle always changes the URL — the scripts are served with
// `immutable` cache headers, and a fresh name is the only thing that busts the
// cache. A tiny pointer manifest `dom-renderer.current` (one line, the current
// hashed filename) is written next to the copies so the SSR layer emits the
// right <script src> without re-hashing. Stale `dom-renderer-*.js` (and the
// pre-hash fixed `dom-renderer.js`) are pruned so the dirs never accumulate.
import { createHash } from "node:crypto";
import { copyFileSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = resolve(root, "dist/pathland-dom-renderer.js");
const bundle = readFileSync(source);
const hash = createHash("sha256").update(bundle).digest("hex").slice(0, 12);
const name = `dom-renderer-${hash}.js`;
const manifest = "dom-renderer.current";

const targets = [
  resolve(root, "../java/pathland-quarkus-demo/src/main/resources/META-INF/resources/_pathland"),
  resolve(root, "../java/pathland-spring-boot-demo/src/main/resources/static/_pathland"),
];

for (const dir of targets) {
  mkdirSync(dir, { recursive: true });

  for (const entry of readdirSync(dir)) {
    if (entry === name || entry === manifest) continue;
    if (!entry.startsWith("dom-renderer-") && entry !== "dom-renderer.js") continue;
    rmSync(join(dir, entry));
    console.log(`pruned stale ${entry} <- ${dir.replace(root, "…")}/`);
  }

  const dest = resolve(dir, name);
  copyFileSync(source, dest);
  console.log(`copied -> ${dest.replace(root, "…")}`);

  writeFileSync(resolve(dir, manifest), name + "\n");
  console.log(`wrote   -> ${resolve(dir, manifest).replace(root, "…")}`);
}