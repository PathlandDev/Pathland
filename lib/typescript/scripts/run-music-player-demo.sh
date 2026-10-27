#!/usr/bin/env bash
# Run the TypeScript music-player demo: server-owned player state, wasm SSR,
# WebSocket deltas, and app-driven media. → http://127.0.0.1:4318
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

corepack pnpm install
corepack pnpm server:build:all
exec corepack pnpm --filter @pathland/example-music-player start
