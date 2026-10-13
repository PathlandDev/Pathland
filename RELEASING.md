# Releasing Pathland

Publishing a **GitHub Release** (which creates the tag, e.g. `v0.1.0`) runs
`.github/workflows/release.yml`, which builds and publishes every artifact and
attaches the built files to the Release:

| Ecosystem | Artifacts | Registry |
|-----------|-----------|----------|
| Rust | `pathland-core`, `pathland-engine`, `pathland-view`, `pathland-core-transport`, `pathland-render-html` | crates.io |
| Java | `org.pathland:*` libraries | Maven Central |

Both ecosystems share one version (`0.1.0` today), so a release uses a single
`vX.Y.Z` tag. Releases are immutable per version — bump before re-releasing.

## Version bump

The version lives in exactly two places; keep them in lockstep, and make the
release tag match:

- Rust: `lib/rust/Cargo.toml` → `[workspace.package] version`
- Java: `lib/java/pom.xml` → `<version>`

The workflow's `version-check` job fails early if the tag does not equal both.

## One-time setup

Repository secrets (Settings → Secrets and variables → Actions):

- `CARGO_REGISTRY_TOKEN` — a crates.io API token with publish scope
  (https://crates.io/settings/tokens).
- *(Phase 2)* `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE` — the signing key.
- *(Phase 2)* `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` — the Sonatype Central
  Portal user token.

## Release

1. Bump the version in `lib/rust/Cargo.toml` and `lib/java/pom.xml`, commit, and
   merge to `main`.
2. Create a **GitHub Release** on `main` tagged `vX.Y.Z` (publish it).
3. The workflow runs:
   - `version-check` — tag == Rust version == Java version.
   - `rust` — tests the publishable crates, publishes them to crates.io in
     dependency order, and uploads the `.crate` files as a build artifact.
   - *(Phase 2)* `java-natives` — builds `libpathland_render_html` for
     `windows-x86_64`, `linux-x86_64`, `linux-aarch64`, `macos-aarch64`;
     `java` — `mvn -P release deploy` to Maven Central.
   - `release-assets` — attaches the `.crate` files (+ the Java jars and native
     libs, in Phase 2) to the GitHub Release.

The publish steps are **idempotent**: a crate+version already on crates.io is
skipped, so re-running a partially failed release is safe (re-run the workflow
via `workflow_dispatch`, passing the `tag`).

## Publishable crates (dependency order)

```
pathland-core → pathland-engine → pathland-view → pathland-core-transport → pathland-render-html
```

(`pathland-view-native`, `pathland-core-capi`, `pathland-render-gtk`,
`pathland-render-gtk-demo`, `pathland-html-golden`, `pathland-ts-codegen` are
**not** published — C-ABI cdylibs, the GTK renderer, and dev tools.)

`cargo publish --dry-run -p pathland-core` validates the leaf crate locally; the
dependent crates can only be dry-run once their dependencies are on crates.io, so
the ordered CI publish validates them.
