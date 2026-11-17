package dev.pathland.server;

import dev.pathland.core.transport.Event;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibleRangeCoalescerTest {

    @Test
    void dispatchesOnlyTheLatestRangePerTarget() throws Exception {
        List<Event> seen = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);
        VisibleRangeCoalescer coalescer = new VisibleRangeCoalescer(100, (sessionId, event) -> {
            seen.add(event);
            delivered.countDown();
        });

        for (int i = 0; i < 20; i++) {
            coalescer.offer("s1", Event.visibleRange(7, i, 1));
        }

        assertTrue(delivered.await(2, TimeUnit.SECONDS), "a coalesced range is dispatched");
        Thread.sleep(100);
        assertEquals(1, seen.size(), "intermediate ranges are replaced, not dispatched");
        assertEquals(19, seen.get(0).visibleFirstIndex(), "the latest range wins");
        coalescer.close();
    }

    @Test
    void rangesForDifferentSessionsAndTargetsAreIndependent() throws Exception {
        List<Event> seen = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(3);
        VisibleRangeCoalescer coalescer = new VisibleRangeCoalescer(100, (sessionId, event) -> {
            seen.add(event);
            delivered.countDown();
        });

        coalescer.offer("a", Event.visibleRange(7, 1, 2));
        coalescer.offer("a", Event.visibleRange(7, 3, 4));
        coalescer.offer("a", Event.visibleRange(8, 5, 6));
        coalescer.offer("b", Event.visibleRange(7, 7, 8));

        assertTrue(delivered.await(2, TimeUnit.SECONDS), "one range per session/target is dispatched");
        Thread.sleep(100);
        assertEquals(3, seen.size());
        assertTrue(seen.stream().anyMatch(e -> e.target() == 7 && e.visibleFirstIndex() == 3));
        assertTrue(seen.stream().anyMatch(e -> e.target() == 8 && e.visibleFirstIndex() == 5));
        assertTrue(seen.stream().anyMatch(e -> e.target() == 7 && e.visibleFirstIndex() == 7));
        coalescer.close();
    }

    @Test
    void closeDropsPendingRanges() throws Exception {
        List<Event> seen = new CopyOnWriteArrayList<>();
        VisibleRangeCoalescer coalescer =
                new VisibleRangeCoalescer(500, (sessionId, event) -> seen.add(event));

        coalescer.offer("s1", Event.visibleRange(7, 1, 2));
        coalescer.close();
        Thread.sleep(100);

        assertEquals(0, seen.size(), "closing the coalescer drops pending work");
    }
}
