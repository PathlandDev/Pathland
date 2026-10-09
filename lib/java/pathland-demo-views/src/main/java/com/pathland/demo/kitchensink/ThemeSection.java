package com.pathland.demo.kitchensink;

import com.pathland.view.Frame;
import com.pathland.demo.DemoTheme;
import com.pathland.view.Alignment;
import com.pathland.view.Color;
import com.pathland.view.HStack;
import com.pathland.view.Rectangle;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.Background;
import com.pathland.view.Padding;
import com.pathland.view.ForegroundStyle;


/**
 * Theme section: demonstrates **design tokens** — {@code Color.token(...)}
 * references that resolve against the active color scheme — plus the global
 * {@code SET_DESIGN_TOKEN} overrides rolled by {@link DemoTheme}. The renderer
 * re-resolves these under {@code prefers-color-scheme: dark} (HTML/JS) or the
 * native GTK dark variant with no re-emission.
 */
public final class ThemeSection implements View {

    @Override
    public View body() {
        return new SectionCard("Theme · design tokens + SET_DESIGN_TOKEN overrides",
                VStack.children(
                        Text.with(t -> t.text("color.primary — accent text"))
                                .modifiers(ForegroundStyle.with(f -> f.color(Color.token("color.primary")))),
                        Text.with(t -> t.text("control.accent — control accent"))
                                .modifiers(ForegroundStyle.with(f -> f.color(Color.token("control.accent")))),
                        HStack.children(
                                Rectangle.modifiers()
                                        .modifiers(Frame.with(f -> f.width(120).height(60).alignment(Alignment.CENTER)))
                                        .modifiers(Background.with(b -> b.color(Color.token("color.surface")))),
                                Rectangle.modifiers()
                                        .modifiers(Frame.with(f -> f.width(120).height(60).alignment(Alignment.CENTER)))
                                        .modifiers(Background.with(b -> b.color(Color.token("dark.color.surface"))))
                        ).modifiers(Padding.with(p -> p.uniform(4))),
                        Text.with(t -> t.text("control.background — a token-referenced control fill"))
                                .modifiers(
                                        Padding.with(p -> p.uniform(8)),
                                        Background.with(b -> b.color(Color.token("control.background"))))
                ).modifiers(Padding.with(p -> p.uniform(4)))
        );
    }
}