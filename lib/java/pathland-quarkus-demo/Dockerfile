# syntax=docker/dockerfile:1
# Multi-stage image for the Pathland Quarkus SSR demo (server-side HTML + /ws deltas).
# Build context is the repository root. The Java path is pure JVM (the HTML
# renderer is a Java port), so the image needs no Rust toolchain and the jar
# embeds no native library:
#
#   docker build -f lib/java/pathland-quarkus-demo/Dockerfile -t pathland-quarkus-demo .

# --- Stage 1: the Java reactor + the Quarkus app ---
FROM maven:3.9-eclipse-temurin-17 AS java
WORKDIR /src
# The render-html module copies the generated base design from the Rust crate's
# committed assets; no Rust build is needed.
COPY lib/rust/crates/pathland-render-html/assets ./lib/rust/crates/pathland-render-html/assets
COPY lib/java ./lib/java
# Include the extension's deployment module in the reactor: the runtime module's
# quarkus-extension-maven-plugin validates its dependencies against it, and it is
# not a Maven dependency of the demo (so -am alone would omit it).
RUN mvn -q -B -f lib/java/pom.xml \
    -pl pathland-quarkus-demo,pathland-quarkus-deployment \
    -am package -Dmaven.test.skip=true

# --- Stage 2: runtime (JRE only) ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=java /src/lib/java/pathland-quarkus-demo/target/quarkus-app/ /app/
EXPOSE 8080
ENV QUARKUS_HTTP_PORT=8080
ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-jar", "/app/quarkus-run.jar"]
