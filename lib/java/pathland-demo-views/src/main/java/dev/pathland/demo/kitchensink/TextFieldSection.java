package dev.pathland.demo.kitchensink;

import dev.pathland.view.Frame;
import dev.pathland.view.Alignment;
import dev.pathland.view.Text;
import dev.pathland.view.TextEditor;
import dev.pathland.view.TextField;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import static dev.pathland.view.signal.Signals.*;
import dev.pathland.view.state.State;
import dev.pathland.view.Padding;
import static dev.pathland.view.Modifiers.*;


/**
 * Text-input section: a single-line {@code TextField} and a multi-line
 * {@code TextEditor}, both bound to one persisted {@code State<String>}, plus a
 * computed greeting.
 */
public final class TextFieldSection implements View {

    State<String> name = new State<>("", "name");

    @Override
    public View body() {
        var greeting = computed(() ->
                "Hello, " + (name.get().isEmpty() ? "stranger" : name.get()) + "!");
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        TextField.make(t -> t.placeholder("Your name").text(name.signal())),
                        TextEditor.make(t -> t.text(name.signal())).modifiers(
                                frame(f -> f.width(240).height(64).alignment(Alignment.CENTER))),
                        Text.make(greeting).modifiers(
                                padding(4)));
        return SectionCard.make("Text · TextField + TextEditor").children(content);
    }
}