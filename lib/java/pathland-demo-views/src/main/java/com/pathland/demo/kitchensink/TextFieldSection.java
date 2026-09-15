package com.pathland.demo.kitchensink;

import com.pathland.view.Alignment;
import com.pathland.view.Text;
import com.pathland.view.TextEditor;
import com.pathland.view.TextField;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.state.State;
import com.pathland.view.FrameMod;
import com.pathland.view.Padding;


/**
 * Text-input section: a single-line {@code TextField} and a multi-line
 * {@code TextEditor}, both bound to one persisted {@code State<String>}, plus a
 * computed greeting.
 */
public final class TextFieldSection implements View {

    State<String> name = new State<>("", "name");
    Signal<String> greeting = Signals.computed(() ->
            "Hello, " + (name.get().isEmpty() ? "stranger" : name.get()) + "!");

    @Override
    public View body() {
        return new SectionCard("Text · TextField + TextEditor",
                VStack.of(
                        TextField.of("Your name", name.signal()),
                        TextEditor.of(name.signal()).modifier(FrameMod.of(240, 64, Alignment.CENTER)),
                        Text.of(greeting).modifier(Padding.of(4))
                ).modifier(Padding.of(4))
        );
    }
}