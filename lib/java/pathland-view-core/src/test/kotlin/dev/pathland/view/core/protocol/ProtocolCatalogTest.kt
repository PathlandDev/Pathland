package dev.pathland.view.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The bundled protocol manifest must stay byte-identical to the generated
 * `spec/protocol.tsv`, and parse into the expected rows for Dev UI tooling.
 */
class ProtocolCatalogTest {

    @Test
    fun bundledManifestMatchesSpec() {
        val spec = File("../../../spec/protocol.tsv")
        assertTrue(spec.exists(), "spec/protocol.tsv must be present when the reactor tests run: ${spec.absolutePath}")
        val expected = spec.readLines().filter { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !trimmed.startsWith("#")
        }
        assertEquals(expected, ProtocolCatalog.rawLines(),
            "the bundled manifest drifted from spec/protocol.tsv — copy the spec file again")
    }

    @Test
    fun parsesTheManifestIntoRows() {
        val rows = ProtocolCatalog.rows()
        assertFalse(rows.isEmpty(), "the manifest resource is present")
        assertTrue(rows.any { it.kind == "component" && it.name == "TEXT" && it.value == "0x0001" })
        assertTrue(rows.any { it.kind == "command" && it.group == "Parameter" && it.name == "SET_TEXT" })
        assertTrue(rows.all { it.kind.isNotEmpty() && it.name.isNotEmpty() && it.value.isNotEmpty() })
    }
}
