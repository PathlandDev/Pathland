package dev.pathland.view.engine

import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.core.transport.Event
import java.util.function.Consumer

/** Routes raw input events into the app's bindings via a RenderResult's maps. */
class InputDispatcher(
    result: RenderResult,
    private val activePath: WritableSignal<String>,
) {
    private val tapActions = result.tapActions
    private val navigateActions = result.navigateActions
    private val textInputs = result.textInputs
    private val valueInputs = result.valueInputs
    private val editingInputs = result.editingInputs
    private val dateInputs = result.dateInputs
    private val mediaInputs = result.mediaInputs
    private val fitInputs = result.fitInputs
    private val navigateHandler = result.navigateHandler

    /** Dispatch every event in a decoded batch. */
    fun dispatch(events: Iterable<Event>) {
        for (event in events) dispatch(event)
    }

    /** Route a single raw input event into the app's bindings. */
    fun dispatch(event: Event) {
        when {
            event.isPointerUp -> {
                val nav = navigateActions[event.target()]
                if (nav != null) nav.run()
                else tapActions[event.target()]?.run()
            }
            event.isTextChanged -> textInputs[event.target()]?.accept(event.text()!!)
            event.isValueChanged -> valueInputs[event.target()]?.accept(event.value())
            event.isEditingChanged -> editingInputs[event.target()]?.accept(event.b() != 0)
            event.isDateChanged -> dateInputs[event.target()]?.accept(event.days(), event.millisOfDay())
            event.isMediaPlayStateChanged -> mediaInputs[event.target()]?.onPlayStateChanged(event.mediaPlaying())
            event.isMediaTimeUpdated -> mediaInputs[event.target()]?.onTimeUpdated(event.mediaValue())
            event.isMediaEnded -> mediaInputs[event.target()]?.onEnded()
            event.isMediaVolumeChanged -> mediaInputs[event.target()]?.onVolumeChanged(event.mediaValue())
            event.isMediaDurationChanged -> mediaInputs[event.target()]?.onDurationChanged(event.mediaValue())
            event.isFitChanged -> fitInputs[event.target()]?.accept(event.fitIndex())
            event.isNavigate -> {
                if (event.url() != null) {
                    activePath.set(event.url()!!)
                } else {
                    navigateHandler?.accept(event)
                }
            }
        }
    }
}
