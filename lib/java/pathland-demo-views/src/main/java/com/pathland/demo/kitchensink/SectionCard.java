package com.pathland.demo.kitchensink;

import com.pathland.view.Frame;
import com.pathland.view.AccessibilityRole;
import com.pathland.view.Alignment;
import com.pathland.view.Color;
import com.pathland.view.Commands;
import com.pathland.view.Divider;
import com.pathland.view.FontWeight;
import com.pathland.view.Roles;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.Background;
import com.pathland.view.Border;
import com.pathland.view.CornerRadius;
import com.pathland.view.FontSize;
import com.pathland.view.ForegroundStyle;
import com.pathland.view.Padding;


/**
 * A reusable kitchen-sink section card: a titled, bordered, rounded container that
 * hosts one section's controls. Demonstrates composition — a custom {@code View}
 * wrapping arbitrary content with style modifiers.
 */
public final class SectionCard implements View {

    private static final Color TITLE_COLOR = Color.rgb(0x55, 0x64, 0x6D);
    private static final Color BORDER = Color.rgb(0xE2, 0xE8, 0xF0);

    private final String title;
    private final View content;

    public SectionCard(String title, View content) {
        this.title = title;
        this.content = content;
    }

    @Override
    public View body() {
        return VStack.children(
                // A section title is a heading → `<h2>`.
                Text.with(t -> t.text(title)).modifiers(FontSize.with(s -> s.size(14)), FontWeight.SEMIBOLD, ForegroundStyle.with(f -> f.color(TITLE_COLOR)))
                        .modifiers(AccessibilityRole.with(a -> a.role(Roles.HEADER))),
                Divider.modifiers(Padding.with(p -> p.edges(0, 0, 8, 0))),
                content
        ).modifiers(
                Padding.with(p -> p.uniform(16)),
                Background.with(b -> b.color(Color.WHITE)),
                Border.with(b -> b.color(BORDER).width(1).radius(10)),
                CornerRadius.with(c -> c.radius(10)))
                .modifiers(Frame.ofWidth(Commands.Size.FILL));
    }
}