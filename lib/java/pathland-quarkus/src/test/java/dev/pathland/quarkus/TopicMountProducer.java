package dev.pathland.quarkus;

import dev.pathland.server.MountedApp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Mounts the topic-subscription test view at {@code /topics}. */
@ApplicationScoped
public class TopicMountProducer {

    @Produces
    @Singleton
    MountedApp topicApp() {
        return MountedApp.of("/topics", KotlinFixtures::customerTopicProbe);
    }
}
