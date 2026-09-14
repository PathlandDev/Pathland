#!/usr/bin/env bash
# Run the Pathland Quarkus demo (SSR + WebSocket deltas, dev mode with hot reload).
# → http://localhost:8080
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

build_rust_html
java_install

cd "$ROOT/lib/java/pathland-quarkus-demo"
exec mvn quarkus:dev