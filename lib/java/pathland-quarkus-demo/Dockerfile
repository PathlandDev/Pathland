# syntax=docker/dockerfile:1
# Multi-stage image for the Pathland Quarkus SSR demo (server-side HTML + /ws deltas).
# Build context is the repository root. The Java path is pure JVM (the HTML
# renderer is a Java port), so the native executable needs no Rust toolchain and
# embeds no native library:
#
#   docker buildx build --platform linux/amd64 \
#     -f lib/java/pathland-quarkus-demo/Dockerfile -t pathland-quarkus-demo .
#
# Quarkus 3.39 requires GraalVM/Mandrel 25+. Mandrel is the Quarkus-recommended
# GraalVM distribution; the builder image already contains `native-image`.
# The builder image does not ship Maven, so `lib/java/mvnw` is used.

# --- Stage 1: native build with Mandrel ---
FROM quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 AS build

USER root
WORKDIR /src
# The render-html module copies the generated base design from the Rust crate's
# committed assets; no Rust build is needed.
COPY lib/rust/crates/pathland-render-html/assets ./lib/rust/crates/pathland-render-html/assets
COPY --chown=quarkus:quarkus lib/java ./lib/java
RUN chmod +x lib/java/mvnw

USER quarkus
# Include the extension's deployment module in the reactor: the runtime module's
# quarkus-extension-maven-plugin validates its dependencies against it, and it is
# not a Maven dependency of the demo (so -am alone would omit it).
RUN --mount=type=cache,target=/home/quarkus/.m2,uid=1001,gid=1001 \
    cd lib/java && ./mvnw -q -B \
      -pl pathland-quarkus-demo,pathland-quarkus-deployment \
      -am package -Dnative -Dmaven.test.skip=true \
      -Dquarkus.native.native-image-xmx=4g

# --- Stage 2: runtime (native executable, no JVM) ---
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0

WORKDIR /work/
COPY --from=build /src/lib/java/pathland-quarkus-demo/target/*-runner /work/application
RUN chmod 775 /work /work/application \
    && chown -R 1001 /work \
    && chmod -R "g+rwX" /work \
    && chown -R 1001:root /work

EXPOSE 8080
ENV QUARKUS_HTTP_PORT=8080
USER 1001
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
