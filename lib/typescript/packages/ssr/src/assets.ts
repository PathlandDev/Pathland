import { readFileSync } from "node:fs";
import { resolve } from "node:path";

export const BUNDLE_MANIFEST = "dom-renderer.current";
export const FALLBACK_BUNDLE = "dom-renderer.js";

/** Resolve the content-hashed DOM-client bundle name from an asset directory. */
export function resolveBundleName(assetDir?: string): string {
  if (!assetDir) return FALLBACK_BUNDLE;
  try {
    const name = readFileSync(resolve(assetDir, BUNDLE_MANIFEST), "utf8").trim();
    return isBundleName(name) ? name : FALLBACK_BUNDLE;
  } catch {
    return FALLBACK_BUNDLE;
  }
}

function isBundleName(name: string): boolean {
  return /^[A-Za-z0-9._-]+\.js$/.test(name);
}
