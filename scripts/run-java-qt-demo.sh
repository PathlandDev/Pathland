#!/usr/bin/env bash
# Run the Java + Qt Quick desktop demo: the shared demo views (SplitNavDemo,
# incl. native navigation) under the Qt renderer, over the libpathland_core
# shared ring.
#
# macOS must run Qt on the main thread (-XstartOnFirstThread); Linux needs no flag.
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

# Point the Qt renderer's build script at Homebrew's qmake if it isn't on PATH.
if command -v qmake6 >/dev/null 2>&1; then
  :
elif [ -x /opt/homebrew/opt/qtbase/bin/qmake ]; then
  export QMAKE=/opt/homebrew/opt/qtbase/bin/qmake
fi

build_qt_dylibs
java_install

case "$(uname -s)" in
  Darwin)  EXT=".dylib"; MAINTHREAD="-XstartOnFirstThread" ;;
  Linux)   EXT=".so";    MAINTHREAD="" ;;
  MINGW*|MSYS*|CYGWIN*) EXT=".dll"; MAINTHREAD="" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

cd "$ROOT/lib/java/pathland-qt-demo"

CPFILE="$(mktemp)"
trap 'rm -f "$CPFILE"' EXIT
mvn -q dependency:build-classpath -Dmdep.outputFile="$CPFILE"
CP="target/classes:$(cat "$CPFILE")"

exec java $MAINTHREAD \
  -Dpathland.core.lib="$ROOT/lib/rust/target/debug/libpathland_core$EXT" \
  -Dpathland.qt.lib="$ROOT/lib/rust/target/debug/libpathland_qt$EXT" \
  -cp "$CP" com.pathland.demo.qt.QtHost