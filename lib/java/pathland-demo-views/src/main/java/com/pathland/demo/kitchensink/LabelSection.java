package com.pathland.demo.kitchensink;

import com.pathland.view.Button;
import com.pathland.view.Label;
import com.pathland.view.LabelStyle;
import com.pathland.view.LabelStyleMod;
import com.pathland.view.Padding;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.state.State;


/**
 * Label section: the {@link Label} composite (title + icon) in its three
 * {@code labelStyle} modes, plus a reactive title bound to a button. Icon sources
 * are illustrative — the renderer resolves them.
 */
public final class LabelSection implements View {

    State<String> status = new State<>("Ready", "label-status");

    @Override
    public View body() {
        return new SectionCard("Label · title + icon, three labelStyles",
                VStack.of(
                        Label.of("Settings", "/_pathland/assets/icons/settings.svg"),
                        Label.of("Save changes", "/_pathland/assets/icons/save.svg")
                                .modifier(LabelStyleMod.of(LabelStyle.TITLE_ONLY)),
                        Label.of("Wi-Fi").modifier(LabelStyleMod.of(LabelStyle.ICON_ONLY)),
                        Label.of("Cloud", "/_pathland/assets/icons/cloud.svg")
                                .modifier(LabelStyleMod.of(LabelStyle.TITLE_AND_ICON)),
                        Label.of(status.signal(), "/_pathland/assets/icons/status.svg"),
                        Button.of("Toggle status",
                                () -> status.set(status.get().equals("Ready") ? "Saving…" : "Ready"))
                ).modifier(Padding.of(4))
        );
    }
}