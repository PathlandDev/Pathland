#!/usr/bin/env bash
# Sync the generated HTML base design into the Rust renderer crate.
#
# The canonical source lives in design/ (Style Dictionary tokens + hand-authored
# reset/components CSS). `design/build/base.css` is the generated bundle the
# renderer embeds with `include_str!`. Run `npm --prefix design run build` first.
#
# Usage:
#   scripts/sync-design-css.sh           copy the bundle into the crate
#   scripts/sync-design-css.sh --check   fail if the committed crate copy drifted
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/design/build/base.css"
DEST_DIR="$ROOT/lib/rust/crates/pathland-render-html/assets"
DEST="$DEST_DIR/base.css"

check=0
if [[ "${1:-}" == "--check" ]]; then
  check=1
elif [[ $# -gt 0 ]]; then
  echo "usage: $0 [--check]" >&2
  exit 2
fi

if [[ ! -f "$SRC" ]]; then
  echo "sync-design-css: missing $SRC — run 'npm --prefix design run build' first" >&2
  exit 1
fi

if [[ $check -eq 1 ]]; then
  if [[ ! -f "$DEST" ]]; then
    echo "sync-design-css: missing $DEST — run scripts/sync-design-css.sh" >&2
    exit 1
  fi
  if ! diff -u "$DEST" "$SRC"; then
    echo "sync-design-css: crate CSS is stale — run 'npm --prefix design run build && scripts/sync-design-css.sh'" >&2
    exit 1
  fi
  echo "sync-design-css: up to date"
  exit 0
fi

mkdir -p "$DEST_DIR"
cp "$SRC" "$DEST"
echo "sync-design-css: wrote $DEST"
