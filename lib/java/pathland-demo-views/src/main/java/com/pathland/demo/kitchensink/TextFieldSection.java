package com.pathland.demo.kitchensink;

import com.pathland.view.Frame;
import com.pathland.view.Alignment;
import com.pathland.view.Text;
import com.pathland.view.TextEditor;
import com.pathland.view.TextField;
import com.pathland.view.VStack;
import com.pathland.view.View;
import static com.pathland.view.signal.Signals.*;
import com.pathland.view.state.State;
import com.pathland.view.Padding;
import static com.pathland.view.Modifiers.*;


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