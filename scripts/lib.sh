#!/usr/bin/env bash
# Shared helpers for the Pathland demo scripts. Source this file; it defines
# $ROOT (the repository root) and the prerequisite-build helpers each demo uses.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Build the Rust HTML renderer cdylib (embedded in the Java pathland-render-html jar).
build_rust_html() {
  (cd "$ROOT/lib/rust" && cargo build -p pathland-render-html)
}

# Build the Rust dylibs the desktop demos link: the core ring (libpathland_core)
# and the GTK4 renderer (libpathland_gtk).
build_gtk_dylibs() {
  (cd "$ROOT/lib/rust" && cargo build -p pathland-core-capi -p pathland-render-gtk)
}

# Install the Java reactor (everything except the two web demos, which need a
# newer JDK to run; they are not required to launch the other demos).
java_install() {
  (cd "$ROOT/lib/java" && mvn -q install -DskipTests \
    -pl '!pathland-quarkus-demo,!pathland-spring-boot-demo')
}