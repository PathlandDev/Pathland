package com.pathland.demo.kitchensink;

import com.pathland.view.Button;
import com.pathland.view.DefaultLabelStyle;
import com.pathland.view.Icon;
import com.pathland.view.IconName;
import com.pathland.view.IconOnlyLabelStyle;
import com.pathland.view.Label;
import com.pathland.view.LabelStyleMod;
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
        return new SectionCard("Label · title + icon, three labelStyles",
                VStack.of(
                        Label.of("Settings", Icon.of(IconName.SETTINGS)),
                        Label.of("Save changes", Icon.of(IconName.SAVE))
                                .with(LabelStyleMod.of(TitleOnlyLabelStyle.INSTANCE)),
                        Label.of("Wi-Fi").with(LabelStyleMod.of(IconOnlyLabelStyle.INSTANCE)),
                        Label.of("Cloud", Icon.of(IconName.CLOUD))
                                .with(LabelStyleMod.of(DefaultLabelStyle.INSTANCE)),
                        Label.of(status.signal(), Icon.of(IconName.INFO)),
                        Button.of("Toggle status",
                                () -> status.set(status.get().equals("Ready") ? "Saving…" : "Ready"))
                ).with(Padding.of(4))
        );
    }
}