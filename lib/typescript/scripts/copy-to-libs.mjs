// Copy the built bundle into the framework libraries' static resource dirs so
// the Spring Boot starter and the Quarkus extension ship the DOM client
// themselves — a consuming application no longer has to bundle it. The bundle
// lives under the reserved framework prefix `/_pathland/` (spec).
//
// The served filename carries a sha-256 content hash (`dom-renderer-<hash>.js`)
// so a new bundle always changes the URL — the scripts are served with
// `immutable` cache headers, and a fresh name is the only thing that busts the
// cache. A tiny pointer manifest `dom-renderer.current` is written next to the
// copies so the SSR layer emits the right <script src> without re-hashing:
//
//   dom-renderer-<sha256-12>.js
//   sha384-<base64>
//
// The second line is the Subresource Integrity digest for the script tag. Stale
// `dom-renderer-*.js` (and the pre-hash fixed `dom-renderer.js`) are pruned so
// the dirs never accumulate.
import { createHash } from "node:crypto";
import { copyFileSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = resolve(root, "dist/pathland-dom-renderer.js");
const bundle = readFileSync(source);
const hash = createHash("sha256").update(bundle).digest("hex").slice(0, 12);
const integrity = "sha384-" + createHash("sha384").update(bundle).digest("base64");
const name = `dom-renderer-${hash}.js`;
const manifest = "dom-renderer.current";

// Spring Boot serves classpath:/static/**; Quarkus serves META-INF/resources/**.
// Both locations are shipped by the respective framework library, so every
// consumer gets the same artifact with no application-side setup.
const targets = [
  resolve(root, "../java/pathland-quarkus/src/main/resources/META-INF/resources/_pathland"),
  resolve(root, "../java/pathland-spring-boot-starter/src/main/resources/static/_pathland"),
];

for (const dir of targets) {
  mkdirSync(dir, { recursive: true });

  for (const entry of readdirSync(dir)) {
    if (entry === name || entry === manifest || entry === "sw.js") continue;
    if (!entry.startsWith("dom-renderer-") && entry !== "dom-renderer.js") continue;
    rmSync(join(dir, entry));
    console.log(`pruned stale ${entry} <- ${dir.replace(root, "…")}/`);
  }

  const dest = resolve(dir, name);
  copyFileSync(source, dest);
  console.log(`copied -> ${dest.replace(root, "…")}`);

  writeFileSync(resolve(dir, manifest), name + "\n" + integrity + "\n");
  console.log(`wrote   -> ${resolve(dir, manifest).replace(root, "…")}`);

  // The per-window-id service worker (issue #155) is copied verbatim under a STABLE
  // name: the browser re-fetches it to byte-compare for updates, so it must NOT be
  // content-hashed (unlike the bundle) and is served `no-cache`.
  copyFileSync(resolve(root, "sw.js"), resolve(dir, "sw.js"));
  console.log(`copied -> ${resolve(dir, "sw.js").replace(root, "…")}`);
}
