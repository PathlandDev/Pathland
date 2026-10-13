package dev.pathland.demo.kitchensink;

import dev.pathland.view.Frame;
import dev.pathland.demo.DemoTheme;
import dev.pathland.view.Alignment;
import dev.pathland.view.Color;
import dev.pathland.view.HStack;
import dev.pathland.view.Rectangle;
import dev.pathland.view.Text;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import dev.pathland.view.Background;
import dev.pathland.view.Padding;
import dev.pathland.view.ForegroundStyle;
import static dev.pathland.view.Modifiers.*;


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