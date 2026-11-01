package dev.pathland.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Minimal deployment module for the DSL-neutral Pathland Quarkus extension.
 *
 * <p>The runtime module carries the CDI beans and HTTP/WebSocket endpoints. This
 * build step keeps the extension visible to Quarkus until a DSL-specific build step
 * needs to be added.
 */
public final class PathlandProcessor {

    private static final String FEATURE = "pathland";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
