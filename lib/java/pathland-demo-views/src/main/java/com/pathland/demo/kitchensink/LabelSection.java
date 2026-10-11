package com.pathland.demo.kitchensink;

import com.pathland.view.Button;
import com.pathland.view.DefaultLabelStyle;
import com.pathland.view.Icon;
import com.pathland.view.IconName;
import com.pathland.view.IconOnlyLabelStyle;
import com.pathland.view.Label;
import com.pathland.view.Padding;
import com.pathland.view.TitleOnlyLabelStyle;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.state.State;


/**
 * Label section: the {@link Label} composite (title + icon) in its three
 * {@code labelStyle} modes, plus a reactive title bound to a button. Icons are
 * semantic ({@link Icon}) — each renderer maps them to its native icon set.
 */
public final class LabelSection implements View {

    State<String> status = new State<>("Ready", "label-status");

    @Override
    public View body() {
        View content = VStack
                .modifiers(Padding.with(4))
                .children(
                        Label.with(l -> l.title("Settings").icon(Icon.with(IconName.SETTINGS))),
                        Label.with(l -> l.title("Save changes").icon(Icon.with(IconName.SAVE)))
                                .modifiers(TitleOnlyLabelStyle.INSTANCE),
                        Label.with(l -> l.title("Wi-Fi")).modifiers(IconOnlyLabelStyle.INSTANCE),
                        Label.with(l -> l.title("Cloud").icon(Icon.with(IconName.CLOUD)))
                                .modifiers(DefaultLabelStyle.INSTANCE),
                        Label.with(l -> l.title(status.signal()).icon(Icon.with(IconName.INFO))),
                        Button.with(b -> b.title("Toggle status")
                                .action(() -> status.set(status.get().equals("Ready") ? "Saving…" : "Ready"))));
        return SectionCard.with("Label · title + icon, three labelStyles").children(content);
    }
}