package com.pathland.server;

import com.pathland.view.Button;
import com.pathland.view.Environment;
import com.pathland.view.Text;
import com.pathland.view.View;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.StateStore;
import com.pathland.view.transport.EnvironmentData;
import com.pathland.view.transport.Event;
import com.pathland.view.transport.FrameCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 100 sessions driven concurrently through the registry — the Scheduler/actor stress
 * guard. Sessions are driven on the shared actor while SSR-style requests construct
 * throwaway sessions on request threads (the exact cross-thread {@code Scheduler}
 * access the thread-safety fix addresses). Every session must reach exactly its own
 * click count, with no lost effects, no cross-session values, and no exceptions.
 */
class RegistryConcurrencyTest {

    private static final int SESSIONS = 100;
    private static final StateStore STORE = new InMemoryStateStore();

    @Test
    void hundredSessionsAreIsolatedUnderConcurrentLoadAndSsr() throws Exception {
        PathlandRegistry registry = new PathlandRegistry(RegistryConcurrencyTest::counterApp, STORE);

        // Open 100 sessions, each with its own recording connection + state scope.
        List<RecordingConnection> conns = new CopyOnWriteArrayList<>();
        int[] clicksPerSession = new int[SESSIONS];
        for (int i = 0; i < SESSIONS; i++) {
            clicksPerSession[i] = (i % 10) + 1; // distinct counts -> strong isolation signal
            RecordingConnection conn = new RecordingConnection();
            conns.add(conn);
            registry.open("ui-" + i, "w-" + i, conn);
            registry.environment("ui-" + i, EnvironmentData.of("/"));
        }

        // Concurrently: dispatch each session's clicks, AND run SSR-style requests
        // (each constructs a throwaway session on its request thread, touching the
        // global Scheduler while the actor flushes live sessions' effects).
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < SESSIONS; i++) {
            final int s = i;
            tasks.add(pool.submit(() -> {
                for (int c = 0; c < clicksPerSession[s]; c++) {
                    registry.dispatch("ui-" + s,
                            FrameCodec.encodeEvents(List.of(Event.pointerUp(1, 0, 0))));
                }
            }));
        }
        for (int t = 0; t < 8; t++) {
            tasks.add(pool.submit(() -> {
                for (int r = 0; r < 10; r++) {
                    assertNotNull(registry.renderHtml("/"), "SSR renders");
                }
            }));
        }
        for (Future<?> f : tasks) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        // Each session's label must reach EXACTLY its own count — no lost effects,
        // no cross-session values, no corruption from the concurrent SSR flushes.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        for (int i = 0; i < SESSIONS; i++) {
            RecordingConnection conn = conns.get(i);
            String target = "n=" + clicksPerSession[i];
            while (!conn.hasText(target) && System.nanoTime() < deadline) {
                sleep(10);
            }
            List<String> texts = conn.setTextValues();
            assertTrue(conn.hasText(target),
                    "session " + i + " reached its own count " + target + ": " + texts);
            int last = -1;
            for (String t : texts) {
                int v = Integer.parseInt(t.substring(2));
                assertTrue(v >= last && v <= clicksPerSession[i],
                        "session " + i + " label sequence is monotonic + bounded: " + texts);
                last = v;
            }
            assertTrue(!conn.hasText("n=" + (clicksPerSession[i] + 1)),
                    "session " + i + " never saw a foreign value: " + texts);
        }
        registry.shutdown();
    }

    /** A counter button: node 1 = Button, node 2 = Text label {@code n=<count>}. */
    private static View counterApp() {
        WritableSignal<Integer> count = Signals.signal(0);
        return Button.of(Text.of(Signals.computed(() -> "n=" + count.get())),
                () -> count.update(i -> i + 1));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A connection that records the decoded SET_TEXT values it received. */
    private static final class RecordingConnection implements PathlandConnection {
        private final List<byte[]> recorded = new CopyOnWriteArrayList<>();

        @Override
        public void send(byte[] bytes) {
            recorded.add(bytes);
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        List<String> setTextValues() {
            List<String> values = new ArrayList<>();
            for (byte[] bytes : recorded) {
                try {
                    Frame frame = FrameCodec.decodeFrame(bytes);
                    for (Opcode op : frame.opcodes()) {
                        if (op.category() == 0x02 && op.command() == 0x03) { // PARAMETER::SET_TEXT
                            values.add(frame.stringAt(op.b()));
                        }
                    }
                } catch (RuntimeException ignored) {
                    // a partial/undecodable batch — keep scanning
                }
            }
            return values;
        }

        boolean hasText(String text) {
            return setTextValues().contains(text);
        }
    }
}