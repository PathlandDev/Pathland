package com.pathland.demo.kitchensink;

import com.pathland.demo.DemoTheme;
import com.pathland.view.Alignment;
import com.pathland.view.Color;
import com.pathland.view.HStack;
import com.pathland.view.Rectangle;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.Background;
import com.pathland.view.FrameMod;
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
                VStack.of(
                        Text.of("color.primary — accent text")
                                .modifier(ForegroundStyle.of(Color.token("color.primary"))),
                        Text.of("control.accent — control accent")
                                .modifier(ForegroundStyle.of(Color.token("control.accent"))),
                        HStack.of(
                                Rectangle.of().modifiers(
                                        FrameMod.of(120, 60, Alignment.CENTER),
                                        Background.of(Color.token("color.surface"))),
                                Rectangle.of().modifiers(
                                        FrameMod.of(120, 60, Alignment.CENTER),
                                        Background.of(Color.token("dark.color.surface")))
                        ).modifier(Padding.of(4)),
                        Text.of("control.background — a token-referenced control fill")
                                .modifiers(
                                        Padding.of(8),
                                        Background.of(Color.token("control.background")))
                ).modifier(Padding.of(4))
        );
    }
}