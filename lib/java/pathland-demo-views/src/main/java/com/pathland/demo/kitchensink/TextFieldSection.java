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
        return new SectionCard("Text · TextField + TextEditor",
                VStack.children(
                        TextField.with(t -> t.placeholder("Your name").text(name.signal())),
                        TextEditor.with(t -> t.text(name.signal())).modifiers(Frame.with(f -> f.width(240).height(64).alignment(Alignment.CENTER))),
                        Text.with(t -> t.text(greeting)).modifiers(Padding.with(p -> p.uniform(4)))
                ).modifiers(Padding.with(p -> p.uniform(4)))
        );
    }
}