package com.pathland.server;

import com.pathland.view.Categories;
import com.pathland.view.Commands;
import com.pathland.view.ValueTypes;
import com.pathland.view.emit.ProtocolFrame;
import com.pathland.view.emit.Opcode;
import com.pathland.view.transport.FrameCodec;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Coalesces session delta frames into a single network batch before sending
 * (the Java analogue of the Rust transport's {@code Batcher}).
 *
 * <p>The emitter produces one small frame per signal change; a continuous input
 * (a slider drag, live text) bursts dozens of tiny WebSocket messages per
 * second. Over a remote/slow link that burst overflows a WebSocket's send
 * queue. This batcher merges the frames — opcodes + a single rebased string
 * section — and flushes them as one batch on a short timer or a byte
 * threshold. Intermediate deltas apply in order, so the last write wins
 * naturally; a drag's values collapse to far fewer messages.
 *
 * <p>Thread-safety: {@link #append} is called on the registry's actor thread;
 * {@link #flush} runs on the batcher's daemon scheduler. Both are synchronized
 * on this instance. The {@code sender} consumer (the connection seam) is
 * invoked on the scheduler thread and must be non-blocking.
 */
public final class DeltaBatcher {

    /** Default flush cadence: ~50 flushes/sec — far below a drag's event rate. */
    public static final long DEFAULT_FLUSH_INTERVAL_MILLIS = 20;
    /** Default max merged batch bytes before an immediate flush. */
    public static final int DEFAULT_MAX_BATCH_BYTES = 16 * 1024;

    private final long flushIntervalMillis;
    private final int maxBatchBytes;
    private final Consumer<byte[]> sender;
    private final PathlandTelemetry telemetry;
    private final String mount;
    private final ScheduledExecutorService scheduler;

    private final List<Opcode> opcodes = new ArrayList<>();
    private final ByteArrayOutputStream strings = new ByteArrayOutputStream();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private int approxBytes;
    /** Per-session monotonic message sequence, stamped on each flushed batch. */
    private int nextSequence = 1;
    /** Emit-pass frames coalesced into the pending batch (metrics only). */
    private int frames;

    public DeltaBatcher(Consumer<byte[]> sender) {
        this(DEFAULT_FLUSH_INTERVAL_MILLIS, DEFAULT_MAX_BATCH_BYTES, sender);
    }

    public DeltaBatcher(long flushIntervalMillis, int maxBatchBytes, Consumer<byte[]> sender) {
        this(flushIntervalMillis, maxBatchBytes, sender, PathlandTelemetry.NOOP, "/");
    }

    public DeltaBatcher(
            Consumer<byte[]> sender, PathlandTelemetry telemetry, String mount) {
        this(DEFAULT_FLUSH_INTERVAL_MILLIS, DEFAULT_MAX_BATCH_BYTES, sender, telemetry, mount);
    }

    public DeltaBatcher(
            long flushIntervalMillis,
            int maxBatchBytes,
            Consumer<byte[]> sender,
            PathlandTelemetry telemetry,
            String mount) {
        this.flushIntervalMillis = flushIntervalMillis;
        this.maxBatchBytes = maxBatchBytes;
        this.sender = sender;
        this.telemetry = telemetry == null ? PathlandTelemetry.NOOP : telemetry;
        this.mount = mount == null ? "/" : mount;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pathland-delta-batcher");
            t.setDaemon(true);
            return t;
        });
    }

    /** Merge a produced frame into the pending batch (no-op for empty frames). */
    public synchronized void append(ProtocolFrame frame) {
        if (frame.isEmpty()) {
            return;
        }
        frames++;
        int base = strings.size();
        for (Opcode op : frame.opcodes()) {
            opcodes.add(rebase(op, base));
        }
        strings.writeBytes(frame.strings());
        approxBytes += frame.opcodes().size() * Opcode.SIZE + frame.strings().length + 4;

        if (approxBytes >= maxBatchBytes) {
            flush();
        } else if (flushScheduled.compareAndSet(false, true)) {
            scheduler.schedule(this::flush, flushIntervalMillis, TimeUnit.MILLISECONDS);
        }
    }

    /** Encode + send everything pending, as one batch. Safe to call anytime.
     *  The {@code sender} runs OUTSIDE the monitor so a blocking/queueing send
     *  can never stall the actor thread's {@link #append} or the flush scheduler. */
    public void flush() {
        byte[] encoded;
        int flushedFrames;
        int flushedOpcodes;
        long nanos;
        synchronized (this) {
            flushScheduled.set(false);
            if (opcodes.isEmpty()) {
                return;
            }
            long start = System.nanoTime();
            ProtocolFrame merged = new ProtocolFrame(List.copyOf(opcodes), strings.toByteArray());
            flushedFrames = frames;
            flushedOpcodes = opcodes.size();
            opcodes.clear();
            strings.reset();
            approxBytes = 0;
            frames = 0;
            // Stamp the batch's per-stream sequence so the client can detect a
            // lost batch (a gap) and recover with META::RESYNC (spec/OPCODE.md
            // §Sequence gap detection). Only non-empty batches consume a number.
            encoded = FrameCodec.encodeFrame(merged, nextSequence++);
            nanos = System.nanoTime() - start;
        }
        telemetry.batchFlushed(mount, flushedFrames, flushedOpcodes, encoded.length, nanos);
        sender.accept(encoded);
    }

    /** Stop the flush scheduler (call when the session closes). */
    public void close() {
        scheduler.shutdownNow();
    }

    /**
     * Rebase a frame's relative string offsets onto the merged section.
     * {@code SET_TEXT}, {@code STRING}/{@code DESIGN_TOKEN}-typed
     * {@code SET_PROPERTY}, and {@code SET_DESIGN_TOKEN} reference the string
     * section by a relative offset (spec/OPCODE.md).
     */
    private static Opcode rebase(Opcode op, int base) {
        int a = op.a();
        int b = op.b();
        int c = op.c();
        if (op.category() == Categories.PARAMETER) {
            int command = op.command();
            if (command == Commands.Parameter.SET_TEXT) {
                b += base;
            } else if (command == Commands.Parameter.SET_PROPERTY) {
                int valueType = b >>> 16;
                // The LIST value type (e.g. a SIZE_THAT_FITS FIT_QUERY threshold
                // table) also references the string section by a relative offset.
                if (valueType == ValueTypes.STRING || valueType == ValueTypes.DESIGN_TOKEN
                        || valueType == ValueTypes.LIST) {
                    c += base;
                }
            } else if (command == Commands.Parameter.SET_DESIGN_TOKEN) {
                a += base;
                if ((b & 0xFF) == ValueTypes.STRING) {
                    c += base;
                }
            }
        }
        return new Opcode(op.category(), op.command(), op.flags(), a, b, c);
    }
}