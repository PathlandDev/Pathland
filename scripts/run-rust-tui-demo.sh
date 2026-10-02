#!/usr/bin/env bash
# Run the Rust Ratatui (TUI) demo (native, zero-copy shared ring; terminal UI).
# Quit with q, Esc, or Ctrl+C.
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

cd "$ROOT/lib/rust"
exec cargo run -p pathland-render-tui-demo