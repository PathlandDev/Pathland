#!/usr/bin/env bash
# Run the TypeScript profile-card demo: server-owned view tree, wasm SSR, and
# WebSocket deltas. → http://127.0.0.1:4317
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

corepack pnpm install
corepack pnpm server:build:all
exec corepack pnpm --filter @pathland/example-profile-card start
