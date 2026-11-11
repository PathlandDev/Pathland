#!/usr/bin/env bash
# Verify the Maven Central release produced the sources + javadoc jars every
# publishable module needs.
#
# Central rejects a bundle whose entries lack a -sources.jar or -javadoc.jar.
# maven-source-plugin's jar-no-fork only archives the *compile source roots*, so
# a module whose sources live outside src/main/java (e.g. Kotlin in
# src/main/kotlin) silently gets no sources jar unless that directory is
# registered as a source root (build-helper-maven-plugin add-source). Run this
# after:
#
#   (cd lib/java && mvn -P release -Dgpg.skip=true -DskipTests package)
#
# The module list comes from the parent POM's <modules>: under `-P release` the
# default-active `demos` profile is off, so the reactor is exactly those modules.
#
# Usage: scripts/check-release-jars.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_DIR="$ROOT/lib/java"

modules=()
while IFS= read -r m; do
  modules+=("$m")
done < <(
  awk '
    /<modules>/ { inmods = 1; next }
    inmods && /<\/modules>/ { exit }
    inmods && /<module>/ { gsub(/.*<module>|<\/module>.*/, ""); print }
  ' "$JAVA_DIR/pom.xml"
)
if ((${#modules[@]} == 0)); then
  echo "no modules found in $JAVA_DIR/pom.xml" >&2
  exit 1
fi

missing=()
for m in "${modules[@]}"; do
  dir="$JAVA_DIR/$m/target"
  compgen -G "$dir/$m-*-sources.jar" >/dev/null || missing+=("$m sources")
  compgen -G "$dir/$m-*-javadoc.jar" >/dev/null || missing+=("$m javadoc")
done

if ((${#missing[@]})); then
  printf 'missing release jars: %s\n' "${missing[*]}" >&2
  exit 1
fi

# A Kotlin module's sources jar must actually carry the Kotlin sources; an
# empty jar passes Central's presence check but is useless to consumers.
for m in pathland-view-core pathland-native-core; do
  jar="$(ls "$JAVA_DIR/$m/target/$m-"*-sources.jar | head -n 1)"
  kt_count="$(unzip -Z1 "$jar" | grep -c '\.kt$' || true)"
  if [ "$kt_count" -eq 0 ]; then
    echo "$m sources jar has no .kt entries: $jar" >&2
    exit 1
  fi
done

echo "release jars OK: ${#modules[@]} modules (sources + javadoc)"
