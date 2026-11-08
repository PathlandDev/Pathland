package dev.pathland.quarkus.devui;

import dev.pathland.server.PathlandDevTrace;
import dev.pathland.view.core.protocol.Categories;
import dev.pathland.view.core.protocol.Commands;
import dev.pathland.view.core.protocol.Opcode;
import dev.pathland.view.core.transport.Event;
import dev.pathland.view.core.transport.FrameCodec;
import dev.pathland.view.core.transport.ProtocolFrame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A bounded, thread-safe ring of runtime trace entries for the Dev UI. Installed as
 * a {@link PathlandDevTrace} only in local development; the no-op default is used
 * everywhere else, so production carries no trace overhead.
 *
 * <p>Outbound batches are decoded eagerly (via {@link FrameCodec}) so the wire page
 * can show categories, commands, operands, and string payloads without re-decoding
 * on every request. A decode failure is recorded as an entry rather than thrown.
 */
public final class PathlandDevTraceStore implements PathlandDevTrace {

    /** Ring capacity; the oldest entries are dropped. */
    public static final int MAX_ENTRIES = 1_000;

    public enum Kind {
        BATCH,
        EVENTS,
        SSR,
        SESSION_OPEN,
        SESSION_CLOSE
    }

    /** One decoded opcode (raw operands + a best-effort string payload). */
    public record OpcodeView(
            int category,
            int command,
            int flags,
            int a,
            int b,
            int c,
            String text) {
    }

    /** One trace entry. Fields are populated per {@link Kind}; unused fields are zero/null. */
    public record Entry(
            long id,
            long timestampMillis,
            Kind kind,
            String sessionId,
            String mount,
            String route,
            int sequence,
            int frames,
            int opcodes,
            int bytes,
            long nanos,
            List<OpcodeView> decoded,
            List<Event> events,
            String error) {
    }

    /** A page of entries newer than {@code sinceId}, plus the last assigned id. */
    public record Snapshot(List<Entry> entries, long nextId) {
    }

    private final Object lock = new Object();
    private final Deque<Entry> entries = new ArrayDeque<>(MAX_ENTRIES);
    private long nextId = 1;

    @Override
    public void sessionOpened(String sessionId, String mount) {
        add(Kind.SESSION_OPEN, sessionId, mount, null, 0, 0, 0, 0, 0, List.of(), List.of(), null);
    }

    @Override
    public void sessionClosed(String sessionId, String mount) {
        add(Kind.SESSION_CLOSE, sessionId, mount, null, 0, 0, 0, 0, 0, List.of(), List.of(), null);
    }

    @Override
    public void batchSent(String sessionId, String mount, int sequence, int frames, byte[] bytes) {
        List<OpcodeView> decoded = List.of();
        String error = null;
        int opcodes = 0;
        try {
            ProtocolFrame frame = FrameCodec.decodeFrame(bytes);
            opcodes = frame.opcodes().size();
            List<OpcodeView> views = new ArrayList<>(opcodes);
            for (Opcode opcode : frame.opcodes()) {
                views.add(new OpcodeView(
                        opcode.category(),
                        opcode.command(),
                        opcode.flags(),
                        opcode.a(),
                        opcode.b(),
                        opcode.c(),
                        stringPayload(frame, opcode)));
            }
            decoded = List.copyOf(views);
        } catch (RuntimeException e) {
            error = e.getMessage();
        }
        add(Kind.BATCH, sessionId, mount, null, sequence, frames, opcodes, bytes.length, 0,
                decoded, List.of(), error);
    }

    @Override
    public void eventsReceived(String sessionId, String mount, List<Event> events) {
        List<Event> copy = List.copyOf(events);
        add(Kind.EVENTS, sessionId, mount, null, 0, 0, 0, 0, 0,
                List.of(), copy, null);
    }

    @Override
    public void ssrRendered(String mount, String route, long nanos, int bytes) {
        add(Kind.SSR, null, mount, route, 0, 0, 0, bytes, nanos,
                List.of(), List.of(), null);
    }

    /** Entries newer than {@code sinceId}, capped at {@code limit} (newest retained). */
    public Snapshot snapshot(long sinceId, int limit) {
        int max = Math.max(1, Math.min(limit, MAX_ENTRIES));
        synchronized (lock) {
            List<Entry> result = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry.id() > sinceId) {
                    result.add(entry);
                }
            }
            if (result.size() > max) {
                result = new ArrayList<>(result.subList(result.size() - max, result.size()));
            }
            return new Snapshot(List.copyOf(result), nextId - 1);
        }
    }

    /** The last id handed out (0 when nothing was recorded). */
    public long lastId() {
        synchronized (lock) {
            return nextId - 1;
        }
    }

    /** Drop every retained entry (the id sequence keeps increasing). */
    public void clear() {
        synchronized (lock) {
            entries.clear();
        }
    }

    /** The number of retained entries (a test/metrics hook). */
    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    private void add(
            Kind kind,
            String sessionId,
            String mount,
            String route,
            int sequence,
            int frames,
            int opcodes,
            int bytes,
            long nanos,
            List<OpcodeView> decoded,
            List<Event> events,
            String error) {
        synchronized (lock) {
            long id = nextId++;
            entries.addLast(new Entry(
                    id,
                    System.currentTimeMillis(),
                    kind,
                    sessionId,
                    mount,
                    route,
                    sequence,
                    frames,
                    opcodes,
                    bytes,
                    nanos,
                    decoded,
                    events,
                    error));
            while (entries.size() > MAX_ENTRIES) {
                entries.removeFirst();
            }
        }
    }

    /** Decode a string operand for the common text-carrying opcodes. */
    private static String stringPayload(ProtocolFrame frame, Opcode opcode) {
        try {
            if (opcode.category() == Categories.PARAMETER && opcode.command() == Commands.Parameter.SET_TEXT) {
                return frame.stringAt(opcode.b());
            }
        } catch (RuntimeException ignored) {
            // a malformed/absent string offset is shown as no payload
        }
        return null;
    }
}
