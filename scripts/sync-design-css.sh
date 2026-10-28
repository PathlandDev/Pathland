#!/usr/bin/env bash
# Sync the generated HTML base design into the Rust renderer crate.
#
# The canonical source lives in design/ (Style Dictionary tokens + hand-authored
# reset/components CSS). `design/build/base.css` is the generated bundle the
# renderer embeds with `include_str!`; `base.css.name` is its content-hashed
# asset name (served by the SSR starters). Run `npm --prefix design run build`
# first.
#
# Usage:
#   scripts/sync-design-css.sh           copy the bundle + name into the crate
#   scripts/sync-design-css.sh --check   fail if the committed crate copy drifted
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$ROOT/design/build"
DEST_DIR="$ROOT/lib/rust/crates/pathland-render-html/assets"
FILES=(base.css base.css.name)

check=0
if [[ "${1:-}" == "--check" ]]; then
  check=1
elif [[ $# -gt 0 ]]; then
  echo "usage: $0 [--check]" >&2
  exit 2
fi

for file in "${FILES[@]}"; do
  if [[ ! -f "$SRC_DIR/$file" ]]; then
    echo "sync-design-css: missing $SRC_DIR/$file — run 'npm --prefix design run build' first" >&2
    exit 1
  fi
done

if [[ $check -eq 1 ]]; then
  for file in "${FILES[@]}"; do
    if [[ ! -f "$DEST_DIR/$file" ]]; then
      echo "sync-design-css: missing $DEST_DIR/$file — run scripts/sync-design-css.sh" >&2
      exit 1
    fi
    if ! diff -u "$DEST_DIR/$file" "$SRC_DIR/$file"; then
      echo "sync-design-css: crate $file is stale — run 'npm --prefix design run build && scripts/sync-design-css.sh'" >&2
      exit 1
    fi
  done
  echo "sync-design-css: up to date"
  exit 0
fi

mkdir -p "$DEST_DIR"
for file in "${FILES[@]}"; do
  cp "$SRC_DIR/$file" "$DEST_DIR/$file"
  echo "sync-design-css: wrote $DEST_DIR/$file"
done
