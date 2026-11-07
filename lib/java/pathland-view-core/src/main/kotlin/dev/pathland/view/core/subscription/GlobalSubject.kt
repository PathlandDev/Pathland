package dev.pathland.view.core.subscription

/**
 * The singleton subject of an unscoped (global) topic.
 *
 * A global topic is still subscription-targeted: every session that called
 * `topic(topic, initial)` receives updates, and sessions that never subscribed
 * are untouched. The subject is reserved — application subject values must not
 * use it.
 */
object GlobalSubject {

    /** The stable wire token reserved for future cross-node fan-out. */
    const val NAME: String = "@global"

    override fun toString(): String = NAME
}
