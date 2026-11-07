package dev.pathland.spring;

import dev.pathland.server.MountedApp;
import dev.pathland.server.PathlandHost;
import dev.pathland.server.test.WsSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end topic subscriptions over the mounted {@code /topics} app: a publish
 * from a background thread reaches only the session subscribed for that customer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = TopicSubscriptionsTest.TestApp.class)
class TopicSubscriptionsTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        MountedApp topicApp() {
            return MountedApp.of("/topics", KotlinFixtures::customerTopicProbe);
        }
    }

    @Autowired
    PathlandHost host;

    @LocalServerPort
    int port;

    @Test
    void backgroundPublishTargetsOnlyTheMatchingSession() throws Exception {
        assertNotNull(host.registry("/topics"), "the topic test app is mounted");

        String ws = "ws://localhost:" + port + "/topics/_pathland/ws";
        try (WsSession customer42 = WsSession.connect(ws, "w42");
             WsSession customer43 = WsSession.connect(ws, "w43")) {

            customer42.sendEnvironment(1280, 800, "/42").sendResync();
            customer43.sendEnvironment(1280, 800, "/43").sendResync();
            await(customer42, "initial-42");
            await(customer43, "initial-43");

            Thread publisher = new Thread(
                    () -> host.signalHub("/topics")
                            .publish(TopicFixtures.CUSTOMER_NAME, 42L, "Alice"),
                    "topic-publisher");
            publisher.start();
            publisher.join(5_000);

            await(customer42, "Alice");
            Thread.sleep(100);
            assertFalse(customer43.textValues().contains("Alice"),
                    "customer 43 must not receive customer 42's update: " + customer43.textValues());
            assertTrue(customer43.textValues().contains("initial-43"));

            Thread globalPublisher = new Thread(
                    () -> host.signalHub("/topics").publish(TopicFixtures.EVENTS, 7L),
                    "global-topic-publisher");
            globalPublisher.start();
            globalPublisher.join(5_000);

            await(customer42, "events-7");
            await(customer43, "events-7");
        }
    }

    private static void await(WsSession session, String text) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!session.textValues().contains(text) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(session.textValues().contains(text),
                "expected '" + text + "', saw " + session.textValues());
    }
}
