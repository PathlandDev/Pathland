package dev.pathland.view.emit;

/**
 * A host-side sink for {@code DATE_CHANGED} events — a {@code DATE_PICKER}'s value.
 * The two-field payload ({@code days} since epoch, {@code millis} of day) does not
 * fit the {@link Consumer}-shaped value inputs, so date controls get a dedicated
 * registry keyed by node id in {@link RenderResult}.
 */
@FunctionalInterface
public interface DateInput {

    /** Route a user-changed date into the control's binding. */
    void accept(int days, int millisOfDay);
}