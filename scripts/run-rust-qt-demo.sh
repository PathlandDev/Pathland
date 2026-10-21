#!/usr/bin/env bash
# Run the Rust Qt Quick desktop demo (native, zero-copy shared ring; no browser).
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

cd "$ROOT/lib/rust"
# Point the Qt renderer's build script at Homebrew's qmake if it isn't on PATH.
if command -v qmake6 >/dev/null 2>&1; then
  :
elif [ -x /opt/homebrew/opt/qtbase/bin/qmake ]; then
  export QMAKE=/opt/homebrew/opt/qtbase/bin/qmake
fi
exec cargo run -p pathland-render-qt-demo