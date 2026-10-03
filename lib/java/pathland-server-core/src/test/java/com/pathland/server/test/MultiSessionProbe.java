package com.pathland.server.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The shared multi-session WS probe used by both starters' stress tests: opens
 * {@value #SESSIONS} real WebSocket sessions concurrently, drives the full protocol
 * (environment → resync → clicks → pings) and asserts expected behavior:
 *
 * <ul>
 *   <li>every session connects and receives its full resync snapshot;</li>
 *   <li>every session's PING gets a transport-level PONG;</li>
 *   <li>sessions are isolated — each reaches exactly its OWN click count, its label
 *       sequence is monotonic + bounded, and no foreign value ever appears (catches
 *       the shared-actor / Scheduler cross-session corruption);</li>
 *   <li>no session is closed by the peer during the test (no spurious closes/stalls).</li>
 * </ul>
 *
 * The app under test is a counter button: node 1 = Button, node 2 = a Text label
 * {@code n=&lt;count&gt;} (each session's root is a fresh view, so every session starts
 * at {@code n=0}).
 */
public final class MultiSessionProbe {

    public static final int SESSIONS = 100;
    private static final int VIEWPORT_WIDTH = 1280;
    private static final int VIEWPORT_HEIGHT = 800;

    private MultiSessionProbe() {}

    /** Run the probe against a Pathland WS base URL (e.g. {@code ws://host:port/_pathland/ws}). */
    public static void run(String wsBaseUrl) throws Exception {
        int[] clicksPerSession = new int[SESSIONS];
        for (int i = 0; i < SESSIONS; i++) {
            clicksPerSession[i] = (i % 10) + 1; // distinct counts -> strong isolation signal
        }

        List<WsSession> sessions = new ArrayList<>(SESSIONS);
        try {
            // 1. Connect + seed every session (environment then a full-snapshot resync).
            for (int i = 0; i < SESSIONS; i++) {
                WsSession s = WsSession.connect(wsBaseUrl, "w-" + i);
                sessions.add(s);
                s.sendEnvironment(VIEWPORT_WIDTH, VIEWPORT_HEIGHT, "/").sendResync();
            }

            // 2. Every session received its snapshot.
            for (int i = 0; i < SESSIONS; i++) {
                WsSession s = sessions.get(i);
                check(s.awaitFrame(f -> true, 10_000), "session " + i + " received its snapshot");
                check(s.opcodeCount() > 0, "session " + i + " snapshot has opcodes");
                check(!s.wasClosedByPeer(), "session " + i + " not closed by the peer after connect");
            }

            // 3. Concurrently drive every session: its clicks + 5 heartbeat pings.
            ExecutorService pool = Executors.newFixedThreadPool(16);
            try {
                List<Future<?>> tasks = new ArrayList<>();
                for (int i = 0; i < SESSIONS; i++) {
                    final int s = i;
                    tasks.add(pool.submit(() -> {
                        WsSession ws = sessions.get(s);
                        for (int c = 0; c < clicksPerSession[s]; c++) {
                            ws.sendClick(1);
                        }
                        for (int p = 0; p < 5; p++) {
                            ws.sendPing();
                        }
                    }));
                }
                for (Future<?> f : tasks) {
                    f.get(60, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            // 4. Assert every session: its own final count, a monotonic bounded label
            //    sequence, a transport PONG, and no peer-initiated close.
            for (int i = 0; i < SESSIONS; i++) {
                WsSession ws = sessions.get(i);
                String target = "n=" + clicksPerSession[i];
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (!ws.textValues().contains(target) && System.nanoTime() < deadline && ws.isOpen()) {
                    Thread.sleep(10);
                }
                List<String> texts = ws.textValues();
                check(texts.contains(target), "session " + i + " reached " + target + ": " + texts);
                int last = -1;
                for (String t : texts) {
                    int v = Integer.parseInt(t.substring(2));
                    check(v >= last && v <= clicksPerSession[i],
                            "session " + i + " label sequence monotonic + bounded: " + texts);
                    last = v;
                }
                check(ws.sawPong(), "session " + i + " got a transport-level PONG");
                check(!ws.wasClosedByPeer(), "session " + i + " was not closed by the peer");
            }
        } finally {
            for (WsSession s : sessions) {
                s.close();
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}