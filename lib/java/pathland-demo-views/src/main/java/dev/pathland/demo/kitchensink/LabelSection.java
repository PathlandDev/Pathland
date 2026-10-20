package dev.pathland.demo.kitchensink;

import dev.pathland.view.Button;
import dev.pathland.view.DefaultLabelStyle;
import dev.pathland.view.Icon;
import dev.pathland.view.IconName;
import dev.pathland.view.IconOnlyLabelStyle;
import dev.pathland.view.Label;
import dev.pathland.view.Padding;
import dev.pathland.view.TitleOnlyLabelStyle;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import dev.pathland.view.state.State;
import static dev.pathland.view.Modifiers.*;


/**
 * Label section: the {@link Label} composite (title + icon) in its three
 * {@code labelStyle} modes, plus a reactive title bound to a button. Icons are
 * semantic ({@link Icon}) — each renderer maps them to its native icon set.
 */
public final class LabelSection implements View {

    State<String> status = new State<>("Ready", "label-status");

    @Override
    public View body() {
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        Label.make(l -> l.title("Settings").icon(Icon.make(IconName.SETTINGS))),
                        Label.make(l -> l.title("Save changes").icon(Icon.make(IconName.SAVE)))
                                .modifiers(TitleOnlyLabelStyle.INSTANCE),
                        Label.make(l -> l.title("Wi-Fi")).modifiers(IconOnlyLabelStyle.INSTANCE),
                        Label.make(l -> l.title("Cloud").icon(Icon.make(IconName.CLOUD)))
                                .modifiers(DefaultLabelStyle.INSTANCE),
                        Label.make(l -> l.title(status.signal()).icon(Icon.make(IconName.INFO))),
                        Button.make(b -> b.title("Toggle status")
                                .action(() -> status.set(status.get().equals("Ready") ? "Saving…" : "Ready"))));
        return SectionCard.make("Label · title + icon, three labelStyles").children(content);
    }
}