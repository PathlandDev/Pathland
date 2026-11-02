package dev.pathland.quarkus.java.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/** Deployment marker for the Pathland Java DSL extension. */
public final class PathlandJavaProcessor {

    private static final String FEATURE = "pathland-java";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
