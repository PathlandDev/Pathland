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
import static com.pathland.view.Modifiers.*;


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
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        Text.make("color.primary — accent text")
                                .modifiers(foregroundStyle(Color.token("color.primary"))),
                        Text.make("control.accent — control accent")
                                .modifiers(foregroundStyle(Color.token("control.accent"))),
                        HStack.make() .modifiers(padding(4))
                                .children(
Rectangle.make().modifiers(frame(f -> f.width(120).height(60).alignment(Alignment.CENTER)))
                                                .modifiers(background(Color.token("color.surface"))),
Rectangle.make().modifiers(frame(f -> f.width(120).height(60).alignment(Alignment.CENTER)))
                                                .modifiers(background(Color.token("dark.color.surface")))),
                        Text.make("control.background — a token-referenced control fill")
                                .modifiers(
                                        padding(8),
                                        background(Color.token("control.background"))));
        return SectionCard.make("Theme · design tokens + SET_DESIGN_TOKEN overrides").children(content);
    }
}