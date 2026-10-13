# Releasing Pathland

The project ships two independently versioned artifact sets, both at the same
version (`0.1.0` today):

| Ecosystem | Artifacts | Registry | Tag | Workflow |
|-----------|-----------|----------|-----|----------|
| Rust | `pathland-core`, `pathland-engine`, `pathland-view`, `pathland-core-transport`, `pathland-render-html` | crates.io | `rust-v*` | `.github/workflows/release-rust.yml` |
| Java | `org.pathland:*` libraries | Maven Central | `java-v*` | `.github/workflows/release-java.yml` |

Both are immutable per version — bump the version before re-releasing.

## Version bump

The version lives in exactly two places; keep them in lockstep:

- Rust: `lib/rust/Cargo.toml` → `[workspace.package] version`
- Java: `lib/java/pom.xml` → `<version>`

## Rust — crates.io

Publishable crates (in dependency order):

```
pathland-core → pathland-engine → pathland-view → pathland-core-transport → pathland-render-html
```

(`pathland-view-native`, `pathland-core-capi`, `pathland-render-gtk`,
`pathland-render-gtk-demo`, `pathland-html-golden`, `pathland-ts-codegen` are
**not** published — C-ABI cdylibs, the GTK renderer, and dev tools.)

### One-time setup

- Add the repository secret `CARGO_REGISTRY_TOKEN` (a crates.io API token with
  publish scope, from https://crates.io/settings/tokens).

### Release

1. Bump `[workspace.package] version` (and the Java version) and commit.
2. Push a tag: `git tag rust-v0.1.0 && git push origin rust-v0.1.0`.
3. `.github/workflows/release-rust.yml` publishes the crates in order. It is
   **idempotent**: a crate+version already on crates.io is skipped, so a re-run
   after a partial failure is safe. (The first crate — `pathland-core` — must
   land before the dependents can be published; the workflow sleeps between
   crates for index propagation.)

### Local pre-flight

`cargo publish --dry-run -p pathland-core` validates the leaf crate. The
dependent crates can only be dry-run **after** their dependencies are on
crates.io, so they are validated by the ordered CI publish.

## Java — Maven Central

_Pending — see the Maven Central phase: parent POM metadata + a `release`
profile (`central-publishing-maven-plugin` + GPG + sources/javadoc), per-platform
native embedding in `pathland-render-html`, and `.github/workflows/release-java.yml`
on a `java-v*` tag._
