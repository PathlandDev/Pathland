#!/usr/bin/env bash
# Verify the crates.io publish set in .github/workflows/release.yml.
#
# Cargo requires every dependency of a published crate (including dev
# dependencies) to be resolvable from the registry. Publishing a crate whose
# path dependency is `publish = false`, or whose dev-dependency is not published
# first, fails late during `cargo publish`. This checks the static dependency
# graph before the release workflow runs.
#
# Usage: scripts/check-release-crates.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$ROOT" <<'PY'
import json
import re
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1])
rust = root / "lib" / "rust"
workflow = root / ".github" / "workflows" / "release.yml"

metadata = json.loads(
    subprocess.check_output(
        ["cargo", "metadata", "--no-deps", "--format-version", "1"],
        cwd=rust,
        text=True,
    )
)
packages = {p["name"]: p for p in metadata["packages"]}

text = workflow.read_text()
match = re.search(r"crates=\(([^)]*)\)", text)
if not match:
    sys.exit("release.yml: could not find the crates=(...) publish list")
listed = match.group(1).split()
if not listed:
    sys.exit("release.yml: the crates publish list is empty")
if len(listed) != len(set(listed)):
    sys.exit("release.yml: duplicate crate in the publish list")

# Dev-only tools that are intentionally never published.
EXCLUDED = {
    "pathland-engine-golden",
    "pathland-html-golden",
    "pathland-markdown-capi",
    "pathland-protocol-manifest",
    "pathland-ts-codegen",
}

# Cargo's metadata marks `publish = false` as an empty list; null means the
# crate is publishable (optionally restricted to explicit registries).
def publishable(pkg):
    publish = pkg.get("publish")
    return publish is None or len(publish) > 0


for name in listed:
    if name not in packages:
        sys.exit(f"release.yml lists unknown workspace crate: {name}")
    if not publishable(packages[name]):
        sys.exit(f"release.yml lists {name}, but it has publish = false")

missing = sorted(
    name
    for name, pkg in packages.items()
    if publishable(pkg) and name not in EXCLUDED and name not in listed
)
if missing:
    sys.exit(
        "publishable crates missing from the release list: " + ", ".join(missing)
    )

index = {name: i for i, name in enumerate(listed)}
for name in listed:
    for dep in packages[name]["dependencies"]:
        # Path dependencies have no source; registry dependencies do.
        if dep.get("source") is not None:
            continue
        if dep["kind"] not in ("normal", "dev", "build"):
            continue
        dep_name = dep["name"]
        if dep.get("req") in (None, "*"):
            sys.exit(
                f"{name} has an unpinned path dependency {dep_name}; "
                "add a version so cargo can publish it"
            )
        if dep_name not in index:
            sys.exit(
                f"{name} depends on {dep_name} ({dep['kind']}), "
                "which is not in the publish list"
            )
        if index[dep_name] >= index[name]:
            sys.exit(
                f"{name} depends on {dep_name} ({dep['kind']}), "
                "which must be published earlier"
            )

print("release crate publish set OK: " + " -> ".join(listed))
PY
