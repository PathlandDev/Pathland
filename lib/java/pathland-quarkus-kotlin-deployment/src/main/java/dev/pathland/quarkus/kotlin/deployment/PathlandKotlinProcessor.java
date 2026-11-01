package dev.pathland.quarkus.kotlin.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/** Deployment marker for the Pathland Kotlin DSL extension. */
public final class PathlandKotlinProcessor {

    private static final String FEATURE = "pathland-kotlin";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
