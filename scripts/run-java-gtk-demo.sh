#!/usr/bin/env bash
# Run the Java + GTK4 desktop demo: the shared demo views (SplitNavDemo) under
# the native GTK renderer, over the libpathland_core shared ring.
#
# macOS must run GTK on the main thread (-XstartOnFirstThread); Linux needs no flag.
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

build_gtk_dylibs
java_install

case "$(uname -s)" in
  Darwin)  EXT=".dylib"; MAINTHREAD="-XstartOnFirstThread" ;;
  Linux)   EXT=".so";    MAINTHREAD="" ;;
  MINGW*|MSYS*|CYGWIN*) EXT=".dll"; MAINTHREAD="" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

cd "$ROOT/lib/java/pathland-gtk-demo"

CPFILE="$(mktemp)"
trap 'rm -f "$CPFILE"' EXIT
mvn -q dependency:build-classpath -Dmdep.outputFile="$CPFILE"
CP="target/classes:$(cat "$CPFILE")"

exec java $MAINTHREAD \
  -Dpathland.core.lib="$ROOT/lib/rust/target/debug/libpathland_core$EXT" \
  -Dpathland.gtk.lib="$ROOT/lib/rust/target/debug/libpathland_gtk$EXT" \
  -cp "$CP" com.pathland.demo.gtk.GtkHost