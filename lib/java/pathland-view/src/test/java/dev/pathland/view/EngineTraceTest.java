package dev.pathland.view;

import dev.pathland.view.core.Environment;
import dev.pathland.view.core.View;
import dev.pathland.view.core.signal.Signals;
import dev.pathland.view.core.signal.WritableSignal;
import dev.pathland.view.core.transport.FrameCodec;
import dev.pathland.view.core.transport.FrameOpcodeSink;
import dev.pathland.view.core.transport.ProtocolFrame;
import dev.pathland.view.engine.Emitter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Cross-language engine traces: the same scenarios are run through the JVM
 * emitter and compared byte-for-byte with self-contained PLPL frames emitted by
 * the canonical Rust engine (`pathland-engine-golden`).
 *
 * <p>These tests cover engine behavior — mount structure, signal deltas, and
 * zero-op unchanged recomputes — not just the opcode layout covered by
 * {@code spec/CONFORMANCE.md}.
 */
class EngineTraceTest {

    @Test
    void mountTextStackMatchesTheRustEngine() throws IOException {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);

        View root = VStack.make(v -> v.spacing(8))
                .children(Text.make("Hello"));
        emitter.mount(root, Environment.DEFAULT);

        assertFrame("mount_text_stack", "000-mount", sink.frame());
    }

    @Test
    void signalTextDeltaMatchesTheRustEngine() throws IOException {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<String> text = Signals.signal("Hello");

        View root = VStack.children(Text.make(t -> t.text(text)));
        emitter.mount(root, Environment.DEFAULT);
        assertFrame("signal_text_delta", "000-mount", sink.frame());

        text.set("World");
        assertFrame("signal_text_delta", "001-set-text", sink.frame());
    }

    @Test
    void propertySignalDeltaMatchesTheRustEngine() throws IOException {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Float> spacing = Signals.signal(8f);

        View root = VStack.make(v -> v.spacing(spacing))
                .children(Text.make("Hello"));
        emitter.mount(root, Environment.DEFAULT);
        assertFrame("property_signal_delta", "000-mount", sink.frame());

        spacing.set(12f);
        assertFrame("property_signal_delta", "001-set-spacing", sink.frame());
    }

    private static void assertFrame(String scenario, String step, ProtocolFrame frame)
            throws IOException {
        Path file = traceRoot().resolve(scenario).resolve(step + ".plpl.hex");
        assertArrayEquals(readHex(file), FrameCodec.encodeFrame(frame),
                scenario + "/" + step);
    }

    private static byte[] readHex(Path file) throws IOException {
        StringBuilder hex = new StringBuilder();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                hex.append(line.strip());
            }
        }
        return HexFormat.of().parseHex(hex.toString());
    }

    private static Path traceRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("spec/engine-traces");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "could not locate spec/engine-traces from " + System.getProperty("user.dir"));
    }
}
