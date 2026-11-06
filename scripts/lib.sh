#!/usr/bin/env bash
# Shared helpers for the Pathland demo scripts. Source this file; it defines
# $ROOT (the repository root) and the prerequisite-build helpers each demo uses.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Build the Rust dylibs the desktop demos link: the core ring (libpathland_core)
# and the GTK4 renderer (libpathland_gtk).
build_gtk_dylibs() {
  (cd "$ROOT/lib/rust" && cargo build -p pathland-core-capi -p pathland-render-gtk)
}

# Build the Rust dylibs the Qt desktop demo links: the core ring
# (libpathland_core) and the Qt Quick renderer (libpathland_qt).
build_qt_dylibs() {
  (cd "$ROOT/lib/rust" && cargo build -p pathland-core-capi -p pathland-render-qt)
}

# Install the Java reactor (everything except the web demos, which are launched
# separately; they are not required to launch the other demos).
java_install() {
  (cd "$ROOT/lib/java" && mvn -q install -DskipTests \
    -pl '!pathland-quarkus-demo,!pathland-spring-boot-demo')
}