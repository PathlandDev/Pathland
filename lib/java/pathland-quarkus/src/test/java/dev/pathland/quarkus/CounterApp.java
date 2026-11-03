package dev.pathland.quarkus;

import dev.pathland.server.PathlandApp;
import dev.pathland.view.core.View;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A Java CDI bean mounting a Kotlin-authored view: the host is Java, the view is
 * not. Node 1 is the button and node 2 its `n=<count>` label (see KotlinFixtures).
 */
@ApplicationScoped
public class CounterApp implements PathlandApp {

    @Override
    public View newRoot() {
        return KotlinFixtures.counterProbe();
    }
}
