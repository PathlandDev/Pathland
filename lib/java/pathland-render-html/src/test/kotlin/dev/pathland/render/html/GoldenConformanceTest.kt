package dev.pathland.render.html

import dev.pathland.view.core.transport.FrameCodec
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Cross-renderer conformance: the Java HTML renderer must reproduce the
 * committed Rust SSR fixtures byte-for-byte (the same contract the TypeScript
 * DOM client asserts).
 */
class GoldenConformanceTest {

    private val fixtures: File =
        File("../../typescript/test/fixtures/ssr").canonicalFile

    @Test
    fun reproducesEveryCommittedFixture() {
        val batches = fixtures.listFiles { file -> file.extension == "plpl" }?.sortedBy { it.name }
        assertTrue(batches != null && batches.isNotEmpty(), "fixtures found at $fixtures")

        val failures = mutableListOf<String>()
        for (batch in batches!!) {
            val name = batch.nameWithoutExtension
            // delta_change is applied to delta_base; delta_result has no batch.
            if (name == "delta_change" || name == "delta_result" || batch.length() == 0L) {
                continue
            }
            val expected = File(fixtures, "$name.html").readText(StandardCharsets.UTF_8)
            val frame = FrameCodec.decodeFrame(batch.readBytes())
            val actual = HtmlRenderer.instance().renderFragment(frame, 1)
            if (actual != expected) {
                failures += "HTML mismatch for $name\n--expected--\n$expected\n--actual--\n$actual"
            }
        }

        // The delta scenario: base + change opcodes share the base string section.
        val base = FrameCodec.decodeFrame(File(fixtures, "delta_base.plpl").readBytes())
        val change = FrameCodec.decodeFrame(File(fixtures, "delta_change.plpl").readBytes())
        val combined = base.copy(opcodes = base.opcodes + change.opcodes)
        val expectedDelta = File(fixtures, "delta_result.html").readText(StandardCharsets.UTF_8)
        val actualDelta = HtmlRenderer.instance().renderFragment(combined, 1)
        if (actualDelta != expectedDelta) {
            failures += "HTML mismatch for delta_result\n--expected--\n$expectedDelta\n--actual--\n$actualDelta"
        }

        assertEquals(emptyList<String>(), failures, failures.joinToString("\n\n"))
    }

    @Test
    fun linkedDocumentMatchesTheHashedStylesheetContract() {
        val frame = FrameCodec.decodeFrame(File(fixtures, "counter.plpl").readBytes())
        val renderer = HtmlRenderer.instance()
        val name = renderer.baseCssName()
        assertTrue(name.matches(Regex("base-[0-9a-f]{12}\\.css")), "hashed name: $name")
        val html = renderer.renderLinked(frame, 1, "/_pathland")
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"/_pathland/$name\">"), html)
        assertTrue(!html.contains("<style>"), "linked document omits inline base CSS")
    }
}
