package dev.pathland.view.engine

/** A host-side sink for DATE_CHANGED events. */
fun interface DateInput {
    fun accept(days: Int, millisOfDay: Int)
}
