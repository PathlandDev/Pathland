#!/usr/bin/env bash
# Run the Java + Ratatui (TUI) desktop demo: the shared MusicPlayerView under
# the native TUI renderer, over the libpathland_core shared ring.
#
# Quit with q or Ctrl+C. No window/display needed — run inside any terminal.
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

# Build the Rust dylibs the demo links: the core ring (libpathland_core) and
# the TUI renderer (libpathland_render_tui).
(cd "$ROOT/lib/rust" && cargo build -p pathland-core-capi -p pathland-render-tui)
java_install

case "$(uname -s)" in
  Darwin)  EXT=".dylib" ;;
  Linux)   EXT=".so" ;;
  MINGW*|MSYS*|CYGWIN*) EXT=".dll" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

cd "$ROOT/lib/java/pathland-tui-demo"

CPFILE="$(mktemp)"
trap 'rm -f "$CPFILE"' EXIT
mvn -q dependency:build-classpath -Dmdep.outputFile="$CPFILE"
CP="target/classes:$(cat "$CPFILE")"

exec java \
  -Dpathland.core.lib="$ROOT/lib/rust/target/debug/libpathland_core$EXT" \
  -Dpathland.tui.lib="$ROOT/lib/rust/target/debug/libpathland_render_tui$EXT" \
  -cp "$CP" com.pathland.demo.tui.TuiHost