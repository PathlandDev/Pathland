package dev.pathland.view.dsl

import dev.pathland.view.core.Environment
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.core.transport.FrameCodec
import dev.pathland.view.core.transport.FrameOpcodeSink
import dev.pathland.view.core.transport.ProtocolFrame
import dev.pathland.view.engine.Emitter
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Cross-language engine traces: the same scenarios are run through the Kotlin
 * JVM emitter and compared byte-for-byte with the self-contained PLPL frames
 * emitted by the canonical Rust engine.
 */
class EngineTraceTest {

    @Test
    fun mountTextStackMatchesTheRustEngine() {
        val sink = FrameOpcodeSink()
        Emitter(sink).mount(VStack(spacing = 8f) { Text("Hello") }, Environment.DEFAULT)
        assertFrame("mount_text_stack", "000-mount", sink.frame())
    }

    @Test
    fun signalTextDeltaMatchesTheRustEngine() {
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val text = signal("Hello")
        emitter.mount(VStack { Text(text) }, Environment.DEFAULT)
        assertFrame("signal_text_delta", "000-mount", sink.frame())

        text.set("World")
        assertFrame("signal_text_delta", "001-set-text", sink.frame())
    }

    @Test
    fun structuralAddRemoveMatchesTheRustEngine() {
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val items = signal(listOf("a"))
        emitter.mount(ForEach(items) { item, _ -> Text(item) }, Environment.DEFAULT)
        assertFrame("structural_add_remove", "000-mount", sink.frame())

        items.set(listOf("a", "b"))
        assertFrame("structural_add_remove", "001-add-child", sink.frame())

        items.set(listOf("a"))
        assertFrame("structural_add_remove", "002-remove-child", sink.frame())
    }

    @Test
    fun styledTextMountMatchesTheRustEngine() {
        val sink = FrameOpcodeSink()
        Emitter(sink).mount(Text("Hello").fontSize(17f), Environment.DEFAULT)
        assertFrame("styled_text_mount", "000-mount", sink.frame())
    }

    @Test
    fun propertySignalDeltaMatchesTheRustEngine() {
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val spacing: WritableSignal<Float> = signal(8f)
        emitter.mount(VStack(spacing = spacing) { Text("Hello") }, Environment.DEFAULT)
        assertFrame("property_signal_delta", "000-mount", sink.frame())

        spacing.set(12f)
        assertFrame("property_signal_delta", "001-set-spacing", sink.frame())
    }

    private fun assertFrame(scenario: String, step: String, frame: ProtocolFrame) {
        val file = traceRoot().resolve(scenario).resolve("$step.plpl.hex")
        assertArrayEquals(readHex(file), FrameCodec.encodeFrame(frame), "$scenario/$step")
    }

    private fun readHex(file: Path): ByteArray {
        val hex = StringBuilder()
        for (line in Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isNotBlank() && !line.startsWith("#")) hex.append(line.trim())
        }
        return hex.toString().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun traceRoot(): Path {
        var dir: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve("spec/engine-traces")
            if (Files.isDirectory(candidate)) return candidate
            dir = dir.parent
        }
        error("could not locate spec/engine-traces from ${System.getProperty("user.dir")}")
    }
}
