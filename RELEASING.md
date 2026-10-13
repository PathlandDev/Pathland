# Releasing Pathland

Publishing a **GitHub Release** (which creates the tag, e.g. `v0.1.0`) runs
`.github/workflows/release.yml`, which builds and publishes every artifact and
attaches the built files to the Release:

| Ecosystem | Artifacts | Registry |
|-----------|-----------|----------|
| Rust | `pathland-core`, `pathland-engine`, `pathland-view`, `pathland-core-transport`, `pathland-render-html` | crates.io |
| Java | `org.pathland:*` libraries (incl. the renderer's native libs for windows-x86_64, linux-x86_64, linux-aarch64, macos-aarch64) | Maven Central |

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
- `GPG_PRIVATE_KEY` — an armored GPG private key whose public key is on a
  keyserver (Central verifies the signatures).
- `GPG_PASSPHRASE` — that key's passphrase.
- `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` — a Sonatype Central Portal **user
  token** (https://central.sonatype.com/account).

## Release

1. Bump the version in `lib/rust/Cargo.toml` and `lib/java/pom.xml`, commit, and
   merge to `main`.
2. Create a **GitHub Release** on `main` tagged `vX.Y.Z` (publish it).
3. The workflow runs:
   - `version-check` — tag == Rust version == Java version.
   - `rust` — tests the publishable crates, publishes them to crates.io in
     dependency order, and uploads the `.crate` files.
   - `java-natives` — builds `libpathland_render_html` for windows-x86_64,
     linux-x86_64, linux-aarch64, macos-aarch64.
   - `java` — stages the natives into the renderer jar and runs
     `mvn -P release deploy` to Maven Central (auto-published after validation).
   - `release-assets` — attaches the `.crate` files, the Java main jars, and the
     four native libs to the GitHub Release.

The publish steps are **idempotent**: a crate+version already on crates.io is
skipped, so re-running a partially failed release is safe (re-run the workflow
via `workflow_dispatch`, passing the `tag`). Maven Central rejects a re-deploy of
an existing version, so a Java re-run only helps if the Java deploy did not
complete.

## Local pre-flight

Rust — `cargo publish --dry-run -p pathland-core` validates the leaf crate (the
dependent crates can only be dry-run once their dependencies are on crates.io,
so the ordered CI publish validates them).

Java — `mvn -P release verify` builds and signs the sources/javadoc jars without
uploading. It needs GPG installed and a `central` server in `~/.m2/settings.xml`:

```xml
<server>
  <id>central</id>
  <username>TOKEN_USERNAME</username>
  <password>TOKEN_PASSWORD</password>
</server>
```

## Publishable crates (dependency order)

```
pathland-core → pathland-engine → pathland-view → pathland-core-transport → pathland-render-html
```

(`pathland-view-native`, `pathland-core-capi`, `pathland-render-gtk`,
`pathland-render-gtk-demo`, `pathland-html-golden`, `pathland-ts-codegen` are
**not** published — C-ABI cdylibs, the GTK renderer, and dev tools.)

## Published Java modules

`pathland-view`, `pathland-view-processor`, `pathland-render-html`,
`pathland-state-redis`, `pathland-server-core`, `pathland-observability`,
`pathland-test-kit`, `pathland-spring-boot-starter`, `pathland-quarkus-starter`
(plus the `pathland-parent` POM). The demo modules
(`pathland-demo-views`, `pathland-gtk-demo`, `pathland-quarkus-demo`,
`pathland-spring-boot-demo`) set `maven.deploy.skip`.
