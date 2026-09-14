#!/usr/bin/env bash
# Run the Pathland Spring Boot demo (SSR + WebSocket deltas).
# → http://localhost:8080
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

build_rust_html
java_install

cd "$ROOT/lib/java/pathland-spring-boot-demo"
mvn -q package -DskipTests
exec java -jar target/pathland-spring-boot-demo-0.1.0.jar