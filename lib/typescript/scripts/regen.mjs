// Regenerate every artifact that is derived from the canonical Rust sources:
// the DOM renderer's protocol constants + token conventions
// (crates/pathland-ts-codegen) and the cross-renderer SSR golden fixtures
// (crates/pathland-html-golden). Requires a Rust toolchain (lib/rust).
// After running, commit the regenerated files together with the change that
// produced them.
import { execFileSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const rust = resolve(dirname(fileURLToPath(import.meta.url)), "../../rust");

for (const [pkg, args] of [
  ["pathland-ts-codegen", []],
  ["pathland-html-golden", ["--", "--emit"]],
]) {
  execFileSync("cargo", ["run", "-p", pkg, ...args], { cwd: rust, stdio: "inherit" });
}

console.log("Regenerated. Commit the updated constants.ts / generated/ / fixtures together with your change.");