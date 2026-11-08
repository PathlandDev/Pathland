package dev.pathland.view.core.protocol

/**
 * The bundled, target-neutral protocol manifest (`spec/protocol.tsv`): categories,
 * components, commands, properties, events, and value types. Ships as a resource in
 * this jar so JVM tooling (the Quarkus Dev UI protocol page) can browse the protocol
 * without a Rust build.
 *
 * A test compares [rawLines] to the repository's `spec/protocol.tsv` so the bundled
 * copy cannot drift.
 */
object ProtocolCatalog {

    /** One manifest row. `group` may be empty for top-level categories. */
    data class Row(
        val kind: String,
        val group: String,
        val name: String,
        val value: String,
    )

    /** Non-comment, non-blank manifest lines, in file order. Empty when the resource is absent. */
    @JvmStatic
    fun rawLines(): List<String> {
        val stream = ProtocolCatalog::class.java.getResourceAsStream("/dev/pathland/protocol.tsv")
            ?: return emptyList()
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && !trimmed.startsWith("#")
            }.toList()
        }
    }

    /** Parsed manifest rows, in file order. */
    @JvmStatic
    fun rows(): List<Row> = rawLines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 4) {
            null
        } else {
            Row(parts[0], parts[1], parts[2], parts[3])
        }
    }
}
