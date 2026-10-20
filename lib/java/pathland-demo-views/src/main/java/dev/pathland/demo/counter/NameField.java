package dev.pathland.demo.counter;

import dev.pathland.view.Text;
import dev.pathland.view.TextField;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import static dev.pathland.view.signal.Signals.*;
import dev.pathland.view.state.State;

/**
 * A view component: the name text field plus its reactive label. The {@code name} state is
 * a {@link State} field — the annotation processor wires it to the session's store
 * automatically (keyed {@code "name"}).
 */
public final class NameField implements View {

    State<String> name = new State<>("");

    @Override
    public View body() {
        var nameLabel = computed(() -> "Name: " + name.get());
        return VStack.make()
                .children(
                        TextField.make(t -> t.placeholder("Your name").text(name.signal())),
                        Text.make(nameLabel));
    }
}