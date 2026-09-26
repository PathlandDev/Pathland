// Copy the built bundle into both demo modules' static resource dirs so the
// Maven jars stay self-contained and both demos serve the SAME artifact. The
// bundle lives under the reserved framework prefix `/_pathland/` (spec).
import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = resolve(root, "dist/pathland-dom-renderer.js");

const targets = [
  resolve(root, "../java/pathland-quarkus-demo/src/main/resources/META-INF/resources/_pathland"),
  resolve(root, "../java/pathland-spring-boot-demo/src/main/resources/static/_pathland"),
];

for (const dir of targets) {
  mkdirSync(dir, { recursive: true });
  const dest = resolve(dir, "dom-renderer.js");
  copyFileSync(source, dest);
  console.log(`copied -> ${dest}`);
}