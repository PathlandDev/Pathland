package com.pathland.demo.counter;

import com.pathland.view.Text;
import com.pathland.view.TextField;
import com.pathland.view.VStack;
import com.pathland.view.View;
import static com.pathland.view.signal.Signals.*;
import com.pathland.view.state.State;

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
        return VStack.children(
                TextField.with(t -> t.placeholder("Your name").text(name.signal())),
                Text.with(t -> t.text(nameLabel))
        );
    }
}