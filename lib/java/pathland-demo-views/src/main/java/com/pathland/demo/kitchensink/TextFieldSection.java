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
                VStack.of(
                        TextField.of("Your name", name.signal()),
                        TextEditor.of(name.signal()).with(Frame.of(240, 64, Alignment.CENTER)),
                        Text.of(greeting).with(Padding.of(4))
                ).with(Padding.of(4))
        );
    }
}