package dev.pathland.view.core.state

import java.util.Optional

/**
 * Optional, development-only enumeration for a [StateStore]. Stores with a
 * non-enumerable backend (e.g. Redis without a scan) simply do not implement it;
 * the Dev UI then reports that inspection is unsupported.
 */
interface DebugStateStore {
    /** Every stored key, in a stable order. */
    fun keys(): List<String>

    /** The raw stored value for [key], or empty when absent. */
    fun get(key: String): Optional<Any>
}
