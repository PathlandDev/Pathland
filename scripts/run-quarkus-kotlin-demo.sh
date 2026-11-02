#!/usr/bin/env bash
# Run the Pathland Kotlin Quarkus demo (SSR + WebSocket deltas, Kotlin DSL).
# → http://localhost:8080
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

build_rust_html
java_install

cd "$ROOT/lib/java/pathland-quarkus-kotlin-demo"
exec mvn quarkus:dev
