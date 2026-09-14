#!/usr/bin/env bash
# Run the Rust GTK4 desktop demo (native, zero-copy shared ring; no browser).
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

cd "$ROOT/lib/rust"
exec cargo run -p pathland-render-gtk-demo