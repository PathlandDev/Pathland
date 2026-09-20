# syntax=docker/dockerfile:1
# Multi-stage image for the Pathland Quarkus SSR demo (server-side HTML + /ws deltas).
# Build context is the repository root — the renderer jar embeds the Rust cdylib and
# the demo module needs the Java reactor sources:
#
#   docker build -f lib/java/pathland-quarkus-demo/Dockerfile -t pathland-quarkus-demo .
#
# See docs/DEPLOY.md.

# --- Stage 1: the Rust HTML renderer cdylib (embedded into the Java jar) ---
FROM rust:1-slim AS rust
WORKDIR /src
COPY lib/rust ./lib/rust
RUN cargo build --manifest-path lib/rust/Cargo.toml -p pathland-render-html

# --- Stage 2: the Java reactor + the Quarkus app (the renderer jar embeds the dylib) ---
FROM maven:3.9-eclipse-temurin-17 AS java
WORKDIR /src
COPY --from=rust /src/lib/rust/target/debug/libpathland_render_html.so \
     lib/rust/target/debug/libpathland_render_html.so
COPY lib/java ./lib/java
RUN mvn -q -B -f lib/java/pom.xml -pl pathland-quarkus-demo -am package -Dmaven.test.skip=true

# --- Stage 3: runtime (JRE only) ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=java /src/lib/java/pathland-quarkus-demo/target/quarkus-app/ /app/
EXPOSE 8080
ENV QUARKUS_HTTP_PORT=8080
ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-jar", "/app/quarkus-run.jar"]