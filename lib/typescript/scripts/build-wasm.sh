#!/usr/bin/env bash
# Build the Rust HTML renderer as a wasm module for the TypeScript SSR server.
# The generated .wasm is a build output and is not committed.
set -euo pipefail

# Prefer the rustup toolchain so the wasm32 std target is available even when a
# Homebrew Rust is earlier on PATH.
export PATH="$HOME/.cargo/bin:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
OUT_DIR="$ROOT/packages/ssr/wasm"
OUT="$OUT_DIR/pathland_render_html.wasm"

cd "$REPO_ROOT/lib/rust"
cargo build -p pathland-render-html-wasm --target wasm32-unknown-unknown --release

mkdir -p "$OUT_DIR"
cp "$REPO_ROOT/lib/rust/target/wasm32-unknown-unknown/release/pathland_render_html_wasm.wasm" "$OUT"
echo "wrote $OUT"
