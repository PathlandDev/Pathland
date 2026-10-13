# Releasing Pathland

Publishing a **GitHub Release** (tagged `vX.Y.Z`) runs
`.github/workflows/release.yml`, which sets the version from the tag, builds and
publishes every artifact, attaches the built files to the Release, and commits
the version back to `main`:

| Ecosystem | Artifacts | Registry |
|-----------|-----------|----------|
| Rust | `pathland-core`, `pathland-engine`, `pathland-view`, `pathland-core-transport`, `pathland-render-html` | crates.io |
| Java | `dev.pathland:*` libraries (incl. the renderer's native libs for windows-x86_64, linux-x86_64, linux-aarch64, macos-aarch64) | Maven Central |

## Versioning (tag-driven)

**The tag drives the version** — no pre-bump commit is needed:

- The workflow writes `X.Y.Z` (from the tag `vX.Y.Z`) into
  `lib/rust/Cargo.toml` (`[workspace.package] version` + the intra-workspace
  path-dep pins, via `cargo set-version --workspace`) and
  `lib/java/pom.xml` (via `mvn versions:set`) **in CI**, then publishes.
- A `bump` job then commits that version back to `main`, so `main` always
  reflects the latest release.

Tags must be `v<major>.<minor>.<patch>` (an optional pre-release suffix is
allowed); the `version` job fails on anything else. Releases are immutable per
version — pick a new tag to re-release.

## One-time setup

Repository secrets (Settings → Secrets and variables → Actions):

- `CARGO_REGISTRY_TOKEN` — a crates.io API token with publish scope
  (https://crates.io/settings/tokens).
- `GPG_PRIVATE_KEY` — an armored GPG private key whose public key is on a
  keyserver (Central verifies the signatures).
- `GPG_PASSPHRASE` — that key's passphrase.
- `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` — a Sonatype Central Portal **user
  token** (https://central.sonatype.com/account).

Also: `main` must be pushable by the workflow's `GITHUB_TOKEN` (the `bump` job).
If `main` is protected, allow the Actions bot, or the bump step fails (after a
successful publish).

## Release

1. Create a **GitHub Release** on `main` tagged `vX.Y.Z` (publish it).
2. The workflow runs:
   - `version` — derive + validate `X.Y.Z` from the tag.
   - `rust` — set the version, test the publishable crates, publish them to
     crates.io in dependency order, upload the `.crate` files.
   - `java-natives` — build `libpathland_render_html` for windows-x86_64,
     linux-x86_64, linux-aarch64, macos-aarch64.
    - `java` — set the version, stage the natives into the renderer jar, run
      `mvn -P release deploy` to Maven Central (auto-published after validation).
    - `bump` — commit the version back to `main`.
    - `release-assets` — attach the `.crate` files, the Java main jars, and the
      four native libs to the GitHub Release.

The Java deploy runs on **Maven 3.9.9** (pinned in the workflow): Maven 3.10
writes a `maven-metadata-local.xml` beside each artifact in the Central staging,
which the Portal rejects ("content that does NOT have a .pom file"). The demos
are **excluded from the release reactor** (`-pl '!pathland-demo-views,!pathland-gtk-demo,!pathland-quarkus-demo,!pathland-spring-boot-demo'`)
— `central-publishing-maven-plugin` bundles every reactor module, ignoring
`maven.deploy.skip`, and the demos must not be published.

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
