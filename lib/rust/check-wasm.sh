#!/usr/bin/env bash
# Verify the no_std / wasm32 direction guard: `pathland-core`, `pathland-view`,
# and `pathland-markdown` must stay `#![no_std]` + `alloc` and compile for
# `wasm32-unknown-unknown`.
#
# This is the guarantee that the protocol core can still target a browser /
# embedded / remote projection later (goal #15, Quarkus SSR + WebSocket). The
# desktop path (`pathland-view-native`) is the only consumer today, but the core
# must not become desktop-only.
#
# Usage: ./check-wasm.sh
#
# Requires the wasm32 target: `rustup target add wasm32-unknown-unknown`.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export PATH="$HOME/.cargo/bin:$PATH"

cd "$REPO_ROOT/lib/rust"

echo "==> Building pathland-core + pathland-view + pathland-markdown for wasm32-unknown-unknown"
cargo build -p pathland-core -p pathland-view -p pathland-markdown --target wasm32-unknown-unknown

echo "OK: core + view + markdown build for wasm32-unknown-unknown (no_std preserved)."