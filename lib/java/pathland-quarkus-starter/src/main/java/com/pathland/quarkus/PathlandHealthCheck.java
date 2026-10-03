package com.pathland.quarkus;

import com.pathland.server.PathlandHost;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness for the Pathland host: UP while the host is running, with the number of
 * live sessions and mounted apps as details. Exposed at {@code /q/health} by
 * {@code quarkus-smallrye-health}. The core host has no downstream dependency of its
 * own (state is app-owned), so this is a liveness-of-the-runtime check; an app can add
 * its own checks for its backends.
 */
@Readiness
@ApplicationScoped
public class PathlandHealthCheck implements HealthCheck {

    @Inject
    PathlandHost host;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("pathland")
                .status(host.isRunning())
                .withData("activeSessions", host.activeSessions())
                .withData("mounts", host.registries().size())
                .build();
    }
}
