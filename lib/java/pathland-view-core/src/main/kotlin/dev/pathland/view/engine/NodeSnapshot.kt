package dev.pathland.view.engine

/**
 * An immutable, read-only snapshot of one retained node, for diagnostic tooling
 * (the Dev UI tree inspector). Property values are stringified; ids remain numeric
 * so a tool can map them through the protocol catalog.
 */
data class NodeSnapshot(
    val id: Int,
    val component: Int,
    val text: String?,
    val reactiveText: Boolean,
    val properties: Map<Int, String>,
    val children: List<NodeSnapshot>,
)
